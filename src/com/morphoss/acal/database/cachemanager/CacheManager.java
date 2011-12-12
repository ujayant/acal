package com.morphoss.acal.database.cachemanager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.ConditionVariable;
import android.util.Log;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.AcalDBHelper;
import com.morphoss.acal.database.DatabaseTableManager;
import com.morphoss.acal.database.DatabaseTableManager.DataChangeEvent;
import com.morphoss.acal.database.resourcesmanager.RRGetEventsInRange;
import com.morphoss.acal.database.resourcesmanager.ResourceChangedEvent;
import com.morphoss.acal.database.resourcesmanager.ResourceChangedListener;
import com.morphoss.acal.database.resourcesmanager.ResourceManager;
import com.morphoss.acal.database.resourcesmanager.ResourceResponse;
import com.morphoss.acal.database.resourcesmanager.ResourceResponseListener;
import com.morphoss.acal.database.resourcesmanager.RRGetEventsInRange.RREventsInRangeResponse;
import com.morphoss.acal.dataservice.EventInstance;
import com.morphoss.acal.dataservice.Resource;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.davacal.VComponentCreationException;

/**
 * 	
 * This class provides an interface for things that want access to the cache.
 * 
 * Call the static getInstance method to get an instance of this Manager.
 * 
 * To use, call sendRequest
 *
 * @author Chris Noldus
 *
 */
public class CacheManager implements Runnable, ResourceChangedListener,  ResourceResponseListener<ArrayList<EventInstance>> {




	//The current instance
	private static CacheManager instance = null;
	public static final String TAG = "aCal CacheManager";

	//Get an instance
	public synchronized static CacheManager getInstance(Context context) {
		if (instance == null) instance = new CacheManager(context);
		return instance;
	}

	//get and instance and add a callback handler to receive notfications of change
	//It is vital that classes remove their handlers when terminating
	public synchronized static CacheManager getInstance(Context context, CacheChangedListener listener) {
		if (instance == null) instance = new CacheManager(context);
		instance.addListener(listener);
		return instance;
	}

	private Context context;
	private long count;	//Number of rows in DB
	private AcalDateTime start; //Current Start time of window
	private AcalDateTime end; //Current End time of window
	private long metaRow; //Current End time of window

	//ThreadManagement
	private ConditionVariable threadHolder = new ConditionVariable();
	private ConditionVariable DBBlocker = new ConditionVariable();
	private boolean pauseQueue = false;
	private Thread workerThread;
	private boolean running = true;
	private final ConcurrentLinkedQueue<CacheRequest> queue = new ConcurrentLinkedQueue<CacheRequest>();


	//DB Constants
	private static final String META_TABLE = "event_cache_meta";
	private static final String FIELD_ID = "_id";
	private static final String FIELD_START = "dtstart";
	private static final String FIELD_END = "dtend";
	private static final String FIELD_COUNT = "count";
	private static final String FIELD_CLOSED = "closed";

	//Comms
	private final CopyOnWriteArraySet<CacheChangedListener> listeners = new CopyOnWriteArraySet<CacheChangedListener>();
	private ResourceManager rm;
	
	//Request Processor Instance
	private CacheTableManager CTMinstance;


	private CacheTableManager getECPInstance() {
		if (instance == null) CTMinstance = new CacheTableManager();
		return CTMinstance;
	}
	
	//Settings
	private static final int DEF_MONTHS_BEFORE = 1;		//these 2 represent the default window size
	private static final int DEF_MONTHS_AFTER = 2;		//relative to todays date
	
	
	/**
	 * CacheManager needs a context to manage the DB. Should run under AcalService.
	 * Loadstate ensures that our DB is consistant and should be run before any resource
	 * modifications can be made by any other part of the system.
	 */
	private CacheManager(Context context) {
		this.context = context;
		this.CTMinstance = this.getECPInstance();
		rm = ResourceManager.getInstance(context);
		loadState();
		DBBlocker.open();
		workerThread = new Thread(this);
		workerThread.start();
		
	}


	/**
	 * Add a lister to change events. Change events are fired whenever a change to the DB occurs
	 * @param ccl
	 */
	public void addListener(CacheChangedListener ccl) {
		synchronized (listeners) {
			this.listeners.add(ccl);
		}
	}
	
