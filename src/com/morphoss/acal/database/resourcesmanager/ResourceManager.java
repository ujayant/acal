package com.morphoss.acal.database.resourcesmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;

import android.content.ContentQueryMap;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.ConditionVariable;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.database.DatabaseTableManager;
import com.morphoss.acal.providers.DavCollections;
import com.morphoss.acal.providers.Servers;

public class ResourceManager implements Runnable {
	// The current instance
	private static ResourceManager instance = null;

	// Get an instance
	public synchronized static ResourceManager getInstance(Context context) {
		if (instance == null)
			instance = new ResourceManager(context);
		return instance;
	}

	// get and instance and add a callback handler to receive notfications of
	// change
	// It is vital that classes remove their handlers when terminating
	public synchronized static ResourceManager getInstance(Context context,
			ResourceChangedListener listener) {
		if (instance == null)
			instance = new ResourceManager(context);
		instance.addListener(listener);
		return instance;
	}

	// Request Processor Instance
	// Instance
	private ResourceTableManager RPinstance;

	private ResourceTableManager getRPInstance() {
		if (instance == null)
			RPinstance = new ResourceTableManager();
		return RPinstance;
	}

	private Context context;

	// ThreadManagement
	private ConditionVariable threadHolder = new ConditionVariable();
	private Thread workerThread;
	private boolean running = true;
	private final ConcurrentLinkedQueue<ResourceRequest> queue = new ConcurrentLinkedQueue<ResourceRequest>();

	// Comms
	private final CopyOnWriteArraySet<ResourceChangedListener> listeners = new CopyOnWriteArraySet<ResourceChangedListener>();

	private ResourceManager(Context context) {
		this.context = context;
		threadHolder.close();
		workerThread = new Thread(this);
		workerThread.start();
	}

	public void addListener(ResourceChangedListener ccl) {
		synchronized (listeners) {
			this.listeners.add(ccl);
		}
	}

	public void removeListener(ResourceChangedListener ccl) {
		synchronized (listeners) {
			this.listeners.remove(ccl);
		}
	}

