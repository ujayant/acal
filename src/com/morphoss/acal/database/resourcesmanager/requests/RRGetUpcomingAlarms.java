package com.morphoss.acal.database.resourcesmanager.requests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import android.content.ContentValues;
import android.content.Context;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.ReadOnlyResourceTableManager;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import com.morphoss.acal.database.resourcesmanager.ResourceProcessingException;
import com.morphoss.acal.database.resourcesmanager.ResourceResponse;
import com.morphoss.acal.database.resourcesmanager.ResourceResponseListener;
import com.morphoss.acal.database.resourcesmanager.requesttypes.ReadOnlyResourceRequestWithResponse;
import com.morphoss.acal.dataservice.Collection;
import com.morphoss.acal.dataservice.Resource;
import com.morphoss.acal.davacal.AcalAlarm;
import com.morphoss.acal.davacal.VCalendar;

public class RRGetUpcomingAlarms extends ReadOnlyResourceRequestWithResponse<ArrayList<AcalAlarm>> {

	public RRGetUpcomingAlarms( ResourceResponseListener<ArrayList<AcalAlarm>> callBack ) {
		super(callBack);
	}

	private Map<Long,Collection> alarmCollections = null;
	private AcalDateTime alarmsAfter = null;
	private boolean processingCompleted = false;

	public void setUp(Context context, AcalDateTime after ) {
		alarmCollections = Collection.getAllCollections(context);
		alarmsAfter = after.clone();
	}

	@Override
	public void process(ReadOnlyResourceTableManager processor)	throws ResourceProcessingException {
		ArrayList<AcalAlarm> alarmList = new ArrayList<AcalAlarm>(); 

		long start = alarmsAfter.getMillis();
		long end = start;
		start -= AcalDateTime.SECONDS_IN_HOUR * 36 * 1000L;
		end   += AcalDateTime.SECONDS_IN_DAY * 70 * 1000L;

		for( Collection collection : alarmCollections.values() ) {
			if ( (!collection.useForEvents && !collection.useForTasks) || !collection.alarmsEnabled ) continue;

			ArrayList<ContentValues> cvs = processor.query(null, 
					ResourceTableManager.COLLECTION_ID+" = ?"+
							" AND ("+ResourceTableManager.LATEST_END+" IS NULL OR " + ResourceTableManager.LATEST_END+" >= ? )" +
							" AND ("+ResourceTableManager.EARLIEST_START+" IS NULL OR "+ResourceTableManager.EARLIEST_START+" <= ? )" +
							" AND ("+ResourceTableManager.RESOURCE_DATA+" LIKE '%BEGIN:VALARM%' )"
							,
					new String[]{collection.collectionId+"", start+"", end+""},
															null,null,null);

			for (ContentValues cv : cvs) {
				Resource r = Resource.fromContentValues(cv);
				VCalendar vc = new VCalendar(r);
				vc.appendAlarmInstancesBetween(alarmList, new AcalDateRange(alarmsAfter, AcalDateTime.addDays(alarmsAfter, 7)));
			}
		}
		Collections.sort(alarmList);
		RRGetUpcomingAlarmsResult response = new RRGetUpcomingAlarmsResult(alarmList);
		
		this.postResponse(response);
	}

	public class RRGetUpcomingAlarmsResult extends ResourceResponse<ArrayList<AcalAlarm>> {

		private ArrayList<AcalAlarm> result;
		
		public RRGetUpcomingAlarmsResult(ArrayList<AcalAlarm> result) { 
			this.result = result;
			setCompleted();
		}
		
		@Override
		public ArrayList<AcalAlarm> result() {return this.result;	}
		
	}

	private synchronized void setCompleted() {
		processingCompleted = true;
	}
	
	@Override
	public synchronized boolean isProcessed() {
		return processingCompleted;
	}

}
