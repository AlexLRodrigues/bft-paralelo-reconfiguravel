/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom.parallelism;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;

import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.server.Executable;
import bftsmart.tom.server.Recoverable;
import bftsmart.tom.server.SingleExecutable;
import bftsmart.tom.util.TOMUtil;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author alchieri
 */
public class ParallelServiceReplicaBatch extends ParallelServiceReplica {

    public ParallelServiceReplicaBatch(int id, int initThreads, Executable executor, Recoverable recoverer, PSMRReconfigurationPolicy rp) {
        super(id, initThreads, executor, recoverer, rp);
    }

    /* protected class MessageContextPairBatch {

        LinkedList<MessageContextPair> batch = new LinkedList<MessageContextPair>();

        MessageContextPairBatch() {
        }

        public MessageContextPair get(int intex) {
            return this.batch.get(intex);
        }

        public int getGroupId() {
            return batch.getFirst().message.groupId;
        }
    }

    public ParallelServiceReplicaBatch(int id, int numThreads, Executable executor, Recoverable recoverer) {
        super(id, numThreads, executor, recoverer, null);
    }

    public ParallelServiceReplicaBatch(int id, String configHome, Executable executor, Recoverable recoverer, ParallelMapping m) {
        super(id, configHome, executor, recoverer, m, null);
    }

    public ParallelServiceReplicaBatch(int id, boolean isToJoin, Executable executor, Recoverable recoverer, ParallelMapping m) {
        super(id, isToJoin, executor, recoverer, m, null);
    }

    protected void initMapping() {
        int tid = 0;
        for (int i = 0; i < mapping.getNumMaxOfThreads(); i++) {
            new ServiceReplicaWorkerBatch(mapping.getThreadQueue(i), tid).start();
            tid++;
        }

    }

    @Override
    public void receiveMessages(int consId[], int regency, TOMMessage[][] requests) {
        int consensusCount = 0;
        MessageContextPairBatch conflictAllBatch = new MessageContextPairBatch();
        for (TOMMessage[] requestsFromConsensus : requests) {
            System.out.println("*********** Num decided: " + requestsFromConsensus.length);
            TOMMessage firstRequest = requestsFromConsensus[0];
            int requestCount = 0;
            for (TOMMessage request : requestsFromConsensus) {
                if (request.getViewID() == SVController.getCurrentViewId()) {
                    if (request.getReqType() == TOMMessageType.ORDERED_REQUEST) {
                        MessageContext msgCtx = new MessageContext(firstRequest.timestamp, firstRequest.nonces, regency, consId[consensusCount], request.getSender(), firstRequest);
                        if (requestCount + 1 == requestsFromConsensus.length) {
                            msgCtx.setLastInBatch();
                        }
                        request.deliveryTime = System.nanoTime();
                        try {
                            MessageContextPair m = new MessageContextPair(request, msgCtx);
                            if (request.groupId == ParallelMapping.CONFLICT_NONE) {

                                MessageContextPairBatch mb = new MessageContextPairBatch();
                                mb.batch.add(m);
                                mapping.getThreadQueue(nextThread).put(mb);

                                nextThread = (nextThread + 1) % mapping.getNumThreadsAC();
                                // System.out.println("GID CN: "+request.groupId +" , threadsNum "+mapping.getNumThreads()); 
                            } else if (request.groupId == ParallelMapping.CONFLICT_ALL) {
                                // System.out.println("GID CA: "+request.groupId +" , threadsNum "+mapping.getNumThreads()); 

                                conflictAllBatch.batch.add(m);
                                LinkedBlockingQueue[] q = mapping.getQueues();
                                 for (LinkedBlockingQueue q1 : q) {
                                 q1.put(m);
                                 }
                            } else {
                                MessageContextPairBatch mb = new MessageContextPairBatch();
                                mb.batch.add(m);
                                //System.out.println("GID: "+request.groupId +" , threadsNum "+mapping.getNumThreads()); 
                                if (request.groupId < mapping.getNumThreadsAC()) {

                                    mapping.getThreadQueue(request.groupId).put(mb);
                                } else {//MULTIGROUP

                                    LinkedBlockingQueue[] q = mapping.getQueues(request.groupId);
                                    for (LinkedBlockingQueue q1 : q) {
                                        q1.put(mb);
                                    }
                                }
                            }

                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                    } else if (request.getReqType() == TOMMessageType.RECONFIG) {

                        SVController.enqueueUpdate(request);
                    } else {
                        throw new RuntimeException("Should never reach here!");
                    }

                } else if (request.getViewID() < SVController.getCurrentViewId()) {
                    // message sender had an old view, resend the message to
                    // him (but only if it came from consensus an not state transfer)
                    tomLayer.getCommunication().send(new int[]{request.getSender()}, new TOMMessage(SVController.getStaticConf().getProcessId(), request.getSession(), request.getSequence(), TOMUtil.getBytes(SVController.getCurrentView()), SVController.getCurrentViewId()));
                }
                requestCount++;
            }
            consensusCount++;
        }

        if (conflictAllBatch.batch.size() > 0) {
            LinkedBlockingQueue[] q = mapping.getQueues();
            try {
                for (LinkedBlockingQueue q1 : q) {
                    q1.put(conflictAllBatch);
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }

        }

        if (SVController.hasUpdates()) {
            TOMMessage reconf = new TOMMessage(0, 0, 0, null, 0, ParallelMapping.CONFLICT_RECONFIGURATION);
            MessageContextPair m = new MessageContextPair(reconf, null);
            LinkedBlockingQueue[] q = mapping.getQueues();
            try {
                for (LinkedBlockingQueue q1 : q) {
                    q1.put(m);
                }
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }

        }
    }

    private class ServiceReplicaWorkerBatch extends Thread {

        private LinkedBlockingQueue<MessageContextPairBatch> requests;
        private int thread_id;

        public ServiceReplicaWorkerBatch(LinkedBlockingQueue<MessageContextPairBatch> requests, int id) {
            this.thread_id = id;
            this.requests = requests;
        }

        private void execute(TOMMessage message, MessageContext msgCtx) {

            byte[] response = ((SingleExecutable) executor).executeOrdered(message.getContent(), msgCtx);
            message.reply = new TOMMessage(id, message.getSession(),
                    message.getSequence(), response, SVController.getCurrentViewId());
            bftsmart.tom.util.Logger.println("(ParallelServiceReplica.receiveMessages) sending reply to " + message.getSender());
            replier.manageReply(message, msgCtx);

        }

        public void run() {
            MessageContextPairBatch batch = null;
            while (true) {

                try {
                    batch = requests.take();

                     if(thread_id == 4){
                     System.out.println("---Thread " + thread_id + " queue size: "+requests.size());
                     }
                    if (batch.getGroupId() == ParallelMapping.CONFLICT_NONE || batch.getGroupId() == thread_id) {
                        //System.out.println("Thread " + thread_id + " vai executar uma operacao conflict none! " + msg.message.toString());
                        //System.exit(0);
                        execute(batch.get(0).message, batch.get(0).msgCtx);
                         byte[] response = ((SingleExecutable) executor).executeOrdered(batch.get(0).message.getContent(), batch.get(0).msgCtx);
                         batch.get(0).message.reply = new TOMMessage(id, batch.get(0).message.getSession(),
                         batch.get(0).message.getSequence(), response, SVController.getCurrentViewId());
                         bftsmart.tom.util.Logger.println("(ParallelServiceReplica.receiveMessages) sending reply to "
                         + batch.get(0).message.getSender());
                         replier.manageReply(batch.get(0).message, batch.get(0).msgCtx);
                         
                    } else if (batch.getGroupId() == ParallelMapping.CONFLICT_RECONFIGURATION) {
                        mapping.getReconfBarrier().await();
                        //System.out.println(">>>Thread " + thread_id + " vai aguardar uma reconfiguração!");
                        mapping.getReconfBarrier().await();
                    } else //System.out.println("vai executar conflic all");
                    //Definir thread de menor id dentre as conflitantes
                    if (thread_id == mapping.getExecutorThread(batch.getGroupId())) {

                        mapping.getBarrier(batch.getGroupId()).await();

                        //System.out.println("Thread " + thread_id + " vai executar um batch de tamanho: "+batch.batch.size());
                        // System.out.println("Thread " + thread_id + " vai executar uma operacao conflict: " + msg.message.groupId);
                        for (int i = 0; i < batch.batch.size(); i++) {

                            execute(batch.get(i).message, batch.get(i).msgCtx);

                        }
                        //System.out.println("Thread " + thread_id + " executou uma operacao!");
                        mapping.getBarrier(batch.getGroupId()).await();
                    } else {
                        //System.out.println(">>>Thread vai liberar execução!"+thread_id);
                        //if(thread_id == 4){

                        //  Thread.sleep(1000);
                        //}
                        mapping.getBarrier(batch.getGroupId()).await();

                        mapping.getBarrier(batch.getGroupId()).await();
                    }
                } catch (Exception ie) {
                    ie.printStackTrace();
                    //continue;
                }
            }

        }

    }
  */  
}
