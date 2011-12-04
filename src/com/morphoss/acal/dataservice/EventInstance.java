package com.morphoss.acal.dataservice;

import java.util.List;

import android.os.Parcelable;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.activity.MonthView;
import com.morphoss.acal.davacal.AcalAlarm;
import com.morphoss.acal.weekview.WeekViewActivity;

public interface EventInstance extends Parcelable, Comparable<EventInstance> {

	int ACTION_DELETE_SINGLE = 0;
	int ACTION_DELETE_ALL_FUTURE = 1;
	int ACTION_MODIFY_SINGLE = 2;
	int ACTION_MODIFY_ALL_FUTURE = 3;
	int ACTION_MODIFY_ALL = 4;
	int ACTION_CREATE = 5;
	int ACTION_DELETE_ALL = 6;
	
	int EVENT_OPERATION_COPY = 7;
	int EVENT_OPERATION_EDIT = 8;
	int EVENT_OPERATION_VIEW = 9;

	
	//Getters that are always needed
	public long getStartMillis();
	public long getEndMillis();
	public Collection getCollection();
	public Resource getResource();
	public List<AcalAlarm> getAlarms();
	public AcalDateTime getStart();
	public 	AcalDateTime getEnd();
	public AcalDuration getDuration();
	public String getRepetition();
	public String getSummary();
	public String getLocation();
	public String getDescription();
	public boolean overlaps(EventInstance eventInstance);
	public String getTimeText(WeekViewActivity context, long currentEpoch,
			long currentEpoch2, boolean b);
	public CharSequence getTimeText(AcalDateTime viewDate, AcalDateTime addDays,
			boolean boolean1);
	public boolean isPending();
	public boolean isSingleInstance();
	public String getTimeText(MonthView context, long epoch, long epoch2,
			boolean boolean1);
	public boolean isAllDay();
	
	//Special methods that are sometimes needed - should be refactored out
	public int getLastWidth();
	public void setLastWidth(int singleWidth);
	public int calulateMaxWidth(int viewWidth, int hSPP);
	public int getActualWidth();
	
	//Methods for writeable instance
	public void setRepetition(String rrule);
	public void setOperation(int eventOperationCopy);
	public void setDates(AcalDateTime start, AcalDuration duration);
	public void setSummary(String string);
	public void setCollection(DUMMYCollectionInstance instance);
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
