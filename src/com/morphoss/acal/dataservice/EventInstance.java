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

	

	Collection getCollection();
	
	Resource getResource();
	
	List<AcalAlarm> getAlarms();

	AcalDateTime getStart();

	AcalDateTime getEnd();

	int getAction();

	String getRepetition();

	void setRepetition(String rrule);

	boolean isModifyAction();

	AcalDuration getDuration();

	String getSummary();

	String getLocation();

	String getDescription();

	boolean isAllDay();

	long getStartMillis();

	long getEndMillis();

	void setOperation(int eventOperationCopy);

	int getLastWidth();

	void setLastWidth(int singleWidth);

	boolean overlaps(EventInstance eventInstance);

	String getTimeText(WeekViewActivity context, long currentEpoch,
			long currentEpoch2, boolean b);

	int calulateMaxWidth(int viewWidth, int hSPP);

	int getActualWidth();

	CharSequence getTimeText(AcalDateTime viewDate, AcalDateTime addDays,
			boolean boolean1);

	int getOperation();

	void setAction(int actionModifyAll);

	void setDates(AcalDateTime start, AcalDuration duration);

	void setSummary(String string);

	void setCollection(DUMMYCollectionInstance instance);

	void setAlarms(List<AcalAlarm> alarmList);

	void setDates(AcalDateTime setTimeZone, AcalDateTime setTimeZone2);

	void setLocation(String newLoc);

	void setDescription(String newDesc);

	void setEndDate(AcalDateTime end);

	void setRepeatRule(String newRule);

	boolean isPending();

	boolean isSingleInstance();

	String getTimeText(MonthView context, long epoch, long epoch2,
			boolean boolean1);

}
