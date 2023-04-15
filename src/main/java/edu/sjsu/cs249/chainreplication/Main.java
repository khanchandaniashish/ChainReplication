package edu.sjsu.cs249.chainreplication;

import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * @author ashish
 */
public class Main {

    public static void main(String[] args) {
        System.exit(new CommandLine(new ServerCli()).execute(args));
    }

    static class ServerCli implements Callable<Integer> {

        @CommandLine.Parameters(index = "0", description = "Name")
        String yourName;

        @CommandLine.Parameters(index = "1", description = "GRPC host:port listen on")
        String grpcServerAddress;

        @CommandLine.Parameters(index = "2", description = "List of zooServers")
        String zooServerList;

        @CommandLine.Parameters(index = "3", description = "Path to look out for sequential nodes")
        String zooPath;

        @Override
        public Integer call() throws Exception {
            new Initializer(yourName, grpcServerAddress, zooServerList, zooPath).start();
            return 0;
        }
    }
}
