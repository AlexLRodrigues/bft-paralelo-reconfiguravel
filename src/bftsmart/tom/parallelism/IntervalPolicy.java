package bftsmart.tom.parallelism;

import bftsmart.tom.core.messages.TOMMessage;
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author alex
 */
public class IntervalPolicy implements PSMRReconfigurationPolicy{
   
    int conflict = 0;
    int nconflict = 0;

    //intervalo de verificação(amostra)
    private final int interval = 10000;
    
    @Override
    public int requestReceived(TOMMessage request, int minThreads, int currentThreads, int maxThreads) {
        
        if (request.getGroupId() == ParallelMapping.CONFLICT_ALL) {
            conflict++;
        }else if (request.getGroupId() == ParallelMapping.CONFLICT_NONE) {
            nconflict++;
        }

        if (conflict + nconflict == interval) {

            int cp = (conflict * 100) / interval;

            this.conflict = 0;
            this.nconflict = 0;
            
            if (cp < 20) {
                return maxThreads - currentThreads; //total
            } else if (cp < 50){
                return ((maxThreads*60)/100 - currentThreads); // 60%
            } else if (cp < 75){
                return ((maxThreads*30)/100 - currentThreads); //30%
            }else{
                return (currentThreads - 1) * (-1); //sequencial mode
            }
        }

        return 0;
    }
    
}
