package com.morphoss.acal.resources;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import android.content.ContentQueryMap;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.ResourceModification;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.providers.DavCollections;
import com.morphoss.acal.providers.Servers;
import com.morphoss.acal.resources.ResourcesManager.RequestProcessor;
import com.morphoss.acal.service.HomeSetsUpdate;
import com.morphoss.acal.service.ServiceJob;
import com.morphoss.acal.service.SyncCollectionContents;
import com.morphoss.acal.service.SynchronisationJobs;
import com.morphoss.acal.service.aCalService;
import com.morphoss.acal.service.SynchronisationJobs.WriteActions;
import com.morphoss.acal.service.connector.AcalRequestor;
import com.morphoss.acal.xml.DavNode;

public class RRInitialCollectionSync implements ResourcesRequest {

	private static final String TAG = "aCal ResourcesRequest: InitialCollectionSync";
	
	private int collectionId = -2;
	private int serverId = -2;
	private String collectionPath = null;
	private ContentValues collectionValues = null;
	private boolean isCollectionIdAssigned = false;
	private boolean collectionNeedsSync = false;
	private AcalRequestor requestor;
	private aCalService acalService;
	private ContentResolver cr;
	private RequestProcessor processor;
	private volatile boolean processed = false;

	private Header[] syncHeaders = new Header[] {
			new BasicHeader("Depth","1"),
			new BasicHeader("Content-Type","text/xml; encoding=UTF-8")
	};
	
	private final String syncData = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"+
										"<sync-collection xmlns=\"DAV:\">"+
											"<sync-token/>"+
											"<prop>"+
												"<getetag/>"+
											"</prop>"+
/**											"<limit>"+
												"<nresults>"+MAX_RESULTS+"</nresults>"+
											"</limit>"+
*/
										"</sync-collection>";
												
	private final String calendarQuery = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
	"<calendar-query xmlns:D=\"DAV:\" xmlns=\"urn:ietf:params:xml:ns:caldav\">\n"+
	"  <D:prop>\n"+
	"   <D:getetag/>\n"+
	"   <calendar-data/>\n"+
	"   <D:getlastmodified/>\n"+
	"   <D:getcontenttype/>\n"+
	"  </D:prop>\n"+
	"  <filter>\n"+
	"    <comp-filter name=\"VCALENDAR\">\n"+
	"      <comp-filter name=\"VEVENT\">\n"+
	"        <time-range start=\"%s\" end=\"%s\"/>\n"+
	"      </comp-filter>\n"+
	"    </comp-filter>\n"+
	"  </filter>\n"+
	"</calendar-query>";
	
	public RRInitialCollectionSync(int collectionId) {
		this.collectionId = collectionId;
		this.isCollectionIdAssigned = true;
		collectionPath = null;
	}

	public RRInitialCollectionSync(int collectionId, int serverId, String collectionPath) {
		this.collectionId = collectionId;
		this.serverId = serverId;
		this.collectionPath = collectionPath;
		if ( collectionPath == null || serverId < 0 || collectionId < 0 )
			throw new IllegalArgumentException("collectionPath, serverId and collectionId should be assigned real values!");
		this.isCollectionIdAssigned = true;
	}

	public RRInitialCollectionSync(int serverId, String collectionPath) {
		this.serverId = serverId;
		this.collectionPath = collectionPath;
	}
	
	public void setService(aCalService svc) {
		this.acalService = svc;
	}

