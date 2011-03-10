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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.DatabaseChangedEvent;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalRepeatRule;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.providers.DavResources;
import com.morphoss.acal.providers.Servers;
import com.morphoss.acal.service.connector.BasicAuth;
import com.morphoss.acal.service.connector.Connector;
import com.morphoss.acal.service.connector.ConnectorRequestError;
import com.morphoss.acal.service.connector.HttpAuthProvider;
import com.morphoss.acal.service.connector.SendRequestFailedException;
import com.morphoss.acal.xml.DavNode;
import com.morphoss.acal.xml.DavXmlTreeBuilder;

public class SynchronisationJobs extends ServiceJob {

	public static final int		HOME_SET_DISCOVERY	= 0;
	public static final int		HOME_SETS_UPDATE	= 1;
	public static final int		CALENDAR_SYNC		= 2;

	public static final String	TAG					= "aCal SynchronisationJobs";

	public int					TIME_TO_EXECUTE		= 0;							// immediately
	private int					jobtype;
	private aCalService			context;

	public static enum WriteActions { UPDATE, INSERT, DELETE };

	
	public SynchronisationJobs( int jobtype ) {
		this.jobtype = jobtype;
	}

	@Override
	public void run( aCalService context ) {
		this.context = context;
		switch (jobtype) {
			case HOME_SET_DISCOVERY:
				refreshHomeSets();
				break;

			case HOME_SETS_UPDATE:
				refreshCollectionsFromHomeSets();
				break;

		}
	}

	public synchronized void refreshHomeSets() {
		Cursor mCursor = context.getContentResolver().query(Servers.CONTENT_URI,
					new String[] { Servers._ID, Servers.ACTIVE }, null, null, null);
		mCursor.moveToFirst();
		//Set<HomeSetDiscovery> hsd = new HashSet<HomeSetDiscovery>();
		while (!mCursor.isAfterLast()) {
			if (mCursor.getInt(1) == 1) {
				ServiceJob sj = new HomeSetDiscovery(mCursor.getInt(0));
				context.addWorkerJob(sj);
			}
			mCursor.moveToNext();
		}
		mCursor.close();
	}

	public synchronized void refreshCollectionsFromHomeSets() {
		Cursor mCursor = context.getContentResolver().query(Servers.CONTENT_URI,
					new String[] { Servers._ID, Servers.ACTIVE }, null, null, null);
		mCursor.moveToFirst();
		while (!mCursor.isAfterLast()) {
			if (mCursor.getInt(1) == 1) {
				ServiceJob sj = new HomeSetsUpdate(mCursor.getInt(0));
				context.addWorkerJob(sj);
			}
			mCursor.moveToNext();
		}
		mCursor.close();

	}

	// The following overrides are to prevent duplication of these jobs in the queue
	public boolean equals(Object o) {
		if (!(o instanceof SynchronisationJobs)) return false;
		if (((SynchronisationJobs) o).jobtype == this.jobtype) return true;
		return false;
	}

	public int hashCode() {
		return this.jobtype;
	}

