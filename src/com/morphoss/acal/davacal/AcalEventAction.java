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

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;

import android.R;
import android.os.Parcel;
import android.os.Parcelable;

public class AcalEventAction implements Parcelable {

	private boolean dirty = false;
	public final AcalEvent event;
	private int action = 0;
	public static final int ACTION_CREATE = 0;
	public static final int ACTION_MODIFY_SINGLE = 1;
	public static final int ACTION_MODIFY_ALL = 2;
	public static final int ACTION_MODIFY_ALL_FUTURE = 3;
	public static final int ACTION_DELETE_SINGLE = 4;
	public static final int ACTION_DELETE_ALL = 5;
	public static final int ACTION_DELETE_ALL_FUTURE = 6;
	
	public static enum EVENT_FIELD {
		resourceId,
		startDate,
		duration,
		summary,
		location,
		description,
		colour,
		collectionId,
		repeatRule,
		alarmList
	}
	
	
	private final boolean[] dirtyFlags = new boolean[EVENT_FIELD.values().length];
	private final Object[] properties = new Object[EVENT_FIELD.values().length];
	
	public boolean isModifyAction() {
		return 	(this.action == ACTION_MODIFY_SINGLE) ||
				(this.action == ACTION_MODIFY_ALL) ||
				(this.action == ACTION_MODIFY_ALL_FUTURE); 
	}
	
	public boolean isPending() {
		return event.isPending;
	}

	public AcalEventAction(Parcel in) {
		event = new AcalEvent(in);
		this.action = ACTION_MODIFY_ALL;
	}
	
	public AcalEventAction(AcalEvent originalEvent) {
		this.event = originalEvent;
		this.action = ACTION_MODIFY_ALL;
	}
	
	public void setAction(int action) {
		this.action = action;
	}
	
	public AcalEventAction( Map<EVENT_FIELD,Object> default_properties ) {
		for ( int i=0; i<this.properties.length; i++ ) {
			properties[i] = null;
			dirtyFlags[i] = true;
		}
		properties[EVENT_FIELD.colour.ordinal()] = R.color.black;
		for( EVENT_FIELD defaultKey : default_properties.keySet() ) {
			properties[defaultKey.ordinal()] = default_properties.get(defaultKey);
		}
		this.action = ACTION_CREATE;
		this.event = null; //AcalEvent.emptyEvent();
	}
	
	public boolean isDirty() { return dirty; }
	public int getAction() {
		return this.action;
	}
	
	public Object getField(EVENT_FIELD field) {
		if (dirtyFlags[field.ordinal()]) return properties[field.ordinal()];
		return event.getField(field);
	}
	public void setField(EVENT_FIELD field, Object val) {
		properties[field.ordinal()] = val;
		dirtyFlags[field.ordinal()] = true;
		dirty=true;
	}
	
	public String getOriginalBlob() {
		if ( event == null ) return null;
		return this.event.getOriginalBlob();
	}
	
	
	public String getTimeText(AcalDateTime viewDateStart, AcalDateTime viewDateEnd, boolean as24HourTime ) {
		AcalDateTime start = (AcalDateTime) getField(EVENT_FIELD.startDate);
		start.applyLocalTimeZone();
		AcalDateTime finish = start.clone();
		finish.addDuration((AcalDuration) getField(EVENT_FIELD.duration));

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
	
	
	public static final Parcelable.Creator<AcalEventAction> CREATOR = new Parcelable.Creator<AcalEventAction>() {
        public AcalEventAction createFromParcel(Parcel in) {
            return getInstanceFromParcel(in);
        }

        public AcalEventAction[] newArray(int size) {
            return new AcalEventAction[size];
        }
    };
    public static AcalEventAction getInstanceFromParcel(Parcel in) {
    	return new AcalEventAction(new AcalEvent(in));
	}
    @Override
	public int describeContents() {
		return 0;
	}
	@Override
	public void writeToParcel(Parcel out, int flags) {
		if (event == null) return;
		AcalEvent.parcelActionEventAsEvent(out, this);
	}

	@SuppressWarnings("unchecked")
	public List<AcalAlarm> getAlarms() {
		return (List<AcalAlarm>) getField(EVENT_FIELD.alarmList);
	}	
}
