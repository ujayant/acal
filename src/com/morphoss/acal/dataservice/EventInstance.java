package com.morphoss.acal.dataservice;

import java.util.List;

import android.os.Parcelable;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.activity.MonthView;
import com.morphoss.acal.davacal.AcalAlarm;
import com.morphoss.acal.davacal.AcalCollection;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.davacal.VEvent;
import com.morphoss.acal.weekview.WeekViewActivity;

public interface EventInstance extends Parcelable, Comparable<EventInstance> {


	
	int EVENT_OPERATION_COPY = 7;
	int EVENT_OPERATION_EDIT = 8;
	int EVENT_OPERATION_VIEW = 9;
	
	//Getters that are always needed
	public abstract long getStartMillis();
	public abstract long getEndMillis();
	public abstract List<AcalAlarm> getAlarms();
	public abstract AcalDateTime getStart();
	public abstract AcalDateTime getEnd();
	public abstract AcalDuration getDuration();
	public abstract String getRepetition();
	public abstract String getSummary();
	public abstract String getLocation();
	public abstract String getDescription();
	public abstract boolean overlaps(EventInstance eventInstance);
	public abstract String getTimeText(WeekViewActivity context, long currentEpoch,
			long currentEpoch2, boolean b);
	public abstract CharSequence getTimeText(AcalDateTime viewDate, AcalDateTime addDays,
			boolean boolean1);
	public abstract boolean isPending();
	public abstract boolean isSingleInstance();
	public abstract String getTimeText(MonthView context, long epoch, long epoch2,
			boolean boolean1);
	public abstract boolean isAllDay();
	public abstract WriteableEventInstance getWriteable();
	public Collection getCollection();
	public Resource getResource();
	public VEvent getMaster();
	
	//Special methods that are sometimes needed - should be refactored out
	public abstract int getLastWidth();
	public abstract void setLastWidth(int singleWidth);
	public abstract int calulateMaxWidth(int viewWidth, int hSPP);
	public abstract int getActualWidth();
	public abstract void setOperation(int eventOperationCopy);



}