	public static ContentValues getServerData(int serverId, ContentResolver cr) {

		ContentValues serverData = new ContentValues();
		Cursor mCursor = null;

		try {
			// get serverData
			mCursor = cr.query(ContentUris.withAppendedId(Servers.CONTENT_URI, serverId), null, null, null, null);
			if ( !mCursor.moveToFirst() ) {
				mCursor.close();
				return null;
			}

			DatabaseUtils.cursorRowToContentValues(mCursor,serverData);

		}
		catch (Exception e) {
			// Error getting data
			Log.e(TAG, "Error getting server data from DB: " + e.getMessage());
			Log.e(TAG, Log.getStackTraceString(e));
			return null;
		}
		finally {
			if (mCursor != null) mCursor.close();
		}

		return serverData;

	}

	
	public static ContentValues getResourceData(long resourceId, ContentResolver cr) {
		ContentValues resourceData = null;
		Cursor mCursor = null;
		try {
			mCursor = cr.query(DavResources.CONTENT_URI, null, DavResources._ID + "=?",
						new String[] {Long.toString(resourceId)}, null);
			if ( !mCursor.moveToFirst() ) {
				Log.e(TAG, "No dav_resource row in DB for " + Long.toString(resourceId));
				mCursor.close();
				return null;
			}
			resourceData = new ContentValues();
			DatabaseUtils.cursorRowToContentValues(mCursor,resourceData);
		}
		catch (Exception e) {
			// Error getting data
			Log.e(TAG, "Error getting resource data from DB: " + e.getMessage());
			Log.e(TAG, Log.getStackTraceString(e));
			mCursor.close();
			return null;
		}
		finally {
			mCursor.close();
		}
		return resourceData;
	}	

	
	public static Connector prepareRequest(int serverId, String path, Header[] headers, String data,
				ContentValues serverData ) {

		HttpAuthProvider auth = null;

		switch (serverData.getAsInteger(Servers.AUTH_TYPE)) {
			case 1:
				auth = new BasicAuth(serverData.getAsString(Servers.USERNAME), serverData.getAsString(Servers.PASSWORD));
				break;
			default:
				break;
		}

		String protocol = "http";
		if (serverData.getAsInteger(Servers.USE_SSL) == 1) protocol = "https";
		Connector con = new Connector(protocol, serverData.getAsInteger(Servers.PORT),
												serverData.getAsString(Servers.HOSTNAME), auth);

		return con;
	}

	
	public static DavNode redirectableRequest(int serverId, String method, String path, Header[] headers, String data,
				ContentValues serverData, String protocol, int port, String hostname, int maxRedirects ) throws ConnectorRequestError {

		HttpAuthProvider auth = null;

		switch (serverData.getAsInteger(Servers.AUTH_TYPE)) {
			case 1:
				auth = new BasicAuth(serverData.getAsString(Servers.USERNAME), serverData.getAsString(Servers.PASSWORD));
				break;
			default:
				break;
		}

		while( maxRedirects > 0 ) {
			Connector con = new Connector(protocol, port, hostname, auth);
	
			try {
				InputStream in = con.sendRequest(method.toUpperCase(), path, headers, data);
				if (in == null) {
					if (Constants.LOG_DEBUG) Log.d(TAG, "Error - Unable to get data stream from server.");
					return null;
				}
				int status = con.getStatusCode();
				switch (status) {
					case 207: // Status O.K.
						return DavXmlTreeBuilder.buildTreeFromXml(in);
					case 301: // Moved permanently
					case 302: // "Found" - actually the most common redirect 
					case 307: // Temporary redirect
						Header[] responseHeaders = con.getResponseHeaders();
						for( int i=0; i< responseHeaders.length; i++ ) {
							if ( Constants.LOG_VERBOSE ) Log.v(TAG, "Response header '"+responseHeaders[i].getName()
											+"' is '"+responseHeaders[i].getValue()+"'");
							if ( responseHeaders[i].getName().equalsIgnoreCase("Location") ) {
								Log.w(TAG,"Following redirect from '"+path+"' to '"+responseHeaders[i].getValue()+"'");
								Pattern p = Pattern.compile("^((https?)://([a-z0-9-\\.]+)(:([0-9]+))?)?(/.*)$");
								Matcher m = p.matcher(responseHeaders[i].getValue()); 
								if ( m.matches() ) {
									path = m.group(6);
									hostname = m.group(3);
									protocol = m.group(2);
									String portNum = m.group(5);
									if ( portNum == null ) {
										port = (protocol.equals("http") ? 80 : 443 );  
									}
									else {
										port = Integer.parseInt(portNum);
									}
									if ( maxRedirects > 0 ) continue;
								}
								throw new ConnectorRequestError(status, responseHeaders[i].getValue() );
							}
						}
						throw new ConnectorRequestError(status, "No 'Location' header supplied" );
					case 404: // Not found
						throw new ConnectorRequestError(status, "Not found" );
					default: // Unknown code
						if (status > 499) {
							Log.e(TAG, "Remote server error code " + status + " on " + method + " request for " + path);
						}
						else {
							Log.w(TAG, "Status " + status + " on " + method + " request for " + path);
						}
						if (Constants.LOG_DEBUG) {
							Log.d(TAG, "Our request headers were:");
							Log.d(TAG, headers.toString());
							Log.d(TAG, "------------------------ request data was:");
							Log.d(TAG, data);
						}
						return null;
				}
			}
			catch (ConnectorRequestError re) {
				Log.e(TAG, "ConnectorRequestError on " + method + " request for " + path + ": " + re.getMessage() );
				throw re;
			}
			catch (SendRequestFailedException e) {
				Log.w(TAG, "Http request failed: " + e.getMessage());
				return null;
			}
			catch (Exception e) {
				Log.e(TAG, "Unknown error when connecting to server: " + e.getMessage());
				Log.e(TAG, Log.getStackTraceString(e));
				return null;
			}
		}
				
		return null;
	}

	
	public static DavNode getXmlTree(int serverId, String method, String path, Header[] headers, String data,
				ContentValues serverData, int maxRedirects ) throws ConnectorRequestError {

		Connector con = prepareRequest(serverId, path, headers, data, serverData );
		if ( con == null ) return null;

		try {
			InputStream in = con.sendRequest(method.toUpperCase(), path, headers, data);
			if (in == null) {
				if (Constants.LOG_DEBUG) Log.d(TAG, "Error - Unable to get data stream from server.");
				return null;
			}
			int status = con.getStatusCode();
			switch (status) {
				case 207: // Status O.K.
					return DavXmlTreeBuilder.buildTreeFromXml(in);
				case 301: // Moved permanently
				case 302: // "Found" - actually the most common redirect 
				case 307: // Temporary redirect
					Header[] responseHeaders = con.getResponseHeaders();
					for( int i=0; i< responseHeaders.length; i++ ) {
						if ( Constants.LOG_VERBOSE ) Log.v(TAG, "Response header '"+responseHeaders[i].getName()
										+"' is '"+responseHeaders[i].getValue()+"'");
						if ( responseHeaders[i].getName().equalsIgnoreCase("Location") ) {
							Log.w(TAG,"Following redirect from '"+path+"' to '"+responseHeaders[i].getValue()+"'");
							Pattern p = Pattern.compile("^((https?)://([a-z0-9-\\.]+)(:([0-9]+))?)?(/.*)$");
							Matcher m = p.matcher(responseHeaders[i].getValue()); 
							if ( m.matches() ) {
								path = m.group(6);
								String hostname = m.group(3);
								String protocol = m.group(2);
								String portNum = m.group(5);
								int port = 0;
								if ( portNum == null ) {
									port = (protocol.equals("http") ? 80 : 443 );  
								}
								else {
									port = Integer.parseInt(portNum);
								}
								if ( maxRedirects > 0 ) {
									return redirectableRequest( serverId, method, path, headers, data, serverData,
														protocol, port, hostname, --maxRedirects );
								}
							}
							else {
								throw new ConnectorRequestError(status, responseHeaders[i].getValue() );
							}
						}
					}
					throw new ConnectorRequestError(status, "No 'Location' header supplied" );
				case 404: // Not found
					throw new ConnectorRequestError(status, "Not found" );
				default: // Unknown code
					if (status > 499) {
						Log.e(TAG, "Remote server error code " + status + " on " + method + " request for " + path);
					}
					else {
						Log.w(TAG, "Status " + status + " on " + method + " request for " + path);
					}
					if (Constants.LOG_DEBUG) {
						Log.d(TAG, "Our request headers were:");
						Log.d(TAG, headers.toString());
						Log.d(TAG, "------------------------ request data was:");
						Log.d(TAG, data);
					}
					return null;
			}
		}
		catch (ConnectorRequestError re) {
			Log.e(TAG, "ConnectorRequestError on " + method + " request for " + path + ": " + re.getMessage() );
			throw re;
		}
		catch (SendRequestFailedException e) {
			Log.w(TAG, "Http request failed: " + e.getMessage());
			return null;
		}
		catch (Exception e) {
			Log.e(TAG, "Unknown error when connecting to server: " + e.getMessage());
			Log.e(TAG, Log.getStackTraceString(e));
			return null;
		}
	}


