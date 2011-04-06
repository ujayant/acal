package com.morphoss.acal.dataservice;

import com.morphoss.acal.dataservice.DataRequestCallBack;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.davacal.AcalEventAction;
import com.morphoss.acal.davacal.AcalAlarm;

interface DataRequest {
	List getEventsForDateRange(in AcalDateRange dateRange);
	boolean isInitialising();
	boolean isProcessing();
	void registerCallback(DataRequestCallBack cb);
	void unregisterCallback(DataRequestCallBack cb);
	AcalAlarm getCurrentAlarm();
	void dismissAlarm(in AcalAlarm alarm);
	void snoozeAlarm(in AcalAlarm alarm);
	void eventChanged(in AcalEventAction action);
}