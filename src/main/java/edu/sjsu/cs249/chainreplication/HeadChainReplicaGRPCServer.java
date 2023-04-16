package edu.sjsu.cs249.chainreplication;

import edu.sjsu.cs249.chain.*;
import io.grpc.stub.StreamObserver;

/**
 * @author ashish
 */
public class HeadChainReplicaGRPCServer extends HeadChainReplicaGrpc.HeadChainReplicaImplBase {

    Initializer initializer;

    final ChainNode chainNode;

    public HeadChainReplicaGRPCServer(Initializer initializer) {
        this.initializer = initializer;
        chainNode = initializer.chainNode;
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void increment(IncRequest request, StreamObserver<HeadResponse> responseObserver) {
        synchronized (chainNode) {
            String key = request.getKey();
            int value = request.getIncValue();

            System.out.println("increment called with key :" + key + " and value :" + value);

            //If head update and pass it on
            if (!chainNode.isHead) {
                //if not head, return 1 and exit
                System.out.println("I'm not the head , so i will reject this request!");
                responseObserver.onNext(HeadResponse.newBuilder().setRc(1).build());
                responseObserver.onCompleted();
                return;
            }

            //Print map
            System.out.println("Map before updates");
//            //chainNode.printMap();

            int updateValue = chainNode.dataMap.getOrDefault(key, 0) + value;
            //Update Map
            chainNode.dataMap.put(key, updateValue);
            int latestId = chainNode.lastSeenXId + 1;
            chainNode.lastSeenXId = latestId;
            System.out.println("new xid : "+ latestId);

            //Print map
            System.out.println("Map after updates");
            ////chainNode.printMap();

            //if tail, send back ack
            if (chainNode.isTail) {
                chainNode.lastSeenXId = latestId;
                responseObserver.onNext(HeadResponse.newBuilder().setRc(0).build());
                responseObserver.onCompleted();
            } else {
                //if not tail, send state to successor
                KeyValuePair pendingPair = new KeyValuePair(key, updateValue);
                chainNode.pendingMap.put(latestId, pendingPair);
                chainNode.diligentObserver.put(latestId, responseObserver);
                //chainNode.printMap();
                //add to pending list : pending list of sent and pending list for head
                sendUpdatesToSuccessor(pendingPair);
            }
        }
    }

    void sendUpdatesToSuccessor(KeyValuePair pendingPair) {
        if (chainNode.isSuccessorAlive) {
            var stub = ReplicaGrpc.newBlockingStub(chainNode.successorChannel);
            System.out.println("sendUpdatesToSuccessor hit with xid:"+ chainNode.lastSeenXId +" key value:" + pendingPair.key+ " "+ pendingPair.value);

            var updateRequest = UpdateRequest.newBuilder()
                    .setXid(chainNode.lastSeenXId)
                    .setKey(pendingPair.key)
                    .setNewValue(pendingPair.value)
                    .build();

            stub.update(updateRequest);
        }
    }
}
