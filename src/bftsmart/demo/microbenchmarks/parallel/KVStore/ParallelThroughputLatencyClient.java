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
package bftsmart.demo.microbenchmarks.parallel.KVStore;

import bftsmart.tom.parallelism.ParallelMapping;
import java.io.IOException;

import java.util.logging.Level;
import java.util.logging.Logger;

import bftsmart.tom.ServiceProxy;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.Storage;
import java.util.Random;

/**
 * Example client that updates a BFT replicated service (a counter).
 *
 */
public class ParallelThroughputLatencyClient {

    private static int VALUE_SIZE = 1024;
    public static int initId = 0;

    @SuppressWarnings("static-access")
    public static void main(String[] args) throws IOException {
        if (args.length < 6) {
            System.out.println("Usage: ... ParallelThroughputLatencyClient <num. threads> <process id> <number of operations> <interval> <verbose?> <parallel?>");
            System.exit(-1);
        }

        int numThreads = Integer.parseInt(args[0]);
        initId = Integer.parseInt(args[1]);

        int numberOfOps = Integer.parseInt(args[2]);
        //int requestSize = Integer.parseInt(args[3]);
        int interval = Integer.parseInt(args[3]);
        boolean verbose = Boolean.parseBoolean(args[4]);
        boolean parallel = Boolean.parseBoolean(args[5]);
        //boolean dos = Boolean.parseBoolean(args[7]);

        Client[] c = new Client[numThreads];

        for (int i = 0; i < numThreads; i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Logger.getLogger(ParallelThroughputLatencyClient.class.getName()).log(Level.SEVERE, null, ex);
            }

            System.out.println("Launching client " + (initId + i));
            c[i] = new ParallelThroughputLatencyClient.Client(initId + i, numberOfOps, interval, verbose, parallel);
            //c[i].start();
        }

        for (int i = 0; i < numThreads; i++) {

            c[i].start();
        }

        for (int i = 0; i < numThreads; i++) {

            try {
                c[i].join();
            } catch (InterruptedException ex) {
                ex.printStackTrace(System.err);
            }
        }

        System.exit(0);
    }

    static class Client extends Thread {

        int id;
        int numberOfOps;
        
        int interval;
        
        boolean verbose;
        //boolean dos;
        //ServiceProxy proxy;
        //byte[] request;
        ParallelKVStore store;

        public Client(int id, int numberOfOps, int interval, boolean verbose, boolean parallel) {
            super("Client " + id);

            this.id = id;
            this.numberOfOps = numberOfOps;
        
            this.interval = interval;
        
            this.verbose = verbose;
            //this.proxy = new ServiceProxy(id);
            //this.request = new byte[this.requestSize];
            
            store = new ParallelKVStore(id, parallel);
            //this.dos = dos;
        }

        private boolean insertValue(int index){
		
		Random rand = new Random();
		/*byte[] valueBytes = new byte[VALUE_SIZE];
		rand.nextBytes(valueBytes);*/
                
                byte[] valueBytes = new String("Key"+index).getBytes();
		byte[] resultBytes = (byte[])store.put(index, valueBytes);
                        
//		System.out.println("resultBytes" + resultBytes);
		if(resultBytes == null)
			return false;
		return true;
		
	}
        
        public void run() {
        
            System.out.println("Warm up...");

            int req = 0;

            for (int i = 0; i < numberOfOps / 2; i++, req++) {
                if (verbose) {
                    System.out.print("Sending req " + req + "...");
                }
               // if (conflict_all) {
                //    reply = proxy.invokeParallel(request, parId);
                //insertValue((i+1)*this.id);
                 byte[] ret = (byte[]) store.get(i);
                 //System.out.println("resultado lido= "+ new String(ret));
//} else {
               //     reply = proxy.invokeParallel(request, ParallelMapping.CONFLICT_NONE);
                    //reply = proxy.invokeOrdered(request);
               // }
                if (verbose) {
                    System.out.println(" sent!");
                }

                if (verbose && (req % 1000 == 0)) {
                    System.out.println(this.id + " // " + req + " operations sent!");
                }
                
            }

            Storage st = new Storage(numberOfOps / 2);

            System.out.println("Executing experiment for " + numberOfOps / 2 + " ops");

            for (int i = 0; i < numberOfOps / 2; i++, req++) {
                
                if (verbose) {
                    System.out.print(this.id + " // Sending req " + req + "...");
                }
                long last_send_instant = System.nanoTime();
                byte[] ret = (byte[]) store.get(i);
                
                
                //if (conflict_all) {
                    
                //} else {
                   // reply = proxy.invokeParallel(request, ParallelMapping.CONFLICT_NONE);
                    //reply = proxy.invokeOrdered(request);
               // }
                if (verbose) {
                    System.out.println(this.id + " // sent!");
                }
                st.store(System.nanoTime() - last_send_instant);
                //System.out.println("resultado lido= "+ new String(ret));
                if (interval > 0) {
                    try {
                        //sleeps interval ms before sending next request
                        Thread.sleep(interval);
                    } catch (InterruptedException ex) {
                    }
                }

                if (verbose && (req % 1000 == 0)) {
                    System.out.println(this.id + " // " + req + " operations sent!");
                }
                
            }

            if (id == initId) {
                System.out.println(this.id + " // Average time for " + numberOfOps / 2 + " executions (-10%) = " + st.getAverage(true) / 1000 + " us ");
                System.out.println(this.id + " // Standard desviation for " + numberOfOps / 2 + " executions (-10%) = " + st.getDP(true) / 1000 + " us ");
                System.out.println(this.id + " // Average time for " + numberOfOps / 2 + " executions (all samples) = " + st.getAverage(false) / 1000 + " us ");
                System.out.println(this.id + " // Standard desviation for " + numberOfOps / 2 + " executions (all samples) = " + st.getDP(false) / 1000 + " us ");
                System.out.println(this.id + " // Maximum time for " + numberOfOps / 2 + " executions (all samples) = " + st.getMax(false) / 1000 + " us ");
            }

            //proxy.close();
        }
        
        
    }
}
