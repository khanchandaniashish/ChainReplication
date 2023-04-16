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

        private final BlockingQueue<T> requestQueue;
        Initializer initializer;
        ChainNode chainNode;
        private boolean isPaused;

        public QueOps(Initializer initializer) {
            requestQueue = new LinkedBlockingQueue<>();
            this.initializer = initializer;
            isPaused = false;
        }

        public void submitRequest(T request) {
            System.out.println("puting req into qu" +request);
            requestQueue.offer(request);
        }

        public boolean isEmpty() {
            return requestQueue.isEmpty();
        }

        public void pause() {
            isPaused = true;
        }
        public void play() {
            isPaused = false;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    T request = requestQueue.take();

                    // Check if queue is paused
                    while (isPaused) {
                        Thread.sleep(100);
                    }
                    executeRequest(request);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        private void executeRequest(T request) throws InterruptedException {
            // execute the request here
            int numRetries = 3;
            int retryCount = 0;
            boolean success = false;
            int delay = 500;

            while(!success && retryCount < numRetries) {
                try {
                    ReplicaGrpc.ReplicaBlockingStub stub;
                    if (request instanceof UpdateRequest) {
                        System.out.println("Channel is : "+ chainNode.successorChannel);
                        stub = ReplicaGrpc.newBlockingStub(chainNode.successorChannel).withDeadlineAfter(5L, TimeUnit.SECONDS);
                        stub.update((UpdateRequest) request);
                    } else if (request instanceof AckRequest) {
                        System.out.println("Channel is : "+ chainNode.predecessorChannel);
                        stub = ReplicaGrpc.newBlockingStub(chainNode.predecessorChannel).withDeadlineAfter(5L, TimeUnit.SECONDS);
                        stub.ack((AckRequest) request);
                    }
                    success = true;
                } catch (StatusRuntimeException var7) {
                    System.err.println("Error occurred while executing the request: " + var7.getMessage());
                    ++retryCount;
                    Thread.sleep((long)delay);
                    delay *= 2;
                }
            }

            if (!success) {
                System.err.println("Failed to execute the request after " + numRetries + " retries: " + request);
            }
        }

}
