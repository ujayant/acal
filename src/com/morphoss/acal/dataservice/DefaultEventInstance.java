package com.morphoss.acal.dataservice;

import java.util.List;

import android.os.Parcel;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.activity.MonthView;
import com.morphoss.acal.davacal.AcalAlarm;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VEvent;
import com.morphoss.acal.weekview.WeekViewActivity;

public class DefaultEventInstance implements EventInstance {
	

	public DefaultEventInstance(VEvent vEvent, AcalDateTime dtstart,
			AcalDuration duration) {
		// TODO Auto-generated constructor stub
	}

	//private constructor for subclasses only
	protected DefaultEventInstance() {
		
	}
	
	public static EventInstance fromDB(long rid, long dtstart) {
		//TODO fix this
		return new DefaultEventInstance();
	}
	
	/** Required Constructors */
	
	
	
	@Override
	public void setOperation(int eventOperationCopy) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public List<AcalAlarm> getAlarms() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AcalDateTime getEnd() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AcalDateTime getStart() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		// TODO Auto-generated method stub
		
	}

	

	@Override
	public String getRepetition() {
		// TODO Auto-generated method stub
		return null;
	}

	

	@Override
	public String getDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AcalDuration getDuration() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getLocation() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getSummary() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getEndMillis() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getStartMillis() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean isAllDay() {
		// TODO Auto-generated method stub
		return false;
	}

	

	@Override
	public int calulateMaxWidth(int viewWidth, int hSPP) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getActualWidth() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getLastWidth() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getTimeText(WeekViewActivity context, long currentEpoch,
			long currentEpoch2, boolean b) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean overlaps(EventInstance eventInstance) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setLastWidth(int singleWidth) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int compareTo(EventInstance another) {
		// TODO Auto-generated method stub
		return 0;
	}

	

	@Override
	public CharSequence getTimeText(AcalDateTime viewDate,
			AcalDateTime addDays, boolean boolean1) {
		// TODO Auto-generated method stub
		return null;
	}

	

	@Override
	public String getTimeText(MonthView context, long epoch, long epoch2,
			boolean boolean1) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isPending() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isSingleInstance() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public WriteableEventInstance getWriteable() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection getCollection() {
		return this.getMaster().getCollection();
	}

	@Override
	public Resource getResource() {
		return this.getMaster().getResource();
	}

	@Override
	public VEvent getMaster() {
		// TODO Auto-generated method stub
		return null;
	}

	public static EventInstance getInstance(Parcel in) {
		// TODO Auto-generated method stub
		return null;
	}
	
}
