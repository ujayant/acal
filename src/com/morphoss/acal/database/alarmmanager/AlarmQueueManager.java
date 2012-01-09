package com.morphoss.acal.database.alarmmanager;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;

import android.content.Context;
import android.os.ConditionVariable;

import com.morphoss.acal.database.DataChangeEvent;
import com.morphoss.acal.database.DatabaseTableManager;
import com.morphoss.acal.database.resourcesmanager.ResourceChangedEvent;
import com.morphoss.acal.database.resourcesmanager.ResourceChangedListener;
import com.morphoss.acal.database.resourcesmanager.ResourceManager;

/**
 * This manager manages the Alarm Database Table(s). It will listen to changes to resources and update the DB
 * automatically. AlarmRequests can be sent to query the table and to notify of changes in alarm state (e.g. dismiss/snooze)
 * 
 * @author Chris Noldus
 *
 */
public class AlarmQueueManager implements Runnable, ResourceChangedListener  {

	//The current instance
	private static AlarmQueueManager instance = null;
	public static final String TAG = "aCal AlarmQueueManager";

	//Get an instance
	public synchronized static AlarmQueueManager getInstance(Context context) {
		if (instance == null) instance = new AlarmQueueManager(context);
		return instance;
	}

	//get and instance and add a callback handler to receive notfications of change
	//It is vital that classes remove their handlers when terminating
	public synchronized static AlarmQueueManager getInstance(Context context, AlarmChangedListener listener) {
		if (instance == null) {
			instance = new AlarmQueueManager(context);
		}
		instance.addListener(listener);
		return instance;
	}

	private Context context;

	//ThreadManagement
	private ConditionVariable threadHolder = new ConditionVariable();
	private Thread workerThread;
	private boolean running = true;
	private final ConcurrentLinkedQueue<AlarmRequest> queue = new ConcurrentLinkedQueue<AlarmRequest>();
	
	//Comms
	private final CopyOnWriteArraySet<AlarmChangedListener> listeners = new CopyOnWriteArraySet<AlarmChangedListener>();
	private ResourceManager rm;
	
	//Request Processor Instance
	private AlarmTableManager ATMinstance;


	private AlarmTableManager getATMInstance() {
		if (instance == null) ATMinstance = new AlarmTableManager();
		return ATMinstance;
	}
	
	/**
	 * CacheManager needs a context to manage the DB. Should run under AcalService.
	 * Loadstate ensures that our DB is consistant and should be run before any resource
	 * modifications can be made by any other part of the system.
	 */
	private AlarmQueueManager(Context context) {
		this.context = context;
		this.ATMinstance = this.getATMInstance();
		rm = ResourceManager.getInstance(context);
		rm.addListener(this);
		workerThread = new Thread(this);
		workerThread.start();

	}
	
	/**
	 * Add a lister to change events. Change events are fired whenever a change to the DB occurs
	 * @param ccl
	 */
	public void addListener(AlarmChangedListener ccl) {
		synchronized (listeners) {
			this.listeners.add(ccl);
		}
	}
	
	/**
	 * Remove an existing listener. Listeners should always be removed when they no longer require changes.
	 * @param ccl
	 */
	public void removeListener(AlarmChangedListener ccl) {
		synchronized (listeners) {
			this.listeners.remove(ccl);
		}
	}
	
	/**
	 * Ensures that this classes closes properly. MUST be called before it is terminated
	 */
	public void close() {
		this.running = false;
		//Keep waking worker thread until it dies 
		while (workerThread.isAlive()) {
			threadHolder.open();
			Thread.yield();
			try { Thread.sleep(100); } catch (Exception e) { }
		}
		workerThread = null;
	}
	
	/**
	 * Method for responding to requests from activities.
	 */
	@Override
	public void run() {
		Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
		while (running) {
			//do stuff
			while (!queue.isEmpty()) {
				AlarmRequest request = queue.poll();
				ATMinstance.process(request);
			}
			//Wait till next time
			threadHolder.close();
			threadHolder.block();
		}
	}
	
	/**
	 * A resource has changed. we need to see if this affects our table
	 */
	@Override
	public void resourceChanged(ResourceChangedEvent event) {
		// TODO Auto-generated method stub
	}
	
	/**
	 * Send a request to the AlarmManager. Requests are queued and processed a-synchronously. No guarantee is given as to 
	 * the order of processing, so if sending multiple requests, consider potential race conditions.
	 * @param request
	 * @throws IllegalStateException thrown if close() has been called.
	 */
	public void sendRequest(AlarmRequest request) throws IllegalStateException {
		if (instance == null || this.workerThread == null || this.ATMinstance == null) 
			throw new IllegalStateException("AM in illegal state - probably because sendRequest was called after close() has been called.");
		queue.offer(request);
		threadHolder.open();
	}
	
	
	private class AlarmTableManager extends DatabaseTableManager {
		/**
		 * Generate a new instance of this processor.
		 * WARNING: Only 1 instance of this class should ever exist. If multiple instances are created bizarre 
		 * side affects may occur, including Database corruption and program instability
		 */
		private AlarmTableManager() {
			super(AlarmQueueManager.this.context);
		}

		public void process(AlarmRequest request) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void dataChanged(ArrayList<DataChangeEvent> changes) {
			// TODO Auto-generated method stub
			
		}

		@Override
		protected String getTableName() {
			// TODO Auto-generated method stub
			return null;
		}
	}
}