	/**
	 * Remove an existing listener. Listeners should always be removed when they no longer require changes.
	 * @param ccl
	 */
	public void removeListener(CacheChangedListener ccl) {
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
		saveState();
		workerThread = null;
	}

	/**
	 * MUST set SAFE to true or cache will be flushed on next load.
	 * Nothing should be able to modify resources after this point.
	 *
	 */
	private void saveState() {
		//save start/end range to meta table
		ContentValues data = new ContentValues();
		data.put(FIELD_START, start.getMillis());
		data.put(FIELD_END, end.getMillis());
		data.put(FIELD_COUNT, count);
		data.put(FIELD_CLOSED, true);

		AcalDBHelper dbHelper = new AcalDBHelper(context);
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		//set CLOSED to true
		db.update(META_TABLE, data, FIELD_ID+" = ?", new String[] {metaRow+""});
		db.close();
		dbHelper.close();
		
		//dereference ourself so GC can clean up
		instance = null;
		this.CTMinstance = null;
		rm.removeListener(this);
		rm = null;
	}

	/**
	 * Called on start up. if safe==false flush cache. set safe to false regardless.
	 */
	private void loadState() {
		ContentValues data = new ContentValues();
		AcalDBHelper dbHelper = new AcalDBHelper(context);
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		//load start/end range from meta table
		Cursor cr = db.query(META_TABLE, null, null, null, null, null, null);
		AcalDateTime defaultWindow = new AcalDateTime();
		if (cr.getCount() < 1) {
			Log.d(TAG, "Initializing cache for first use.");
			data.put(FIELD_CLOSED, true);
			data.put(FIELD_COUNT, 0);
			data.put(FIELD_START,  defaultWindow.getMillis());
			data.put(FIELD_END,  defaultWindow.getMillis());
		} else  {
			cr.moveToFirst();
			DatabaseUtils.cursorRowToContentValues(cr, data);
		}
		cr.close();
		if (!data.getAsBoolean(FIELD_CLOSED)) {
			Log.d(TAG, "Application not closed correctly last time. Resetting cache.");
			this.CTMinstance.clearCache();
			data.put(FIELD_COUNT, 0);
			data.put(FIELD_START,  defaultWindow.getMillis());
			data.put(FIELD_END,  defaultWindow.getMillis());
		}
		else data.put(FIELD_CLOSED, false);

		//set CLOSED to false
		db.delete(META_TABLE, null, null);
		data.remove(FIELD_ID);
		this.metaRow = db.insert(META_TABLE, null, data);
		db.close();
		dbHelper.close();
		this.start = AcalDateTime.fromMillis(data.getAsLong(FIELD_START));
		this.end = AcalDateTime.fromMillis(data.getAsLong(FIELD_END));
		this.count = data.getAsLong(FIELD_COUNT);
		
		rm.addListener(this);

	}

	/**
	 * Method for responding to requests from activities.
	 */
	@Override
	public void run() {
		while (running) {
			//do stuff
			while (!queue.isEmpty()) {
				if (pauseQueue) {
					try { Thread.sleep(100); } catch (Exception e) { }
					continue;
				}
				CacheRequest request = queue.poll();
				CTMinstance.process(request);
			}
			//Wait till next time
			threadHolder.close();
			threadHolder.block();
		}
	}

	/**
	 * Send a request to the CacheManager. Requests are queued and processed a-synchronously. No guarantee is given as to 
	 * the order of processing, so if sending multiple requests, consider potential race conditions.
	 * @param request
	 * @throws IllegalStateException thrown if close() has been called.
	 */
	public void sendRequest(CacheRequest request) throws IllegalStateException {
		if (instance == null || this.workerThread == null || this.CTMinstance == null) 
			throw new IllegalStateException("CM in illegal state - probably because sendRequest was called after close() has been called.");
		queue.offer(request);
		threadHolder.open();
	}
	
	//Request events (FROM RESOURCES) that start within the range provided. Expand window on result.
	private void retrieveRange(long start, long end) {
		AcalDateRange range = new AcalDateRange(AcalDateTime.fromMillis(start), AcalDateTime.fromMillis(end));
		Log.d(TAG,"Retreiving events in range "+range.start+"-->"+range.end);
		ResourceManager.getInstance(context).sendRequest(new RRGetEventsInRange(range, this));
	}
	
