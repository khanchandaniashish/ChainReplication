package edu.sjsu.cs249.chainreplication;

import com.google.rpc.Code;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.checkerframework.checker.units.qual.A;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * @author ashish
 */
public class ZooManager {

    String zooServers;

    ZooKeeper zk;

    String zooPath;

    String grpcServerAddress;

    String zName = "";

    edu.sjsu.cs249.chainreplication.LogManager logManager;

    private static final Logger logger = LogManager.getLogger(ZooManager.class);

    ZooManager(String zooServers, String zooPath,String grpcServerAddress){
        this.zooServers = zooServers;
        this.zooPath = zooPath;
        this.grpcServerAddress = grpcServerAddress;
    }

    ZooKeeper startZooKeeper() {
        try {
            System.out.println("Starting ZOOKEEPER");
            zk = new ZooKeeper(zooServers, 15000, System.out::println);
            System.out.println("Started ZOOKEEPER");
        } catch (Exception e) {
            System.out.println("Failed to start zookeeper due to exception :" + e.getMessage());
            e.printStackTrace();
        }
        return zk;
    }

    void stopZooKeeper() {
        try {
            System.out.println("Shutting down zookeeper!");
            zk.close(1000);
        } catch (Exception e) {
            System.out.println("Failed to stop zookeeper due to exception :" + e.getMessage());
            e.printStackTrace();
        }
    }

    void createZooNode(String nodeName) {
        try {
            System.out.println("Creating node : "+ nodeName);
            zName = zk.create(zooPath + "/replica-", (grpcServerAddress + "\n" + nodeName).getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            zName = zName.replace(zooPath + "/", "");
            System.out.println("Node created : "+zName);
        } catch (KeeperException | InterruptedException e) {
            System.out.println("---------------------");
            System.out.println("node creation issue!");
            System.out.println("---------------------");
            e.printStackTrace();
            System.out.println("---------------------");
        }
    }
}
