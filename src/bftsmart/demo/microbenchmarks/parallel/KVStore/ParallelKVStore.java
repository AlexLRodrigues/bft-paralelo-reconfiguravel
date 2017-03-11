/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.demo.microbenchmarks.parallel.KVStore;

import bftsmart.demo.bftmap.*;
import bftsmart.tom.parallelism.ParallelMapping;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.TreeMap;
import java.util.Map;

import bftsmart.tom.ServiceProxy;

/**
 * Map implementation backed by a BFT replicated table.
 * 
 * @author sweta
 */
public class ParallelKVStore implements Map<Integer,Object> {

    
        static final int CONTAINS_KEY = 1;
	static final int PUT = 2;
	static final int GET = 3;
	static final int REMOVE = 4;
        static final int SIZE = 5;
        
    
	private ServiceProxy proxy = null;
        private ByteArrayOutputStream out = null;
        
        private boolean parallel = false;
        
	public ParallelKVStore(int id, boolean parallelExecution) {
		proxy = new ServiceProxy(id);
                this.parallel = parallelExecution;
	}
	

	
	public Object get(Integer key) {
		try {
			out = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(out); 
			dos.writeInt(GET);
			dos.writeInt(key.intValue());
                        byte[] rep = null;
                        if(parallel){
                            rep = proxy.invokeParallel(out.toByteArray(),ParallelMapping.CONFLICT_NONE);
                        }else{
                            rep = proxy.invokeOrdered(out.toByteArray());
                        }
			ByteArrayInputStream bis = new ByteArrayInputStream(rep) ;
			ObjectInputStream in = new ObjectInputStream(bis) ;
                        Object resp = in.readObject();
			in.close();
			return resp;
		} catch (ClassNotFoundException ex) {
			Logger.getLogger(ParallelKVStore.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		} catch (IOException ex) {
			Logger.getLogger(ParallelKVStore.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		}

	}

	public boolean constainsKey(Integer key) {
		try {
			out = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(out); 
			dos.writeInt(CONTAINS_KEY);
			dos.writeInt(key.intValue());
			
			byte[] rep = null;
                        if(parallel){
                            rep = proxy.invokeParallel(out.toByteArray(),ParallelMapping.CONFLICT_NONE);
                        }else{
                            rep = proxy.invokeOrdered(out.toByteArray());
                        }
                        
			
                        ByteArrayInputStream bis = new ByteArrayInputStream(rep) ;
			ObjectInputStream in = new ObjectInputStream(bis) ;
                        boolean resp = in.readBoolean();
			in.close();
			return resp;
                        
                        
		} catch (IOException ex) {
			Logger.getLogger(ParallelKVStore.class.getName()).log(Level.SEVERE, null, ex);
			return false;
		}
	}

	
	public Object put(Integer key, Object value) {
		try {
			out = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(out); 
			dos.writeInt(PUT);
			dos.writeInt(key.intValue());
			//ByteArrayOutputStream bos = new ByteArrayOutputStream() ;
			ObjectOutputStream  out1 = new ObjectOutputStream(out) ;
			out1.writeObject(value);
			out1.close();
			byte[] rep = null;
                        if(parallel){
                            rep = proxy.invokeParallel(out.toByteArray(),ParallelMapping.CONFLICT_ALL);
                        }else{
                            rep = proxy.invokeOrdered(out.toByteArray());
                        }
			ByteArrayInputStream bis = new ByteArrayInputStream(rep) ;
			ObjectInputStream in = new ObjectInputStream(bis) ;
			Object ret = in.readObject();
			in.close();
			return ret;

		} catch (ClassNotFoundException ex) {
			ex.printStackTrace();
			Logger.getLogger(ParallelKVStore.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		} catch (IOException ex) {
			ex.printStackTrace();
			Logger.getLogger(ParallelKVStore.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		}
	}

	

	
	public Object remove(Integer key) {
		try {
			out = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(out); 
			dos.writeInt(REMOVE);
			dos.writeInt(key.intValue());
			byte[] rep = null;
                        if(parallel){
                            rep = proxy.invokeParallel(out.toByteArray(),ParallelMapping.CONFLICT_ALL);
                        }else{
                            rep = proxy.invokeOrdered(out.toByteArray());
                        }

			ByteArrayInputStream bis = new ByteArrayInputStream(rep) ;
			ObjectInputStream in = new ObjectInputStream(bis) ;
			Object ret =  in.readObject();
			in.close();
			return ret;
		} catch (ClassNotFoundException ex) {
			Logger.getLogger(ParallelKVStore.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		} catch (IOException ex) {
			Logger.getLogger(ParallelKVStore.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		}

	}

	
	public int size() {
		try {
			out = new ByteArrayOutputStream();
			new DataOutputStream(out).writeInt(SIZE);
			byte[] rep;
                        if(parallel){
                            rep = proxy.invokeParallel(out.toByteArray(),ParallelMapping.CONFLICT_NONE);
                        }else{
                            rep = proxy.invokeOrdered(out.toByteArray());
                        }
                        
			ByteArrayInputStream in = new ByteArrayInputStream(rep);
			int size = new DataInputStream(in).readInt();
			return size;
		} catch (IOException ex) {
			Logger.getLogger(ParallelKVStore.class.getName()).log(Level.SEVERE, null, ex);
			return -1;
		}
	}

    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean containsKey(Object key) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Object get(Object key) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void putAll(Map<? extends Integer, ? extends Object> m) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Set<Integer> keySet() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Collection<Object> values() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Set<Entry<Integer, Object>> entrySet() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}