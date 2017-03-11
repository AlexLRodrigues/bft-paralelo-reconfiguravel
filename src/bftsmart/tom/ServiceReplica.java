/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and
 * the authors indicated in the @author tags
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package bftsmart.tom;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.consensus.executionmanager.ExecutionManager;
import bftsmart.consensus.executionmanager.LeaderModule;
import bftsmart.consensus.messages.MessageFactory;
import bftsmart.consensus.roles.Acceptor;
import bftsmart.consensus.roles.Proposer;
import bftsmart.reconfiguration.Reconfiguration;
import bftsmart.reconfiguration.ReconfigureReply;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.reconfiguration.VMMessage;
import bftsmart.tom.core.ReplyManager;
import bftsmart.tom.core.TOMLayer;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
//alex
//import bftsmart.tom.parallelism.ParallelMapping;
//alex
import bftsmart.tom.server.BatchExecutable;
import bftsmart.tom.server.Executable;
import bftsmart.tom.server.FIFOExecutable;
import bftsmart.tom.server.Recoverable;
import bftsmart.tom.server.Replier;
import bftsmart.tom.server.SingleExecutable;
import bftsmart.tom.server.defaultservices.DefaultReplier;
import bftsmart.tom.util.ShutdownHookThread;
import bftsmart.tom.util.TOMUtil;

/**
 * This class receives messages from DeliveryThread and manages the execution
 * from the application and reply to the clients. For applications where the
 * ordered messages are executed one by one, ServiceReplica receives the batch
 * decided in a consensus, deliver one by one and reply with the batch of
 * replies. In cases where the application executes the messages in batches, the
 * batch of messages is delivered to the application and ServiceReplica doesn't
 * need to organize the replies in batches.
 */
public class ServiceReplica {
    //alex
    //protected ParallelMapping mapping;
    //alex

    protected class MessageContextPair {

        TOMMessage message;
        MessageContext msgCtx;

        MessageContextPair(TOMMessage message, MessageContext msgCtx) {
            this.message = message;
            this.msgCtx = msgCtx;
        }
    }
    // replica ID
    protected int id;
    // Server side comunication system
    protected ServerCommunicationSystem cs = null;
    protected ReplyManager repMan = null;
    protected ServerViewController SVController;
    protected boolean isToJoin = false;
    protected ReentrantLock waitTTPJoinMsgLock = new ReentrantLock();
    protected Condition canProceed = waitTTPJoinMsgLock.newCondition();
    /**
     * THIS IS JOAO'S CODE, TO HANDLE CHECKPOINTS
     */
    protected Executable executor = null;
    protected Recoverable recoverer = null;
    protected TOMLayer tomLayer = null;
    protected boolean tomStackCreated = false;
    protected ReplicaContext replicaCtx = null;
    protected Replier replier = null;

    /**
     * ****************************************************
     */
    /**
     * Constructor
     *
     * @param id Replica ID
     */
    public ServiceReplica(int id, Executable executor, Recoverable recoverer) {
        this(id, "", executor, recoverer);
    }

    /**
     * Constructor
     *
     * @param id Process ID
     * @param configHome Configuration directory for JBP
     */
    public ServiceReplica(int id, String configHome, Executable executor, Recoverable recoverer) {
        this(id, configHome, false, executor, recoverer);
    }

    //******* EDUARDO BEGIN **************//
    /**
     * Constructor
     *
     * @param id Replica ID
     * @param isToJoin: if true, the replica tries to join the system, otherwise
     * it waits for TTP message informing its join
     */
    public ServiceReplica(int id, boolean isToJoin, Executable executor, Recoverable recoverer) {
        this(id, "", isToJoin, executor, recoverer);
    }

    public ServiceReplica(int id, String configHome, boolean isToJoin, Executable executor, Recoverable recoverer) {
        this.isToJoin = isToJoin;
        this.id = id;
        this.SVController = new ServerViewController(id, configHome);
        this.executor = executor;
        this.recoverer = recoverer;
        this.replier = new DefaultReplier();
        System.out.println("CHAMOU INIT");
        this.init();
        System.out.println("PASSOU INIT");
        this.recoverer.setReplicaContext(replicaCtx);
        this.replier.setReplicaContext(replicaCtx);

    }

