package com.morphoss.acal.dataservice;

import java.util.ArrayList;

import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.davacal.AcalAlarm;
import com.morphoss.acal.davacal.Masterable;
import com.morphoss.acal.davacal.PropertyName;
import com.morphoss.acal.davacal.RecurrenceId;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.davacal.VComponentCreationException;
import com.morphoss.acal.davacal.VEvent;
import com.morphoss.acal.davacal.VTodo;

public abstract class CalendarInstance {

	private static final String TAG = "aCal CalendarInstance";
	private static final boolean DEBUG = true & Constants.DEBUG_MODE;
	
	protected long collectionId;
	protected long resourceId;
	protected AcalDateTime dtstart;
	protected AcalDateTime dtend;
	protected ArrayList<AcalAlarm> alarms;
	protected String rrule;
	protected String rrid;
	protected String summary;
	protected String location;
	protected String description;
	protected String etag;
	

	/**
	 * Default constructor. Nulls can be applied to any variable. The only constraint is that cid is a valid collection Id.
	 * @param cid CollectionId
	 * @param rid ResourceID (negative means new)
	 * @param start Start time
	 * @param end End time
	 * @param alarms
	 * @param rrule Recurrence Rule (null if there is none)
	 * @param summary 
	 * @param location
	 * @param description
	 */
	protected CalendarInstance(long cid, long rid, AcalDateTime start, AcalDateTime end, ArrayList<AcalAlarm> alarms, String rrule,
			String rrid, String summary, String location, String description, String etag) {
		
		this.collectionId = cid; if (cid < 0) throw new IllegalArgumentException("Collection ID must be a valid collection!");
		this.resourceId = (rid<0) ? -1 : rid;
		this.dtstart = start;
		this.dtend = end;
		this.alarms = (alarms == null) ? this.alarms = new ArrayList<AcalAlarm>() : alarms;
		this.rrule = rrule;
		this.rrid = rrid;
		this.summary = (summary == null) ? "" : summary; 
		this.location = (location == null) ? "" : location; 
		this.description = (description == null) ? "" : description; 
		this.etag = (etag == null) ? "" : etag;
		
		
	}

	public CalendarInstance(Masterable masterInstance, AcalDateTime dtstart, AcalDateTime dtend) {
		this(masterInstance.getCollectionId(),
				masterInstance.getResourceId(),
				dtstart,
				dtend,
				masterInstance.getAlarms(),
				masterInstance.getRRule(),
				(dtstart == null ? (dtend == null ? null : dtend.toPropertyString(PropertyName.RECURRENCE_ID)) : dtstart.toPropertyString(PropertyName.RECURRENCE_ID)),
				masterInstance.getSummary(), 
				masterInstance.getLocation(),
				masterInstance.getDescription(),
				masterInstance.getResource().getEtag());
	}

	public AcalDateTime getEnd() {
		return this.dtend;
	}
	
	//getters
	public AcalDuration getDuration() { 
		if (dtstart == null) return null;
		return dtstart.getDurationTo(getEnd()); 
	}
	
	public AcalDateTime getStart() { return (dtstart  == null) ? null : this.dtstart.clone(); };
	public ArrayList<AcalAlarm> getAlarms() { return alarms; } 
	public String getRRule() { return this.rrule; }
	public String getSummary() { return this.summary; }
	public String getLocation() { return location; }
	public String getDescription() { return this.description; }
	public boolean isSingleInstance() { return (rrule == null || rrule.equals("")); }
	public long getCollectionId() { return this.collectionId; }
	public long getResourceId() { return this.resourceId; }
	public String getRecurrenceId() { return this.rrid; }

	
	public void setAlarms(ArrayList<AcalAlarm> alarms) {
		this.alarms = (alarms == null) ? this.alarms = new ArrayList<AcalAlarm>() : alarms;
	}
	public void setCollectionId(long cid) {
		if (cid < 0) throw new IllegalArgumentException("Collection ID must be a valid collection!");
		this.collectionId = cid; 
	}
	public void setDates(AcalDateTime start, AcalDateTime end) {
		this.dtstart = start.clone();
		this.dtend = end.clone();
	}
	public void setStartDate(AcalDateTime start) {
		this.dtstart = start.clone();
	}
	public void setEndDate(AcalDateTime end) {
		this.dtend = end.clone();
	}
	public void setSummary(String summary) {
		this.summary = (summary == null) ? "" : summary; 
	}
	public void setDescription(String newDesc) {
		this.description = (newDesc == null) ? "" : newDesc; 
	}
	public void setLocation(String newLoc) {
		this.location = (newLoc == null) ? "" : newLoc;
	}
	public void setRepeatRule(String newRule) {
		this.rrule = newRule;
	}
	

	public static CalendarInstance fromResourceAndRRId(Resource res, String rrid) throws IllegalArgumentException {
		try {
			VComponent comp = VComponent.createComponentFromResource(res);
			if (!(comp instanceof VCalendar)) throw new IllegalArgumentException("Resource provided is not a VCalendar");
			Masterable obj;
			if ( rrid == null )
				obj = ((VCalendar)comp).getMasterChild();
			else
				obj = ((VCalendar)comp).getChildFromRecurrenceId(RecurrenceId.fromString(rrid));

			if (obj instanceof VEvent) {
				return new EventInstance((VEvent)obj, obj.getStart(), obj.getEnd());
			} else if (obj instanceof VTodo) {
				return new TodoInstance((VTodo)obj, obj.getStart(), obj.getEnd());
			} else {
				throw new IllegalArgumentException("Resource does not map to a known Componant Type");
			}
		} catch (VComponentCreationException e) {
			throw new IllegalArgumentException(e);
		}
		
	}
	
	public String getEtag() {
		return this.etag;
	}

}
