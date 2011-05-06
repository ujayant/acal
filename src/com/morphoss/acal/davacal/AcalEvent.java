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

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.davacal.AcalEventAction.EVENT_FIELD;

/**
 * 
 * @author Morphoss Ltd
 *
 */
public class AcalEvent implements Serializable, Parcelable, Comparable<AcalEvent>{

	private static final long serialVersionUID = 1L;
	public static final String TAG = "AcalEvent";
	public final AcalDateTime dtstart;
	public final AcalDuration duration;
	public final String summary;
	public final String description;
	public final String location;
	public final String repetition;
	public final int colour;
	public final boolean hasAlarms;
	public final int resourceId;
	public final List<AcalAlarm> alarmList = new ArrayList<AcalAlarm>();
	private final String originalBlob;
	private final int collection;
	public final boolean isPending;
	

	public static final Parcelable.Creator<AcalEvent> CREATOR = new Parcelable.Creator<AcalEvent>() {
	        public AcalEvent createFromParcel(Parcel in) {
	            return getInstanceFromParcel(in);
	        }
	
	        public AcalEvent[] newArray(int size) {
	            return new AcalEvent[size];
	        }
	    };

	public static AcalEvent getInstanceFromParcel(Parcel in) {
		AcalEvent event = null;
		event = new AcalEvent(in); 
		return event;
	}

	@Override
	public int describeContents() {
		return 0;
	}
	@Override
	public void writeToParcel(Parcel out, int flags) {
		getStart().writeToParcel( out, flags);
		duration.writeToParcel(out, 0);
		out.writeString(getSummary());
		out.writeString(getLocation());
		out.writeString(getDescription());
		out.writeString(getRepetition());
		out.writeInt(getColour());
		out.writeByte((byte) (hasAlarms() ? 'T' : 'F'));
		out.writeInt(getResourceId());
		out.writeString(originalBlob);
		out.writeInt(collection);
		out.writeTypedList(alarmList);
		out.writeByte((byte) (isPending ? 'T' : 'F'));
	}

	public AcalEvent(Parcel in) {
		this.dtstart = AcalDateTime.unwrapParcel(in);
		this.duration = new AcalDuration(in);
		this.summary = in.readString();
		this.location = in.readString();
		this.description = in.readString();
		this.repetition = in.readString();
		this.colour = in.readInt();
		this.hasAlarms = in.readByte() == 'T';
		this.resourceId = in.readInt();
		this.originalBlob = in.readString();
		this.collection = in.readInt();
		in.readTypedList(this.alarmList, AcalAlarm.CREATOR);
		this.isPending = in.readByte() == 'T';
    }
	
	public static void parcelActionEventAsEvent(Parcel out, AcalEventAction action) {
		AcalDateTime dtStart = (AcalDateTime) action.getField(EVENT_FIELD.startDate);
		dtStart.writeToParcel(out, 0);
		((AcalDuration)action.getField(EVENT_FIELD.duration)).writeToParcel(out, 0);
		out.writeString((String)action.getField(EVENT_FIELD.summary));
		out.writeString((String)action.getField(EVENT_FIELD.location));
		out.writeString((String)action.getField(EVENT_FIELD.description));
		out.writeString((String)action.getField(EVENT_FIELD.repeatRule));
		out.writeInt((Integer)action.getField(EVENT_FIELD.colour));
		List<AcalAlarm> alarmList = (List<AcalAlarm>)action.getField(EVENT_FIELD.alarmList);
		char bit = (alarmList.isEmpty() ? 'F' : 'T');
		out.writeByte((byte) (bit));
		out.writeInt((Integer)action.getField(EVENT_FIELD.resourceId));
		out.writeString(action.getOriginalBlob());
		out.writeInt((Integer)action.getField(EVENT_FIELD.collectionId));
		out.writeTypedList(alarmList);
		bit = (action.isPending() ? 'F' : 'T');
		out.writeByte((byte) (bit));
	}
	
	@Override
	public int compareTo(AcalEvent other) {
		if ( getStart().before(other.getStart())) return -1;
		if ( getStart().after(other.getStart())) return 1;
		if ( getEnd().before(other.getEnd())) return -1;
		if ( getEnd().after(other.getEnd())) return 1;
		return 0;
	}

	public AcalEvent(VComponent event, AcalDateTime startDate, AcalDuration duration, boolean isPending ) {
		this.resourceId = event.getResourceId();
		dtstart = startDate;

		if ( duration == null ) {
			AcalProperty dtend = event.getProperty("DTEND");  
			if (dtend != null) 
				duration = startDate.getDurationTo(AcalDateTime.fromAcalProperty(dtend));
		} 
		if ( duration == null ) {
				duration = new AcalDuration();
				duration.setDuration(1, 0);
		}

		this.duration = duration;
		summary = safeEventPropertyValue(event, "SUMMARY");
		description = safeEventPropertyValue(event, "DESCRIPTION");
		location = safeEventPropertyValue(event, "LOCATION");
		originalBlob = event.getTopParent().getOriginalBlob();
		String repeatRule = safeEventPropertyValue(event,"RRULE");
		String repeatInstances = safeEventPropertyValue(event,"RDATE");
		repetition = repeatRule + (repeatInstances.equals("")?"":"\n"+repeatInstances);

		String alarmString = "";
		List<AcalAlarm> theseAlarms = new ArrayList<AcalAlarm>();
		for( VComponent child : event.getChildren() ) {
			if ( child instanceof VAlarm ) {
				VAlarm alarm = (VAlarm) child;
				theseAlarms.add(new AcalAlarm((VAlarm) child, (Masterable) event, dtstart.clone(), AcalDateTime.addDuration(dtstart, duration)));
		//		if ( !alarmString.equals("") ) alarmString += "\n";
		//		alarmString += alarm.toPrettyString();
			}
		}
		//alarms = alarmString;
		
		alarmList.addAll(theseAlarms);
		hasAlarms = alarmList.size() > 0;
		
		int aColor = Color.BLUE;
		try {
			aColor = event.getCollectionColour();
		} catch (Exception e) {
			Log.e(TAG,"Error Creating UnModifiableAcalEvent - "+e.getMessage());
			Log.e(TAG,Log.getStackTraceString(e));
		}
		colour = aColor;
		
		int collectionId;
		try {
			collectionId = event.getCollectionId();
		}
		catch( NullPointerException e ) {
			collectionId = -1;
		}
		collection = collectionId;
		this.isPending = isPending;
	}

	
	private String safeEventPropertyValue(VComponent event, String propertyName ) {
		String propertyValue = null;
		try {
			propertyValue = event.getProperty(propertyName).getValue();
		}
		catch (Exception e) {
		}
		if (propertyValue == null) propertyValue = "";
		return propertyValue;
	}

	
	

