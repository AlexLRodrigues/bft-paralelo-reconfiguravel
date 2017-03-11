/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package bftsmart.tom.diversity;

/**
 *
 * @author alchieri
 */
public class ClientLaunch {
    
     public static void main(String[] args){
          if(args.length < 1) {
            System.out.println("Use: java Serverlaunch <processId>");
            System.exit(-1);
        }
        ClientWrapper c = new ClientWrapper(Integer.parseInt(args[0]));
        
        byte[] resp = c.executeOrdered((new String("TESTE Ordered")).getBytes());
        
        System.out.println("Resposta executeOrdered: "+new String(resp));
        
        resp = c.executeUnordered((new String("TESTE Unordered")).getBytes());
        
        System.out.println("Resposta executeUnordered: "+new String(resp));
        
        
    }
    
}
