package com.morphoss.acal.database.resourcesmanager.requests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import android.content.ContentValues;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.alarmmanager.AlarmRow;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.ReadOnlyResourceTableManager;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import com.morphoss.acal.database.resourcesmanager.ResourceProcessingException;
import com.morphoss.acal.database.resourcesmanager.ResourceResponse;
import com.morphoss.acal.database.resourcesmanager.requesttypes.ReadOnlyBlockingRequestWithResponse;
import com.morphoss.acal.dataservice.Collection;
import com.morphoss.acal.dataservice.Resource;
import com.morphoss.acal.davacal.VCalendar;

public class RRGetUpcomingAlarms extends ReadOnlyBlockingRequestWithResponse<ArrayList<AlarmRow>> {

	private Map<Long,Collection> alarmCollections = null;
	private AcalDateTime alarmsAfter = null;

	public RRGetUpcomingAlarms(AcalDateTime after) {
		super();
		alarmsAfter = after.clone();
	}

	@Override
	public void process(ReadOnlyResourceTableManager processor)	throws ResourceProcessingException {
		alarmCollections = Collection.getAllCollections(processor.getContext());
		ArrayList<AlarmRow> alarmList = new ArrayList<AlarmRow>(); 

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

	public class RRGetUpcomingAlarmsResult extends ResourceResponse<ArrayList<AlarmRow>> {

		private ArrayList<AlarmRow> result;
		
		public RRGetUpcomingAlarmsResult(ArrayList<AlarmRow> result) { 
			this.result = result;
			setProcessed();
		}
		
		@Override
		public ArrayList<AlarmRow> result() {return this.result;	}
		
	}

}
