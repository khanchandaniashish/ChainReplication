package edu.sjsu.cs249.chainreplication;

import edu.sjsu.cs249.chain.GetRequest;
import edu.sjsu.cs249.chain.GetResponse;
import edu.sjsu.cs249.chain.TailChainReplicaGrpc;
import io.grpc.stub.StreamObserver;

/**
 * @author ashish
 */
public class TailChainReplicaGRPCServer extends TailChainReplicaGrpc.TailChainReplicaImplBase {

    Initializer initializer;
    ChainNode chainNode;
    public TailChainReplicaGRPCServer(Initializer initializer) {
        this.initializer = initializer;
        chainNode = initializer.chainNode;
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        if(chainNode.isTail){
            String key = request.getKey();
            int value= chainNode.dataMap.getOrDefault(key,0);
            System.out.println("I'm the tail, returning GRPC with rc:0 and  key : "+  key + " value :"+ value);
            chainNode.printMap();
            responseObserver.onNext(GetResponse.newBuilder().setValue(value).setRc(0).build());
            responseObserver.onCompleted();
        } else {
            System.out.println("I'm not the tail, returning GRPC with 1");
            chainNode.printMap();
            responseObserver.onNext(GetResponse.newBuilder().setRc(1).build());
            responseObserver.onCompleted();
        }
    }
}
