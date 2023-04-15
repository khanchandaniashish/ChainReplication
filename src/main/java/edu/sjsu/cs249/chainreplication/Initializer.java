package edu.sjsu.cs249.chainreplication;

/**
 * @author ashish
 */

import edu.sjsu.cs249.chain.*;
import io.grpc.*;
import org.apache.zookeeper.*;

import java.io.IOException;
import java.util.concurrent.Semaphore;

public class Initializer {
    String yourName;
    String grpcServerAddress;
    String zooServerList;
    String zooPath;
    ZooManager zooManager;
    ChainNode chainNode;

    Initializer(String yourName, String grpcServerAddress, String zooServerList, String zooPath) {
        this.yourName = yourName;
        this.grpcServerAddress = grpcServerAddress;
        this.zooServerList = zooServerList;
        this.zooPath = zooPath;
        zooManager = new ZooManager(zooServerList, zooPath, grpcServerAddress);
        zooManager.startZooKeeper();
        this.chainNode = new ChainNode(zooManager);
    }

    void start() throws IOException, InterruptedException, KeeperException {

        zooManager.createZooNode(yourName);

        chainNode.getChildren();
        chainNode.callPredecessorAndSetSuccessorData();

        HeadChainReplicaGRPCServer headChainReplicaGRPCServer = new HeadChainReplicaGRPCServer(this);
        TailChainReplicaGRPCServer tailChainReplicaGRPCServer = new TailChainReplicaGRPCServer(this);
        ReplicaGRPCServer replicaGRPCServer = new ReplicaGRPCServer(this);
        ChainDebugInstance chainDebugInstance = new ChainDebugInstance(this);

        Server server = ServerBuilder.forPort(Integer.parseInt(grpcServerAddress.split(":")[1]))
                .addService(headChainReplicaGRPCServer)
                .addService(tailChainReplicaGRPCServer)
                .addService(replicaGRPCServer)
                .addService(chainDebugInstance)
                .build();

        server.start();
        System.out.printf("will listen on port %s\n", server.getPort());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            zooManager.stopZooKeeper();
            server.shutdown();
            System.out.println("Successfully stopped the server");
        }));
        server.awaitTermination();
    }

    public ManagedChannel createChannel(String serverAddress) {
        var lastColon = serverAddress.lastIndexOf(':');
        var host = serverAddress.substring(0, lastColon);
        var port = Integer.parseInt(serverAddress.substring(lastColon + 1));
        return ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
    }
}