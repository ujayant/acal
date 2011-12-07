package com.morphoss.acal.resources;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;

import android.content.ContentQueryMap;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.ConditionVariable;
import android.util.Log;

import com.morphoss.acal.Constants;

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

	//Request Processor Instance
	//Instance
	private RequestProcessor RPinstance;


	private RequestProcessor getRPInstance() {
		if (instance == null) RPinstance = new RequestProcessor();
		return RPinstance;
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
					getRPInstance().process(request);
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

	//This special class provides encapsulation of database operations as is set up to enforce
	//Scope. I.e. ONLY ResourceManager can start a request
	public class RequestProcessor {

		public static final String		_ID					= "_id";
		public static final String		COLLECTION_ID		= "collection_id";
		public static final String		RESOURCE_NAME		= "name";
		public static final String		ETAG				= "etag";
		public static final String		LAST_MODIFIED		= "last_modified";
		public static final String		CONTENT_TYPE		= "content_type";
		public static final String		RESOURCE_DATA		= "data";
		public static final String		NEEDS_SYNC			= "needs_sync";
		public static final String		EARLIEST_START		= "earliest_start";
		public static final String		LATEST_END			= "latest_end";
		public static final String		EFFECTIVE_TYPE		= "effective_type";

		// This is not a field, but we sometimes put this into the ContentValues as if
		// it were, when there is a pending change for this resource.
		public static final String IS_PENDING="is_pending";


		public static final String TYPE_EVENT="'VEVENT'";
		public static final String TYPE_TASK="'VTODO'";
		public static final String TYPE_JOURNAL="'VJOURNAL'";
		public static final String TYPE_ADDRESS="'VCARD'";

		//Database + Table
		private SQLiteDatabase AcalDB;
		private static final String DATABASE_TABLE = "dav_resource";

		public static final String TAG = "acal Resources RequestProccessor";

		private ResourcesRequest currentRequest;

		private RequestProcessor() { }

		public void process(ResourcesRequest r) {
			currentRequest = r;
			try {
				r.process(this);
			} catch (ResourceProccessingException e) {
				Log.e(TAG, "Error Procssing Resource Request: "+Log.getStackTraceString(e));
			} catch (Exception e) {
				Log.e(TAG, "INVALID TERMINATION while processing Resource Request: "+Log.getStackTraceString(e));
			} finally {
				//make sure db was closed properly
				closeDB();
			}
			currentRequest = null;
		}

		public ContentResolver getContentResolver() {
			return ResourcesManager.this.context.getContentResolver();
		}

		public SQLiteDatabase getReadableDB() {
			//TODO 
			return null;
		}

		public SQLiteDatabase getWriteableDB() {
			//TODO
			return null;
		}

		public void closeDB() {

		}

		//this little puppy deliberately obfuscates table name so no-one will try and do
		//db mods out of scope. Of course there always ways around it but it should server
		//at least as a warning - DONT MODIFY THIS TABLE unless you are RR and have permission.
		public String getTableName(ResourcesRequest rr) {
			if (currentRequest == null || currentRequest != rr) {
				throw new IllegalStateException("Some bugger is accessing DavResources without my permission!");
			}
			return DATABASE_TABLE;
		}

		public ConnectivityManager getConectivityService() {
			return (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		}

		/**
		 * Method to retrieve a particular database row for a given resource ID.
		 */
		public ContentValues getRow(long rid) {
			SQLiteDatabase db = this.getReadableDB();
			ContentValues resourceData = null;
			Cursor c = null;
			try {
				c =  db.query(DATABASE_TABLE, null, _ID+" = ?", new String[]{rid+""}, null, null, null);
				if ( !c.moveToFirst() ) {
					if ( Constants.LOG_DEBUG )
						Log.d(TAG, "No dav_resource row for collection " + Long.toString(rid));
					c.close();
					closeDB();
					return null;
				}
				resourceData = new ContentValues();
				DatabaseUtils.cursorRowToContentValues(c,resourceData);
			}
			catch (Exception e) {
				// Error getting data
				Log.e(TAG, "Error getting dav_resources data from DB: " + e.getMessage());
				Log.e(TAG, Log.getStackTraceString(e));
				c.close();
				closeDB();
				return null;
			}

			c.close();
			closeDB();
			return resourceData;
		}

		public Context getContext() {
			return context;
		}


		/**
		 * Static method to retrieve a particular database row for a given collectionId & resource name.
		 * @param collectionId
		 * @param name
		 * @param contentResolver
		 * @return A ContentValues which is the dav_resource row, or null
		 */
		public ContentValues getResourceInCollection(long collectionId, String name) {
			ContentValues resourceData = null;
			SQLiteDatabase db = this.getReadableDB();
			Cursor c = null;
			try {
				c = db.query(DATABASE_TABLE,  null, RESOURCE_NAME+"=?", new String[] { name }, null,null,null);
				if ( !c.moveToFirst() ) {
					if ( Constants.LOG_DEBUG )
						Log.d(TAG, "No dav_resource row for collection " + Long.toString(collectionId)+", "+name);
					c.close();
					closeDB();
					return null;
				}
				resourceData = new ContentValues();
				DatabaseUtils.cursorRowToContentValues(c,resourceData);
			}
			catch (Exception e) {
				// Error getting data
				Log.e(TAG, "Error getting server data from DB: " + e.getMessage());
				Log.e(TAG, Log.getStackTraceString(e));
				c.close();
				closeDB();
				return null;
			}
			finally {
				c.close();
				closeDB();
			}
			return resourceData;
		}

		/**
		 * <p>
		 * Finds the resources which have been marked as needing synchronisation in our local database.
		 * </p>
		 * 
		 * @return A map of String/Data which are the hrefs we need to sync
		 */
		public Map<String, ContentValues> findSyncNeededResources(long collectionId) {
			long start = System.currentTimeMillis();
			Map<String, ContentValues> originalData = null;

			// step 1a get list of resources from db
			start = System.currentTimeMillis();

			SQLiteDatabase db = this.getReadableDB();
			Cursor mCursor = db.query(DATABASE_TABLE, null, COLLECTION_ID+" = ? "+NEEDS_SYNC + " = 1 OR "+RESOURCE_DATA+" IS NULL" , new String[]{collectionId+""}, null, null, null);
			ContentQueryMap cqm = new ContentQueryMap(mCursor, RequestProcessor.RESOURCE_NAME, false, null);
			cqm.requery();
			originalData = cqm.getRows();
			mCursor.close();
			closeDB();
			if (Constants.LOG_VERBOSE && Constants.debugSyncCollectionContents )
				Log.println(Constants.LOGV,TAG, "DavCollections ContentQueryMap retrieved in " + (System.currentTimeMillis() - start) + "ms");
			return originalData;
		}

		/**
		 * Returns a Map of href to database record for the current database state.
		 * @return
		 */
		public Map<String,ContentValues> getCurrentResourceMap(long collectionId) {
			SQLiteDatabase db = this.getReadableDB();
			
			Cursor resourceCursor = db.query(
						DATABASE_TABLE,
						new String[] { RequestProcessor._ID, RequestProcessor.RESOURCE_NAME, RequestProcessor.ETAG }, 
						COLLECTION_ID+" = ? ",
						new String[]{collectionId+""},
						null, null, null);
			if ( !resourceCursor.moveToFirst()) {
				resourceCursor.close();
				return new HashMap<String,ContentValues>();
			}
			ContentQueryMap cqm = new ContentQueryMap(resourceCursor, RequestProcessor.RESOURCE_NAME, false, null);
			cqm.requery();
			Map<String, ContentValues> databaseList = cqm.getRows();
			cqm.close();
			resourceCursor.close();
			db.close();
			return databaseList;
		}
		
		public int deleteByCollectionId(long id) {
			SQLiteDatabase db = this.getWriteableDB();
			int count = db.delete(DATABASE_TABLE, COLLECTION_ID+" = ?", new String[]{id+""});
			closeDB();
			return count;
		}
	}
	
	
	
}