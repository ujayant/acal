package com.morphoss.acal.dataservice;

import java.util.ArrayList;
import java.util.List;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.davacal.AcalAlarm;
import com.morphoss.acal.davacal.VEvent;

public class DefaultWriteableEventInstance extends DefaultEventInstance implements WriteableEventInstance {

	public DefaultWriteableEventInstance(VEvent vEvent, AcalDateTime dtstart,
			AcalDuration duration) {
		super(vEvent, dtstart, duration);
		// TODO Auto-generated constructor stub
	}
	
	
	//Private constructor for builder only.
	private DefaultWriteableEventInstance() {
		super();
	}

	public static class EVENT_BUILDER {
		private List<AcalAlarm> alarmList = new ArrayList<AcalAlarm>();
		private AcalDateTime start;
		private AcalDuration duration;
		private String summary;
		private long collectionId = -1;
		private int action = -1;

		
		public AcalDateTime getStart() { return this.start; }
		public AcalDuration getDuration() { return this.duration; }

		public EVENT_BUILDER setStart(AcalDateTime start) {
			this.start = start;
			return this;
		}

		public EVENT_BUILDER setDuration(AcalDuration duration) {
			this.duration = duration;
			return this;
		}

		public EVENT_BUILDER setSummary(String summary) {
			this.summary = summary;
			return this;
		}

		public EVENT_BUILDER setCollection(long collectionId) {
			this.collectionId = collectionId;
			return this;
		}

		public EVENT_BUILDER addAlarm(AcalAlarm alarm) {
			this.alarmList.add(alarm);
			return this;
		}
		
		public EVENT_BUILDER setAction(int action) {
			this.action = action;
			return this;
		}
		
		public DefaultWriteableEventInstance build() throws BadlyConstructedEventException {
			throw new BadlyConstructedEventException();
		}


	}

	public static class BadlyConstructedEventException extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
	}

	public static WriteableEventInstance getInstance(DefaultEventInstance source) {
		return new DefaultWriteableEventInstance(source.getMaster(), source.getStart(), source.getDuration());
	}

	@Override
	public int getAction() {
		// TODO Auto-generated method stub
		return 0;
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
	public int getOperation() {
		// TODO Auto-generated method stub
		return 0;
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
	public void setCollection(Collection instance) {
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
}
