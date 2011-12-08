package com.morphoss.acal.resources;

import java.util.ArrayList;
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
import android.net.ConnectivityManager;
import android.os.ConditionVariable;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.DatabaseManager;

public class ResourcesManager implements Runnable {
	// The current instance
	private static ResourcesManager instance = null;

	// Get an instance
	public synchronized static ResourcesManager getInstance(Context context) {
		if (instance == null)
			instance = new ResourcesManager(context);
		return instance;
	}

	// get and instance and add a callback handler to receive notfications of
	// change
	// It is vital that classes remove their handlers when terminating
	public synchronized static ResourcesManager getInstance(Context context,
			ResourcesChangedListener listener) {
		if (instance == null)
			instance = new ResourcesManager(context);
		instance.addListener(listener);
		return instance;
	}

	// Request Processor Instance
	// Instance
	private RequestProcessor RPinstance;

	private RequestProcessor getRPInstance() {
		if (instance == null)
			RPinstance = new RequestProcessor();
		return RPinstance;
	}

	private Context context;

	// ThreadManagement
	private ConditionVariable threadHolder = new ConditionVariable();
	private Thread workerThread;
	private boolean running = true;
	private final ConcurrentLinkedQueue<ResourcesRequest> queue = new ConcurrentLinkedQueue<ResourcesRequest>();

	// Comms
	private final CopyOnWriteArraySet<ResourcesChangedListener> listeners = new CopyOnWriteArraySet<ResourcesChangedListener>();

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

	@Override
	public void run() {
		while (running) {
			// do stuff
			while (!queue.isEmpty()) {
				final ResourcesRequest request = queue.poll();
				try {
					getRPInstance().process(request);
				} catch (Exception e) {
					// log message
				}
			}
			// Wait till next time
			threadHolder.close();
			threadHolder.block();
		}

	}

	/**
	 * Ensures that this classes closes properly. MUST be called before it is
	 * terminated
	 */
	public synchronized void close() {
		this.running = false;
		// Keep waking worker thread until it dies
		while (workerThread.isAlive()) {
			threadHolder.open();
			Thread.yield();
			try {
				Thread.sleep(100);
			} catch (Exception e) {
			}
		}
		instance = null;
	}

	// Request handlers
	public void sendRequest(ResourcesRequest request) {
		queue.offer(request);
		threadHolder.open();
	}

	// This special class provides encapsulation of database operations as is
	// set up to enforce
	// Scope. I.e. ONLY ResourceManager can start a request
	public class RequestProcessor extends DatabaseManager {

		public static final String _ID = "_id";
		public static final String COLLECTION_ID = "collection_id";
		public static final String RESOURCE_NAME = "name";
		public static final String ETAG = "etag";
		public static final String LAST_MODIFIED = "last_modified";
		public static final String CONTENT_TYPE = "content_type";
		public static final String RESOURCE_DATA = "data";
		public static final String NEEDS_SYNC = "needs_sync";
		public static final String EARLIEST_START = "earliest_start";
		public static final String LATEST_END = "latest_end";
		public static final String EFFECTIVE_TYPE = "effective_type";

		// This is not a field, but we sometimes put this into the ContentValues
		// as if
		// it were, when there is a pending change for this resource.
		public static final String IS_PENDING = "is_pending";

		public static final String TYPE_EVENT = "'VEVENT'";
		public static final String TYPE_TASK = "'VTODO'";
		public static final String TYPE_JOURNAL = "'VJOURNAL'";
		public static final String TYPE_ADDRESS = "'VCARD'";

		// Database + Table
		private static final String DATABASE_TABLE = "dav_resource";

		public static final String TAG = "acal Resources RequestProccessor";

		private ResourcesRequest currentRequest;

		private RequestProcessor() {
			super(ResourcesManager.this.context);
		}

		@Override
		protected String getTableName() {
			return DATABASE_TABLE;
		}
		
