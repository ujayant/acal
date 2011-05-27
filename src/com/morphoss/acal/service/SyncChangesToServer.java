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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.net.ssl.SSLException;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.ResourceModification;
import com.morphoss.acal.StaticHelpers;
import com.morphoss.acal.database.AcalDBHelper;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VCard;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.providers.DavCollections;
import com.morphoss.acal.providers.DavResources;
import com.morphoss.acal.providers.PendingChanges;
import com.morphoss.acal.providers.Servers;
import com.morphoss.acal.service.SynchronisationJobs.WriteActions;
import com.morphoss.acal.service.connector.AcalRequestor;
import com.morphoss.acal.service.connector.SendRequestFailedException;

public class SyncChangesToServer extends ServiceJob {

	public static final String	TAG					= "aCal SyncChangesToServer";

	private long				timeToWait			= 90000;
	private ContentResolver		cr;
	private aCalService			context;

	private AcalRequestor 		requestor;
	
	List<ContentValues>			pendingChangesList	= null;
	int							pendingPos			= -1;
	Set<Integer>				collectionsToSync	= null;
	
	/**
	 * <p>
	 * Constructor
	 * </p>
	 * 
	 */
	public SyncChangesToServer() {
		this.TIME_TO_EXECUTE = System.currentTimeMillis();
	}

	
	@Override
	public void run(aCalService context) {
		this.context = context;
		this.cr = this.context.getContentResolver();
		this.requestor = new AcalRequestor();
		
		if ( marshallCollectionsToSync() ) {
			try {
				ContentValues collectionValues;
				while( (collectionValues = getChangeToSync()) != null ) {
					updateCollectionProperties(collectionValues);
				}
			}
			catch( Exception e ) {
				Log.e(TAG,Log.getStackTraceString(e));
			}
		}

		if ( !marshallChangesToSync() ) {
			if (Constants.LOG_DEBUG) Log.d(TAG, "No local changes to synchronise.");
			return; // without rescheduling
		}
		else if ( !connectivityAvailable() ) {
			if (Constants.LOG_DEBUG) Log.d(TAG, "No connectivity available to sync local changes.");
		}
		else {
			if (Constants.LOG_DEBUG)
				Log.d(TAG, "Starting sync of local changes");
			
			collectionsToSync = new HashSet<Integer>();
	
			try {
				ContentValues pendingChange;
				while( (pendingChange = getChangeToSync()) != null ) {
					syncOneChange(pendingChange);
				}

				if ( collectionsToSync.size() > 0 ) {
					for( Integer collectionId : collectionsToSync ) {
						context.addWorkerJob(new SyncCollectionContents(collectionId, true) );
					}
					
					// Fallback hack to really make sure the updated event actually gets displayed.
					// Push this out 30 seconds in the future to nag us to fix it properly!
					ServiceJob job = new SynchronisationJobs(SynchronisationJobs.CACHE_RESYNC);
					job.TIME_TO_EXECUTE = 30000L;
					context.addWorkerJob(job);
				}
			}
			catch( Exception e ) {
				Log.e(TAG,Log.getStackTraceString(e));
			}

		}

		if ( updateSyncStatus() ) {
			this.TIME_TO_EXECUTE = System.currentTimeMillis() + timeToWait;
			context.addWorkerJob(this);
		}
	}

	
	private boolean connectivityAvailable() {
		try {
			ConnectivityManager conMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo netInfo = conMan.getActiveNetworkInfo();
			if ( netInfo.isConnected() ) return true;
		}
		catch ( Exception e ) {
		}
		return false;
	}


