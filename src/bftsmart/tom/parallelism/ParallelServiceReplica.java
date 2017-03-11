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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CyclicBarrier;

/**
 *
 * @author alchieri
 */
public class ParallelServiceReplica extends ServiceReplica {

    protected ParallelMapping mapping;

    //alex
    protected int nextThread = 0;
    protected PSMRReconfigurationPolicy reconf;
    //alex end

    protected class MessageContextPair {

        TOMMessage message;
        MessageContext msgCtx;

        MessageContextPair(TOMMessage message, MessageContext msgCtx) {
            this.message = message;
            this.msgCtx = msgCtx;
        }

    }
    
    
//entender depois o batch
    public ParallelServiceReplica(int id, int initThreads, Executable executor, Recoverable recoverer, PSMRReconfigurationPolicy rp) {
        this(id, initThreads / 2, initThreads, 2 * initThreads, executor, recoverer, rp);
    }

    public ParallelServiceReplica(int id, int maxNumberOfThreads, int minNumberOfThreads, int initialNumberOfThreads, Executable executor, Recoverable recoverer, PSMRReconfigurationPolicy rp) {
        super(id, "", executor, recoverer);
        this.mapping = new ParallelMapping(maxNumberOfThreads, minNumberOfThreads, initialNumberOfThreads);
        //alex
        if (rp == null) {
            this.reconf = new DefaultPSMRReconfigurationPolicy();
        } else {
            this.reconf = rp;
        }
        //alexend
        initMapping();
    }

    public ParallelServiceReplica(int id, String configHome, Executable executor, Recoverable recoverer, ParallelMapping m, PSMRReconfigurationPolicy rp) {
        super(id, configHome, false, executor, recoverer);
        this.mapping = m;
        //alex
        if (rp == null) {
            this.reconf = new DefaultPSMRReconfigurationPolicy();
        } else {
            this.reconf = rp;
        }
        //alexend
        initMapping();
    }

    public ParallelServiceReplica(int id, boolean isToJoin, Executable executor, Recoverable recoverer, ParallelMapping m, PSMRReconfigurationPolicy rp) {
        super(id, "", isToJoin, executor, recoverer);
        this.mapping = m;
        //alexend
        if (rp == null) {
            this.reconf = new DefaultPSMRReconfigurationPolicy();
        } else {
            this.reconf = rp;
        }
        //alexend
        initMapping();
    }

    public int getNumActiveThreads() {
        return this.mapping.getNumCurrentThreads();
    }

    public boolean addExecutionConflictGroup(int groupId, int[] threadsId) {
        return this.mapping.addMultiGroup(groupId, threadsId);
    }

    protected void initMapping() {
        int tid = 0;
        
        for (int i = 0; i < mapping.getNumMaxThreads(); i++) {
            new ServiceReplicaWorker(mapping.getThreadQueue(i), tid).start();
            tid++;
        }

    }

    public CyclicBarrier getReconfBarrier() {
        return this.mapping.getReconfBarrier();
    }

