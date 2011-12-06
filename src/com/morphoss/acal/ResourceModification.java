package com.morphoss.acal;

import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalRepeatRule;
import com.morphoss.acal.database.AcalDBHelper;
import com.morphoss.acal.davacal.Masterable;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VCard;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.providers.OldDavResources;
import com.morphoss.acal.providers.PendingChanges;
import com.morphoss.acal.service.SynchronisationJobs.WriteActions;
import com.morphoss.acal.service.aCalService;

public class ResourceModification {

	private static final String TAG = "Acal ResourceModification";
	private final WriteActions modificationAction;
	private final ContentValues resourceValues;
	private final Integer pendingId;
	private Integer resourceId = null;
	private DatabaseChangedEvent dbChangeNotification = null;
	
	public ResourceModification(WriteActions action, ContentValues inResourceValues, Integer pendingId) {
		if ( action == WriteActions.UPDATE || action == WriteActions.INSERT ) {
			VComponent vCal = null;
			try {
				vCal = VCalendar.createComponentFromResource(inResourceValues, null);
				String effectiveType = null;
				if ( vCal instanceof VCard )
					effectiveType = "VCARD";
				else if ( vCal instanceof VCalendar ) {
					try {
						Masterable firstMaster = ((VCalendar) vCal).getMasterChild();
						effectiveType = firstMaster.getName();
						AcalRepeatRule rRule = AcalRepeatRule.fromVCalendar((VCalendar) vCal);
						if ( rRule != null ) {
							try {
								AcalDateRange instancesRange = rRule.getInstancesRange();
							
								inResourceValues.put(OldDavResources.EARLIEST_START, instancesRange.start.getMillis());
								if ( instancesRange.end == null )
									inResourceValues.putNull(OldDavResources.LATEST_END);
								else
									inResourceValues.put(OldDavResources.LATEST_END, instancesRange.end.getMillis());
							}
							catch ( Exception e ) {
								Log.e(TAG,"Failed to get earliest_start / latest_end from resource of type: "+effectiveType, e );
							}
						}
					}
					catch ( Exception e ) {
						Log.i(TAG,"Failed to get type for resource - assuming VEVENT\n"+vCal.getOriginalBlob());
						Log.i(TAG,Log.getStackTraceString(e));
						effectiveType = "VEVENT";
					}
				}
				if ( effectiveType != null )
					inResourceValues.put(OldDavResources.EFFECTIVE_TYPE, effectiveType);
			}
			catch (Exception e) {
				Log.w(TAG,"Type of resource is just plain weird!\n"+inResourceValues.getAsString(OldDavResources.RESOURCE_DATA));
				Log.w(TAG,Log.getStackTraceString(e));
			}
		}

		this.modificationAction = action;
		this.resourceValues = inResourceValues;
		this.pendingId = pendingId;
		this.resourceId = resourceValues.getAsInteger(OldDavResources._ID);
	}


	/**
	 * Commit this change to the dav_resource table.
	 * @param db The database handle to use for the commit
	 */
	public void commit( SQLiteDatabase db ) {
		getResourceId();

		if ( Constants.LOG_DEBUG )
			Log.d(TAG, "Writing '"+resourceValues.getAsString(OldDavResources.EFFECTIVE_TYPE)
						+ "' resource with " + modificationAction + " on resource ID "
						+ (resourceId == null ? "new" : Integer.toString(resourceId)));

		switch (modificationAction) {
			case UPDATE:
				db.update(OldDavResources.DATABASE_TABLE, resourceValues, OldDavResources._ID + " = ?",
							new String[] { Integer.toString(resourceId) });
				if (resourceValues.getAsString(OldDavResources.RESOURCE_DATA) != null)
					dbChangeNotification = new DatabaseChangedEvent( DatabaseChangedEvent.DATABASE_RECORD_UPDATED,
								OldDavResources.class, resourceValues);
				break;
			case INSERT:
				resourceId = (int) db.insert(OldDavResources.DATABASE_TABLE, null, resourceValues);
				resourceValues.put(OldDavResources._ID, resourceId);
				if (resourceValues.getAsString(OldDavResources.RESOURCE_DATA) != null)
					dbChangeNotification = new DatabaseChangedEvent( DatabaseChangedEvent.DATABASE_RECORD_INSERTED,
								OldDavResources.class, resourceValues);
				break;
			case DELETE:
				if (Constants.LOG_DEBUG)
					Log.d(TAG,"Deleting resources with ResourceId = " + Integer.toString(resourceId) );
				db.delete(OldDavResources.DATABASE_TABLE, OldDavResources._ID + " = ?",
							new String[] { Integer.toString(resourceId) });
				dbChangeNotification = new DatabaseChangedEvent( DatabaseChangedEvent.DATABASE_RECORD_DELETED,
								OldDavResources.class, resourceValues);
				break;
		}

		if ( pendingId != null ) {
			// We can retire this change now
			int removed = db.delete(PendingChanges.DATABASE_TABLE,
					  PendingChanges._ID+"=?",
					  new String[] { Integer.toString(pendingId) });
			if ( Constants.LOG_DEBUG )
				Log.d(TAG, "Deleted "+removed+" one pending_change record ID="+pendingId+" for resourceId="+resourceId);

			ContentValues pending = new ContentValues();
			pending.put(PendingChanges._ID, pendingId);
			aCalService.databaseDispatcher.dispatchEvent(new DatabaseChangedEvent(DatabaseChangedEvent.DATABASE_RECORD_DELETED, PendingChanges.class, pending));
		}
	}


	/**
	 * Sends a notification of this change to the dataservice, if needed.
	 */
	public void notifyChange() {
		if ( dbChangeNotification == null ) {
			if ( Constants.LOG_VERBOSE ) Log.v(TAG,"No change to notify to databaseDispatcher");
			return;
		}
		aCalService.databaseDispatcher.dispatchEvent(dbChangeNotification);
	}

	/**
	 * Gets the ID of the resource being committed.  After a new resource is created this
	 * will return the resourceID it was created as.
	 * @return
	 */
	public Integer getResourceId() {
		return resourceId;
	}

	
	public Integer getPendingId() {
		return pendingId;
	}

	/**
	 * Use this to apply the resource modifications to the database.  In this case you
	 * will need to commit the database transaction afterwards
	 * @param changeList The List of ResourceModification objects to be applied
	 */
	public static boolean applyChangeList(SQLiteDatabase db, List<ResourceModification> changeList) {
		boolean completed =false;
		try {

			for ( ResourceModification changeUnit : changeList ) {
				changeUnit.commit(db);
				db.yieldIfContendedSafely();
			}
			completed = true;
		}
		catch (Exception e) {
			Log.e(TAG, "Exception updating resources DB: " + e.getMessage());
			Log.e(TAG, Log.getStackTraceString(e));
		}
		return completed;
	}

	
	/**
	 * Use this to actually commit our resource modifications to the database.
	 * @param changeList The List of ResourceModification objects to be committed
	 */
	public static void commitChangeList(Context c, List<ResourceModification> changeList) {
		AcalDBHelper dbHelper = new AcalDBHelper(c);
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		db.beginTransaction();

		boolean successful = applyChangeList(db,changeList); 
		if ( successful ) db.setTransactionSuccessful();

		db.endTransaction();
		db.close();
		if ( successful ) {
			DatabaseChangedEvent.beginResourceChanges();
			for ( ResourceModification changeUnit : changeList ) {
				changeUnit.notifyChange();
			}
			DatabaseChangedEvent.endResourceChanges();
		}
	}
	
}