	/**
	 * <p>
	 * Writes the modified resource back to the database, creating, updating or deleting depending on the
	 * action requested.  After it's written to the database we notify the CalendarDataService of the new
	 * event data.
	 * </p>
	 * 
	 * @param db
	 * @param action
	 * @param resourceValues
	 * @throws Exception 
	 */
	public static void writeResource( SQLiteDatabase db, WriteActions action, ContentValues resourceValues ) throws Exception {

		String data = resourceValues.getAsString(DavResources.RESOURCE_DATA);

		if ( action == WriteActions.UPDATE || action == WriteActions.INSERT ) {
			updateInstanceRange(resourceValues);
		}

		Integer resourceId = resourceValues.getAsInteger(DavResources._ID);
		Log.i(TAG, "Writing Resource with " + action + " on resource ID "
					+ (resourceId == null ? "new" : Integer.toString(resourceId)));
		switch (action) {
			case UPDATE:
				db.update(DavResources.DATABASE_TABLE, resourceValues, DavResources._ID + " = ?",
							new String[] { Integer.toString(resourceId) });
				if (data != null)
					aCalService.databaseDispatcher.dispatchEvent(new DatabaseChangedEvent(
								DatabaseChangedEvent.DATABASE_RECORD_UPDATED, DavResources.class, resourceValues));
				break;
			case INSERT:
				resourceId = (int) db.insert(DavResources.DATABASE_TABLE, null, resourceValues);
				resourceValues.put(DavResources._ID, resourceId);
				if (data != null)
					aCalService.databaseDispatcher.dispatchEvent(new DatabaseChangedEvent(
								DatabaseChangedEvent.DATABASE_RECORD_INSERTED, DavResources.class, resourceValues));
				break;
			case DELETE:
				if (Constants.LOG_DEBUG)
					Log.d(TAG,"Deleting resources with ResourceId = " + Integer.toString(resourceId) );
				db.delete(DavResources.DATABASE_TABLE, DavResources._ID + " = ?",
							new String[] { Integer.toString(resourceId) });
				aCalService.databaseDispatcher.dispatchEvent(new DatabaseChangedEvent(
								DatabaseChangedEvent.DATABASE_RECORD_DELETED, DavResources.class, resourceValues));
				break;
		}

	}

	
	private static void updateInstanceRange(ContentValues resourceValues) {
		VComponent vCal = null;
		try {
			vCal = VCalendar.createComponentFromResource(resourceValues, null);
		}
		catch (Exception e) {
			return;
		}
		if ( ! (vCal instanceof VCalendar ) ) return;

		try {
			AcalRepeatRule rRule = AcalRepeatRule.fromVCalendar((VCalendar) vCal);
			AcalDateRange instancesRange = rRule.getInstancesRange();
		
			resourceValues.put(DavResources.EARLIEST_START, instancesRange.start.getMillis());
			if ( instancesRange.end == null )
				resourceValues.putNull(DavResources.LATEST_END);
			else
				resourceValues.put(DavResources.LATEST_END, instancesRange.end.getMillis());
		}
		catch ( Exception e ) {
			
		}
		
	}


	
	@Override
	public String getDescription() {
		switch( jobtype ) {
			case HOME_SETS_UPDATE:
				return "Updating collections in all home sets";
			case HOME_SET_DISCOVERY:
				return "Discovering home sets for all servers";
		}
		Log.e(TAG,"No description defined for jobtype "+jobtype );
		return "Unknown SynchronisationJobs jobtype!";
	}

}
