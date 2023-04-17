package edu.sjsu.cs249.chainreplication;

import edu.sjsu.cs249.chain.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * @author ashish
 */
public class ChainNode {

    HashMap<String, Integer> dataMap;

    boolean isHead;

    boolean isTail;

    ZooManager zooManager;

    int lastSeenXId = -1;

    String successorName;

    String successorHostPort;

    boolean isSuccessorAlive;

    String predecessorHostPort;

    HashMap<Integer, KeyValuePair> pendingMap;

    HashMap<Integer, StreamObserver> diligentObserver;

    long lastSeenZxid = -1;

    int lastSeenAckXid = -1;

    List<String> zNodes;

    Semaphore onePermitLock;

    ManagedChannel updateChannel;
    ManagedChannel ackChannel;

    QueOps predecessorQueOps;

    public ChainNode(ZooManager zooManager, QueOps predecessorQueOps) {
        this.zooManager = zooManager;
        this.dataMap = new HashMap<>();
        this.pendingMap = new HashMap<>();
        this.diligentObserver = new HashMap<>();
        this.isHead = false;
        this.isTail = false;
        this.isSuccessorAlive = false;
        onePermitLock = new Semaphore(1);
        this.predecessorQueOps = predecessorQueOps;
    }


    //A method to be able to see data in all maps
    public void printMap() {
        System.out.println("-----------------------------");
        System.out.println("Map is");
        dataMap.entrySet().forEach(entry -> {
            System.out.println(entry.getKey() + " " + entry.getValue());
        });
        System.out.println("-----------------------------");

        System.out.println("-----------------------------");
        System.out.println(" ObserverMap is");
        diligentObserver.entrySet().forEach(entry -> {
            System.out.println(entry.getKey() + " " + entry.getValue());
        });
        System.out.println("-----------------------------");

        System.out.println(" Pending Map is");
        pendingMap.entrySet().forEach(entry -> {
            System.out.println(entry.getKey() + " " + entry.getValue());
        });
        System.out.println("-----------------------------");
    }


    private Watcher babysitter() {
        return watchedEvent -> {
            System.out.println("BabySitter Service triggered to watch the Children!");
            System.out.println("WatchedEvent: " + watchedEvent.getType() + " on " + watchedEvent.getPath());
            getChildren();
            joinTheChain();
        };
    }

    public void getChildren() {
        try {
            Stat replicaPath = new Stat();
            List<String> children = zooManager.zk.getChildren(zooManager.zooPath, babysitter(), replicaPath);
            lastSeenZxid = replicaPath.getPzxid();
            zNodes = getzNodes(children);
            System.out.println("getChildren Znodes: " + zNodes +
                    ", with lastSeenZxid: " + lastSeenZxid);
        } catch (InterruptedException | KeeperException e) {
            System.out.println("Error getting children with getChildrenInPath()");
        }
    }

    private static List<String> getzNodes(List<String> children) {
        ArrayList<String> res = new ArrayList<>();
        for (String child : children) {
            if (child.contains("replica-")) {
                res.add(child);
            }
        }
        return res;
    }


    /**
     * Triggers first time on initialize.
     * Triggers when there is a change in children path.
     */
    void joinTheChain() {
        List<String> zNodesOrdered = OrderZNodes();
        String myNode = zooManager.zName;

        //check and set if Head or Tail
        isHead = zNodesOrdered.get(0).equals(myNode);
        System.out.println("MyNode : Head Status: " + isHead);
        isTail = zNodesOrdered.get(zNodesOrdered.size() - 1).equals(myNode);
        System.out.println("MyNode : Tail Status: " + isTail);

        hookUpPredecessor(zNodesOrdered);
        hookUpSuccessor(zNodesOrdered);
    }


