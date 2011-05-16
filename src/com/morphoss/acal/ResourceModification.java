package com.morphoss.acal;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalRepeatRule;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.providers.DavResources;
import com.morphoss.acal.service.aCalService;
import com.morphoss.acal.service.SynchronisationJobs.WriteActions;

public class ResourceModification {

	private static final String TAG = "Acal ResourceModification";
	private final WriteActions modificationAction;
	private final ContentValues resourceValues;
	private final Integer pendingId;
	private DatabaseChangedEvent dbChangeNotification = null;
	
	public ResourceModification(WriteActions action, ContentValues inResourceValues, Integer pendingId) {
		if ( action == WriteActions.UPDATE || action == WriteActions.INSERT ) {
			VComponent vCal = null;
			try {
				vCal = VCalendar.createComponentFromResource(inResourceValues, null);
				try {
					AcalRepeatRule rRule = AcalRepeatRule.fromVCalendar((VCalendar) vCal);
					AcalDateRange instancesRange = rRule.getInstancesRange();
				
					inResourceValues.put(DavResources.EARLIEST_START, instancesRange.start.getMillis());
					if ( instancesRange.end == null )
						inResourceValues.putNull(DavResources.LATEST_END);
					else
						inResourceValues.put(DavResources.LATEST_END, instancesRange.end.getMillis());
				}
				catch ( Exception e ) {
					
				}
			}
			catch (Exception e) {
			}
		}

		this.modificationAction = action;
		this.resourceValues = inResourceValues;
		this.pendingId = pendingId;
	}

	public void commit( SQLiteDatabase db ) {
		Integer resourceId = resourceValues.getAsInteger(DavResources._ID);

		if ( Constants.LOG_DEBUG )
			Log.d(TAG, "Writing Resource with " + modificationAction + " on resource ID "
						+ (resourceId == null ? "new" : Integer.toString(resourceId)));

		switch (modificationAction) {
			case UPDATE:
				db.update(DavResources.DATABASE_TABLE, resourceValues, DavResources._ID + " = ?",
							new String[] { Integer.toString(resourceId) });
				if (resourceValues.getAsString(DavResources.RESOURCE_DATA) != null)
					dbChangeNotification = new DatabaseChangedEvent( DatabaseChangedEvent.DATABASE_RECORD_UPDATED,
								DavResources.class, resourceValues);
				break;
			case INSERT:
				resourceId = (int) db.insert(DavResources.DATABASE_TABLE, null, resourceValues);
				resourceValues.put(DavResources._ID, resourceId);
				if (resourceValues.getAsString(DavResources.RESOURCE_DATA) != null)
					dbChangeNotification = new DatabaseChangedEvent( DatabaseChangedEvent.DATABASE_RECORD_INSERTED,
								DavResources.class, resourceValues);
				break;
			case DELETE:
				if (Constants.LOG_DEBUG)
					Log.d(TAG,"Deleting resources with ResourceId = " + Integer.toString(resourceId) );
				db.delete(DavResources.DATABASE_TABLE, DavResources._ID + " = ?",
							new String[] { Integer.toString(resourceId) });
				dbChangeNotification = new DatabaseChangedEvent( DatabaseChangedEvent.DATABASE_RECORD_DELETED,
								DavResources.class, resourceValues);
				break;
		}
		
	}

	public void notifyChange() {
		if ( dbChangeNotification == null ) {
			if ( Constants.LOG_VERBOSE ) Log.v(TAG,"No change to notify to databaseDispatcher");
			return;
		}
		aCalService.databaseDispatcher.dispatchEvent(dbChangeNotification);
	}
}