	@Override
	public void process(RequestProcessor processor) throws ResourceProccessingException {
		this.processor = processor;  
		cr = processor.getContentResolver();

		if ( !getCollectionId() ) {
			processed = true;
			return;
		}

		collectionNeedsSync = false;

		if (Constants.LOG_DEBUG) Log.d(TAG, "Starting initial sync process for server " + serverId + ", Collection: " + collectionPath);
		ContentValues serverData;
		try {
			// get serverData
			serverData = Servers.getRow(serverId, cr);
			if (serverData == null) {
				processed = true;
				throw new ResourceProccessingException("No record for ID " + serverId);
			}
			requestor = AcalRequestor.fromServerValues(serverData);
			requestor.setPath(collectionPath);
		}
		catch (Exception e) {
			// Error getting data
			Log.e(TAG, "Error getting server data: " + e.getMessage());
			Log.e(TAG, "Deleting invalid collection Record.");
			cr.delete(Uri.withAppendedPath(DavCollections.CONTENT_URI,Long.toString(collectionId)), null, null);
			processed = true;
			return;
		}

		if ( null == serverData.getAsInteger(Servers.HAS_SYNC) || 0 == serverData.getAsInteger(Servers.HAS_SYNC) ) {
			Log.i(TAG, "Skipping initial sync process since server does not support WebDAV synchronisation");
			collectionNeedsSync = true;

			if ( collectionValues.getAsInteger(DavCollections.ACTIVE_EVENTS) == 1 ) {
				syncRecentEvents();
			}
		}
		else {

			DavNode root = requestor.doXmlRequest("REPORT", collectionPath, syncHeaders, syncData);
			if (requestor.getStatusCode() == 404) {
				Log.i(TAG, "Sync REPORT got 404 on " + collectionPath + " so a HomeSetsUpdate is being scheduled.");
				ServiceJob sj = new HomeSetsUpdate(serverId);
				acalService.addWorkerJob(sj);
				processed = true;
				return;
			}
			if ( root == null ) {
				Log.w(TAG, "Unable to do intial sync - no XML data from server.");
				collectionNeedsSync = true;
			}
			else {
				processSyncToDatabase(root);
			}
		}

		// Now schedule a sync contents on this.
		acalService.addWorkerJob(new SyncCollectionContents(collectionId,collectionNeedsSync));
		processed = true;
	}

	public boolean running() {
		return processed;
	}
	
	private boolean getCollectionId() {
		Cursor cursor = null;
		try {
			if ( collectionPath == null ) {
				collectionValues = DavCollections.getRow(collectionId, cr);
				if ( collectionValues != null ) {
					serverId = collectionValues.getAsInteger(DavCollections.SERVER_ID);
					collectionPath = collectionValues.getAsString(DavCollections.COLLECTION_PATH);
					isCollectionIdAssigned = true;
				}
				else {
					Log.e(TAG,"Cannot find collection ID "+collectionId+" which I should sync!");
				}
			}
			else if ( serverId > 0 && collectionId < 0 ) {
				cursor = cr.query(DavCollections.CONTENT_URI, null, 
						DavCollections.SERVER_ID + "=? AND " + DavCollections.COLLECTION_PATH + "=?",
						new String[] { "" + serverId, collectionPath }, null);
				if ( cursor.moveToFirst() ) {
					collectionValues = new ContentValues();
					DatabaseUtils.cursorRowToContentValues(cursor, collectionValues);
					collectionId = collectionValues.getAsInteger(DavCollections._ID);
					isCollectionIdAssigned = true;
				}
				else {
					Log.e(TAG,"Cannot find collection "+collectionPath+" which I should sync!");
				}
			}
			else if ( collectionId < 0 ) {
				Log.e(TAG,"Cannot find collection which I should sync!");
				throw new IllegalStateException();
			}
		}
		catch ( Exception e ) {
			Log.e(TAG,"Error finding "+collectionPath+" in database: " + e.getMessage());
			Log.e(TAG,Log.getStackTraceString(e));
		}
		finally {
			if (cursor != null) cursor.close();
		}
		return ( isCollectionIdAssigned && collectionPath != null );
	}

