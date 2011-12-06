package com.morphoss.acal.resources;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;

import android.content.Context;
import android.os.ConditionVariable;

public class ResourcesManager implements Runnable {
	//The current instance
	private static ResourcesManager instance = null;

	//Get an instance
	public synchronized static ResourcesManager getInstance(Context context) {
		if (instance == null) instance = new ResourcesManager(context);
		return instance;
	}

	//get and instance and add a callback handler to receive notfications of change
	//It is vital that classes remove their handlers when terminating
	public synchronized static ResourcesManager getInstance(Context context, ResourcesChangedListener listener) {
		if (instance == null) instance = new ResourcesManager(context);
		instance.addListener(listener);
		return instance;
	}

	private Context context;
	
	//ThreadManagement
	private ConditionVariable threadHolder = new ConditionVariable();
	private Thread workerThread;
	private boolean running = true;
	private final ConcurrentLinkedQueue<ResourcesRequest> queue = new ConcurrentLinkedQueue<ResourcesRequest>();
	
	//Comms
	private final CopyOnWriteArraySet<ResourcesChangedListener> listeners = new CopyOnWriteArraySet<ResourcesChangedListener>();
	
	//Cache Ops
	private ResourceDbOps dbops = new ResourceDbOps();
	
	private ResourcesManager(Context context) {
		this.context = context;
		threadHolder.close();
		workerThread = new Thread(this);
		workerThread.start();
	}


	public void addListener(ResourcesChangedListener ccl) {
		this.listeners.add(ccl);
	}

	public void removeListener(ResourcesChangedListener ccl) {
		this.listeners.remove(ccl);
	}
	
	private class ResourceDbOps {
		
	}

	@Override
	public void run() {
		while (running) {
			//do stuff
			while (!queue.isEmpty()) {
				final ResourcesRequest request = queue.poll();
				try {
					switch (request.getCode()) {
					
					
					}
				} catch (Exception e) {
					//log message
				}
			}
			//Wait till next time
			threadHolder.close();
			threadHolder.block();
		}
		
	}
	
	/**
	 * Ensures that this classes closes properly. MUST be called before it is terminated
	 */
	public synchronized void close() {
		this.running = false;
		//Keep waking worker thread until it dies 
		while (workerThread.isAlive()) {
			threadHolder.open();
			Thread.yield();
			try { Thread.sleep(100); } catch (Exception e) { }
		}
		instance = null;
	}
	
	//Request handlers
	public void sendRequest(ResourcesRequest request) {
		queue.offer(request);
		threadHolder.open();
	}
}