	private boolean marshallChangesToSync() {
		Cursor pendingCursor = cr.query(PendingChanges.CONTENT_URI, null, null, null, null);
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
		pendingPos = -1;
		
		return ( pendingChangesList.size() != 0 );
	}

	
	private ContentValues getChangeToSync() {
		if ( ++pendingPos < pendingChangesList.size() )
			return pendingChangesList.get(pendingPos);
		return null;
	}

	
	private void syncOneChange(ContentValues pending) throws SSLException {

		int collectionId = pending.getAsInteger(PendingChanges.COLLECTION_ID);
		ContentValues collectionData = DavCollections.getRow(collectionId, cr);
		if (collectionData == null) {
			invalidPendingChange(pending.getAsInteger(PendingChanges._ID), 
						"Error getting collection data from DB - deleting invalid pending change record." );				
			return;
		}

		int serverId = collectionData.getAsInteger(DavCollections.SERVER_ID);
		ContentValues serverData = Servers.getRow(serverId, cr);
		if (serverData == null) {
			invalidPendingChange(pending.getAsInteger(PendingChanges._ID), 
						"Error getting server data from DB - deleting invalid pending change record." );				
			Log.e(TAG, "Deleting invalid collection Record.");
			cr.delete(Uri.withAppendedPath(DavCollections.CONTENT_URI,Long.toString(collectionId)), null, null);
			return;
		}
		requestor.applyFromServer(serverData, false);

		String collectionPath = collectionData.getAsString(DavCollections.COLLECTION_PATH);
		String newData = pending.getAsString(PendingChanges.NEW_DATA);
		String oldData = pending.getAsString(PendingChanges.OLD_DATA);
		
		WriteActions action = WriteActions.UPDATE;

		ContentValues resourceData = null;
		String latestDbData = null;
		String eTag = "*";
		String resourcePath = null;
		BasicHeader eTagHeader = null;
		BasicHeader contentHeader = new BasicHeader("Content-type", getContentType(newData) );

		Integer resourceId = pending.getAsInteger(PendingChanges.RESOURCE_ID);
		Integer pendingId = pending.getAsInteger(PendingChanges._ID);
		if ( resourceId == null || resourceId < 1 ) {
			action = WriteActions.INSERT;
			eTagHeader = new BasicHeader("If-None-Match", "*" );
			resourcePath = null;
			String contentExtension = getContentType(newData);
			if ( contentExtension.length() > 14 && contentExtension.substring(0,13).equals("text/calendar") )
				contentExtension = ".ics";
			else if ( contentExtension.substring(0,10).equals("text/vcard") )
				contentExtension = ".vcf";
			else
				contentExtension = ".txt";
			
			try {
				VComponent vc = VComponent.createComponentFromBlob(newData, -1, null);
				if ( vc instanceof VCard )
					resourcePath = StaticHelpers.rTrim(vc.getProperty("UID").getValue()) + ".vcf";
				else if ( vc instanceof VCalendar )
					resourcePath = StaticHelpers.rTrim(((VCalendar) vc).getMasterChild().getProperty("UID").getValue()) + ".ics";
			}
			catch ( Exception e ) {
				if ( Constants.LOG_DEBUG )
					Log.d(TAG,"Unable to get UID from resource");
				if ( Constants.LOG_VERBOSE )
					Log.v(TAG,Log.getStackTraceString(e));
			};
			if ( resourcePath == null ) {
					resourcePath = UUID.randomUUID().toString() + ".ics";
			}
			resourceData = new ContentValues();
			resourceData.put(DavResources.RESOURCE_NAME, resourcePath);
			resourceData.put(DavResources.COLLECTION_ID, collectionId);
		}
		else {
			resourceData = DavResources.getRow(resourceId, cr);
			if (resourceData == null) {
				invalidPendingChange(pendingId, 
							"Error getting resource data from DB - deleting invalid pending change record." );				
				return;
			}
			latestDbData = resourceData.getAsString(DavResources.RESOURCE_DATA);
			eTag = resourceData.getAsString(DavResources.ETAG);
			resourcePath = resourceData.getAsString(DavResources.RESOURCE_NAME);
			eTagHeader = new BasicHeader("If-Match", eTag );

			if ( newData == null ) {
				action = WriteActions.DELETE;
			}
			else {
				if ( oldData != null && latestDbData != null && oldData.equals(latestDbData) ) {
					newData = mergeAsyncChanges( oldData, latestDbData, newData );
				}
			}
		}

		String path = collectionPath + resourcePath;

		Header[] headers = new Header[] { eTagHeader, contentHeader};
		
		if (Constants.LOG_DEBUG)	Log.d(TAG, "Making "+action.toString()+" request to "+path);

		// If we made it this far we should do a sync on this collection ASAP after we're done
		collectionsToSync.add(collectionId);
		
		InputStream in = null;
		try {
			if ( action == WriteActions.DELETE )
				in = requestor.doRequest( "DELETE", path, headers, null);
			else
				in = requestor.doRequest( "PUT", path, headers, newData);
		}
		catch (SendRequestFailedException e) {
			Log.w(TAG,"HTTP Request failed: "+e.getMessage());
		}
			
		int status = requestor.getStatusCode();
		switch (status) {
			case 201: // Status Created (normal for INSERT).
			case 204: // Status No Content (normal for DELETE).
			case 200: // Status OK.
				if (Constants.LOG_DEBUG) Log.d(TAG, "Response "+status+" against "+path);
				resourceData.put(DavResources.RESOURCE_DATA, newData);
				resourceData.put(DavResources.NEEDS_SYNC, true);
				resourceData.put(DavResources.ETAG, "");
				for (Header hdr : requestor.getResponseHeaders()) {
					if (hdr.getName().equalsIgnoreCase("ETag")) {
						resourceData.put(DavResources.ETAG, hdr.getValue());
						resourceData.put(DavResources.NEEDS_SYNC, false);
						break;
					}
				}

				if (Constants.LOG_DEBUG) Log.d(TAG, "Applying resource modification to local database");
				ResourceModification changeUnit = new ResourceModification( action, resourceData, pendingId);
				AcalDBHelper dbHelper = new AcalDBHelper(this.context);
				SQLiteDatabase db = dbHelper.getWritableDatabase();
				boolean completed =false;
				try {
					// Attempting to keep our transaction as narrow as possible.
					db.beginTransaction();
					changeUnit.commit(db);
					db.setTransactionSuccessful();
					completed = true;
				}
				catch (Exception e) {
					Log.w(TAG, action.toString()+": Exception applying resource modification: " + e.getMessage());
					Log.d(TAG, Log.getStackTraceString(e));
				}
				finally {
					db.endTransaction();
					db.close();
					
					if ( completed ) changeUnit.notifyChange();

				}
				break;

			case 403: // Server won't accept it
				Log.w(TAG, action.toString()+": Status " + status + " on request for " + path + " giving up on change.");
				PendingChanges.deletePendingChange(context, pendingId);
				break;

			default: // Unknown code
				Log.w(TAG, action.toString()+": Status " + status + " on request for " + path);
				if ( in != null ) {
					// Possibly we got an error message...
					byte[] buffer = new byte[1024];
					try {
						in.read(buffer, 0, 1020);
						System.setProperty("file.encoding","UTF-8");
						String response = new String(buffer);
						Log.i(TAG,"Server response was: "+response);
					}
					catch (IOException e) {
					}
				}
		}
	}