    @Override
    public void receiveMessages(int consId[], int regency, TOMMessage[][] requests) {
        int consensusCount = 0;
        for (TOMMessage[] requestsFromConsensus : requests) {
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
                            //Alex Inicio
                            int ntReconfiguration = this.reconf.requestReceived(request, this.mapping.getNumMinThreads(), this.mapping.getNumCurrentThreads(), this.mapping.getNumMaxThreads());
                            //examina se é possível reconfigurar ntReconfiguration threads
                            ntReconfiguration = this.mapping.checkNumReconfigurationThreads(ntReconfiguration);

                            if (ntReconfiguration < 0) {
                                nextThread = 0;
                                mapping.setNumThreadsAC(mapping.getNumCurrentThreads() + ntReconfiguration);
                                //COLOCAR NA FILA DE TODAS AS THREADS UMA REQUEST DO TIPO THREADS_RECONFIGURATION
                                TOMMessage reconf = new TOMMessage(0, 0, 0, null, 0, ParallelMapping.THREADS_RECONFIGURATION);
                                MessageContextPair mRec = new MessageContextPair(reconf, null);
                                LinkedBlockingQueue[] q = mapping.getQueues();
                                try {
                                    for (LinkedBlockingQueue q1 : q) {
                                        q1.put(mRec);
                                    }
                                } catch (InterruptedException ie) {
                                    ie.printStackTrace();
                                }

                            } else if (ntReconfiguration > 0) {
                                nextThread = 0;
                                mapping.setNumThreadsAC(mapping.getNumCurrentThreads() + ntReconfiguration);
                                //COLOCAR NA FILA DE TODAS AS THREADS UMA REQUEST DO TIPO THREADS_RECONFIGURATION
                                TOMMessage reconf = new TOMMessage(0, 0, 0, null, 0, ParallelMapping.THREADS_RECONFIGURATION);
                                MessageContextPair mRec = new MessageContextPair(reconf, null);
                                LinkedBlockingQueue[] q = mapping.getQueues();
                                try {
                                    for (LinkedBlockingQueue q1 : q) {
                                        q1.put(mRec);
                                    }
                                } catch (InterruptedException ie) {
                                    ie.printStackTrace();
                                }

                            }
                            //Alex Fim

                            if (request.groupId == ParallelMapping.CONFLICT_NONE) {

                                /* while (mapping.getThreadQueue(nextThread).size() > 1){
                                 nextThread = (nextThread + 1) % mapping.getNumGroups();
                                 }
                                 */
                                //NAO VAI COLOCAR NAS THREADS inativas POIS DECREMENTEIO NUM DE THREADS
                                mapping.getThreadQueue(nextThread).put(m);
                                nextThread = (nextThread + 1) % mapping.getNumCurrentThreads();
                                // System.out.println("GID CN: "+request.groupId +" , threadsNum "+mapping.getNumThreads()); 
                            } else if (request.groupId == ParallelMapping.CONFLICT_ALL) {
                                // System.out.println("GID CA: "+request.groupId +" , threadsNum "+mapping.getNumThreads()); 

                                //LinkedBlockingQueue[] q = mapping.getQueues();
                                //pegar so as filas das threads ativas!!
                                LinkedBlockingQueue[] q = mapping.getQueuesActive();
                                for (LinkedBlockingQueue q1 : q) {
                                    q1.put(m);
                                }
                            } else //System.out.println("GID: "+request.groupId +" , threadsNum "+mapping.getNumThreads()); 
                            // o getnumthreads nesse caso retorna as threads ativas, logo nao precisa ser modificado
                            {
                                if (request.groupId < mapping.getNumCurrentThreads()) {

                                    mapping.getThreadQueue(request.groupId).put(m);

                                } else {//MULTIGROUP
                                    //DESCOBRIR COMO TRATAR ESSA PARTE PARA NAO COLOCAR MAIS REQUISICOES NAS THREADS INATIVAS
                                    LinkedBlockingQueue[] q = mapping.getQueues(request.groupId);

                                    if (q != null) {
                                        for (LinkedBlockingQueue q1 : q) {

                                            q1.put(m);
                                        }
                                    } else {
                                        //TRATAR COMO CONFLICT ALL
                                        request.groupId = ParallelMapping.CONFLICT_ALL;
                                        q = mapping.getQueuesActive();
                                        for (LinkedBlockingQueue q1 : q) {
                                            q1.put(m);
                                        }
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

    private class ServiceReplicaWorker extends Thread {

        private LinkedBlockingQueue<MessageContextPair> requests;
        private int thread_id;

        public ServiceReplicaWorker(LinkedBlockingQueue<MessageContextPair> requests, int id) {
            this.thread_id = id;
            this.requests = requests;
            //System.out.println("Criou um thread: " + id);

        }

        public void run() {
            MessageContextPair msg = null;

            while (true) {

                try {

                    msg = requests.take();

                    //System.out.println(">>>Thread " + thread_id + " PEGOU REQUEST");
                    //Thread.sleep(400);
                    if (msg.message.groupId == ParallelMapping.CONFLICT_NONE || msg.message.groupId == thread_id) {
                        //System.out.println("Thread " + thread_id + " vai executar uma operacao conflict none! " + msg.message.toString());
                        //sleep(5000);
                        //System.exit(0);
                        byte[] response = ((SingleExecutable) executor).executeOrdered(msg.message.getContent(), msg.msgCtx);
                        msg.message.reply = new TOMMessage(id, msg.message.getSession(),
                                msg.message.getSequence(), response, SVController.getCurrentViewId());
                        bftsmart.tom.util.Logger.println("(ParallelServiceReplica.receiveMessages) sending reply to "
                                + msg.message.getSender());
                        replier.manageReply(msg.message, msg.msgCtx);

                    } else if (msg.message.groupId == ParallelMapping.CONFLICT_RECONFIGURATION) {
                        mapping.getReconfBarrier().await();
                        //System.out.println(">>>Thread " + thread_id + " vai aguardar uma reconfiguração!");
                        mapping.getReconfBarrier().await();

                    } else if (msg.message.groupId == ParallelMapping.THREADS_RECONFIGURATION) {
                        if (thread_id == 0) {
                            mapping.getReconfThreadBarrier().await();
                            //ATUALIZAR AS BARREIRAS CONFLIC_ALL
                            mapping.reconfigureBarrier();
                            //Thread.sleep(1000);
                            mapping.getReconfThreadBarrier().await();
                            //System.out.println(">>>Thread " + thread_id + " SAIU DA BARREIRA");
                        } else {
                            mapping.getReconfThreadBarrier().await();
                            //System.out.println(">>>Thread " + thread_id + " ESPERANDO RECONFIGURACAO DE NUM T");
                            //Thread.sleep(1000);
                            mapping.getReconfThreadBarrier().await();
                            //System.out.println(">>>Thread " + thread_id + "SAIU DA BARREIRA");
                        }
                    } else if (thread_id == mapping.getExecutorThread(msg.message.groupId)) {//CONFLIC_ALL ou MULTIGROUP

                        mapping.getBarrier(msg.message.groupId).await();
                        // System.out.println("Thread " + thread_id + " );

                        byte[] response = ((SingleExecutable) executor).executeOrdered(msg.message.getContent(), msg.msgCtx);

                        msg.message.reply = new TOMMessage(id, msg.message.getSession(),
                                msg.message.getSequence(), response, SVController.getCurrentViewId());
                        bftsmart.tom.util.Logger.println("(ParallelServiceReplica.receiveMessages) sending reply to "
                                + msg.message.getSender());
                        replier.manageReply(msg.message, msg.msgCtx);

                        //System.out.println("Thread " + thread_id + " executou uma operacao!");
                        mapping.getBarrier(msg.message.groupId).await();
                    } else {
                        //System.out.println(">>>Thread vai liberar execução!"+thread_id);
                        //if(thread_id == 4){

                        //  Thread.sleep(1000);
                        //}
                        mapping.getBarrier(msg.message.groupId).await();
                        //System.out.println(">>>Thread " + thread_id + " vai aguardar a execucao de uma operacao conflict: " + msg.message.groupId);
                        mapping.getBarrier(msg.message.groupId).await();
                    }
                } catch (Exception ie) {
                    ie.printStackTrace();
                    continue;
                }
            }

        }

    }

}
