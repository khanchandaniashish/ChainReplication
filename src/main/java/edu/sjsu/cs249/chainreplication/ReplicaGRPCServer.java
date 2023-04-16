package edu.sjsu.cs249.chainreplication;

import com.google.rpc.Code;
import edu.sjsu.cs249.chain.*;
import io.grpc.stub.StreamObserver;
import org.apache.zookeeper.KeeperException;

import java.util.Objects;
import java.util.concurrent.Semaphore;


/**
 * @author ashish
 */
public class ReplicaGRPCServer extends ReplicaGrpc.ReplicaImplBase  {

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
            String key = request.getKey();
            int updatedValue = request.getNewValue();
            int xid = request.getXid();

            System.out.println("Update called with key" + key + " new value : " + updatedValue + " and XID:" + xid);
            chainNode.dataMap.put(key, updatedValue);
            chainNode.pendingMap.put(xid, new KeyValuePair(key, updatedValue));
            System.out.println("Added to pending map : key" + key + " new value : " + updatedValue + " and XID:" + xid);
            chainNode.lastSeenXId = xid;


            if (chainNode.isTail) {
                System.out.println("Acking back from TAIL!");
                chainNode.ackXid(xid);
            } else if(!chainNode.isSuccessorAlive){
                System.out.println("Succ hasnt reached out yet! Acking calls");
                chainNode.ackXid(xid);
            }
            else {
                synchronized (chainNode) {
//                var stub = ReplicaGrpc.newBlockingStub(chainNode.successorChannel);
                    var updateRequest = UpdateRequest.newBuilder()
                            .setXid(xid)
                            .setKey(key)
                            .setNewValue(updatedValue)
                            .build();
                    successorQueOps.submitRequest(updateRequest);
//                stub.update(updateRequest);
                }
            }
            System.out.println("UpdateResponse on complete called");
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
            predecessorQueOps.pause();
            long lastZxidSeen = request.getLastZxidSeen();
            int lastXid = request.getLastXid();
            int lastAck = request.getLastAck();
            String requestedSuccessor = request.getZnodeName();

            System.out.println("nuw succ called with ");
            System.out.println("lastZxidSeen: " + lastZxidSeen);
            System.out.println("lastXid: " + lastXid);
            System.out.println(("lastAck: " + lastAck));
            System.out.println("znodeName: " + requestedSuccessor);

            if (lastZxidSeen > chainNode.lastSeenZxid) {
                System.out.println("Gotta call sync");
                initializer.zooManager.zk.sync(initializer.zooManager.zooPath, (i, s, o) -> {
                    if (i == Code.OK_VALUE && Objects.equals(initializer.chainNode.successorHostPort, requestedSuccessor)) {
                        successorProcedure(lastAck, lastXid, requestedSuccessor, responseObserver);
                    }
                }, null);

            } else if (lastZxidSeen == chainNode.lastSeenZxid) {
                System.out.println("my successorReplicaName: " + chainNode.successorName);
                if (Objects.equals(chainNode.successorName, requestedSuccessor)) {
                    successorProcedure(lastAck, lastXid, requestedSuccessor, responseObserver);
                } else {
                    System.out.println("replica is notf the replica i saw in my view of zookeeper");
                    responseObserver.onNext(NewSuccessorResponse.newBuilder().setRc(-1).build());
                    responseObserver.onCompleted();
                }
            } else {
                System.out.println("replica has older view of zookeeper than me, ignoring request");
                responseObserver.onNext(NewSuccessorResponse.newBuilder().setRc(-1).build());
                responseObserver.onCompleted();
            }
            predecessorQueOps.play();
        }
    }

    private void successorProcedure(int lastAck, int lastXid, String requestedSuccessor, StreamObserver<NewSuccessorResponse> responseObserver) {

        NewSuccessorResponse.Builder newSuccessor = NewSuccessorResponse.newBuilder();
        newSuccessor.setRc(1);

        if (lastAck == -1) {
            newSuccessor.setRc(0)
                    .putAllState(chainNode.dataMap);
        }

        System.out.println("State transfer begins with xid :" + (lastXid+1));
        for (int xid = lastXid + 1; xid <= chainNode.lastSeenXId; xid += 1) {
            if (chainNode.pendingMap.containsKey(xid)) {
                newSuccessor.addSent(UpdateRequest.newBuilder()
                        .setXid(xid)
                        .setKey(chainNode.pendingMap.get(xid).key)
                        .setNewValue(chainNode.pendingMap.get(xid).value)
                        .build());
            }
        }

        //chainNode.printMap();

        for (int myAckXid = chainNode.lastAckXid + 1; myAckXid <= lastAck; myAckXid += 1) {
            chainNode.ackXid(myAckXid);
        }

        newSuccessor.setLastXid(chainNode.lastSeenXId);


        try {
            String data = new String(initializer.zooManager.zk.getData(initializer.zooManager.zooPath + "/" + requestedSuccessor, false, null));
            chainNode.successorHostPort = data.split("\n")[0];
            chainNode.successorChannel = chainNode.createChannel(chainNode.successorHostPort);
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


    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void ack(AckRequest request, StreamObserver<AckResponse> responseObserver) {
        int xid = request.getXid();

        try {
            chainNode.ackSemaphore.acquire();
            if (!chainNode.isHead) {
                  chainNode.ackXid(xid);
//                System.out.println("calling ack method of predecessor: " + chainNode.predecessorHostPort);
//                var channel = initializer.createChannel(chainNode.predecessorHostPort);
//                var stub = ReplicaGrpc.newBlockingStub(channel);
//                stub.ack(AckRequest.newBuilder().setXid(xid).build());
//                channel.shutdown();
            } else {
                System.out.println("Sending the ack back from head with xid:"+ xid);
                System.out.println("observer state:");
                System.out.println(chainNode.diligentObserver.get(xid));
                System.out.println("--------");
                chainNode.lastAckXid = xid;
                chainNode.pendingMap.remove(xid);
                StreamObserver<HeadResponse> headResponseStreamObserver = chainNode.diligentObserver.remove(xid);
                headResponseStreamObserver.onNext(HeadResponse.newBuilder().setRc(0).build());
                headResponseStreamObserver.onCompleted();
            }
            responseObserver.onNext(AckResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (InterruptedException e) {
            System.out.println("caught InterruptedException");
            e.printStackTrace();
        } finally {
            System.out.println("release lock!");
            chainNode.ackSemaphore.release();
        }
    }
}