	public List<AcalAlarm> getAlarms() {
		return this.alarmList;
	}

	public int getColour() {
		return this.colour;
	}

	public String getDescription() {
		return this.description;
	}

	public AcalDateTime getEnd() {
		return AcalDateTime.addDuration(dtstart, duration);
	}
	
	public AcalDuration getDuration() {
		return this.duration;
	}

	public String getLocation() {
		return this.location;
	}

	public String getRepetition() {
		return this.repetition;
	}

	public AcalDateTime getStart() {
		return this.dtstart;
	}

	public String getTimeText(AcalDateTime viewDateStart, AcalDateTime viewDateEnd, boolean as24HourTime ) {
		AcalDateTime start = this.getStart();
		start.applyLocalTimeZone();
		AcalDateTime finish = this.getEnd();
		if ( finish != null ) finish.applyLocalTimeZone();
		String timeText = "";
		String timeFormatString = (as24HourTime ? "HH:mm" : "hh:mmaa");
		SimpleDateFormat timeFormatter = new SimpleDateFormat(timeFormatString);
		
		if ( start.before(viewDateStart) || (finish != null && finish.after(viewDateEnd)) ){
			if ( start.isDate() ) {
				timeText = AcalDateTime.fmtDayMonthYear(start)+ ", all day";
			}
			else {
				SimpleDateFormat startFormatter = timeFormatter;
				SimpleDateFormat finishFormatter = timeFormatter;
				
				if ( start.before(viewDateStart) )
					startFormatter  = new SimpleDateFormat("MMM d, "+timeFormatString);
				if ( finish.after(viewDateEnd) )
					finishFormatter = new SimpleDateFormat("MMM d, "+timeFormatString);
		
				timeText = (startFormatter.format(start.toJavaDate())+" - "
							+ (finish == null ? "null" : finishFormatter.format(finish.toJavaDate())));
			}
		}
		else if ( start.isDate()) {
			timeText = "All Day";
		}
		else {
			timeText = (timeFormatter.format(start.toJavaDate())+" - "
						+ (finish == null ? "null" : timeFormatter.format(finish.toJavaDate())));
		}
		return timeText;
	}

	public boolean overlaps( AcalDateRange range ) {
		return range.overlaps(dtstart, dtstart.addDuration(duration));
	}

	/**
	 * Get the value from the SUMMARY field.
	 * @return
	 */
	public String getSummary() {
		return this.summary;
	}

	/**
	 * Ascertain whether the event has alarms.
	 * @return true, if the event has alarms
	 */
	public boolean hasAlarms() {
		return this.hasAlarms;
	}
	
	public int getResourceId() {
		return this.resourceId;
	}
	
	public String getOriginalBlob() {
		return this.originalBlob;
	}
	
	public Object getField(AcalEventAction.EVENT_FIELD field) {
		switch (field) {
			case startDate : 	return this.dtstart;
			case duration : 	return this.duration;
			case summary : 		return this.summary;
			case location : 	return this.location;
			case colour : 		return this.colour;
			case resourceId : 	return this.resourceId;
			case description : 	return this.description;
			case collectionId : return this.collection;
			case alarmList :	return this.alarmList;
			case repeatRule :	return this.repetition;
			default:
				Log.w(TAG,".getField("+field+") Trying to get a field that does not exist!");
//				throw new IllegalArgumentException();
		}
		return null;
	}

	
	/**	
	private AcalEvent(AcalDateTime dtstart,	AcalDuration duration, String summary,
						String description, String location, 
						String repetition, int colour, boolean hasAlarms,
						int resourceId, List<AcalAlarm> alarmList, String originalBlob, int collection) {
		this.dtstart = dtstart;
		this.duration = duration;
		this.summary = summary;
		this.description = description;
		this.location = location;
		this.repetition = repetition;
		this.colour = colour;
		this.hasAlarms = hasAlarms;
		this.resourceId = resourceId;
		this.alarmList.addAll(alarmList);
		this.originalBlob = originalBlob;
		this.collection = collection;
		
	}

	public static AcalEvent emptyEvent() {

		return new AcalEvent(
					null,
					null,
					null,
					null,
					null,
					null,
					0,
					false,
					-1,
					new ArrayList<AcalAlarm>(),
					null,
					-1
				);
	}
	*/
}
