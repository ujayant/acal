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