    // Ordering to figure out myNode's position in the chain
    private List<String> OrderZNodes() {
        return zNodes.stream().sorted(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        }).collect(Collectors.toList());
    }

    public void hookUpPredecessor(List<String> orderedzNodes) {
        System.out.println("Started joining the chain by connecting to predeccsor!");
        if (isHead) {
            System.out.println("Setting head props as I'm head!");
            setHeadProperties();
            return;
        }

        String myChainNode = zooManager.zName;
        //TODO remove if all fine
//                .replace(zooManager.zooPath + "/", "");
//        printMap();
        int myPosition = orderedzNodes.indexOf(myChainNode);
        String predecessorName = orderedzNodes.get(myPosition - 1);

        try {
            String currData = new String(zooManager.zk.getData(zooManager.zooPath + "/" + predecessorName, false, null));
            String currPredecessorHostPort = currData.split("\n")[0];

            if (!currPredecessorHostPort.equals(predecessorHostPort)) {
                String currPredecessorName = currData.split("\n")[1];
                repositionPredecessor(currPredecessorHostPort, currPredecessorName);
            }
        } catch (InterruptedException | KeeperException e) {
            System.out.println("Error getting children with getChildrenInPath()");
        }
    }

    private void repositionPredecessor(String updatedPredecessorHostPort, String updatedPredecessorName) {
        System.out.println("Repositioning Predec as Curr Predecessor does not match old Predecessor! New Predec details : " + updatedPredecessorName + " " + updatedPredecessorName);

        predecessorHostPort = updatedPredecessorHostPort;
        NewSuccessorResponse newSuccessorResponse = sendNewSuccessorRequest(predecessorHostPort);
        long newSuccResponseCode = newSuccessorResponse.getRc();

        if (newSuccResponseCode == -1) {
            //TODO check if we need this? MVP2
            retry();
        } else if (newSuccResponseCode == 0) {
            System.out.println("newSuccResponseCode returned with 0 successfully with lastSeenXId: " + newSuccessorResponse.getLastXid());
            lastSeenXId = newSuccessorResponse.getLastXid();
            System.out.println("Transferring State...");
            for (String key : newSuccessorResponse.getStateMap().keySet()) {
                dataMap.put(key, newSuccessorResponse.getStateMap().get(key));
            }
            transferPendingMap(newSuccessorResponse);
        } else {
            System.out.println("newSuccResponseCode returned with 1 successfully with lastSeenXId: " + newSuccessorResponse.getLastXid());
            lastSeenXId = newSuccessorResponse.getLastXid();
            System.out.println("Transferring only updates!");
            transferPendingMap(newSuccessorResponse);
        }
    }

    private void retry() {
        getChildren();
        joinTheChain();
    }

    private NewSuccessorResponse sendNewSuccessorRequest(String receiverAddr) {
        System.out.println("sendNewSuccessorRequest routed to :" + receiverAddr);
        ackChannel = this.getNewChannel(receiverAddr);
        var stub = ReplicaGrpc.newBlockingStub(ackChannel);
        var newSuccessorRequest = NewSuccessorRequest.newBuilder()
                .setLastZxidSeen(lastSeenZxid)
                .setLastXid(lastSeenXId)
                .setLastAck(lastSeenAckXid)
                .setZnodeName(zooManager.zName).build();
        return stub.newSuccessor(newSuccessorRequest);
    }


    private void setHeadProperties() {
        //If I'm head : shutdown the ack channel and unset predecessor
        if (ackChannel != null) {
            ackChannel.shutdownNow();
        }
        predecessorHostPort = "";
    }

    private void transferPendingMap(NewSuccessorResponse newSuccessorResponse) {
        System.out.println("Transferring pending Map");
        List<UpdateRequest> pendingMapPredec = newSuccessorResponse.getSentList();

        for (UpdateRequest updateRequest : pendingMapPredec) {
            String key = updateRequest.getKey();
            int newValue = updateRequest.getNewValue();
            int xid = updateRequest.getXid();
            pendingMap.put(xid, new KeyValuePair(key, newValue));
            System.out.println("Added from PredecSentList : xid: " + xid + ", key: " + key + ", value: " + newValue);
        }

        // TODO check MVP2
        if (isTail && pendingMap.size() > 0) {
            System.out.println("Acking back all the req's from the tail!");
            Map<Integer, KeyValuePair> iterMap = new HashMap<Integer, KeyValuePair>(pendingMap);
            for (int xid : iterMap.keySet()) {
                sendAcks(xid);
            }
        }
    }

    public void sendAcks(int xid) {
        System.out.println("Sending Ack to: " + predecessorHostPort);
        lastSeenAckXid = xid;
        pendingMap.remove(xid);
        System.out.println(" Acked and Removed XID form pendingMap: " + xid);
        var ackRequest = AckRequest.newBuilder()
                .setXid(xid).build();
        predecessorQueOps.enque(ackRequest);
    }


    void hookUpSuccessor(List<String> zNodesOrdered) {
        if (isTail) {
            setTailProps();
            return;
        }

        int index = zNodesOrdered.indexOf(zooManager.zName);
        String updatedSuccessorZnode = zNodesOrdered.get(index + 1);

        //Check if Succesor is updated
        if (!updatedSuccessorZnode.equals(successorName)) {
            successorName = updatedSuccessorZnode;
            isSuccessorAlive = false;
            System.out.println("new successor");
            System.out.println("successorName: " + successorName);
        }
    }

    private void setTailProps() {
        //Since tail no need to update anyone else
        if (updateChannel != null) {
            updateChannel.shutdownNow();
        }
        //unset successors
        successorHostPort = "";
        successorName = "";
        isSuccessorAlive = false;
    }

    public ManagedChannel getNewChannel(String serverAddress) {
        var lastColon = serverAddress.lastIndexOf(':');
        var host = serverAddress.substring(0, lastColon);
        var port = Integer.parseInt(serverAddress.substring(lastColon + 1));
        return ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
    }
}

