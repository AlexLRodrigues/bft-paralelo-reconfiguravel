/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package bftsmart.tom.diversity;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;

/**
 *
 * @author alchieri
 */
public class ServerWrapper extends DefaultSingleRecoverable{

    public ServerWrapper(int id){
         new ServiceReplica(id, this, this);
    }
    
    @Override
    public byte[] appExecuteOrdered(byte[] command, MessageContext msgCtx) {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        System.out.println("executeOrdered");
        return command;
    }

    @Override
    public byte[] executeUnordered(byte[] command, MessageContext msgCtx) {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        System.out.println("executeUnordered");
        return command;
        }

    @Override
    public void installSnapshot(byte[] state) {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public byte[] getSnapshot() {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        return new byte[0];
    }


    
    
    
}
