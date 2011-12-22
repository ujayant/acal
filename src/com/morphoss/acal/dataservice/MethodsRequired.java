package com.morphoss.acal.dataservice;

import java.util.ArrayList;
import java.util.List;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.davacal.AcalAlarm;
import com.morphoss.acal.davacal.VCalendar;

public class MethodsRequired {

	public List<EventInstance> getEventsForDays(AcalDateRange range) {
		// TODO Auto-generated method stub
		return null;
	}

	public int getNumberEventsForDay(AcalDateTime day) {
		// TODO Auto-generated method stub
		return 0;
	}

	public EventInstance getNthEventForDay(AcalDateTime day, int n) {
		// TODO Auto-generated method stub
		return null;
	}

	public void snoozeAlarm(AcalAlarm currentAlarm) {
		// TODO Auto-generated method stub
		
	}

	public void dismissAlarm(AcalAlarm currentAlarm) {
		// TODO Auto-generated method stub
		
	}

	public AcalAlarm getCurrentAlarm() {
		// TODO Auto-generated method stub
		return null;
	}

	public void eventChanged(EventInstance event) {
		// TODO Auto-generated method stub
		
	}

	public void todoChanged(VCalendar vc, int action) {
		// TODO Auto-generated method stub
		
	}

	public int getNumberTodos() {
		// TODO Auto-generated method stub
		return 0;
	}

	public void flushCache() {
		// TODO Auto-generated method stub
		
	}

	public ArrayList<EventInstance> getEventsForDay(AcalDateTime day) {
		// TODO Auto-generated method stub
		return null;
	}

}
