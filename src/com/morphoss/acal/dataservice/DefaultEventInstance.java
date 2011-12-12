package com.morphoss.acal.dataservice;

import java.util.ArrayList;

import android.os.Parcel;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.activity.MonthView;
import com.morphoss.acal.davacal.AcalAlarm;
import com.morphoss.acal.davacal.VEvent;
import com.morphoss.acal.weekview.WeekViewActivity;

public class DefaultEventInstance implements EventInstance {
	
	private VEvent baseEvent;
	private AcalDateTime dtstart;
	private AcalDuration duration;

	public DefaultEventInstance(VEvent vEvent, AcalDateTime dtstart,
			AcalDuration duration) {

		this.baseEvent = vEvent;
		this.dtstart = dtstart;
		this.duration = duration;
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
	public ArrayList<AcalAlarm> getAlarms() {
		//TODO
		return new ArrayList<AcalAlarm>();
	}

	@Override
	public AcalDateTime getEnd() {
		return duration.getEndDate(dtstart);
	}

	@Override
	public AcalDateTime getStart() {
		return this.dtstart.clone();
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
		return this.baseEvent.getRepetition();
	}

	

	@Override
	public String getDescription() {
		return this.baseEvent.getDescription();
	}

	@Override
	public AcalDuration getDuration() {
		//TODO should clone.
		return this.duration;
	}

	@Override
	public String getLocation() {
		return this.baseEvent.getLocation();
	}

	@Override
	public String getSummary() {
		return this.baseEvent.getSummary();
	}

	@Override
	public boolean isAllDay() {
		return dtstart.isDate();
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
		//AcalDateTimeFormatter.getDisplayTimeText(c, viewDate, addDays, this.dtstart.getMillis(), duration.getEndDate(dtstart).getMillis(), true, this.isAllDay());
		// TODO Auto-generated method stub
		return "TODO";
	}

	

	@Override
	public String getTimeText(MonthView context, long epoch, long epoch2,
			boolean boolean1) {
		// TODO Auto-generated method stub
		return "TODO";
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
	public long getCollectionId() {
		return this.getMaster().getCollectionId();
	}

	@Override
	public Resource getResource() {
		return this.getMaster().getResource();
	}

	@Override
	public VEvent getMaster() {
		return this.baseEvent;
	}

	public static EventInstance getInstance(Parcel in) {
		// TODO Auto-generated method stub
		return null;
	}

	
}