	@Override
	public void resourceResponse(ResourceResponse<ArrayList<EventInstance>> response) {
		if (response instanceof RREventsInRangeResponse<?>) {
			RREventsInRangeResponse<ArrayList<EventInstance>> res = (RREventsInRangeResponse<ArrayList<EventInstance>>) response;
			//put new data on the process queue
			synchronized(CTMinstance) {
				pauseQueue = true;
				DBBlocker.block();
			}
			
			Log.d(TAG, "Have response from Resource manager for range request.");
			//We should have exclusive DB access at this point
			AcalDateRange range = res.requestedRange();
			ArrayList<EventInstance> events = res.result();
			CTMinstance.beginTransaction();
			Log.d(TAG, "Deleteing Existing records...");
			int count = CTMinstance.delete(FIELD_START+" >= ? AND "+FIELD_START+" <= ?", new String[]{range.start.getMillis()+"", range.end.getMillis()+""});
			Log.d(TAG, count + "Records Deleted. Inserting new records...");
			count = 0;
			for (EventInstance ei : events) {
				CTMinstance.insert(null, CacheObject.fromEventInstance(ei).getCacheCVs());
				count++;
			}
			Log.d(TAG,count+" Records Inserted.");
			
			
			
			CTMinstance.setTxSuccessful();
			CTMinstance.endTransaction();
			if (range.start.before(this.start)) this.start = range.start.clone();
			if (range.end.after(this.end)) this.end = range.end.clone();
			Log.d(TAG, "Cache Window: "+start+" -> "+end);
			pauseQueue = false;
			
		}
	}

	/**
	 * Static class to encapsulate all database operations 
	 * @author Chris Noldus
	 *
	 */
	public final class CacheTableManager extends DatabaseTableManager {
		
		public static final String TAG = "acal EventCacheProcessor";
		
		private static final String TABLE = "event_cache";
		public static final String FIELD_ID = "_id";
		public static final String FIELD_RESOURCE_ID = "resource_id";
		public static final String FIELD_CID ="collection_id";
		public static final String FIELD_SUMMARY ="summary";
		public static final String FIELD_LOCATION ="location";
		public static final String FIELD_DTSTART = "dtstart";
		public static final String FIELD_DT_END = "dtend";
		public static final String FIELD_FLAGS ="flags";
		
		/**
		 * The current request being processed. Presently not used but may become useful.
		 */
		//private CacheRequest currentRequest;

		/**
		 * Generate a new instance of this processor.
		 * WARNING: Only 1 instance of this class should ever exist. If multiple instances are created bizarre 
		 * side affects may occur, including Database corruption and program instability
		 */
		private CacheTableManager() {
			super(CacheManager.this.context);
		}
		
		@Override
		protected String getTableName() {
			return TABLE;
		}