		public void process(ResourcesRequest r) {
			currentRequest = r;
			try {
				r.process(this);
				if (this.inTx) {
					this.endTransaction();
					throw new ResourceProccessingException(
							"Process started a transaction without ending it!");
				}
			} catch (ResourceProccessingException e) {
				Log.e(TAG, "Error Procssing Resource Request: "
						+ Log.getStackTraceString(e));
			} catch (Exception e) {
				Log.e(TAG,
						"INVALID TERMINATION while processing Resource Request: "
								+ Log.getStackTraceString(e));
			} finally {
				// make sure db was closed properly
				if (this.db != null)
					try {
						endQuery();
					} catch (Exception e) {
					}
			}
			currentRequest = null;
		}

		public ContentResolver getContentResolver() {
			return ResourcesManager.this.context.getContentResolver();
		}

		public ConnectivityManager getConectivityService() {
			return (ConnectivityManager) context
					.getSystemService(Context.CONNECTIVITY_SERVICE);
		}

		/**
		 * Method to retrieve a particular database row for a given resource ID.
		 */
		public ContentValues getRow(long rid) {
			ArrayList<ContentValues> res = this.query( null, _ID + " = ?",	new String[] { rid + "" }, null, null, null);
			if (res == null || res.isEmpty()) return null;
			return res.get(0);
		}

		public Context getContext() {
			return context;
		}

		/**
		 * Static method to retrieve a particular database row for a given
		 * collectionId & resource name.
		 * 
		 * @param collectionId
		 * @param name
		 * @param contentResolver
		 * @return A ContentValues which is the dav_resource row, or null
		 */
		public ContentValues getResourceInCollection(long collectionId,	String name) {
			ArrayList<ContentValues> res = this.query(  null, RESOURCE_NAME + "=?",	new String[] { name }, null, null, null);
			if (res == null || res.isEmpty()) return null;
			return res.get(0);
		}

		/**
		 * <p>
		 * Finds the resources which have been marked as needing synchronisation
		 * in our local database.
		 * </p>
		 * 
		 * @return A map of String/Data which are the hrefs we need to sync
		 */
		public Map<String, ContentValues> findSyncNeededResources(long collectionId) {
			beginReadQuery();
			long start = System.currentTimeMillis();
			Map<String, ContentValues> originalData = null;

			// step 1a get list of resources from db
			start = System.currentTimeMillis();

			Cursor mCursor = db.query(DATABASE_TABLE, null, COLLECTION_ID
					+ " = ? " + NEEDS_SYNC + " = 1 OR " + RESOURCE_DATA
					+ " IS NULL", new String[] { collectionId + "" }, null,
					null, null);
			ContentQueryMap cqm = new ContentQueryMap(mCursor,
					RequestProcessor.RESOURCE_NAME, false, null);
			cqm.requery();
			originalData = cqm.getRows();
			mCursor.close();
			endQuery();
			if (Constants.LOG_VERBOSE && Constants.debugSyncCollectionContents)
				Log.println(Constants.LOGV, TAG,
						"DavCollections ContentQueryMap retrieved in "
								+ (System.currentTimeMillis() - start) + "ms");
			return originalData;
		}

		/**
		 * Returns a Map of href to database record for the current database
		 * state.
		 * 
		 * @return
		 */
		public Map<String, ContentValues> getCurrentResourceMap(long collectionId) {
			beginReadQuery();

			Cursor resourceCursor = db.query(DATABASE_TABLE,null, COLLECTION_ID + " = ? ", new String[] { collectionId + "" }, null, null, null);
			if (!resourceCursor.moveToFirst()) {
				resourceCursor.close();
				return new HashMap<String, ContentValues>();
			}
			ContentQueryMap cqm = new ContentQueryMap(resourceCursor,
					RequestProcessor.RESOURCE_NAME, false, null);
			cqm.requery();
			Map<String, ContentValues> databaseList = cqm.getRows();
			cqm.close();
			resourceCursor.close();
			endQuery();
			return databaseList;
		}

		public int deleteByCollectionId(long id) {
			return delete(COLLECTION_ID + " = ?", new String[] { id + "" });
		}
		
	}

}