	@Override
	public void run() {
		while (running) {
			// do stuff
			while (!queue.isEmpty()) {
				final ResourceRequest request = queue.poll();
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
	public void sendRequest(ResourceRequest request) {
		queue.offer(request);
		threadHolder.open();
	}

	// This special class provides encapsulation of database operations as is
	// set up to enforce
	// Scope. I.e. ONLY ResourceManager can start a request
	public class ResourceTableManager extends DatabaseTableManager {

		// Resources Table Constants
		private static final String RESOURCE_DATABASE_TABLE = "dav_resource";
		public static final String RESOURCE_ID = "_id";
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


		//PendingChanges Table Constants
		//Table Fields - All other classes should use these constants to access fields.
		public static final String		PENDING_DATABASE_TABLE		= "pending_change";
		public static final String		PENDING_ID					= "_id";
		public static final String		PEND_COLLECTION_ID			= "collection_id";
		public static final String		PEND_RESOURCE_ID			= "resource_id";
		public static final String		MODIFICATION_TIME			= "modification_time";
		public static final String		AWAITING_SYNC_SINCE			= "awaiting_sync_since";
		public static final String		OLD_DATA					= "old_data";
		public static final String		NEW_DATA					= "new_data";
		public static final String		UID							= "uid";

		// This is not a field, but we sometimes put this into the ContentValues
		// as if
		// it were, when there is a pending change for this resource.
		public static final String IS_PENDING = "is_pending";

		public static final String TYPE_EVENT = "'VEVENT'";
		public static final String TYPE_TASK = "'VTODO'";
		public static final String TYPE_JOURNAL = "'VJOURNAL'";
		public static final String TYPE_ADDRESS = "'VCARD'";



		public static final String TAG = "acal Resources RequestProccessor";

		private ResourceTableManager() {
			super(ResourceManager.this.context);
		}

		@Override
		protected String getTableName() {
			return RESOURCE_DATABASE_TABLE;
		}

		public void process(ResourceRequest r) {
			try {
				r.process(this);
				if (this.inTx) {
					this.endTransaction();
					throw new ResourceProcessingException(
					"Process started a transaction without ending it!");
				}
			} catch (ResourceProcessingException e) {
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
		}

		public ConnectivityManager getConectivityService() {
			return (ConnectivityManager) context
			.getSystemService(Context.CONNECTIVITY_SERVICE);
		}

		/**
		 * Method to retrieve a particular database row for a given resource ID.
		 */
		public ContentValues getRow(long rid) {
			ArrayList<ContentValues> res = this.query( null, RESOURCE_ID + " = ?",	new String[] { rid + "" }, null, null, null);
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

			Cursor mCursor = db.query(RESOURCE_DATABASE_TABLE, null, COLLECTION_ID
					+ " = ? " + NEEDS_SYNC + " = 1 OR " + RESOURCE_DATA
					+ " IS NULL", new String[] { collectionId + "" }, null,
					null, null);
			ContentQueryMap cqm = new ContentQueryMap(mCursor,
					ResourceTableManager.RESOURCE_NAME, false, null);
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

			Cursor resourceCursor = db.query(RESOURCE_DATABASE_TABLE,null, COLLECTION_ID + " = ? ", new String[] { collectionId + "" }, null, null, null);
			if (!resourceCursor.moveToFirst()) {
				resourceCursor.close();
				return new HashMap<String, ContentValues>();
			}
			ContentQueryMap cqm = new ContentQueryMap(resourceCursor,
					ResourceTableManager.RESOURCE_NAME, false, null);
			cqm.requery();
			Map<String, ContentValues> databaseList = cqm.getRows();
			cqm.close();
			resourceCursor.close();
			endQuery();
			return databaseList;
		}

		public void deleteByCollectionId(long id) {
			this.beginTransaction();
			db.delete(PENDING_DATABASE_TABLE, PEND_COLLECTION_ID+" = ?", new String[]{id+""});
			delete(COLLECTION_ID + " = ?", new String[] { id + "" });
			this.setTxSuccessful();
			this.endTransaction();
		}

		public boolean doSyncListAndToken(DMQueryList newChangeList, long collectionId, String syncToken) {
			this.beginTransaction();
			boolean success = newChangeList.process(this);

			if ( syncToken != null && success) {
				//Update sync token
				ContentValues cv = new ContentValues();
				cv.put(DavCollections.SYNC_TOKEN, syncToken);
				db.update(DavCollections.DATABASE_TABLE, cv,
						DavCollections._ID+"=?", new String[] {collectionId+""});
			}
			this.setTxSuccessful();
			this.endTransaction();
			return success;
		}

		public boolean syncToServer(DMAction action, long resourceId, Integer pendingId) {
			// TODO Auto-generated method stub
			this.beginTransaction();
			action.process(this);
			if ( pendingId != null ) {
				// We can retire this change now
				int removed = db.delete(PENDING_DATABASE_TABLE,
						PENDING_ID+"=?",
						new String[] { Integer.toString(pendingId) });
				if ( Constants.LOG_DEBUG )
					Log.d(TAG, "Deleted "+removed+" one pending_change record ID="+pendingId+" for resourceId="+resourceId);

				ContentValues pending = new ContentValues();
				pending.put(PENDING_ID, pendingId);
			}
			this.setTxSuccessful();
			this.endTransaction();
			return true;

		}

		@Override
		public void dataChanged(List<DataChange> changes) {
			ResourceChangedEvent rce = new ResourceChangedEvent(changes);
			synchronized (listeners) {
				for (ResourceChangedListener listener : listeners) {
					listener.resourceChanged(rce);
				}
			}
		}

		public ContentValues getServerData(int serverId) {
			return Servers.getRow(serverId, context.getContentResolver());
		}

		public void deleteInvalidCollectionRecord(int collectionId) {
			context.getContentResolver().delete(Uri.withAppendedPath(DavCollections.CONTENT_URI,Long.toString(collectionId)), null, null);
		}

		public ContentValues getCollectionRow(int collectionId) {
			return DavCollections.getRow(collectionId, context.getContentResolver());
		}

		public boolean getCollectionIdByPath(ContentValues values, long serverId, String collectionPath ) {
			Cursor cursor = context.getContentResolver().query(DavCollections.CONTENT_URI, null, 
					DavCollections.SERVER_ID + "=? AND " + DavCollections.COLLECTION_PATH + "=?",
					new String[] { "" + serverId, collectionPath }, null);
			if ( cursor.moveToFirst() ) {
				DatabaseUtils.cursorRowToContentValues(cursor, values);
				cursor.close();
				return true;
			}
			cursor.close();
			return false;
		}
	
	
	public boolean marshallChangesToSync(ArrayList<ContentValues> pendingChangesList) {
		this.beginReadQuery();
		Cursor pendingCursor = db.query(PENDING_DATABASE_TABLE, null, null, null, null, null, null);
		
		if ( pendingCursor.getCount() == 0 ) {
			pendingCursor.close();
			return false;
		}
		pendingCursor.moveToFirst();
		while( pendingCursor.moveToNext() ) {
			ContentValues cv = new ContentValues();
			DatabaseUtils.cursorRowToContentValues(pendingCursor, cv);
			pendingChangesList.add(cv);
		}
		
		pendingCursor.close();
		this.endQuery();
		return ( pendingChangesList.size() != 0 );
	}

	public void deletePendingChange(Integer pendingId) {
		this.beginWriteQuery();
		db.delete(PENDING_DATABASE_TABLE, PENDING_ID+" = ?", new String[]{pendingId+""});
		this.endQuery();
	}

	public void updateCollection(long collectionId, ContentValues collectionData) {
		this.beginWriteQuery();
		db.update(DavCollections.DATABASE_TABLE, collectionData, DavCollections._ID+" =?", new String[]{collectionId+""});
		this.endQuery();
		
	}
	public boolean marshallCollectionsToSync(ArrayList<ContentValues> pendingChangesList) {
		Cursor pendingCursor = context.getContentResolver().query(DavCollections.CONTENT_URI, null,
					DavCollections.SYNC_METADATA+"=1 AND ("+DavCollections.ACTIVE_EVENTS
						+"=1 OR "+DavCollections.ACTIVE_TASKS
						+"=1 OR "+DavCollections.ACTIVE_JOURNAL
						+"=1 OR "+DavCollections.ACTIVE_ADDRESSBOOK+"=1) "
						,
					null, null);
		if ( pendingCursor.getCount() == 0 ) {
			pendingCursor.close();
			return false;
		}

		pendingChangesList = new ArrayList<ContentValues>();
		while( pendingCursor.moveToNext() ) {
			ContentValues cv = new ContentValues();
			DatabaseUtils.cursorRowToContentValues(pendingCursor, cv);
			pendingChangesList.add(cv);
		}
		
		pendingCursor.close();
		
		return ( pendingChangesList.size() != 0 );
	}

	
	
	
	}
}