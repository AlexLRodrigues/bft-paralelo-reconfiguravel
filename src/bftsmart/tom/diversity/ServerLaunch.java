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
public class ServerLaunch {
    
    
    public static void main(String[] args){
          if(args.length < 1) {
            System.out.println("Use: java Serverlaunch <processId>");
            System.exit(-1);
        }
        new ServerWrapper(Integer.parseInt(args[0]));
        
        
    }
}
