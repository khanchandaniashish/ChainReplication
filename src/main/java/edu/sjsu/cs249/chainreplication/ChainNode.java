package edu.sjsu.cs249.chainreplication;

import edu.sjsu.cs249.chain.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Semaphore;

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

    HashMap<Integer,KeyValuePair> pendingMap;

    HashMap<Integer,StreamObserver> diligentObserver;

    long lastSeenZxid = -1;

    int lastAckXid = -1 ;

    List<String> zNodes;

    Semaphore ackSemaphore;

    ManagedChannel successorChannel;
    ManagedChannel predecessorChannel;

    public ChainNode(ZooManager zooManager) {
        this.zooManager = zooManager;
        this.dataMap = new HashMap<>();
        this.pendingMap = new HashMap<>();
        this.diligentObserver = new HashMap<>();
        this.isHead = false;
        this.isTail = false;
        this.isSuccessorAlive = false;
        ackSemaphore = new Semaphore(1);
    }


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
            System.out.println("childrenWatcher triggered");
            System.out.println("WatchedEvent: " + watchedEvent.getType() + " on " + watchedEvent.getPath());
            try {
                this.getChildren();
                this.callPredecessorAndSetSuccessorData();
            } catch (InterruptedException | KeeperException e) {
                System.out.println("Error getting children with getChildrenInPath()");
            }
        };
    }

    public void getChildren() throws InterruptedException, KeeperException {
        Stat replicaPath = new Stat();
        List<String> children = zooManager.zk.getChildren(zooManager.zooPath, babysitter(), replicaPath);
        lastSeenZxid = replicaPath.getPzxid();
        zNodes = children.stream().filter(
                child -> child.contains("replica-")).toList();
        System.out.println("Current replicas: " + zNodes +
                ", lastZxidSeen: " + lastSeenZxid);
    }

    /**
     Triggers first time on initialize.
     Triggers when there is a change in children path.
     */
    void callPredecessorAndSetSuccessorData() throws InterruptedException, KeeperException {
        List<String> sortedzNodes = zNodes.stream().sorted(Comparator.naturalOrder()).toList();

        String myReplicaName = zooManager.zName.replace(zooManager.zooPath + "/", "");
        isHead = sortedzNodes.get(0).equals(myReplicaName);
        isTail = sortedzNodes.get(sortedzNodes.size() - 1).equals(myReplicaName);
        System.out.println("isHead: " + isHead + ", isTail: " + isTail);

        callPredecessor(sortedzNodes);
        setSuccessorData(sortedzNodes);
    }

    public void callPredecessor(List<String> sortedzNodes) throws InterruptedException, KeeperException {
        if(isHead){
            if (predecessorChannel != null) {
                predecessorChannel.shutdownNow();
            }
            predecessorHostPort = "";
            return;
        }

        String myReplicaName = zooManager.zName.replace(zooManager.zooPath + "/", "");
        System.out.println("myReplicaName : "+ myReplicaName);
        printMap();
        int myPosition = sortedzNodes.indexOf(myReplicaName);
        String predecessorName = sortedzNodes.get(myPosition-1);



        String data = new String(zooManager.zk.getData(zooManager.zooPath + "/" + predecessorName, false, null));
        String newPredecessorAddress = data.split("\n")[0];
        String newPredecessorName = data.split("\n")[1];

        if (!newPredecessorAddress.equals(predecessorHostPort)) {
            System.out.println("new predecessor");
            System.out.println("newPredecessorAddress: " + newPredecessorAddress);
            System.out.println("newPredecessorName: " + newPredecessorName);

            System.out.println("calling newSuccessor of new predecessor.");
            System.out.println("params:" +
                    ", lastZxidSeen: " + lastSeenZxid +
                    ", lastXid: " + lastSeenXId +
                    ", lastAck: " + lastAckXid +
                    ", myReplicaName: " + zooManager.zName);

            predecessorHostPort = newPredecessorAddress;
            predecessorChannel = this.createChannel(predecessorHostPort);
            var stub = ReplicaGrpc.newBlockingStub(predecessorChannel);
            var newSuccessorRequest = NewSuccessorRequest.newBuilder()
                    .setLastZxidSeen(lastSeenZxid)
                    .setLastXid(lastSeenXId)
                    .setLastAck(lastAckXid)
                    .setZnodeName(zooManager.zName).build();

            NewSuccessorResponse newSuccessorResponse = stub.newSuccessor(newSuccessorRequest);
                    long rc = newSuccessorResponse.getRc();
                    System.out.println("Response received");
                    System.out.println("rc: " + rc);
                    if (rc == -1) {
                        try {
                            getChildren();
                            callPredecessorAndSetSuccessorData();
                        } catch (InterruptedException | KeeperException e) {
                            System.out.println("Error getting children with getChildrenInPath()");
                        }
                    } else if (rc == 0) {
                        lastAckXid = newSuccessorResponse.getLastXid();
                        //TODO changes
                        System.out.println("lastSeenXId: " + lastSeenXId);
                        System.out.println("state value:");
                        for (String key : newSuccessorResponse.getStateMap().keySet()) {
                            dataMap.put(key, newSuccessorResponse.getStateMap().get(key));
                            System.out.println(key + ": " + newSuccessorResponse.getStateMap().get(key));
                        }
                        addPendingUpdateRequests(newSuccessorResponse);
                    } else {
                        lastSeenXId = newSuccessorResponse.getLastXid();
                        System.out.println("lastSeenXId: " + lastSeenXId);
                        addPendingUpdateRequests(newSuccessorResponse);
                    }
                }
        }

    private void addPendingUpdateRequests(NewSuccessorResponse newSuccessorResponse) {
        List<UpdateRequest> sent = newSuccessorResponse.getSentList();
        System.out.println("sent requests: ");
        for (UpdateRequest request : sent) {
            String key = request.getKey();
            int newValue = request.getNewValue();
            int xid = request.getXid();
            pendingMap.put(xid, new KeyValuePair(key, newValue));
            System.out.println("xid: " + xid + ", key: " + key + ", value: " + newValue);
        }

        if (isTail && pendingMap.size() > 0) {
            System.out.println("I am tail, have to ack back all pending requests!");
            for (int xid: pendingMap.keySet()) {
                ackXid(xid);
            }
        }
    }

    public void ackXid (int xid) {
        System.out.println("calling ack method of predecessor: " + predecessorHostPort);
        lastSeenXId = xid;
        pendingMap.remove(xid);
        System.out.println(" Got ack and removed xid : "+ xid);
        var stub = ReplicaGrpc.newBlockingStub(predecessorChannel);
        var ackRequest = AckRequest.newBuilder()
                .setXid(xid).build();
        stub.ack(ackRequest);
    }

    public ManagedChannel createChannel(String serverAddress){
        var lastColon = serverAddress.lastIndexOf(':');
        var host = serverAddress.substring(0, lastColon);
        var port = Integer.parseInt(serverAddress.substring(lastColon+1));
        return ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
    }

    void setSuccessorData(List<String> sortedReplicas) {
        if (isTail) {
            if (successorChannel != null) {
                successorChannel.shutdownNow();
            }
            successorHostPort = "";
            successorName = "";
            isSuccessorAlive = false;
            return;
        }

        int index = sortedReplicas.indexOf(zooManager.zName);
        String newSuccessorZNode = sortedReplicas.get(index + 1);

        // If the curr successor replica name matches the new one,
        // then hasSuccessorContacted should be the old value of hasSuccessorContacted
        // else it should be false
        if (!newSuccessorZNode.equals(successorName)) {
            successorName = newSuccessorZNode;
            isSuccessorAlive = false;
            System.out.println("new successor");
            System.out.println("successorName: " + successorName);
        }
    }
}



class KeyValuePair{
    String key;
    int value;


    public KeyValuePair(String key, int value) {
        this.key = key;
        this.value = value;
    }
}
