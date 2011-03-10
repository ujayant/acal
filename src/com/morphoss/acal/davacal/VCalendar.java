/*
 * Copyright (C) 2011 Morphoss Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.morphoss.acal.davacal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.acaltime.AcalRepeatRule;
import com.morphoss.acal.acaltime.AcalRepeatRuleParser;
import com.morphoss.acal.davacal.AcalEventAction.EVENT_FIELD;

public class VCalendar extends VComponent {
	public static final String TAG = "aCal VCalendar";
	private AcalDateRange dateRange = null;
	private AcalRepeatRule repeatRule = null;
	private Boolean masterHasOverrides = null;
	private Boolean hasAlarms = null;
	private Long earliestStart;
	private Long latestEnd;
	private boolean isPending = false;


	public VCalendar(ComponentParts splitter, Integer resourceId, Long earliestStart, Long latestEnd, AcalCollection collectionObject,VComponent parent) {
		super(splitter, resourceId,collectionObject,parent);
		this.earliestStart = earliestStart;
		this.latestEnd = latestEnd;
		if ( earliestStart != null ) {
			this.dateRange = new AcalDateRange(AcalDateTime.fromMillis(earliestStart),
					(latestEnd == null ? null : AcalDateTime.fromMillis(latestEnd)));
		}
	}


	protected VCalendar(AcalCollection collection) {
		super( VComponent.VCALENDAR, collection, null );
		try { setPersistentOn(); } catch (YouMustSurroundThisMethodInTryCatchOrIllEatYouException e) { }
		addProperty(new AcalProperty("CALSCALE","GREGORIAN"));
		addProperty(new AcalProperty("PRODID","-//morphoss.com//aCal 1.0//EN"));
		addProperty(new AcalProperty("VERSION","2.0"));
	}


	public static VCalendar getGenericCalendar( AcalCollection collection, AcalEventAction newEventData) {
		VCalendar vcal = new VCalendar(collection);
		VEvent event = new VEvent(vcal);
		// TODO: addChild should really probably setParent() on the child as it does it
		vcal.addChild(event); 
		return vcal;
	}

	public void setPending(boolean isPending) {
		this.isPending = isPending;
	}
	public boolean isPending() {
		return this.isPending;
	}
	
	public VCalendar clone() {
		return new VCalendar(this.content, this.resourceId, this.earliestStart, this.latestEnd, this.collectionData, this.parent);
	}

	public String applyAction(AcalEventAction action) {
		try {
			this.setPersistentOn();
			this.populateChildren();
			this.populateProperties();

			Masterable mast = this.getMasterChild();
			mast.setPersistentOn();
			mast.populateChildren();
			mast.populateProperties();

			// first, strip any existing properties which we always modify
			mast.removeProperties( new String[] {"DTSTAMP", "LAST-MODIFIED" } );

			// change DTStamp
			AcalDateTime lastModified = new AcalDateTime();
			lastModified.setTimeZone(TimeZone.getDefault().getID());
			lastModified.shiftTimeZone("UTC");

			mast.addProperty(AcalProperty.fromString(lastModified.toPropertyString("DTSTAMP")));
			mast.addProperty(AcalProperty.fromString(lastModified.toPropertyString("LAST-MODIFIED")));

			
			if ( action.getAction() == AcalEventAction.ACTION_DELETE_SINGLE) {
				AcalProperty exDate = mast.getProperty("EXDATE");
				if ( exDate == null || exDate.getValue().equals("") ) 
					exDate = AcalProperty.fromString(action.event.dtstart.toPropertyString("EXDATE"));
				else {
					mast.removeProperties( new String[] {"EXDATE"} );
					exDate = AcalProperty.fromString(exDate.toRfcString() + "," + action.event.dtstart.fmtIcal() );
				}
				mast.addProperty(exDate);
			}
			else if (action.getAction() == AcalEventAction.ACTION_DELETE_ALL_FUTURE) {
				AcalRepeatRuleParser parsedRule = AcalRepeatRuleParser.parseRepeatRule((String) action.getField(EVENT_FIELD.repeatRule));
				AcalDateTime until = action.event.dtstart.clone();
				until.addSeconds(-1);
				parsedRule.setUntil(until);
				String rrule = parsedRule.toString();
				action.setField(EVENT_FIELD.repeatRule, rrule);
				mast.removeProperties( new String[] {"RRULE"} );
				mast.addProperty(new AcalProperty("RRULE",rrule));
			}
			else if (action.isModifyAction()) {
				this.applyModify(mast,action);

			}
			String ret = this.getCurrentBlob();
			this.destroyProperties();
			this.destroyChildren();
			this.setPersistentOff();
			return ret;
		}
		catch (Exception e) {
			Log.w(TAG,Log.getStackTraceString(e));
			return "";
		}

	}

	private void applyModify(Masterable mast, AcalEventAction action) {
		//there are 3 possible modify actions:
		if (action.getAction() == AcalEventAction.ACTION_MODIFY_SINGLE) {
			// Only modify the single instance
		}
		else if (action.getAction() == AcalEventAction.ACTION_MODIFY_ALL_FUTURE) {
			// Modify this instance, and all future instances.

		}
		else if (action.getAction() == AcalEventAction.ACTION_MODIFY_ALL) {
			// Modify all instances

			// First, strip any existing properties which we modify
			mast.removeProperties( new String[] {"DTSTART", "DTEND", "DURATION",
					"SUMMARY", "LOCATION", "DESCRIPTION", "RRULE" } );

			AcalDateTime dtStart = (AcalDateTime) action.getField(EVENT_FIELD.startDate);
			mast.addProperty( AcalProperty.fromString( dtStart.toPropertyString("DTSTART")));

			mast.addProperty(new AcalProperty("DURATION",
					((AcalDuration) action.getField(EVENT_FIELD.duration)).toString() ) );

			mast.addProperty(new AcalProperty("SUMMARY",
					(String) action.getField(EVENT_FIELD.summary)));

			String location = (String) action.getField(EVENT_FIELD.location);
			if ( !location.equals("") )
				mast.addProperty(new AcalProperty("LOCATION",location));

			String description = (String) action.getField(EVENT_FIELD.description);
			if ( !description.equals("") )
				mast.addProperty(new AcalProperty("DESCRIPTION",description));

			String rrule = (String) action.getField(EVENT_FIELD.repeatRule);
			if ( rrule != null && !rrule.equals(""))
				mast.addProperty(new AcalProperty("RRULE",rrule));


			@SuppressWarnings("unchecked")
			List<AcalAlarm> alarmlist = (List<AcalAlarm>) action.getField(EVENT_FIELD.alarmList);
			if ( alarmlist != null && alarmlist.size() > 0 ) {
				List<VComponent> children = mast.getChildren();
				for( VComponent child : children ) {
					if ( child instanceof VAlarm ) mast.removeChild(child);
				}
				for( AcalAlarm alarm : alarmlist ) {
					VAlarm vAlarm = alarm.getVAlarm(mast);
					mast.addChild(vAlarm);
				}
			}
		}
	}

	public void checkRepeatRule() {
		try {
			if (repeatRule == null) repeatRule = AcalRepeatRule.fromVCalendar(this);
		}
		catch ( Exception e ) {
			Log.w(TAG,Log.getStackTraceString(e));
		}
	}

	public boolean appendAlarmInstancesBetween(List<AcalAlarm> alarmList, AcalDateRange rangeRequested) {
		if (repeatRule == null) checkRepeatRule();
		this.repeatRule.appendAlarmInstancesBetween(alarmList, rangeRequested);
		return true;
	}
	public boolean appendEventInstancesBetween(List<AcalEvent> eventList, AcalDateRange rangeRequested, boolean isPending) {
		if (isPending) {
			this.isPending = true;
			this.repeatRule = null;
		}
		try {
			if (dateRange != null) {
				AcalDateRange intersection = rangeRequested.getIntersection(this.dateRange);
				if (intersection != null) {
					if (repeatRule == null) checkRepeatRule();
					if (repeatRule != null) {
						//						Log.d(TAG,"Processing event: Summary="+new UnModifiableAcalEvent(thisEvent,new AcalCalendar(), new AcalCalendar()).summary);
						this.repeatRule.appendEventsInstancesBetween(eventList, intersection);
						return true;
					}
				}
			}
			else {
				checkRepeatRule();
				if (repeatRule != null) {
					this.repeatRule.appendEventsInstancesBetween(eventList, rangeRequested);
					return true;
				}
			}
			//			Log.d(TAG,"Skipped event: Summary="+new UnModifiableAcalEvent(thisEvent,new AcalCalendar(), new AcalCalendar()).summary);
		}
		catch(Exception e) {
			if (Constants.LOG_DEBUG)Log.d(TAG,"Exception in RepeatRule handling");
			if (Constants.LOG_DEBUG)Log.d(TAG,Log.getStackTraceString(e));
		}
		return false;
	}

	public Masterable getMasterChild() {
		if (childrenSet) {
			for (VComponent vc : this.getChildren()) {
				if ( vc instanceof VEvent)   return (VEvent)vc;
				if ( vc instanceof VTodo )	 return (VTodo) vc;
			}
		}
		for( PartInfo childInfo : content.partInfo ) {
			if ( childInfo.type.equals(VEVENT) ) {
				return new VEvent(new ComponentParts(childInfo.getComponent(content.componentString)),
						resourceId, collectionData,this);
			}
			else if ( childInfo.type.equals(VTODO) ) {
				return new VTodo(new ComponentParts(childInfo.getComponent(content.componentString)),
						resourceId, collectionData,this);
			}
		}
		return null;
	}

	public AcalDateTime getRangeEnd() {
		return dateRange.end;
	}

	public boolean masterHasOverrides() {
		if ( masterHasOverrides == null ) {
			int countMasterables = 0;
			for( PartInfo childInfo : content.partInfo ) {
				if ( childInfo.type.equals(VEVENT) || childInfo.type.equals(VTODO) ) {
					countMasterables++;
					if ( countMasterables > 1 ) break;
				}
			}
			if ( masterHasOverrides == null ) masterHasOverrides = (countMasterables > 1);
		}
		return masterHasOverrides;
	}


	public Masterable getChildFromRecurrenceId(RecurrenceId recurrenceProperty) {
		if ( masterHasOverrides() ) return this.getMasterChild();
		try {
			this.setPersistentOn();
			List<Masterable> matchingChildren = new ArrayList<Masterable>();
			for (VComponent vc: this.getChildren()) {
				if (vc.containsPropertyKey(recurrenceProperty.getName()) && vc instanceof Masterable)
					matchingChildren.add((Masterable) vc);
			}
			if (matchingChildren.isEmpty()) {
				// Won't happen since we test for this in masterHasOverrides()
				return this.getMasterChild();
			}
			Collections.sort(matchingChildren, RecurrenceId.getVComponentComparatorByRecurrenceId());
			for (int i = matchingChildren.size()-1; i>= 0; i--) {
				RecurrenceId cur = (RecurrenceId) matchingChildren.get(i).getProperty("RECURRENCE-ID");
				if (cur.notAfter(recurrenceProperty)) {
					this.setPersistentOff();
					return matchingChildren.get(i);
				}
			}
		} catch (YouMustSurroundThisMethodInTryCatchOrIllEatYouException e) {
			Log.w(TAG,Log.getStackTraceString(e));
		} finally {
			this.setPersistentOff();	
		}

		return this.getMasterChild();
	}

	public boolean hasAlarm() {
		if ( this.hasAlarms != null ) return this.hasAlarms;
		for( PartInfo childInfo : content.partInfo ) {
			if ( childInfo.type.equals(VEVENT) ) {
				VEvent vc = new VEvent(new ComponentParts(childInfo.getComponent(content.componentString)),
						resourceId, collectionData,this);
				for( PartInfo childChildInfo : vc.content.partInfo ) {
					if ( childChildInfo.type.equals(VALARM)) {
						this.hasAlarms = true;
						return true;
					}
				}
			}
			else if ( childInfo.type.equals(VTODO) ) {
				VTodo vc = new VTodo(new ComponentParts(childInfo.getComponent(content.componentString)),
						resourceId, collectionData,this);
				for( PartInfo childChildInfo : vc.content.partInfo ) {
					if ( childChildInfo.type.equals(VALARM))  {
						this.hasAlarms = true;
						return true;
					}
				}
			}
			else if ( childInfo.type.equals(VALARM))  {
				this.hasAlarms = true;
				return true;
			}
		}
		this.hasAlarms = false;
		return false;
	}
}
