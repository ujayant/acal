package com.morphoss.acal.database.resourcesmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.PriorityBlockingQueue;

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
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalRepeatRule;
import com.morphoss.acal.database.DMAction;
import com.morphoss.acal.database.DMDeleteQuery;
import com.morphoss.acal.database.DMInsertQuery;
import com.morphoss.acal.database.DMQueryBuilder;
import com.morphoss.acal.database.DMQueryList;
import com.morphoss.acal.database.DMUpdateQuery;
import com.morphoss.acal.database.DataChangeEvent;
import com.morphoss.acal.database.DatabaseTableManager;
import com.morphoss.acal.database.cachemanager.CacheManager;
import com.morphoss.acal.database.resourcesmanager.requesttypes.BlockingResourceRequestWithResponse;
import com.morphoss.acal.database.resourcesmanager.requesttypes.ReadOnlyBlockingRequestWithResponse;
import com.morphoss.acal.database.resourcesmanager.requesttypes.ReadOnlyResourceRequest;
import com.morphoss.acal.database.resourcesmanager.requesttypes.ResourceRequest;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.providers.DavCollections;
import com.morphoss.acal.providers.Servers;
import com.morphoss.acal.service.SyncChangesToServer;
import com.morphoss.acal.service.WorkerClass;

public class ResourceManager implements Runnable {
	// The current instance
	private static ResourceManager instance = null;
	public static boolean DEBUG = false && Constants.DEBUG_MODE;

	private volatile int numReadsProcessing = 0;

	// Get an instance
	public synchronized static ResourceManager getInstance(Context context) {
		if (instance == null)
			instance = new ResourceManager(context);
		return instance;
	}

