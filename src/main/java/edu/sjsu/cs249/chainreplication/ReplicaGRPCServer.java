package edu.sjsu.cs249.chainreplication;

import com.google.rpc.Code;
import edu.sjsu.cs249.chain.*;
import io.grpc.stub.StreamObserver;
import org.apache.zookeeper.KeeperException;

import java.util.Objects;


/**
 * @author ashish
 */
public class ReplicaGRPCServer extends ReplicaGrpc.ReplicaImplBase {

    Initializer initializer;

    final ChainNode chainNode;

    QueOps successorQueOps;

    QueOps predecessorQueOps;


    public ReplicaGRPCServer(Initializer initializer) {
        this.initializer = initializer;
        chainNode = initializer.chainNode;
        successorQueOps = initializer.successorQueOps;
        predecessorQueOps = initializer.predecessorQueOps;
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void update(UpdateRequest request, StreamObserver<UpdateResponse> responseObserver) {
        synchronized (chainNode) {

            //Update Req received with following data
            String key = request.getKey();
            int updatedValue = request.getNewValue();
            int xid = request.getXid();
            System.out.println("Update Req received with key" + key + " new value : " + updatedValue + " and XID:" + xid);

            //Update Chain Node DataMap and PendingMap
            chainNode.dataMap.put(key, updatedValue);
            KeyValuePair pendingPair = new KeyValuePair(key, updatedValue);
            chainNode.pendingMap.put(xid, pendingPair);
            chainNode.lastSeenXId = xid;
            System.out.println("Added to pending map : key" + key + " new value : " + updatedValue + " and XID:" + xid);

            if (chainNode.isTail) {
                System.out.println("Acking back from TAIL!");
                chainNode.sendAcks(xid);
                //TODO MVP2 check
            } else if (!chainNode.isSuccessorAlive) {
                System.out.println("Succ hasnt reached out yet! Acking calls");
                chainNode.sendAcks(xid);
            } else {
//                synchronized (chainNode) {
                var updateRequest = UpdateRequest.newBuilder()
                        .setXid(xid)
                        .setKey(key)
                        .setNewValue(updatedValue)
                        .build();
                successorQueOps.enque(updateRequest);
//                }
            }

            responseObserver.onNext(UpdateResponse.newBuilder().build());
            responseObserver.onCompleted();
        }
    }

    /**
     * <pre>
     * will be called by a new successor to this replica
     * </pre>
     *
     * @param request
     * @param responseObserver
     */
    @Override
    public void newSuccessor(NewSuccessorRequest request, StreamObserver<NewSuccessorResponse> responseObserver) {
        synchronized (chainNode) {
            predecessorQueOps.freeze();
            long lastestZxid = request.getLastZxidSeen();
            int lastestXid = request.getLastXid();
            int lastestAck = request.getLastAck();
            String requestedSuccessor = request.getZnodeName();

//            printdebughelp(lastestZxid, lastestXid, lastestAck, requestedSuccessor);

            if (lastestZxid > chainNode.lastSeenZxid) {
                System.out.println("Calling sync as I need updates!");
                syncWithTheZoo(responseObserver, lastestXid, lastestAck, requestedSuccessor);

            } else if (lastestZxid == chainNode.lastSeenZxid) {
                System.out.println("SuccessorName: " + chainNode.successorName);
                if (Objects.equals(chainNode.successorName, requestedSuccessor)) {
                    handleStateTransferToSuccessor(lastestAck, lastestXid, requestedSuccessor, responseObserver);
                } else {
                    System.out.println("replica is not the replica i saw in my view of zookeeper");
                    rejectAndAskSuccToUpdate(responseObserver);
                }
            } else {
                System.out.println("replica has older view of zookeeper than me, ignoring request");
                rejectAndAskSuccToUpdate(responseObserver);
            }
            predecessorQueOps.flow();
        }
    }

//    private static void printdebughelp(long lastestZxid, int lastestXid, int lastestAck, String requestedSuccessor) {
//        System.out.println("nuw succ called with ");
//        System.out.println("lastestZxid: " + lastestZxid);
//        System.out.println("lastestXid: " + lastestXid);
//        System.out.println(("lastestAck: " + lastestAck));
//        System.out.println("znodeName: " + requestedSuccessor);
//    }

    private static void rejectAndAskSuccToUpdate(StreamObserver<NewSuccessorResponse> responseObserver) {
        responseObserver.onNext(NewSuccessorResponse.newBuilder().setRc(-1).build());
        responseObserver.onCompleted();
    }

    private void syncWithTheZoo(StreamObserver<NewSuccessorResponse> responseObserver, int lastestXid, int lastestAck, String requestedSuccessor) {
        initializer.zooManager.zk.sync(initializer.zooManager.zooPath, (returnCode, var2, var3) -> {
            if (returnCode == Code.OK_VALUE && Objects.equals(initializer.chainNode.successorHostPort, requestedSuccessor)) {
                handleStateTransferToSuccessor(lastestAck, lastestXid, requestedSuccessor, responseObserver);
            }
        }, null);
    }

    private void handleStateTransferToSuccessor(int lastAck, int lastXid, String requestedSuccessor, StreamObserver<NewSuccessorResponse> responseObserver) {

        NewSuccessorResponse.Builder newSuccessor = NewSuccessorResponse.newBuilder();
        newSuccessor.setRc(1);

        //new instance needs a complete state transfer
        transferCompleteStateIfNewNode(lastAck, newSuccessor);

        System.out.println("State transfer begins with xid :" + (lastXid + 1));
        for (int xid = lastXid + 1; xid <= chainNode.lastSeenXId; xid += 1) {
            if (chainNode.pendingMap.containsKey(xid)) {
                newSuccessor.addSent(UpdateRequest.newBuilder()
                        .setXid(xid)
                        .setKey(chainNode.pendingMap.get(xid).key)
                        .setNewValue(chainNode.pendingMap.get(xid).value)
                        .build());
            }
        }

        for (int myAckXid = chainNode.lastSeenAckXid + 1; myAckXid <= lastAck; myAckXid += 1) {
            chainNode.sendAcks(myAckXid);
        }

        newSuccessor.setLastXid(chainNode.lastSeenXId);

        try {
            String data = new String(initializer.zooManager.zk.getData(initializer.zooManager.zooPath + "/" + requestedSuccessor, false, null));
            chainNode.successorHostPort = data.split("\n")[0];
            chainNode.updateChannel = chainNode.getNewChannel(chainNode.successorHostPort);
            System.out.println("new successor");
            System.out.println("successorAddress: " + chainNode.successorHostPort);
            System.out.println("successor name: " + data.split("\n")[1]);
        } catch (InterruptedException | KeeperException e) {
            System.out.println("error in getting successor address from zookeeper");
        }
        chainNode.isSuccessorAlive = true;
        responseObserver.onNext(newSuccessor.build());
        responseObserver.onCompleted();
    }

    private void transferCompleteStateIfNewNode(int lastAck, NewSuccessorResponse.Builder newSuccessor) {
        if (lastAck == -1) {
            newSuccessor.setRc(0)
                    .putAllState(chainNode.dataMap);
        }
    }


    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void ack(AckRequest request, StreamObserver<AckResponse> responseObserver) {
        int xid = request.getXid();
        try {
            chainNode.onePermitLock.acquire();
            if (!chainNode.isHead) {
                chainNode.sendAcks(xid);
            } else {
                sendAckFromHead(xid);
            }
            responseObserver.onNext(AckResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (InterruptedException e) {
            System.out.println("caught InterruptedException");
            e.printStackTrace();
        } finally {
            System.out.println("release lock!");
            chainNode.onePermitLock.release();
        }
    }

    private void sendAckFromHead(int xid) {
        System.out.println("Sending the ack back from head with xid:" + xid);
        System.out.println("observer state:");
        System.out.println(chainNode.diligentObserver.get(xid));
        System.out.println("--------");
        chainNode.lastSeenAckXid = xid;
        chainNode.pendingMap.remove(xid);
        StreamObserver<HeadResponse> headResponseStreamObserver = chainNode.diligentObserver.remove(xid);
        headResponseStreamObserver.onNext(HeadResponse.newBuilder().setRc(0).build());
        headResponseStreamObserver.onCompleted();
    }
}