	public void processSyncToDatabase( DavNode root ) {
		SQLiteDatabase db = processor.getWriteableDB();
		Cursor resourceCursor;
		try {

			// begin transaction
			if (Constants.LOG_VERBOSE) Log.v(TAG, "DavResources DB Transaction started.");

			// Get a map of all existing records where Name is the key.
			resourceCursor = db.query(processor.getTableName(this),
					null,
					RequestProcessor.COLLECTION_ID+"=?", new String[] { Integer.toString(collectionId) },
					null, null, null);
			resourceCursor.moveToFirst();
			ContentQueryMap cqm = new ContentQueryMap(resourceCursor, RequestProcessor.RESOURCE_NAME, false, null);
			cqm.requery();
			Map<String, ContentValues> databaseList = cqm.getRows();
			cqm.close();			
			resourceCursor.close();

			Map<String, ContentValues> serverList = new HashMap<String,ContentValues>();
			List<DavNode> responseList = root.getNodesFromPath("multistatus/response");

			if ( Constants.LOG_VERBOSE ) {
				Log.v(TAG,"Database list has "+databaseList.size()+" rows.");
				Log.v(TAG,"Response list has "+responseList.size()+" rows.");
			}

			// This could potentially be a really big list of small response sections, so
			// we allocate variables here and re-use inside loop for performance.
			List<DavNode> propstats;
			String name;
			DavNode prop = null;

			//iterate through each response and add to serverList
			for (DavNode response : responseList) {
				//get each prop
				prop = null;
				propstats = response.getNodesFromPath("propstat");
				name = response.segmentFromFirstHref("href");
				for (DavNode propstat : propstats) {
					if ( !propstat.getFirstNodeText("status").equalsIgnoreCase("HTTP/1.1 200 OK") ) continue;
					prop = propstat.getNodesFromPath("prop").get(0); 
				}
				if ( name == null || prop == null ) continue;

				ContentValues cv = new ContentValues();
				cv.put(RequestProcessor.COLLECTION_ID, collectionId);
				cv.put(RequestProcessor.RESOURCE_NAME, name);
				cv.put(RequestProcessor.NEEDS_SYNC, 1);

				serverList.put(name, cv);

				//Remove subtree to free up memory
				root.removeSubTree(response);
			}
			//Remove all duplicates
			removeDuplicates(databaseList, serverList);

			List<ResourceModification> changeList = new ArrayList<ResourceModification>(databaseList.size());
			for ( Entry<String,ContentValues> e : databaseList.entrySet() ) {
				changeList.add(new ResourceModification(WriteActions.DELETE, e.getValue(), null));
			}
			String id;
			ContentValues cv;
			for ( Entry<String,ContentValues> e : serverList.entrySet() ) {
				cv = e.getValue();
				id = cv.getAsString(RequestProcessor._ID);
				changeList.add(new ResourceModification((id == null || id.equals("")
						? WriteActions.INSERT
								: WriteActions.UPDATE),
								cv, null));
			}

			db.beginTransaction();

			boolean successful = ResourceModification.applyChangeList(db,changeList, processor.getTableName(this)); 

			if ( root != null ) {
				//Update sync token
				cv = new ContentValues();
				String syncToken = root.getFirstNodeText("multistatus/sync-token");
				cv.put(DavCollections.SYNC_TOKEN, syncToken);
				db.update(DavCollections.DATABASE_TABLE, cv,
						DavCollections._ID+"=?", new String[] {Integer.toString(collectionId)});
			}

			// Finish the transaction, as soon as possible
			if ( successful ) db.setTransactionSuccessful();
			db.endTransaction();

			if (Constants.LOG_VERBOSE)
				Log.v(TAG, databaseList.size() + " records deleted, " + serverList.size() + " updated");			

			if ( collectionValues.getAsInteger(DavCollections.ACTIVE_EVENTS) == 1 ) {
				syncRecentEvents();
			}

			//We can now approve the transaction
		}
		catch (Exception e) {
			Log.w(TAG, "Initial Sync transaction failed. Data not will not be saved.");
			Log.e(TAG,Log.getStackTraceString(e));
			if ( db.inTransaction() ) db.endTransaction();
		}
		processor.closeDB();

		//lastly, create new regular sync
		acalService.addWorkerJob(new SyncCollectionContents(this.collectionId)); 
	}


	/**
	 * When we have a lot of events to sync, we want to make sure that the period
	 * of time around the present day is in sync first.
	 */
	private void syncRecentEvents() {
		AcalDateTime from = new AcalDateTime().applyLocalTimeZone().addDays(-32).shiftTimeZone("UTC");
		AcalDateTime until = new AcalDateTime().applyLocalTimeZone().addDays(+68).shiftTimeZone("UTC");

		if (Constants.LOG_DEBUG)
			Log.d(TAG, "Doing a recent sync of events from "+from.toString()+" to "+until.toString());			

		DavNode root = requestor.doXmlRequest("REPORT", collectionPath, SynchronisationJobs.getReportHeaders(1),
				String.format(calendarQuery, from.fmtIcal(), until.fmtIcal()));

		ArrayList<ResourceModification> changeList = new ArrayList<ResourceModification>(); 

		List<DavNode> responses = root.getNodesFromPath("multistatus/response");

		SQLiteDatabase db = processor.getReadableDB();
		for (DavNode response : responses) {
			String name = response.segmentFromFirstHref("href");
			ContentValues cv = null;
			Cursor c = db.query(processor.getTableName(this), null,
					RequestProcessor.COLLECTION_ID+"=? AND "+RequestProcessor.RESOURCE_NAME+"=?",
					new String[] {Integer.toString(collectionId), name}, null, null, null);
			if ( c.moveToFirst() ) {
				cv = new ContentValues();
				DatabaseUtils.cursorRowToContentValues(c, cv);
			}
			c.close();

			WriteActions action = WriteActions.UPDATE;
			if ( cv == null ) {
				cv = new ContentValues();
				cv.put(RequestProcessor.COLLECTION_ID, collectionId);
				cv.put(RequestProcessor.RESOURCE_NAME, name);
				cv.put(RequestProcessor.NEEDS_SYNC, 1);
				action = WriteActions.INSERT;
			}
			if ( !parseResponseNode(response, cv) ) continue;

			changeList.add( new ResourceModification(action,cv,null));
		}
		processor.closeDB();

		ResourceModification.commitChangeList(acalService, changeList, processor.getTableName(this));

	}