	public static final String TAG = "aCal ResourceManager";

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
		if (RPinstance == null)
			RPinstance = new ResourceTableManager();
		return RPinstance;
	}

	private Context context;

	// ThreadManagement
	private ConditionVariable threadHolder = new ConditionVariable();
	private Thread workerThread;
	private boolean running = true;
	private final ConcurrentLinkedQueue<ResourceRequest> writeQueue = new ConcurrentLinkedQueue<ResourceRequest>();
	private final PriorityBlockingQueue<ReadOnlyResourceRequest> readQueue = new PriorityBlockingQueue<ReadOnlyResourceRequest>();

	/**
	 * IMPORTANT INVARIANT:
	 * listeners should only ever be told about changes by the worker thread calling dataChanged in the enclosed class.
	 * 
	 * Notifying listeners in any other way can lead to Race Conditions.
	 */
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
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		while (running) {
			// do stuff
			if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD,TAG,"Thread Opened...");

			while ( !readQueue.isEmpty() || !writeQueue.isEmpty() ){

				//process reads first
				if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD,TAG,readQueue.size()+" items in read queue, "+writeQueue.size()+" items in write queue");

				if (!readQueue.isEmpty()) {
					//Tell the processor that we are about to send a buch of reads.
					getRPInstance().beginReadTransaction();

					//Start all read processes
					while (!readQueue.isEmpty()) {
						if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD,TAG,readQueue.size()+" items in read queue.");
						final ReadOnlyResourceRequest request = readQueue.poll();
						if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD,TAG,"Processing Read Request: "+request.getClass());
						this.numReadsProcessing++;
						try {
							new Thread(new Runnable() {
								public void run() {
									try {
										getRPInstance().processRead(request);
									} catch (Exception e) {
										Log.e(TAG, "Error processing read request: "+Log.getStackTraceString(e));
									}
								}
							}).start();
						} catch (Exception e) {
							Log.e(TAG, "Error processing read request: "+Log.getStackTraceString(e));
						}
					}

					//Wait until all processes have finished
					while (this.numReadsProcessing > 0) {
						try {
							Thread.sleep(10);
						} catch (Exception e) {

						}
					}

					//tell processor that we are done
					getRPInstance().endTransaction();

				}
				else {
					//process writes
					CacheManager.setResourceInTx(context, true);
					while (!writeQueue.isEmpty()) {
						if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD,TAG,writeQueue.size()+" items in write queue.");
						final ResourceRequest request = writeQueue.poll();
						if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD,TAG,"Processing Write Request: "+request.getClass());
						try {
							getRPInstance().process(request);
						} catch (Exception e) {
							Log.e(TAG, "Error processing write request: "+Log.getStackTraceString(e));
						}
					}
					CacheManager.setResourceInTx(context, false);
				}
			}
			// do stuff
			if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD,TAG,"Finished processing, closing & blocking.");

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
		if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD,TAG, "Received Write Request: "+request.getClass());
		writeQueue.offer(request);
		threadHolder.open();
	}

	public <E> ResourceResponse<E> sendBlockingRequest(BlockingResourceRequestWithResponse<E> request) {
		if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD,TAG, "Received Blocking Request: "+request.getClass());
		writeQueue.offer(request);
		threadHolder.open();
		int priority = Thread.currentThread().getPriority();
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		while (!request.isProcessed()) {
			try { Thread.sleep(10); } catch (Exception e) {	}
		}
		Thread.currentThread().setPriority(priority);
		return request.getResponse();
	}

	// Request handlers
	public void sendRequest(ReadOnlyResourceRequest request) {
		if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD,TAG, "Received Read Request: "+request.getClass());
		readQueue.offer(request);
		threadHolder.open();
	}

	public <E> ResourceResponse<E> sendBlockingRequest(ReadOnlyBlockingRequestWithResponse<E> request) {
		if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD,TAG, "Received Blocking Read Request: "+request.getClass());
		readQueue.offer(request);
		threadHolder.open();
		int priority = Thread.currentThread().getPriority();
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		while (!request.isProcessed()) {
			try { Thread.sleep(10); } catch (Exception e) {	}
		}
		Thread.currentThread().setPriority(priority);
		return request.getResponse();
	}


	public interface WriteableResourceTableManager extends ReadOnlyResourceTableManager {
		public long insert(String nullColumnHack, ContentValues values);
		public int update(ContentValues values, String whereClause,	String[] whereArgs);
		public void deleteByCollectionId(long id);
		public void deleteInvalidCollectionRecord(long collectionId);
		public void deletePendingChange(Integer pendingId);
		public void updateCollection(long collectionId, ContentValues collectionData);
		public boolean doSyncListAndToken(DMQueryList newChangeList, long collectionId, String syncToken);
		public boolean syncToServer(DMAction action, long resourceId, Integer pendingId);
		public DMQueryList getNewQueryList();
		public DMDeleteQuery getNewDeleteQuery(String whereClause, String[] whereArgs);
		public DMInsertQuery getNewInsertQuery(String nullColumnHack, ContentValues values);
		public DMUpdateQuery getNewUpdateQuery(ContentValues values, String whereClause, String[] whereArgs);
		public DMQueryBuilder getNewQueryBuilder();
		public boolean processActions(DMQueryList queryList);
		public long addPending(long l, long m, String oldBlob, String newBlob, String uid);
	}

	public interface ReadOnlyResourceTableManager {
		public void process(ResourceRequest r);
		public ConnectivityManager getConectivityService();
		public ContentValues getRow(long rid);
		public Context getContext();
		public ContentValues getResourceInCollection(long collectionId,	String name);
		public Map<String, ContentValues> findSyncNeededResources(long collectionId);
		public Map<String, ContentValues> getCurrentResourceMap(long collectionId);
		public ContentValues getServerData(int serverId);
		public ContentValues getCollectionRow(long collectionId);
		public boolean getCollectionIdByPath(ContentValues values, long serverId, String collectionPath );
		public boolean marshallChangesToSync(ArrayList<ContentValues> pendingChangesList);
		public boolean marshallCollectionsToSync(ArrayList<ContentValues> pendingChangesList);
		public ArrayList<ContentValues> query(String[] columns, String selection, String[] selectionArgs,
				String groupBy, String having, String orderBy);
		public ArrayList<ContentValues> getPendingResources();

	}

	// This special class provides encapsulation of database operations as is
	// set up to enforce
	// Scope. I.e. ONLY ResourceManager can start a request
	public class ResourceTableManager extends DatabaseTableManager implements WriteableResourceTableManager, ReadOnlyResourceTableManager {

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

		public static final String IS_PENDING = "is_pending";	//this is a quasi field that tells use weather a resource came from the pending
		//table or the resource table


		//PendingChanges Table Constants
		//Table Fields - All other classes should use these constants to access fields.
		public static final String		PENDING_DATABASE_TABLE		= "pending_change";
		public static final String		PENDING_ID					= "_id";
		public static final String		PEND_COLLECTION_ID			= "collection_id";
		public static final String		PEND_RESOURCE_ID			= "resource_id";
		public static final String		OLD_DATA					= "old_data";
		public static final String		NEW_DATA					= "new_data";
		public static final String		UID							= "uid";


		public static final String TYPE_EVENT = "'VEVENT'";
		public static final String TYPE_TASK = "'VTODO'";
		public static final String TYPE_JOURNAL = "'VJOURNAL'";
		public static final String TYPE_ADDRESS = "'VCARD'";



		public static final String TAG = "acal Resources RequestProccessor";


		private ResourceTableManager() {
			super(ResourceManager.this.context);
		}


		public void processRead(ReadOnlyResourceRequest request) {
			try {
				request.process(this);
			} catch (ResourceProcessingException e) {
				Log.e(TAG, "Error Procssing Resource Request: "
						+ Log.getStackTraceString(e));
			} catch (Exception e) {
				Log.e(TAG,
						"INVALID TERMINATION while processing Resource Request: "
						+ Log.getStackTraceString(e));
			} finally {
				numReadsProcessing--;
			}
		}

		@Override
		protected String getTableName() {
			return RESOURCE_DATABASE_TABLE;
		}

		public void process(ResourceRequest r) {
			if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD,TAG,"Begin Processing");
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
			if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD,TAG,"End Processing");
		}

		public ConnectivityManager getConectivityService() {
			return (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
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

		private ContentValues preProcessValues(ContentValues values) {
			ContentValues toWrite = new ContentValues(values);
			if (toWrite.containsKey(IS_PENDING)) toWrite.remove(IS_PENDING);
			values = toWrite;
			String effectiveType = null;
			String resourceData = values.getAsString(RESOURCE_DATA); 
			if ( resourceData != null ) {
				try {

					VComponent comp = VComponent.createComponentFromBlob(resourceData);

					if ( comp == null ) {
						Log.w(TAG, "Unable to parse VComponent from:\n"+resourceData);
					}
					else {
						effectiveType = comp.getEffectiveType();

						if (comp instanceof VCalendar) {
							VCalendar vCal = (VCalendar)comp;
							AcalRepeatRule rrule = AcalRepeatRule.fromVCalendar(vCal,AcalRepeatRule.VALUE_NOT_ASSIGNED,AcalRepeatRule.VALUE_NOT_ASSIGNED);
							if ( rrule != null ) {
								AcalDateRange range = rrule.getInstancesRange();
								values.put(EARLIEST_START, range.start.getMillis());
								if (range.end != null) values.put(LATEST_END, range.end.getMillis());
								else values.putNull(LATEST_END); 
							}  else {
								values.putNull(EARLIEST_START);
							}
						}
					}
				} catch (Exception e) {
					Log.e(TAG, "Error updating VComponent from resource: "+Log.getStackTraceString(e));
				}
			}
			if ( effectiveType != null ) values.put(EFFECTIVE_TYPE, effectiveType);
			return values;
		}

		/**
		 * This override is important to ensure earliest start and latest end are always set
		 */
		@Override
		public long insert(String nullColumnHack, ContentValues values) {
			if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD,TAG, "Resource Insert Begin");
			return super.insert(nullColumnHack, preProcessValues(values));
		}

		/**
		 * This override is important to ensure earliest start and latest end are always set
		 */
		public int update(ContentValues values, String whereClause,	String[] whereArgs) {
			if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD,TAG, "Resource Update Begin");
			return super.update(preProcessValues(values), whereClause, whereArgs);
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
					+ " = ? AND " + NEEDS_SYNC + " = 1 OR " + RESOURCE_DATA
					+ " IS NULL", new String[] { collectionId + "" }, null,
					null, null);
			ContentQueryMap cqm = new ContentQueryMap(mCursor,
					ResourceTableManager.RESOURCE_NAME, false, null);
			cqm.requery();
			originalData = cqm.getRows();
			mCursor.close();
			endQuery();
			if (Constants.LOG_VERBOSE && Constants.debugSyncCollectionContents)
				if ( ResourceManager.DEBUG ) Log.println(Constants.LOGV, TAG,
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
				endQuery();
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
			boolean success = this.processActions(newChangeList);

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
			this.beginTransaction();
			action.process(this);
			if ( pendingId != null ) {
				// We can retire this change now
				int removed = db.delete(PENDING_DATABASE_TABLE,
						PENDING_ID+"=?",
						new String[] { Integer.toString(pendingId) });
				if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD,TAG, "Deleted "+removed+" one pending_change record ID="+pendingId+" for resourceId="+resourceId);

				ContentValues pending = new ContentValues();
				pending.put(PENDING_ID, pendingId);
			}
			this.setTxSuccessful();
			this.endTransaction();
			return true;

		}

		//Never ever ever ever call resourceChanged on listeners anywhere else.
		@Override
		public void dataChanged(ArrayList<DataChangeEvent> changes) {
			if (changes.isEmpty()) return;
			synchronized (listeners) {
				for (ResourceChangedListener listener : listeners) {
					ResourceChangedEvent rce = new ResourceChangedEvent(new ArrayList<DataChangeEvent>(changes));
					listener.resourceChanged(rce);
				}
			}
		}

		public ContentValues getServerData(int serverId) {
			return Servers.getRow(serverId, context.getContentResolver());
		}

		public void deleteInvalidCollectionRecord(long collectionId) {
			context.getContentResolver().delete(Uri.withAppendedPath(DavCollections.CONTENT_URI,Long.toString(collectionId)), null, null);
		}

		public ContentValues getCollectionRow(long collectionId) {
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

		@Override
		public DMDeleteQuery getNewDeleteQuery(String whereClause, String[] whereArgs) {
			return new DMDeleteQuery(whereClause, whereArgs);
		}

		@Override
		public DMInsertQuery getNewInsertQuery(String nullColumnHack, ContentValues values) {
			return new DMInsertQuery(nullColumnHack, values);
		}

		@Override
		public DMQueryBuilder getNewQueryBuilder() {
			return new DMQueryBuilder();
		}

		@Override
		public DMQueryList getNewQueryList() {
			return new DMQueryList();
		}

		@Override
		public DMUpdateQuery getNewUpdateQuery(ContentValues values, String whereClause, String[] whereArgs) {
			return new DMUpdateQuery(values, whereClause, whereArgs);
		}


		@Override
		public long addPending(long cid, long rid, String oldBlob, String newBlob, String uid) {
			//add a new pending resource and return the resultant resource.
			//if oldBlob == null then this is a create not an edit
			long row = rid;
			Cursor mCursor = null;
			try {
				
				this.beginTransaction();				
				if (oldBlob == null || oldBlob.equals("")) row = createNewPending(cid,newBlob,uid);
				else {
					ContentValues newResource = new ContentValues();
					//Check if this resource already exists
					mCursor = db.query(PENDING_DATABASE_TABLE, null, 
							PEND_RESOURCE_ID+" = ? AND "+PEND_COLLECTION_ID+" = ?", 
							new String[]{rid+"",cid+""}, null,null,null);
					
					if (mCursor.getColumnCount() > 1) {
						DatabaseUtils.cursorRowToContentValues(mCursor, newResource);
						newResource.put(NEW_DATA, newBlob);
						newResource.put(UID, uid);
						db.update(PENDING_DATABASE_TABLE, newResource,
									PEND_RESOURCE_ID+" = ? AND "+PEND_COLLECTION_ID+" = ?", 
									new String[] {rid+"",cid+""});
					} else {
						newResource.put(PEND_COLLECTION_ID, cid);
						newResource.put(PEND_RESOURCE_ID, row);
						newResource.put(OLD_DATA, oldBlob);
						newResource.put(NEW_DATA, newBlob);
						newResource.put(UID, uid);
						db.insert(PENDING_DATABASE_TABLE, null, newResource);
					}
					mCursor.close();
					
				}
				this.setTxSuccessful();
			} catch (Exception e) {
				Log.e(TAG, "Error updating pending resource: "+e);
			} finally {
				if (mCursor != null && !mCursor.isClosed()) mCursor.close();
				this.endTransaction();
			}
			
			WorkerClass.getExistingInstance().addJobAndWake(new SyncChangesToServer());
			
			return row;
		}
		
		private long createNewPending(long cid, String blob, String uid) {
			ContentValues newResource = new ContentValues();
			newResource.put(COLLECTION_ID, cid);
			long row = this.insert(null, newResource);
			newResource = new ContentValues();
			newResource.put(PEND_COLLECTION_ID, cid);
			newResource.put(PEND_RESOURCE_ID, row);
			newResource.putNull(OLD_DATA);
			newResource.put(NEW_DATA, blob);
			newResource.put(UID, uid);
			db.insert(PENDING_DATABASE_TABLE, null, newResource);
			return row;
			
		}


		@Override
		public ArrayList<ContentValues> getPendingResources() {
			ArrayList<ContentValues> res = new ArrayList<ContentValues>();
			beginReadQuery();
			Cursor mCursor = db.query(PENDING_DATABASE_TABLE, null, null, null, null, null, null);
			if (mCursor.getCount() > 0) {
				for (mCursor.moveToFirst(); !mCursor.isAfterLast(); mCursor.moveToNext()) {
					ContentValues vals = new ContentValues();
					DatabaseUtils.cursorRowToContentValues(mCursor, vals);
					res.add(vals);
				}
					
			}
			mCursor.close();
			endQuery();
			return res;
		}
	}
}