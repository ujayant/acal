package com.morphoss.acal.dataservice;

import com.morphoss.acal.dataservice.DataRequestCallBack;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.davacal.AcalAlarm;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.davacal.AcalEvent;
import com.morphoss.acal.davacal.SimpleAcalEvent;
import com.morphoss.acal.davacal.SimpleAcalTodo;
import com.morphoss.acal.davacal.VCalendar;

interface DataRequest {
	void resetCache();
	List<AcalEvent> getEventsForDateRange(in AcalDateRange dateRange);
	boolean isInitialising();
	boolean isProcessing();
	void registerCallback(DataRequestCallBack cb);
	void unregisterCallback(DataRequestCallBack cb);
	AcalAlarm getCurrentAlarm();
	void dismissAlarm(in AcalAlarm alarm);
	void snoozeAlarm(in AcalAlarm alarm);
	void eventChanged(in AcalEvent action);
	List<SimpleAcalEvent> getEventsForDays(in AcalDateRange days);
	List<SimpleAcalEvent> getEventsForDay(in AcalDateTime day);
	int getNumberEventsForDay(in AcalDateTime day);
	SimpleAcalEvent getNthEventForDay(in AcalDateTime day, int n);
	void deleteEvent(in AcalDateTime day, int n);
	List<SimpleAcalTodo> getTodos(in boolean listCompleted, in boolean listFuture);
	void todoChanged(in VCalendar changedResource, in int action);
	int getNumberTodos();
	SimpleAcalTodo getNthTodo(int n);
	void flushCache();
	void flushDay(in AcalDateTime day);
}