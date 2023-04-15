package edu.sjsu.cs249.chainreplication;

import edu.sjsu.cs249.chain.AckRequest;
import edu.sjsu.cs249.chain.ReplicaGrpc;
import edu.sjsu.cs249.chain.UpdateRequest;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author ashish
 */
public class QueOps {

    public class QueueableRequest<T> extends Thread {
        private final BlockingQueue<T> requestQueue;
        ChainReplicationInstance chainReplicationInstance;
        private boolean isPaused;

        public QueueableRequest(ChainReplicationInstance chainReplicationInstance) {
            requestQueue = new LinkedBlockingQueue<>();
            this.chainReplicationInstance = chainReplicationInstance;
            isPaused = false;
        }

        public void submitRequest(T request) {
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

        private void executeRequest(T request) {
            // execute the request here
            if (request instanceof UpdateRequest) {
                ReplicaGrpc.ReplicaBlockingStub stub = ReplicaGrpc.newBlockingStub(chainReplicationInstance.successorChannel).withDeadlineAfter(5L, TimeUnit.SECONDS);
                stub.update((UpdateRequest) request);
            } else if (request instanceof AckRequest) {
                ReplicaGrpc.ReplicaBlockingStub stub = ReplicaGrpc.newBlockingStub(chainReplicationInstance.predecessorChannel).withDeadlineAfter(5L, TimeUnit.SECONDS);
                stub.ack((AckRequest) request);
            }
        }
    }
}