	private String mergeAsyncChanges(String oldData, String latestDbData, String newData) {
		/**
		 * TODO Around here is where we should handle the case where latestDbData != oldData. We
		 * need to parse out both objects and work out what the differences are between oldData
		 * and newData, and see if we can apply them to latestDbData without them overwriting
		 * differences between oldData and latestDbData... 
		 */
		return newData;
	}


	private String getContentType(String fromData) {
		if ( fromData == null ) return "text/plain";

		if ( fromData.substring(6, 15).equalsIgnoreCase("vcalendar") ) {
			return "text/calendar; charset=\"utf-8\"";
		}
		else if ( fromData.substring(6, 11).equalsIgnoreCase("vcard") ) {
			return "text/vcard; charset=\"utf-8\"";
		}
		 return "text/plain";
	}

	
	private void invalidPendingChange(int pendingId, String msg) {
		Log.e(TAG, msg );
		cr.delete(Uri.withAppendedPath(PendingChanges.CONTENT_URI, Integer.toString(pendingId)), null, null);
	}

	
	private boolean updateSyncStatus() {
		return true;
	}

	
	private boolean marshallCollectionsToSync() {
		Cursor pendingCursor = cr.query(DavCollections.CONTENT_URI, null,
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

	
	final private static Header[] proppatchHeaders = new Header[] {
		new BasicHeader("Content-Type","text/xml; encoding=UTF-8")
	};

	final static String baseProppatch = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"+
		"<propertyupdate xmlns=\""+Constants.NS_DAV+"\"\n"+
		"    xmlns:ACAL=\""+Constants.NS_ACAL+"\">\n"+
		"<set>\n"+
		"  <prop>\n"+
		"   <ACAL:collection-colour>%s</ACAL:collection-colour>\n"+
		"  </prop>\n"+
		" </set>\n"+
		"</propertyupdate>\n";

	
	private void updateCollectionProperties( ContentValues collectionData ) {
		String proppatchRequest = String.format(baseProppatch,
					collectionData.getAsString(DavCollections.COLOUR)
				);

		try {
			ContentValues serverData = Servers.getRow(collectionData.getAsInteger(DavCollections.SERVER_ID), cr);
			requestor.applyFromServer(serverData, false);
			requestor.doRequest("PROPPATCH", collectionData.getAsString(DavCollections.COLLECTION_PATH),
						proppatchHeaders, proppatchRequest);

			collectionData.put(DavCollections.SYNC_METADATA, 0);
			cr.update(Uri.withAppendedPath(DavCollections.CONTENT_URI, collectionData.getAsString(DavCollections._ID)),
						collectionData, null, null);
		}
		catch (Exception e) {
			Log.e(TAG,"Error with proppatch to "+requestor.fullUrl());
			Log.d(TAG,Log.getStackTraceString(e));
		}

	}

	
	@Override
	public String getDescription() {
		return "Syncing local changes back to the server.";
	}

}
