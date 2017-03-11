/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom.parallelism;

import bftsmart.tom.core.messages.TOMMessage;

/**
 *
 * @author alex
 */
public class LazyPolicy implements PSMRReconfigurationPolicy {

    private int conflict = 0;
    private int nconflict = 0;
    //intervalo de verificação(amostra)
    private final int interval = 10000;

    @Override
    public int requestReceived(TOMMessage request, int minThreads, int currentThreads, int maxThreads) {
        if (request.getGroupId() == ParallelMapping.CONFLICT_ALL) {
            conflict++;
        } else if (request.getGroupId() == ParallelMapping.CONFLICT_NONE) {
            nconflict++;
        }

        if ((conflict + nconflict) == interval) {

            int cp = (conflict * 100) / interval;

            this.conflict = 0;
            this.nconflict = 0;
            if (cp > 50) {
                return -1;
            }
            if (cp < 20) {
                return 1;
            }

        }

        return 0;
    }

}
