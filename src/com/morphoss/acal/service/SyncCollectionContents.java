/*
 * Copyright (C) 2011 Morphoss Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.morphoss.acal.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import android.app.ActivityManager;
import android.content.ContentQueryMap;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Debug;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.DatabaseChangedEvent;
import com.morphoss.acal.HashCodeUtil;
import com.morphoss.acal.ResourceModification;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.AcalDBHelper;
import com.morphoss.acal.providers.DavCollections;
import com.morphoss.acal.providers.DavResources;
import com.morphoss.acal.providers.Servers;
import com.morphoss.acal.service.SynchronisationJobs.WriteActions;
import com.morphoss.acal.service.connector.AcalRequestor;
import com.morphoss.acal.xml.DavNode;

public class SyncCollectionContents extends ServiceJob {

	public static final String	TAG					= "aCal SyncCollectionContents";
	private static final int	nPerMultiget		= 30;

	private int					collectionId		= -1;
	private int					serverId			= -1;
	private String				collectionPath		= null;
	private String				syncToken			= "";
	private boolean				isAddressbook		= false;

	ContentValues				collectionData;
	private boolean				collectionChanged	= false;
	ContentValues				serverData;

	private String				dataType			= "calendar";
	private String				multigetReportTag	= "calendar-multiget";
	private String				nameSpace			= Constants.NS_CALDAV;

	// Note that this defines how often we wake up and see if we should be
	// doing a sync.  Not how often we actually wake up and hit the server
	// with a request.  Nevertheless we should not do this more than every
	// minute or so in production.
	private static final long	minBetweenSyncs		= (Constants.LOG_DEBUG ? 30000 : 300000);	// milliseconds

	private ContentResolver		cr;
	private aCalService			context;
	private boolean	synchronisationForced			= false;
	private AcalRequestor 		requestor;

	/**
	 * <p>
	 * Constructor
	 * </p>
	 * 
	 * @param collectionId2
	 *            <p>
	 *            The ID of the collection to be synced
	 *            </p>
	 * @param context
	 *            <p>
	 *            The context to use for all those things contexts are used for.
	 *            </p>
	 */
	public SyncCollectionContents(int collectionId) {
		this.collectionId = collectionId;
		this.TIME_TO_EXECUTE = 0;
	}


	/**
	 * <p>
	 * Schedule a sync of the contents of a collection, potentially forcing it to happen now even
	 * if this would otherwise be considered too early according to the normal schedule.
	 * </p>  
	 * @param collectionId
	 * @param forceSync
	 */
	public SyncCollectionContents(int collectionId, boolean forceSync ) {
		this.collectionId = collectionId;
		this.synchronisationForced = forceSync;
		this.TIME_TO_EXECUTE = 0;
	}

	
	@Override
	public void run(aCalService context) {
		this.context = context;
		this.cr = context.getContentResolver();
		if (!getCollectionInfo()) {
			Log.w(TAG, "Could not read collection " + collectionId + " for server " + serverId
						+ " from collection table!");
			return;
		}

		if (Constants.LOG_DEBUG)
			Log.d(TAG, "Starting sync on collection " + this.collectionPath + " (" + this.collectionId + ")");

		if (!(1 == serverData.getAsInteger(Servers.ACTIVE))) {
			if (Constants.LOG_DEBUG) Log.d(TAG, "Server is no longer active - sync cancelled: " + serverData.getAsInteger(Servers.ACTIVE)
							+ " " + serverData.getAsString(Servers.FRIENDLY_NAME));
			return;
		}

		long start = System.currentTimeMillis();
		try {
			aCalService.databaseDispatcher.dispatchEvent(new DatabaseChangedEvent(
						DatabaseChangedEvent.DATABASE_BEGIN_RESOURCE_CHANGES, DavCollections.class, collectionData));

			syncMarkedResources();
			
			if ( ! timeToRun() ) {
				scheduleNextInstance();
				return;
			}

			if ((1 == serverData.getAsInteger(Servers.HAS_SYNC))) {
				if (doRegularSyncReport()) syncMarkedResources();
			}
			else {
				if (doRegularSyncPropfind()) syncMarkedResources();
			}

			// update last checked flag for collection
			collectionData.put(DavCollections.LAST_SYNCHRONISED, new AcalDateTime().fmtIcal());
			collectionData.put(DavCollections.NEEDS_SYNC, 0);
			if ( syncToken != null ) collectionData.put(DavCollections.SYNC_TOKEN, syncToken);
			cr.update(DavCollections.CONTENT_URI, collectionData, DavCollections._ID + "=?",
															new String[] { "" + collectionId });
			aCalService.databaseDispatcher.dispatchEvent(
							new DatabaseChangedEvent(DatabaseChangedEvent.DATABASE_RECORD_UPDATED,
															DavCollections.class, collectionData)
						);

		}
		catch (Exception e) {
			Log.e(TAG, "Error syncing collection " + this.collectionId + ": " + e.getMessage());
			Log.e(TAG, Log.getStackTraceString(e));
		}
		finally {
			aCalService.databaseDispatcher.dispatchEvent(new DatabaseChangedEvent(
						DatabaseChangedEvent.DATABASE_END_RESOURCE_CHANGES, DavCollections.class, collectionData));
		}
		long finish = System.currentTimeMillis();
		if (Constants.LOG_VERBOSE) Log.v(TAG, "Collection sync finished in " + (finish - start) + "ms");

		scheduleNextInstance();
	}

	/**
	 * <p>
	 * Called from the constructor to initialise all of the collection-related data we need to be able to sync
	 * properly. This includes a bunch of server related data pulled into serverData
	 * </p>
	 * 
	 * @return Whether it successfully read enough data to proceed
	 */
	private boolean getCollectionInfo() {
		long start = System.currentTimeMillis();
		collectionData = DavCollections.getRow(collectionId, cr);
		if ( collectionData == null ) {
			Log.e(TAG, "Error getting collection data from DB for collection ID " + collectionId);
			return false;
		}

		serverId = collectionData.getAsInteger(DavCollections.SERVER_ID);
		collectionPath = collectionData.getAsString(DavCollections.COLLECTION_PATH);
		syncToken = collectionData.getAsString(DavCollections.SYNC_TOKEN);
		isAddressbook = (1 == collectionData.getAsInteger(DavCollections.ACTIVE_ADDRESSBOOK));
		dataType = "calendar";
		multigetReportTag = "calendar-multiget";
		nameSpace = Constants.NS_CALDAV;
		if (isAddressbook) {
			dataType = "address";
			multigetReportTag = dataType + "book-multiget";
			nameSpace = Constants.NS_CARDDAV;
		}

		try {
			// get serverData
			serverData = Servers.getRow(serverId, cr);
			if (serverData == null) throw new Exception("No record for ID " + serverId);
			requestor = AcalRequestor.fromServerValues(serverData);
			requestor.setPath(collectionPath);
		}
		catch (Exception e) {
			// Error getting data
			Log.e(TAG, "Error getting server data: " + e.getMessage());
			Log.e(TAG, "Deleting invalid collection Record.");
			cr.delete(Uri.withAppendedPath(DavCollections.CONTENT_URI,Long.toString(collectionId)), null, null);
			return false;
		}

		if (Constants.LOG_VERBOSE) Log.v(TAG, "getCollectionInfo() completed in " + (System.currentTimeMillis() - start) + "ms");
		return true;
	}

	/**
	 * <p>
	 * Do a sync run using a sync-collection REPORT against the collection, hopefully retrieving the -data
	 * pseudo-properties at the same time, but in any case getting a list of changed resources to process.
	 * Quick and light on the bandwidth, we hope.
	 * </p>
	 * 
	 * @return true if we still need to syncMarkedResources() afterwards.
	 */
	private boolean doRegularSyncReport() {
		DavNode root = doCalendarRequest("REPORT", 1,
					"<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
					+ "<sync-collection xmlns=\"DAV:\">"
						+ "<prop>"
							+ "<getetag/>"
							+ "<getlastmodified/>"
							+ "<" + dataType + "-data xmlns=\"" + nameSpace + "\"/>"
						+ "</prop>"
					+ "<sync-token>" + syncToken + "</sync-token>"
					+ "</sync-collection>"
				);

		boolean needSyncAfterwards = false; 

		if (root == null) {
			Log.i(TAG, "Unable to sync collection " + this.collectionPath + " (ID:" + this.collectionId
						+ " - no data from server.");
			return false;
		}

		ArrayList<ResourceModification> changeList = new ArrayList<ResourceModification>(); 

		if (Constants.LOG_VERBOSE) Log.v(TAG, "Start processing response");
		long start2 = System.currentTimeMillis();
		List<DavNode> responses = root.getNodesFromPath("multistatus/response");

		for (DavNode response : responses) {
			String name = response.segmentFromFirstHref("href");
			WriteActions action = WriteActions.UPDATE;

			ContentValues cv = DavResources.getResourceInCollection( collectionId, name, cr);
			if ( cv == null ) {
				cv = new ContentValues();
				cv.put(DavResources.COLLECTION_ID, collectionId);
				cv.put(DavResources.RESOURCE_NAME, name);
				action = WriteActions.INSERT;
			}
			
			List<DavNode> aNode = response.getNodesFromPath("status");
			if ( aNode.isEmpty()
						|| aNode.get(0).getText().equalsIgnoreCase("HTTP/1.1 201 Created")
						|| aNode.get(0).getText().equalsIgnoreCase("HTTP/1.1 200 OK") ) {

				Log.i(TAG,"Updating node "+name );
				// We are dealing with an update or insert
				if ( !parseResponseNode(response, cv) ) continue;
				if ( cv.getAsBoolean(DavResources.NEEDS_SYNC) ) needSyncAfterwards = true; 
			}
			else if ( action == WriteActions.INSERT ) {
				Log.i(TAG,"Ignoring delete sync on node '"+name+"' which is already deleted from our DB." );
			}
			else {
				// We should be dealing with a DELETE, but maybe we should check...
				Log.i(TAG,"Deleting node '"+name+"'with status: "+aNode.get(0).getText() );
				action = WriteActions.DELETE;
			}

			changeList.add( new ResourceModification(action, cv, null) );
			
			// Pull the syncToken we will update with.
			syncToken = root.getFirstNodeText("multistatus/sync-token");

			if (Constants.LOG_VERBOSE)
				Log.v(TAG, "Completed processing in completed in " + (System.currentTimeMillis() - start2) + "ms");
		}

		
		AcalDBHelper dbHelper = new AcalDBHelper(this.context);
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		db.beginTransaction();
		boolean completed =false;
		try {

			for ( ResourceModification changeUnit : changeList ) {
				changeUnit.commit(db);
			}
			db.setTransactionSuccessful();
			completed = true;
		}
		catch (Exception e) {
			Log.e(TAG, "Exception updating resources DB: " + e.getMessage());
			Log.e(TAG, Log.getStackTraceString(e));
		}
		finally {
			db.endTransaction();
			db.close();
			if ( completed ) {
				for ( ResourceModification changeUnit : changeList ) {
					changeUnit.notifyChange();
				}
			}
		}

		return needSyncAfterwards;
	}


	/**
	 * <p>
	 * Do a sync run using a PROPFIND against the collection and a pass through the DB comparing all resources
	 * currently on file with the ones we got from the PROPFIND. Laborious and potentially bandwidth hogging.
	 * </p>
	 * 
	 * @return true if we still need to syncMarkedResources() afterwards.
	 */
	private boolean doRegularSyncPropfind() {
		boolean needSyncAfterwards = false;
		if ( !collectionTagChanged() ) return false;

		DavNode root = 	doCalendarRequest("PROPFIND", 1,
					"<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
					+ "<propfind xmlns=\"DAV:\" xmlns:CS=\"http://calendarserver.org/ns/\">"
						+ "<prop>"
							+ "<getetag/>"
								+ "<CS:getctag/>"
						+ "</prop>"
					+ "</propfind>"
				);

		if (root == null ) {
			Log.i(TAG, "Unable to PROPFIND collection " + this.collectionPath + " (ID:" + this.collectionId
						+ " - no data from server.");
			return false;
		}

		Map<String, ContentValues> ourResourceMap = getCurrentResourceMap();

		AcalDBHelper dbHelper = new AcalDBHelper(this.context);
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		db.beginTransaction();
		
		try {
			if (Constants.LOG_VERBOSE) Log.v(TAG, "Start processing PROPFIND response");
			long start2 = System.currentTimeMillis();
			List<DavNode> responses = root.getNodesFromPath("multistatus/response");

			for (DavNode response : responses) {
				String name = response.segmentFromFirstHref("href");
				String oldEtag = "";

				ContentValues cv = null;				
				WriteActions action = WriteActions.UPDATE;
				if ( ourResourceMap != null && ourResourceMap.containsKey(name)) {
					cv = ourResourceMap.get(name);
					ourResourceMap.remove(name);
					oldEtag = cv.getAsString(DavResources.ETAG);
				}
				else {
					cv = new ContentValues();
					cv.put(DavResources.COLLECTION_ID, collectionId);
					cv.put(DavResources.RESOURCE_NAME, name);
					action = WriteActions.INSERT;
				}
				
				if ( !parseResponseNode(response, cv) ) continue;

				if ( cv.getAsString(DavResources.ETAG) != null && oldEtag.equals(cv.getAsString(DavResources.ETAG)) ) {
					// Resource in both places, but is unchanged.
					continue;
				}
				needSyncAfterwards = true; 
				cv.put(DavResources.NEEDS_SYNC, true);

				writeResource(db, action, cv);
			}
			
			if ( ourResourceMap != null ) {
				// Delete any records still in ourResourceMap (hence not on server any longer)
				Set<String> names = ourResourceMap.keySet();
				for( String name : names ) {
					ContentValues cv = ourResourceMap.get(name);
					writeResource(db, WriteActions.DELETE, cv );
				}
			}

			db.setTransactionSuccessful();
			db.endTransaction();
			db.close();
			
			if (Constants.LOG_VERBOSE)	Log.v(TAG, "Completed processing in completed in " + (System.currentTimeMillis() - start2) + "ms");
		}
		catch (Exception e) {
			Log.e(TAG, "Exception updating resources DB: " + e.getMessage());
			Log.e(TAG, Log.getStackTraceString(e));
			db.endTransaction();
			db.close();
		}

		return needSyncAfterwards;
	}

	
	/**
	 * Returns a Map of href to database record for the current database state.
	 * @return
	 */
	private Map<String,ContentValues> getCurrentResourceMap() {
		Cursor resourceCursor = cr.query(Uri.parse(DavResources.CONTENT_URI.toString() + "/collection/" + this.collectionId),
					new String[] { DavResources._ID, DavResources.RESOURCE_NAME, DavResources.ETAG }, null, null, null);
		if ( !resourceCursor.moveToFirst()) {
			resourceCursor.close();
			return new HashMap<String,ContentValues>();
		}
		ContentQueryMap cqm = new ContentQueryMap(resourceCursor, DavResources.RESOURCE_NAME, false, null);
		cqm.requery();
		Map<String, ContentValues> databaseList = cqm.getRows();
		cqm.close();
		resourceCursor.close();
		return databaseList;
	}

	
	/**
	 * <p>
	 * Checks for an old CalendarServer-style ctag for this collection
	 * </p>
	 * 
	 * @return <p>
	 *         Returns true if the CTag is different from the previous one, or if either is null.
	 *         </p>
	 */
	private boolean collectionTagChanged() {
		if (Constants.LOG_DEBUG) Log.d(TAG, "Requesting CTag on collection.");
		DavNode root = doCalendarRequest("PROPFIND", 0,
					"<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
					+ "<propfind xmlns=\"DAV:\" xmlns:CS=\"http://calendarserver.org/ns/\">"
						+ "<prop>"
	          				+ "<CS:getctag/>"
						+ "</prop>"
					+ "</propfind>"
				);
		
		if ( root == null ) {
			Log.i(TAG,"No response from server - deferring sync.");
			return false;
		}

		List<DavNode> responses = root.getNodesFromPath("multistatus/response");

		for (DavNode response : responses) {

			List<DavNode> propstats = response.getNodesFromPath("propstat");

			for (DavNode propstat : propstats) {
				if ( !propstat.getFirstNodeText("status").equalsIgnoreCase("HTTP/1.1 200 OK") )
					continue;

				DavNode prop = propstat.getNodesFromPath("prop").get(0);
				String ctag = prop.getFirstNodeText("getctag");
				String collectionTag = collectionData.getAsString(DavCollections.COLLECTION_TAG);
				
				if ( ctag == null || collectionTag == null ) return true;
				return ! ctag.equals(collectionTag);
			}
		}
		return true;
	}



	private Header[] getHeaders( int depth ) {
		return new Header[] {
					new BasicHeader("Content-Type", "text/xml; encoding=UTF-8"),
					new BasicHeader("Depth", Integer.toString(depth))
				};
	}

	
	/**
	 * <p>
	 * Does a request against the collection path
	 * </p>
	 * 
	 * @return <p>
	 *         A DavNode which is the root of the multistatus response.
	 *         </p>
	 */
	private DavNode doCalendarRequest( String method, int depth, String xml) {
		DavNode root = requestor.doXmlRequest(method, collectionPath, getHeaders(depth), xml);
		if ( requestor.getStatusCode() == 404 ) {
			Log.i(TAG,"Sync PROPFIND got 404 on "+collectionPath+" so a HomeSetsUpdate is being scheduled.");
			ServiceJob sj = new HomeSetsUpdate(serverId);
			context.addWorkerJob(sj);
			return null;
		}
		return root;
	}

	
	/**
	 * Checks the resources we have in the DB currently flagged as needing synchronisation, and synchronises
	 * them if they are using an addressbook-multiget or calendar-multiget request, depending on the
	 * collection type.
	 */
	private void syncMarkedResources() {

		// step 1 are there any 'needs_sync' in dav_resources?
		Map<String, ContentValues> originalData = findSyncNeededResources();

		if (originalData.size() < 1) {
			if (Constants.LOG_DEBUG) Log.d(TAG, "No resources marked for synchronisation.");
			return;
		}
		if (Constants.LOG_DEBUG) Log.d(TAG, "Found " + originalData.size() + " resources marked as needing synchronisation.");

		Set<String> hrefSet = originalData.keySet();
		Object[] hrefs = hrefSet.toArray();

		if (1 == serverData.getAsInteger(Servers.HAS_MULTIGET)) {
			syncWithMultiget(originalData, hrefs);
		}
		else {
			syncWithGet(originalData, hrefs);
		}
	}

	/**
	 * <p>
	 * Performs a sync using a series of multiget REPORT requests to retrieve the resources needing sync.
	 * </p>
	 * 
	 * @param originalData
	 *            <p>
	 *            The href => Contentvalues map.
	 *            </p>
	 * @param hrefs
	 *            <p>
	 *            The array of hrefs
	 *            </p>
	 */
	private void syncWithMultiget(Map<String, ContentValues> originalData, Object[] hrefs) {
		long fullMethod = System.currentTimeMillis();

		String baseXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
			+ "<" + multigetReportTag + " xmlns=\"" + nameSpace + "\" xmlns:D=\"DAV:\">\n"
				+ "<D:prop>\n"
					+ "<D:getetag/>\n"
					+ "<D:getcontenttype/>\n"
					+ "<D:getlastmodified/>\n"
					+ "<" + dataType + "-data/>\n"
				+ "</D:prop>\n"
				+ "%s"
				+ "</" + multigetReportTag + ">";
		
		for (int hrefIndex = 0; hrefIndex < hrefs.length; hrefIndex += nPerMultiget) {
			int limit = nPerMultiget + hrefIndex;
			if ( limit > hrefs.length ) limit = hrefs.length;
			StringBuilder hrefList = new StringBuilder();
			for (int i = hrefIndex; i < limit; i++) {
				hrefList.append(String.format("<D:href>%s</D:href>", collectionPath + hrefs[i].toString()));
			}
		
			if (Constants.LOG_DEBUG) Log.d(TAG, "Requesting " + multigetReportTag + " for " + nPerMultiget + " resources.");
			DavNode root = doCalendarRequest( "REPORT", 1, String.format(baseXml,hrefList.toString()) );

			if (root == null) {
				Log.w(TAG, "Unable to sync collection " + this.collectionPath + " (ID:" + this.collectionId
							+ " - no data from server).");
				return;
			}
			AcalDBHelper dbHelper = new AcalDBHelper(this.context);
			SQLiteDatabase db = dbHelper.getWritableDatabase();
			db.beginTransaction();
			try {
				// step 1c parse response
				if (Constants.LOG_VERBOSE) Log.v(TAG, "Start processing response");
				long start2 = System.currentTimeMillis();
				List<DavNode> responses = root.getNodesFromPath("multistatus/response");

				for (DavNode response : responses) {
					String name = response.segmentFromFirstHref("href");
					ContentValues cv = originalData.get(name);
					WriteActions action = WriteActions.UPDATE;
					if ( cv == null ) {
						cv = new ContentValues();
						cv.put(DavResources.COLLECTION_ID, collectionId);
						cv.put(DavResources.RESOURCE_NAME, name);
						action = WriteActions.INSERT;
					}
					if ( !parseResponseNode(response, cv) ) continue;
					
					writeResource( db, action, cv);
				}
				db.setTransactionSuccessful();
				db.endTransaction();
				db.close();

				if (Constants.LOG_VERBOSE)	Log.v(TAG, "Completed processing in completed in " + (System.currentTimeMillis() - start2) + "ms");
			}
			catch (Exception e) {
				Log.e(TAG, "Exception updating resources DB: " + e.getMessage());
				Log.e(TAG, Log.getStackTraceString(e));
				db.endTransaction();
				db.close();
			}
			if ( hrefIndex + nPerMultiget < hrefs.length ) {
				if ( Constants.LOG_DEBUG ) {
					Debug.MemoryInfo mi = new Debug.MemoryInfo();
					try {
						Debug.getMemoryInfo(mi);
					}
					catch ( Exception e ) {
						Log.i(TAG,"Unable to get Debug.MemoryInfo() because: " + e.getMessage());
					}
					if ( mi != null ) {
						Log.d(TAG,String.format("MemoryInfo: Dalvik(%d,%d,%d), Native(%d,%d,%d), Other(%d,%d,%d)",
									mi.dalvikPrivateDirty, mi.dalvikPss, mi.dalvikSharedDirty,
									mi.nativePrivateDirty, mi.nativePss, mi.nativeSharedDirty,
									mi.otherPrivateDirty,  mi.otherPss,  mi.otherSharedDirty ) );
					}
				}
				ActivityManager.MemoryInfo ammi = new ActivityManager.MemoryInfo();
				if ( ammi.lowMemory ) {
					Log.i(TAG, "Android thinks we're low memory right now, rescheduling more sync in 30 seconds time." );
					// Reschedule for another run, rather than continue now.
					SyncCollectionContents sj = new SyncCollectionContents(collectionId,true);
					sj.TIME_TO_EXECUTE = System.currentTimeMillis() + 30000;
					context.addWorkerJob(sj);
					return;
				}
			}
		}

		long dbstart = System.currentTimeMillis();

		if (Constants.LOG_VERBOSE)
			Log.v(TAG, "DB transaction write completed in " + (System.currentTimeMillis() - dbstart) + "ms");
		if (Constants.LOG_VERBOSE) Log.v(TAG, "checkResources() completed in " + (System.currentTimeMillis() - fullMethod) + "ms");
		return;
	}


	/**
	 * <p>
	 * Finds the resources which have been marked as needing synchronisation in our local database.
	 * </p>
	 * 
	 * @return A map of String/Data which are the hrefs we need to sync
	 */
	private Map<String, ContentValues> findSyncNeededResources() {
		long start = System.currentTimeMillis();
		Map<String, ContentValues> originalData = null;

		// step 1a get list of resources from db
		start = System.currentTimeMillis();

		Cursor mCursor = this.cr.query(
					Uri.parse(DavResources.CONTENT_URI.toString() + "/collection/" + this.collectionId), new String[] { }, DavResources.NEEDS_SYNC + " = 1", null, null);
		ContentQueryMap cqm = new ContentQueryMap(mCursor, DavResources.RESOURCE_NAME, false, null);
		cqm.requery();
		originalData = cqm.getRows();
		mCursor.close();
		if (Constants.LOG_VERBOSE) Log.v(TAG, "DavCollections ContentQueryMap retrieved in " + (System.currentTimeMillis() - start) + "ms");
		return originalData;
	}

	/**
	 * <p>
	 * Parse a single &lt;response&gt; node within a &lt;multistatus&gt;
	 * </p>
	 * @return true If we need to write to the database, false otherwise.
	 */
	private boolean parseResponseNode(DavNode responseNode, ContentValues cv) {
		long start = System.currentTimeMillis();
		// long href = System.currentTimeMillis();
		// if (Constants.LOG_VERBOSE) Log.v(TAG,
		// "href nodes retrieved in  "+(System.currentTimeMillis()-href)+"ms");

		// long props = System.currentTimeMillis();
		List<DavNode> propstats = responseNode.getNodesFromPath("propstat");
		if ( propstats.size() < 1 ) return false;
		// if (Constants.LOG_VERBOSE) Log.v(TAG,
		// propstats.size()+"propstat nodes retrieved in  "+(System.currentTimeMillis()-props)+"ms");

		for (DavNode propstat : propstats) {
			if ( !propstat.getFirstNodeText("status").equalsIgnoreCase("HTTP/1.1 200 OK") ) {
				responseNode.removeSubTree(propstat);
				continue;
			}

			DavNode prop = propstat.getNodesFromPath("prop").get(0);
			String ctag = prop.getFirstNodeText("getctag");
			if ( ctag != null ) {
				collectionChanged = (collectionData.getAsString(DavCollections.COLLECTION_TAG) == null
									|| ctag.equals(collectionData.getAsString(DavCollections.COLLECTION_TAG)));
				if ( collectionChanged )
					collectionData.put(DavCollections.COLLECTION_TAG, ctag);

				return false;
			}

			String etag = prop.getFirstNodeText("getetag");
			String data = prop.getFirstNodeText(dataType + "-data");
			String last_modified = prop.getFirstNodeText("getlastmodified");
			String content_type = prop.getFirstNodeText("getcontenttype");
			
			String oldEtag = cv.getAsString(DavResources.ETAG);
			String oldData = cv.getAsString(DavResources.RESOURCE_DATA);
			
			if ( etag != null && oldEtag != null && oldEtag.equals(etag)
						&& ( (data == null && oldData == null)
							 || (data != null && oldData != null && oldData.equals(data) ) )
							 ) {
				cv.put(DavResources.NEEDS_SYNC, (oldData == null ? "1" : "0") );
				return false;
			}

			if ( etag != null ) cv.put(DavResources.ETAG, etag);
			if ( data != null ) cv.put(DavResources.RESOURCE_DATA, data); 
			if ( last_modified != null ) cv.put(DavResources.LAST_MODIFIED, last_modified);
			if ( content_type != null ) cv.put(DavResources.CONTENT_TYPE, content_type);
			cv.put(DavResources.NEEDS_SYNC, (data == null || etag == null) );
		}

		// We remove our references to this now, since we've finished with it.
		responseNode.getParent().removeSubTree(responseNode);

		if (Constants.debugSyncCollectionContents && Constants.LOG_VERBOSE)
			Log.v(TAG, "Single response process time: completed in " + (System.currentTimeMillis() - start) + "ms");
		return true;
	}

	/**
	 * <p>
	 * Performs a sync using a series of GET requests to retrieve each resource needing sync. This is a
	 * fallback strategy and we really expect multiget to work in almost all circumstances.
	 * </p>
	 * 
	 * @param originalData
	 *            <p>
	 *            The href => Contentvalues map.
	 *            </p>
	 * @param hrefs
	 *            <p>
	 *            The array of hrefs
	 *            </p>
	 */
	private void syncWithGet(Map<String, ContentValues> originalData, Object[] hrefs) {
		long fullMethod = System.currentTimeMillis();

		Header[] headers = new Header[] {};

		AcalDBHelper dbHelper = new AcalDBHelper(this.context);
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		db.beginTransaction();

		for (int hrefIndex = 0; hrefIndex < hrefs.length; hrefIndex++) {
			try {
				String path = collectionPath + hrefs[hrefIndex];

				InputStream in = requestor.doRequest("GET", path, headers, "");
				if (in == null) {
					if (Constants.LOG_DEBUG) Log.d(TAG, "Error - Unable to get data stream from server.");
					db.endTransaction();
					db.close();
					return;
				}
				else {
					int status = requestor.getStatusCode();
					switch (status) {
						case 200: // Status O.K.
							ContentValues cv = originalData.get(hrefs[hrefIndex]);
							cv.put(DavResources.RESOURCE_DATA, in.toString());
							for (Header hdr : requestor.getResponseHeaders()) {
								if (hdr.getName().equalsIgnoreCase("ETag")) {
									cv.put(DavResources.ETAG, hdr.getValue());
									cv.put(DavResources.NEEDS_SYNC, false);
									break;
								}
							}
							writeResource( db, WriteActions.UPDATE, cv);
							break;

						default: // Unknown code
							Log.w(TAG, "Status " + status + " on GET request for " + path);
					}
				}
			}
			catch (Exception e) {
				Log.e(TAG, "Exception GETting resource to DB: " + e.getMessage());
				Log.e(TAG, Log.getStackTraceString(e));
			}
		}
		db.setTransactionSuccessful();
		db.endTransaction();
		db.close();

		if (Constants.LOG_VERBOSE)	Log.v(TAG, "syncWithGet() for " + hrefs.length + " resources took "
						+ (System.currentTimeMillis() - fullMethod) + "ms");
		return;
	}

	
	private void writeResource( SQLiteDatabase db, WriteActions action, ContentValues resourceValues ) throws Exception {
		try {
			SynchronisationJobs.writeResource(db,action,resourceValues);
		}
		catch ( Exception e ) {
			Log.w(TAG,"Exception during collection contents sync: requesting full resync of collection.");
			Log.w(TAG,Log.getStackTraceString(e));
			ServiceJob job = new InitialCollectionSync( serverId, collectionPath);
			context.addWorkerJob(job);
			throw e; // So we give up on this update.
		}
	}

	

	private boolean timeToRun() {
		if ( synchronisationForced ) {
			if (Constants.LOG_VERBOSE) Log.v(TAG, "Synchronising now, since a sync has been forced.");
			return true;
		}
		String needsSyncNow = collectionData.getAsString(DavCollections.NEEDS_SYNC);
		if ( needsSyncNow == null || needsSyncNow.equals("1") ) {
			if (Constants.LOG_VERBOSE) Log.v(TAG, "Synchronising now, since needs_sync is true.");
			return true; 
		}
		String lastSyncString = collectionData.getAsString(DavCollections.LAST_SYNCHRONISED);
		if ( lastSyncString == null ) { 
			if (Constants.LOG_VERBOSE) Log.v(TAG, "Synchronising now, since last_sync is null.");
			return true; 
		}
		AcalDateTime lastRunTime = null;

		lastRunTime = AcalDateTime.fromString(lastSyncString);
		if ( lastRunTime == null ) return true; 
		
		lastRunTime.applyLocalTimeZone();
		
		ConnectivityManager conMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo netInfo = conMan.getActiveNetworkInfo();
		long maxAgeMs = minBetweenSyncs;
		if ( netInfo != null && netInfo.getType() == ConnectivityManager.TYPE_MOBILE )
			maxAgeMs = collectionData.getAsLong(DavCollections.MAX_SYNC_AGE_3G);
		else
			maxAgeMs = collectionData.getAsLong(DavCollections.MAX_SYNC_AGE_WIFI);

		if ( maxAgeMs < minBetweenSyncs ) maxAgeMs = minBetweenSyncs; 

		if (Constants.LOG_VERBOSE) Log.v(TAG, "Considering whether we are " + maxAgeMs / 1000 + "s past "
						+ lastRunTime.fmtIcal() + "("+lastRunTime.getMillis()+") yet? "
						+ "Now: " + new AcalDateTime().fmtIcal() + "("+System.currentTimeMillis()+")... So: "
						+ ((maxAgeMs + lastRunTime.getMillis() < System.currentTimeMillis()) ? "yes" : "no"));

		return (maxAgeMs + lastRunTime.getMillis() < System.currentTimeMillis());
	}

	
	private void scheduleNextInstance() {
		String lastSync = collectionData.getAsString(DavCollections.LAST_SYNCHRONISED);
		long timeToWait = minBetweenSyncs;
		if ( lastSync != null ) {
			long maxAge3g = collectionData.getAsLong(DavCollections.MAX_SYNC_AGE_3G);
			long maxAgeWifi = collectionData.getAsLong(DavCollections.MAX_SYNC_AGE_WIFI);
			timeToWait = (maxAge3g > maxAgeWifi ? maxAgeWifi : maxAge3g);

			AcalDateTime lastRunTime = null;
			lastRunTime = AcalDateTime.fromString(lastSync);
			lastRunTime.applyLocalTimeZone();
			timeToWait += (lastRunTime.getMillis() - System.currentTimeMillis());
			
			if ( minBetweenSyncs > timeToWait ) timeToWait = minBetweenSyncs;
		}

		if ( Constants.LOG_DEBUG ) Log.d(TAG, "Scheduling next check for "+collectionId+" - '"
						+ collectionData.getAsString(DavCollections.DISPLAYNAME)
						+"' in " + Long.toString(timeToWait / 1000) + " seconds.");
		
		this.TIME_TO_EXECUTE = timeToWait;
		context.addWorkerJob(this);
	}

	public boolean equals(Object that) {
		if (this == that) return true;
		if (!(that instanceof SyncCollectionContents)) return false;
		SyncCollectionContents thatCis = (SyncCollectionContents) that;
		return this.collectionId == thatCis.collectionId;
	}

	public int hashCode() {
		int result = HashCodeUtil.SEED;
		result = HashCodeUtil.hash(result, this.collectionId);
		return result;
	}

	@Override
	public String getDescription() {
		return "Syncing collection contents of collection " + collectionId;
	}

}
