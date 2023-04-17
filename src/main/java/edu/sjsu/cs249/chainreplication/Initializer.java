package edu.sjsu.cs249.chainreplication;

/**
 * @author ashish
 */

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.zookeeper.KeeperException;

import java.io.IOException;

public class Initializer {
    String yourName;
    String grpcServerAddress;
    String zooServerList;
    String zooPath;
    ZooManager zooManager;
    ChainNode chainNode;
    QueOps predecessorQueOps;

    QueOps successorQueOps;

    Initializer(String yourName, String grpcServerAddress, String zooServerList, String zooPath) {
        this.yourName = yourName;
        this.grpcServerAddress = grpcServerAddress;
        this.zooServerList = zooServerList;
        this.zooPath = zooPath;
        zooManager = new ZooManager(zooServerList, zooPath, grpcServerAddress);
        zooManager.startZooKeeper();
        predecessorQueOps = new QueOps<>(this);
        successorQueOps = new QueOps<>(this);
        predecessorQueOps.start();
        successorQueOps.start();
        this.chainNode = new ChainNode(zooManager, predecessorQueOps);
        predecessorQueOps.chainNode = chainNode;
        successorQueOps.chainNode = chainNode;
    }

    void start() throws IOException, InterruptedException, KeeperException {

        //Create the Replica in Zookeeper
        zooManager.createZooNode(yourName);

        //Get children to figure out if your own position in chain
        chainNode.getChildren();

        //Set yourself up in chain
        chainNode.joinTheChain();

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