		/**
		 * Process a CacheRequest. This class will provide an interface to the CacheRequest giving it access to the Cache Table.
		 * Will warn if given request has misused the DB, but will not cause program to exit. Will ensure that database state is kept
		 * consistant.
		 * @param r
		 */
		public synchronized void process(CacheRequest r) { 
			//currentRequest = r;

			DBBlocker.close();
			try {
				r.process(this);
				if (this.inTx) {
					this.endTransaction();
					throw new CacheProcessingException("Process started a transaction without ending it!");
				}
			} catch (CacheProcessingException e) {
				Log.e(TAG, "Error Procssing Resource Request: "+Log.getStackTraceString(e));
			} catch (Exception e) {
				Log.e(TAG, "INVALID TERMINATION while processing Resource Request: "+Log.getStackTraceString(e));
			} finally {
				//make sure db was closed properly
				if (this.db != null)
				try { endQuery(); } catch (Exception e) { }
			}
			//currentRequest = null;
			DBBlocker.open();
		}

		
		/**
		 * Called when table is deemed to have been corrupted.
		 */
		private void clearCache() {
			this.beginTransaction();
			this.delete(null, null);
			this.setTxSuccessful();
			this.endTransaction();
		}
		
		
		/**Checks that the window has been populated with the requested range
		 * range can be NULL in which case the default range is used.
		 * If the range is NOT covered, a request is made to resource
		 * manager to get the required data.
		 * Returns weather or not the cache fully covers a specified (or default) range
		 */
		public boolean checkWindow(AcalDateRange requestedRange) {
			Log.d(TAG,"Checking Cache Window: Request "+requestedRange.start.getMillis()+" --> "+requestedRange.end.getMillis());
			Log.d(TAG,"Checking Cache Window: Current Window:"+ CacheManager.this.start.getMillis()+" --> "+CacheManager.this.end.getMillis());
			long start;
			long end;
			if (requestedRange != null) {
				start = requestedRange.start.getMillis();
				end = requestedRange.start.getMillis();
			} else {
				AcalDateTime st = new AcalDateTime();
				AcalDateTime en = new AcalDateTime();
				st.addMonths(-DEF_MONTHS_BEFORE);
				en.addMonths(DEF_MONTHS_AFTER);
				start =st.getMillis();
				end = en.getMillis();
			}
			long wStart = CacheManager.this.start.getMillis();
			long wEnd = CacheManager.this.end.getMillis();
			
			//start & end should fall between this.start and this.end
			//check start >= this.strt && <= this.end && end <= this.end
			if (start >= wStart && start <= wEnd && end <= wEnd) {
				Log.d(TAG, "Cache Window already large enough");
				return true;
			}

			Log.d(TAG, "Expanding Cache Window");
			
			//we now need to work out what range we need to request
			//3 Options - 
					//start -> wStart
					//wEnd -> wEnd
					//or both
			if (wStart == wEnd) retrieveRange(start, end);
			else {
				if (start < wStart) retrieveRange(start, wStart);
				if (end > wEnd) retrieveRange(wEnd,end);
			}
			return false;
		}

		@Override
		public void dataChanged(List<DataChangeEvent> changes) {
			if (changes.isEmpty()) return;
			CacheChangedEvent cce = new CacheChangedEvent(changes);
			synchronized (listeners) {
				for (CacheChangedListener listener: listeners) {
					listener.cacheChanged(cce);
				}
			}
			
		}

		public void resourceDeleted(long rid) {
			this.delete(FIELD_RESOURCE_ID+" = ?", new String[]{rid+""});
		}	
		
		/**
		 * Begin std DB Operations
		 * 
		 * ALL db operations need to start with a beginQuery call and end with an endQuery call.
		 * DO NOT Open/Close DB directly as db my be in a Transaction. The parent class is responsible for maintaining state.
		 *
		 *	If writing to DB without using parent methods, don't forget to kick of db change events!
		 */
		
		
	}
	
	public static CacheObject fromContentValues(ContentValues row) {
		return new CacheObject(
					row.getAsLong(CacheTableManager.FIELD_RESOURCE_ID), 
					row.getAsInteger(CacheTableManager.FIELD_CID),
					row.getAsString(CacheTableManager.FIELD_SUMMARY),
					row.getAsString(CacheTableManager.FIELD_LOCATION),
					row.getAsLong(CacheTableManager.FIELD_DTSTART),
					row.getAsLong(CacheTableManager.FIELD_DT_END),
					row.getAsInteger(CacheTableManager.FIELD_FLAGS)
				);
	}

	@Override
	public void resourceChanged(ResourceChangedEvent event) {
		// TODO Auto-generated method stub
		List<DataChangeEvent> changes = event.getChanges();
		if (changes == null || changes.isEmpty()) return;	//dont care
		for (DataChangeEvent change : changes) {
			
			//TODO NOTE: changes need to be thread safe!
			
			switch (change.action) {
			case INSERT:
			case UPDATE:
				Resource r = event.getResource(change);
				//If this resource in our window?
				
				//Construct resource
				VComponent comp;
				try {
					comp = VComponent.createComponentFromResource(r);
				} catch (VComponentCreationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				//get instances within window
				
				//replace all existing with same RID 
				
				break;
			case DELETE:
				long rid = change.getData().getAsLong(ResourceManager.ResourceTableManager.RESOURCE_ID);
				//delete
			}
		}
	}

	
}
