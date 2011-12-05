package com.morphoss.acal.dataservice;

import java.util.List;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.davacal.AcalAlarm;

public interface WriteableEventInstance extends EventInstance {

	int ACTION_DELETE_SINGLE = 0;
	int ACTION_DELETE_ALL_FUTURE = 1;
	int ACTION_MODIFY_SINGLE = 2;
	int ACTION_MODIFY_ALL_FUTURE = 3;
	int ACTION_MODIFY_ALL = 4;
	int ACTION_CREATE = 5;
	int ACTION_DELETE_ALL = 6;
	
	//Methods for writeable instance
	public void setRepetition(String rrule);
	
	public void setDates(AcalDateTime start, AcalDuration duration);
	public void setSummary(String string);
	public void setCollection(Collection instance);
	public void setAlarms(List<AcalAlarm> alarmList);
	public void setDates(AcalDateTime setTimeZone, AcalDateTime setTimeZone2);
	public void setLocation(String newLoc);
	public void setDescription(String newDesc);
	public void setEndDate(AcalDateTime end);
	public void setRepeatRule(String newRule);
	public int getAction();
	public boolean isModifyAction();
	public int getOperation();
	void setAction(int actionModifyAll);
}
