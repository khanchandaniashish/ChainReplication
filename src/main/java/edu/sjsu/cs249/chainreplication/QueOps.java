package edu.sjsu.cs249.chainreplication;

import edu.sjsu.cs249.chain.AckRequest;
import edu.sjsu.cs249.chain.ReplicaGrpc;
import edu.sjsu.cs249.chain.UpdateRequest;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import io.grpc.StatusRuntimeException;

/**
 * @author ashish
 */
public class QueOps<T> extends Thread  {

        // A blocking que to buffer up the requests
        private final BlockingQueue<T> buffer;
        Initializer initializer;
        ChainNode chainNode;
        private boolean isPaused;

        public QueOps(Initializer initializer) {
            buffer = new LinkedBlockingQueue<>();
            this.initializer = initializer;
            isPaused = false;
        }

        public void enque(T request) {
            System.out.println("puting req into qu" +request);
            buffer.offer(request);
        }

        public void freeze() {
            isPaused = true;
        }
        public void flow() {
            isPaused = false;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    T request = buffer.take();
                    // Check if queue is paused
                    while (isPaused) {
                        Thread.sleep(100);
                    }
                    WorkTheBuffer(request);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        private void WorkTheBuffer(T request) throws InterruptedException {
            int retryCount = 0;
            boolean connectionStatus = false;
            int delay = 500;

            while(!connectionStatus) {
                try {
                    System.out.println("Retrying to connect to chain");
                    ReplicaGrpc.ReplicaBlockingStub stub;
                    requestRouter(request);
                    connectionStatus = true;
                } catch (StatusRuntimeException exc) {
                    System.out.println("Error occurred while executing the request: " + exc.getMessage());
                    System.out.println("Could not connect. Perhaps try to reconnect?");
                    exc.printStackTrace();
                    Thread.sleep((long)delay);
                    delay *= 2;
                }
            }
        }

    private  void requestRouter(T request) {
        ReplicaGrpc.ReplicaBlockingStub stub;
        if (request instanceof UpdateRequest) {
            System.out.println("Channel is : "+ chainNode.updateChannel);
            stub = ReplicaGrpc.newBlockingStub(chainNode.updateChannel).withDeadlineAfter(5L, TimeUnit.SECONDS);
            stub.update((UpdateRequest) request);
        } else if (request instanceof AckRequest) {
            System.out.println("Channel is : "+ chainNode.ackChannel);
            stub = ReplicaGrpc.newBlockingStub(chainNode.ackChannel).withDeadlineAfter(5L, TimeUnit.SECONDS);
            stub.ack((AckRequest) request);
        }
    }

}
