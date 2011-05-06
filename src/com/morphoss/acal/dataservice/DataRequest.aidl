package com.morphoss.acal.dataservice;

import com.morphoss.acal.dataservice.DataRequestCallBack;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.davacal.AcalEventAction;
import com.morphoss.acal.davacal.AcalAlarm;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.davacal.AcalEvent;


interface DataRequest {
	void resetCache();
	List getEventsForDateRange(in AcalDateRange dateRange);
	boolean isInitialising();
	boolean isProcessing();
	void registerCallback(DataRequestCallBack cb);
	void unregisterCallback(DataRequestCallBack cb);
	AcalAlarm getCurrentAlarm();
	void dismissAlarm(in AcalAlarm alarm);
	void snoozeAlarm(in AcalAlarm alarm);
	void eventChanged(in AcalEventAction action);
	List getEventsForDay(in AcalDateTime day);
	List getEventsForDays(in AcalDateRange days);
	int getNumberEventsForDay(in AcalDateTime day);
	AcalEvent getNthEventForDay(in AcalDateTime day, int n);
	void deleteEvent(in AcalDateTime day, int n);
	void flushCache();
}