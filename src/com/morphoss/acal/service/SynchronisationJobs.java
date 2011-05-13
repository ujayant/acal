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

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.DatabaseChangedEvent;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalRepeatRule;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.providers.DavCollections;
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

	/**
	 * Creates a sync job for ALL active collections.  If the collection was
	 * last synchronised less than 14 days ago we do a syncCollectionContents
	 * otherwise we do an initialCollectionSync.  We try and do these sync
	 * jobs with gaps between them.
	 *  
	 * @param worker
	 * @param context
	 */
	public static void startCollectionSync(WorkerClass worker, Context context) {
		ContentValues[] collectionsList = DavCollections.getCollections(context.getContentResolver(),
					DavCollections.INCLUDE_ALL_ACTIVE);
		String lastSyncString;
		AcalDateTime lastSync;
		int collectionId;
		long timeOfFirst = System.currentTimeMillis() + 500;

		for (ContentValues collectionValues : collectionsList) {
			collectionId = collectionValues.getAsInteger(DavCollections._ID);
			lastSyncString = collectionValues.getAsString(DavCollections.LAST_SYNCHRONISED);
			if (lastSyncString != null) {
				lastSync = AcalDateTime.fromString(lastSyncString);
				if (lastSync.addDays(14).getMillis() > System.currentTimeMillis()) {
					// In this case we will schedule a normal sync on the collection
					// which will hopefully be *much* lighter weight.
					SyncCollectionContents job = new SyncCollectionContents(collectionId);
					job.TIME_TO_EXECUTE = timeOfFirst;
					worker.addJobAndWake(job);
					timeOfFirst += 5000;
				}
			}
			else {
				InitialCollectionSync job = new InitialCollectionSync(collectionId);
				job.TIME_TO_EXECUTE = timeOfFirst;
				worker.addJobAndWake(job);
				timeOfFirst += 15000;
			}
		}

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
	public static void writeResource( SQLiteDatabase db, WriteActions action, ContentValues resourceValues ) {

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


	/**
	 * Returns a minimal Header[] array for a REPORT request.
	 * @param depth
	 * @return
	 */
	public static Header[] getReportHeaders( int depth ) {
		return new Header[] {
					new BasicHeader("Content-Type", "text/xml; encoding=UTF-8"),
					new BasicHeader("Depth", Integer.toString(depth))
				};
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
