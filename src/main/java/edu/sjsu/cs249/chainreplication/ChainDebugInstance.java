package edu.sjsu.cs249.chainreplication;

import edu.sjsu.cs249.chain.*;
import io.grpc.stub.StreamObserver;

/**
 * @author ashish
 */
public class ChainDebugInstance extends ChainDebugGrpc.ChainDebugImplBase {

    Initializer initializer;

    final ChainNode chainNode;

    public ChainDebugInstance(Initializer initializer) {
        this.initializer = initializer;
        this.chainNode = initializer.chainNode;
    }


    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void debug(ChainDebugRequest request, StreamObserver<ChainDebugResponse> responseObserver) {
        synchronized (chainNode) {
            ChainDebugResponse.Builder builder = ChainDebugResponse.newBuilder();
            System.out.println("Debug called!");
            builder.setXid(chainNode.lastSeenXId)
                    .putAllState(chainNode.dataMap);

            for (int key : chainNode.pendingMap.keySet()) {
                builder.addSent(UpdateRequest.newBuilder()
                        .setXid(key)
                        .setKey(chainNode.pendingMap.get(key).key)
                        .setNewValue(chainNode.pendingMap.get(key).value)
                        .build());
            }
            System.out.println("Printing some debug Stats");
            System.out.println("-------------------------------");
            System.out.println(builder.getXid());
            System.out.println(builder.getStateMap());
            System.out.println(builder.getSentList());
            System.out.println("End of Debug Stats");
            System.out.println("-------------------------------");
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void exit(ExitRequest request, StreamObserver<ExitResponse> responseObserver) {
        synchronized (chainNode) {
            try {
                chainNode.onePermitLock.acquire();
                System.out.println("Debug Exit Called");
                responseObserver.onNext(ExitResponse.newBuilder().build());
                responseObserver.onCompleted();
                System.out.println("releasing semaphore for exit");
                chainNode.onePermitLock.release();
                System.exit(0);
            } catch (InterruptedException e) {
                System.out.println("Problem acquiring semaphore");
                System.out.println(e.getMessage());
            }
        }
    }

}