    public void setReplyController(Replier replier) {
        this.replier = replier;
    }

    //******* EDUARDO END **************//
    // this method initializes the object
    private void init() {
        try {
            cs = new ServerCommunicationSystem(this.SVController, this);
        } catch (Exception ex) {
            Logger.getLogger(ServiceReplica.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException("Unable to build a communication system.");
        }

        //******* EDUARDO BEGIN **************//
        if (this.SVController.isInCurrentView()) {
            System.out.println("In current view: " + this.SVController.getCurrentView());
            initTOMLayer(-1, -1); // initiaze the TOM layer
        } else {

            System.out.println("Not in current view: " + this.SVController.getCurrentView());
            if (this.isToJoin) {
                System.out.println("Sending join: " + this.SVController.getCurrentView());
                //Não está na visão inicial e é para executar um join;
                int port = this.SVController.getStaticConf().getServerToServerPort(id) - 1;
                String ip = this.SVController.getStaticConf().getServerToServerRemoteAddress(id).getAddress().getHostAddress();
                ReconfigureReply r = null;
                Reconfiguration rec = new Reconfiguration(id);
                do {
                    //System.out.println("while 1");
                    rec.addServer(id, ip, port);
                    rec.connect();
                    r = rec.execute();
                } while (!r.getView().isMember(id));
                rec.close();
                this.SVController.processJoinResult(r);

                // initiaze the TOM layer
                initTOMLayer(r.getLastExecConsId(), r.getExecLeader());

                this.cs.updateServersConnections();
                this.cs.joinViewReceived();
            } else {
                //Not in the initial view, just waiting for the view where the join has been executed
                System.out.println("Waiting for the TTP: " + this.SVController.getCurrentView());
                waitTTPJoinMsgLock.lock();
                try {
                    canProceed.awaitUninterruptibly();
                } finally {
                    waitTTPJoinMsgLock.unlock();
                }
            }

        }
        initReplica();
    }

    public void joinMsgReceived(VMMessage msg) {
        ReconfigureReply r = msg.getReply();

        if (r.getView().isMember(id)) {
            this.SVController.processJoinResult(r);

            initTOMLayer(r.getLastExecConsId(), r.getExecLeader()); // initiaze the TOM layer
            //this.startState = r.getStartState();
            cs.updateServersConnections();
            this.cs.joinViewReceived();
            waitTTPJoinMsgLock.lock();
            canProceed.signalAll();
            waitTTPJoinMsgLock.unlock();
        }
    }

    private void initReplica() {
        cs.start();
        repMan = new ReplyManager(SVController.getStaticConf().getNumRepliers(), cs);
    }
    //******* EDUARDO END **************//

    /**
     * This message delivers a readonly message, i.e., a message that was not
     * ordered to the replica and gather the reply to forward to the client
     *
     * @param message the request received from the delivery thread
     */
    public final void receiveReadonlyMessage(TOMMessage message, MessageContext msgCtx) {
        byte[] response = null;
        if (executor instanceof FIFOExecutable) {
            response = ((FIFOExecutable) executor).executeUnorderedFIFO(message.getContent(), msgCtx, message.getSender(), message.getOperationId());
        } else {
            response = executor.executeUnordered(message.getContent(), msgCtx);
        }

        // build the reply and send it to the client
        message.reply = new TOMMessage(id, message.getSession(), message.getSequence(),
                response, SVController.getCurrentViewId(), TOMMessageType.UNORDERED_REQUEST);
        if (SVController.getStaticConf().getNumRepliers() > 0) {
            repMan.send(message);
        } else {
            cs.send(new int[]{message.getSender()}, message.reply);
        }
    }

    public void receiveMessages(int consId[], int regency, TOMMessage[][] requests) {
        int numRequests = 0;
        int consensusCount = 0;
        List<TOMMessage> toBatch = new ArrayList<TOMMessage>();
        List<MessageContext> msgCtxts = new ArrayList<MessageContext>();

        for (TOMMessage[] requestsFromConsensus : requests) {
            TOMMessage firstRequest = requestsFromConsensus[0];
            int requestCount = 0;
            for (TOMMessage request : requestsFromConsensus) {
                if (request.getViewID() == SVController.getCurrentViewId()) {
                    if (request.getReqType() == TOMMessageType.ORDERED_REQUEST) {
                        numRequests++;
                        MessageContext msgCtx = new MessageContext(firstRequest.timestamp, firstRequest.nonces, regency, consId[consensusCount], request.getSender(), firstRequest);
                        if (requestCount + 1 == requestsFromConsensus.length) {
                            msgCtx.setLastInBatch();
                        }
                        request.deliveryTime = System.nanoTime();
                        
                        if (executor instanceof BatchExecutable) {
                            msgCtxts.add(msgCtx);
                            toBatch.add(request);
                        } else if (executor instanceof FIFOExecutable) {
                            byte[] response = ((FIFOExecutable) executor).executeOrderedFIFO(request.getContent(), msgCtx, request.getSender(), request.getOperationId());
                            request.reply = new TOMMessage(id, request.getSession(),
                                    request.getSequence(), response, SVController.getCurrentViewId());
                            bftsmart.tom.util.Logger.println("(ServiceReplica.receiveMessages) sending reply to " + request.getSender());
                            replier.manageReply(request, msgCtx);
                        } else if (executor instanceof SingleExecutable) {
                            byte[] response = ((SingleExecutable) executor).executeOrdered(request.getContent(), msgCtx);
                            request.reply = new TOMMessage(id, request.getSession(),
                                    request.getSequence(), response, SVController.getCurrentViewId());
                            bftsmart.tom.util.Logger.println("(ServiceReplica.receiveMessages) sending reply to " + request.getSender());
                            replier.manageReply(request, msgCtx);
                        } else {
                            throw new UnsupportedOperationException("Interface not existent");
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

    }

    /**
     * This method makes the replica leave the group
     */
    public void leave() {
        ReconfigureReply r = null;
        Reconfiguration rec = new Reconfiguration(id);
        do {
            //System.out.println("while 1");
            rec.removeServer(id);
            rec.connect();
            r = rec.execute();
        } while (r.getView().isMember(id));
        rec.close();
        this.cs.updateServersConnections();
    }

    /**
     * This method initializes the object
     *
     * @param cs Server side communication System
     * @param conf Total order messaging configuration
     */
    private void initTOMLayer(int lastExec, int lastLeader) {
        if (tomStackCreated) { // if this object was already initialized, don't do it again
            return;
        }

        //******* EDUARDO BEGIN **************//
        if (!SVController.isInCurrentView()) {
            throw new RuntimeException("I'm not an acceptor!");
        }
        //******* EDUARDO END **************//

        // Assemble the total order messaging layer
        MessageFactory messageFactory = new MessageFactory(id);

        LeaderModule lm = new LeaderModule();

        Acceptor acceptor = new Acceptor(cs, messageFactory, lm, SVController);
        cs.setAcceptor(acceptor);

        Proposer proposer = new Proposer(cs, messageFactory, SVController);

        ExecutionManager executionManager = new ExecutionManager(SVController, acceptor, proposer, id);

        acceptor.setExecutionManager(executionManager);

        tomLayer = new TOMLayer(executionManager, this, recoverer, lm, acceptor, cs, SVController);

        executionManager.setTOMLayer(tomLayer);

        SVController.setTomLayer(tomLayer);

        cs.setTOMLayer(tomLayer);
        cs.setRequestReceiver(tomLayer);

        acceptor.setTOMLayer(tomLayer);

        if (SVController.getStaticConf().isShutdownHookEnabled()) {
            Runtime.getRuntime().addShutdownHook(new ShutdownHookThread(cs, lm, acceptor, executionManager, tomLayer));
        }
        replicaCtx = new ReplicaContext(cs, SVController);
        tomStackCreated = true;
        tomLayer.start(); // start the layer execution

    }

    /**
     * Obtains the current replica context (getting access to several
     * information and capabilities of the replication engine).
     *
     * @return this replica context
     */
    public final ReplicaContext getReplicaContext() {
        return replicaCtx;
    }

}
