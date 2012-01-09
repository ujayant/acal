package com.morphoss.acal.database.alarmmanager;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Semaphore;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.ConditionVariable;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.AcalDBHelper;
import com.morphoss.acal.database.DMQueryList;
import com.morphoss.acal.database.DataChangeEvent;
import com.morphoss.acal.database.DatabaseTableManager;
import com.morphoss.acal.database.cachemanager.CacheManager;
import com.morphoss.acal.database.cachemanager.CacheProcessingException;
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
	
	//Meta Table Management
	private static Semaphore lockSem = new Semaphore(1, true);
	private static volatile boolean lockdb = false;
	private long metaRow = 0;
	//DB Constants
	private static final String META_TABLE = "alarm_meta";
	private static final String FIELD_ID = "_id";
	private static final String FIELD_CLOSED = "closed";

	
	
	//Comms
	private final CopyOnWriteArraySet<AlarmChangedListener> listeners = new CopyOnWriteArraySet<AlarmChangedListener>();
	private ResourceManager rm;
	
	//Request Processor Instance
	private AlarmTableManager ATMinstance;


	//States
	public enum ALARM_STATE { PENDING, DISMISSED, SNOOZED };

	
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
		loadState();
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
	
	private synchronized static void acquireMetaLock() {
		try { lockSem.acquire(); } catch (InterruptedException e1) {}
		while (lockdb) try { Thread.sleep(10); } catch (Exception e) { }
		lockdb = true;
		lockSem.release();
	}
	
	private synchronized static void releaseMetaLock() {
		if (!lockdb) throw new IllegalStateException("Cant release a lock that hasnt been obtained!");
		lockdb = false;
	}

	
	/**
	 * Called on start up. if safe==false flush cache. set safe to false regardless.
	 */
	private void loadState() {
		acquireMetaLock();
		ContentValues data = new ContentValues();
		AcalDBHelper dbHelper = new AcalDBHelper(context);
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		//load start/end range from meta table
		AcalDateTime defaultWindow = new AcalDateTime();
		Cursor mCursor = db.query(META_TABLE, null, null, null, null, null, null);
		int closedState = 0;
		try {
			if (mCursor.getCount() < 1) {
				if ( CacheManager.DEBUG && Constants.LOG_DEBUG ) Log.println(Constants.LOGD,TAG, "Initializing cache for first use.");
				data.put(FIELD_CLOSED, 1);
				rebuild();
				
			} else  {
				mCursor.moveToFirst();
				DatabaseUtils.cursorRowToContentValues(mCursor, data);
			}
			closedState = data.getAsInteger(FIELD_CLOSED);
		}
		catch( Exception e ) {
			Log.i(TAG,Log.getStackTraceString(e));
		}
		finally {
			if ( mCursor != null ) mCursor.close();
		}

		if ( !(closedState == 1)) {
			Log.println(Constants.LOGI,TAG, "Rebuiliding alarm cache.");
			rebuild();
		}
		data.put(FIELD_CLOSED, 1);
		db.delete(META_TABLE, null, null);
		data.remove(FIELD_ID);
		this.metaRow = db.insert(META_TABLE, null, data);
		db.close();
		dbHelper.close();
		rm.addListener(this);
		releaseMetaLock();

	}
	
	
	/**
	 * MUST set SAFE to true or cache will be flushed on next load.
	 * Nothing should be able to modify resources after this point.
	 *
	 */
	private void saveState() {
		//save start/end range to meta table
		acquireMetaLock();
		ContentValues data = new ContentValues();
		data.put(FIELD_CLOSED, true);

		AcalDBHelper dbHelper = new AcalDBHelper(context);
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		//set CLOSED to true
		db.update(META_TABLE, data, FIELD_ID+" = ?", new String[] {metaRow+""});
		db.close();
		dbHelper.close();
		
		//dereference ourself so GC can clean up
		instance = null;
		this.ATMinstance = null;
		rm.removeListener(this);
		rm = null;
		releaseMetaLock();
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
		saveState();
	}
	
	/**
	 * Forces AlarmManager to rebuild alarms from scratch. Should only be called if table has become invalid.
	 */
	private void rebuild() {
		this.sendRequest(new ARRebuildRequest());
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
	
	
	public final class AlarmTableManager extends DatabaseTableManager {
		/**
		 * Generate a new instance of this processor.
		 * WARNING: Only 1 instance of this class should ever exist. If multiple instances are created bizarre 
		 * side affects may occur, including Database corruption and program instability
		 */
		
		private static final String TABLENAME = "alarms";
        private static final String FIELD_ID = "_id";
		private static final String FIELD_TIME_TO_FIRE = "ttf";
		private static final String FIELD_RID = "rid";
		private static final String FIELD_RRID = "rrid";
		private static final String FIELD_STATE ="state";
		
		//Change this to set how far back we look for alarms and database first time use/rebuild
		private static final int LOCKBACK_MINUTES = 4*60;		//default 4 hours
        
		private AlarmTableManager() {
			super(AlarmQueueManager.this.context);
		}

		public void process(AlarmRequest request) {
			try {
				request.process(this);
				if (this.inTx) {
					this.endTransaction();
					throw new AlarmProcessingException("Process started a transaction without ending it!");
				}
			} catch (AlarmProcessingException e) {
				Log.e(TAG, "Error Procssing Resource Request: "+Log.getStackTraceString(e));
			} catch (Exception e) {
				Log.e(TAG, "INVALID TERMINATION while processing Resource Request: "+Log.getStackTraceString(e));
			} finally {
				//make sure db was closed properly
				if (this.db != null)
				try { endQuery(); } catch (Exception e) { }
			}
		}

		@Override
		public void dataChanged(ArrayList<DataChangeEvent> changes) {
			AlarmChangedEvent event = new AlarmChangedEvent(changes);
			for (AlarmChangedListener acl : listeners) acl.alarmChanged(event);
		}

		@Override
		protected String getTableName() {
			return TABLENAME;
		}

		//Override parent db functions - we don't want other classes having direct access.
		
		@Override
		public ArrayList<ContentValues> query(String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {
			throw new UnsupportedOperationException("Direct access to Alarm Table Prohibited.");
		}
		
		@Override
		public int delete(String whereClause, String[] whereArgs) {
			throw new UnsupportedOperationException("Direct access to Alarm Table Prohibited.");
		}
		
		@Override
		public int update(ContentValues values, String whereClause,	String[] whereArgs) {
			throw new UnsupportedOperationException("Direct access to Alarm Table Prohibited.");
		}
		
		@Override
		public long insert(String nullColumnHack, ContentValues values) {
			throw new UnsupportedOperationException("Direct access to Alarm Table Prohibited.");
		}
		
		@Override
		public boolean processActions(DMQueryList queryList) {
			throw new UnsupportedOperationException("Direct access to Alarm Table Prohibited.");
		}
		
		//Custom operations
		
		/**
		 * Wipe and rebuild alarm table - called if it has become corrupted.
		 */
		public void rebuild() {
			//Display up to the last x hours of alarms.
			
			//Step 1 - request a list of all resources so we can find the next alarm trigger for each
			
			//step 2 - begin db transaction, delete all existing and insert new list
			
			//step 3 schedule alarm intent
			scheduleAlarmIntent();
		}
		
		/**
		 * Get the next alarm to go off
		 * @return
		 */
		public Object getNextDueAlarm() {
			return null;
		}
		
		/**
		 * Snooze a specific alarm.
		 * @param alarm
		 */
		public void snoozeAlarm(Object alarm) {
			
		}
		
		/**
		 * Dismiss a specific alarm
		 */
		public void dismissAlarm(Object alarm) {
			//update table row to show alarm dismissed (if exists)
			scheduleAlarmIntent();
		}
		
		/**
		 * Schedule the next alarm intent - Should be called whenever there is a change to the db.
		 */
		public void scheduleAlarmIntent() {
		
		}
	}
}
