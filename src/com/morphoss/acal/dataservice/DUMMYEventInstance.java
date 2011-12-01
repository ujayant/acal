package com.morphoss.acal.dataservice;

import java.util.List;

import android.os.Parcel;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.davacal.AcalAlarm;
import com.morphoss.acal.weekview.WeekViewActivity;

public class DUMMYEventInstance implements EventInstance {
	
	public static EventInstance getIntance(Object ... params) {
		return new DUMMYEventInstance();
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
	public int getAction() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getRepetition() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setRepetition(String rrule) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isModifyAction() {
		// TODO Auto-generated method stub
		return false;
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
	public void setOperation(int eventOperationCopy) {
		// TODO Auto-generated method stub
		
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
	public int getOperation() {
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
	public void setAction(int actionModifyAll) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setAlarms(List<AcalAlarm> alarmList) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setCollection(DUMMYCollectionInstance instance) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setDates(AcalDateTime start, AcalDuration duration) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setDates(AcalDateTime setTimeZone, AcalDateTime setTimeZone2) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setSummary(String string) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setDescription(String newDesc) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setEndDate(AcalDateTime end) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setLocation(String newLoc) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setRepeatRule(String newRule) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Collection getCollection() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Resource getResource() {
		// TODO Auto-generated method stub
		return null;
	}
	
}