	/**
	 * <p>
	 * Parse a single &lt;response&gt; node within a &lt;multistatus&gt;
	 * </p>
	 * @return true If we need to write to the database, false otherwise.
	 */
	private boolean parseResponseNode(DavNode responseNode, ContentValues cv) {

		List<DavNode> propstats = responseNode.getNodesFromPath("propstat");
		if ( propstats.size() < 1 ) return false;

		for (DavNode propstat : propstats) {
			if ( !propstat.getFirstNodeText("status").equalsIgnoreCase("HTTP/1.1 200 OK") ) {
				responseNode.removeSubTree(propstat);
				continue;
			}

			DavNode prop = propstat.getNodesFromPath("prop").get(0);

			String etag = prop.getFirstNodeText("getetag");
			String data = prop.getFirstNodeText("calendar-data");
			String last_modified = prop.getFirstNodeText("getlastmodified");
			String content_type = prop.getFirstNodeText("getcontenttype");
			if ( etag == null || data == null ) return false;

			String oldEtag = cv.getAsString(RequestProcessor.ETAG);

			if ( oldEtag != null && oldEtag.equals(etag) ) {
				if ( Constants.LOG_VERBOSE ) Log.v(TAG,"ETag matches existing record, so no need to sync.");
				cv.put(RequestProcessor.NEEDS_SYNC, 0 );
				return false;
			}

			if ( last_modified != null ) cv.put(RequestProcessor.LAST_MODIFIED, last_modified);
			if ( content_type != null ) cv.put(RequestProcessor.CONTENT_TYPE, content_type);
			if ( data != null ) {
				cv.put(RequestProcessor.RESOURCE_DATA, data); 
				cv.put(RequestProcessor.ETAG, etag);
				cv.put(RequestProcessor.NEEDS_SYNC, 0 );
				if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Got data now, so no need to sync later.");
				return true;
			}
			if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Need to sync "+cv.getAsString(RequestProcessor.RESOURCE_NAME));
			cv.put(RequestProcessor.NEEDS_SYNC, 1 );
		}

		// We remove our references to this now, since we've finished with it.
		responseNode.getParent().removeSubTree(responseNode);

		return true;
	}

	public boolean equals(Object that) {
		if ( this == that ) return true;
		if ( !(that instanceof RRInitialCollectionSync) ) return false;
		RRInitialCollectionSync thatCis = (RRInitialCollectionSync)that;
		if ( this.collectionPath == null && thatCis.collectionPath == null ) return true;
		if ( this.collectionPath == null || thatCis.collectionPath == null ) return false;
		return (
				this.collectionPath.equals(thatCis.collectionPath) &&
				this.serverId == thatCis.serverId
		);
	}
	
	private void removeDuplicates(Map<String,ContentValues> db, Map<String,ContentValues> server) {
		String[] names = new String[server.size()];
		server.keySet().toArray(names);
		for (String name : names) {
			if (!db.containsKey(name)) continue;	//New value from server
			if ( db.get(name).getAsString(RequestProcessor.ETAG) != null && server.get(name).getAsString(RequestProcessor.ETAG) != null
					&& db.get(name).getAsString(RequestProcessor.ETAG).equals(server.get(name).getAsString(RequestProcessor.ETAG)))  {
				//records match, remove from both
				server.remove(name);
			}
			else {
				server.get(name).put(RequestProcessor._ID, db.get(name).getAsString(RequestProcessor._ID));	//record to be updated. Insert ID
			}
			db.remove(name);
		}
	}
}
