package com.morphoss.acal.service;

import java.util.ArrayList;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.resourcesmanager.ResourceManager;
import com.morphoss.acal.database.resourcesmanager.ResourceResponse;
import com.morphoss.acal.database.resourcesmanager.ResourceResponseListener;
import com.morphoss.acal.database.resourcesmanager.requests.RRGetUpcomingAlarms;
import com.morphoss.acal.davacal.AcalAlarm;

public class ScheduleNextAlarmWakeup extends ServiceJob {

	private final RRGetUpcomingAlarms request = new RRGetUpcomingAlarms( new AlarmListListener() );
	private final AcalDateTime alarmsAfter;

	ScheduleNextAlarmWakeup(AcalDateTime after) {
		super();
		alarmsAfter = after;
	}

	@Override
	public void run(aCalService context) {
		request.setUp(context,alarmsAfter);
		ResourceManager rm = ResourceManager.getInstance(context);
		//send request
		rm.sendRequest(request);
	}

	@Override
	public String getDescription() {
		return "Scheduling next alarm wakeup after "+alarmsAfter;
	}

	public class AlarmListListener extends Object implements ResourceResponseListener<ArrayList<AcalAlarm>> {

		@Override
		public void resourceResponse(ResourceResponse<ArrayList<AcalAlarm>> response) {
		}
	}
}
