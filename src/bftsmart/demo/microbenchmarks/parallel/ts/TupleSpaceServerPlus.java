/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.demo.microbenchmarks.parallel.ts;

import bftsmart.statemanagement.ApplicationState;
import bftsmart.statemanagement.StateManager;
import bftsmart.statemanagement.strategy.StandardStateManager;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ReplicaContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.parallelism.ParallelMapping;
import bftsmart.tom.parallelism.ParallelServiceReplica;
import bftsmart.tom.server.Recoverable;
import bftsmart.tom.server.SingleExecutable;
import bftsmart.tom.util.Storage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

/**
 *
 * @author alchieri
 */
public class TupleSpaceServerPlus extends TupleSpaceServer {

    public TupleSpaceServerPlus(int id, int interval, int numThreads, int entries, int stateSize, boolean context) {
        super(id, interval, numThreads, entries, stateSize, context);

    }

    protected void initReplica(int numThreads, int id) {

        if (numThreads == 0) {
            System.out.println("Replica in sequential execution model.");
            replica = new ServiceReplica(id, this, this);
        } else {
            System.out.println("Replica in parallel execution model.");

           

            replica = new ParallelServiceReplica(id, numThreads, this, this, null);
            
            
            

            for(int j = numThreads; j < 10; j++){
                
                int[] ids = new int[1];
                ids[0] = j%numThreads;
                ((ParallelServiceReplica)replica).addExecutionConflictGroup(j, ids);
                
                System.out.println("Grupo +"+j+" thread "+ids[0]);
            }
            
           
        }

    }

    public static void main(String[] args) {
        if (args.length < 6) {
            System.out.println("Usage: ... TupleSpaceServerPlus <processId> <measurement interval> <num threads> <initial entries> <state size> <context?>");
            System.exit(-1);
        }

        int processId = Integer.parseInt(args[0]);
        int interval = Integer.parseInt(args[1]);
        int nt = Integer.parseInt(args[2]);
        int entries = Integer.parseInt(args[3]);
        int stateSize = Integer.parseInt(args[4]);
        boolean context = Boolean.parseBoolean(args[5]);

        new TupleSpaceServerPlus(processId, interval, nt, entries, stateSize, context);
    }

}
