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

import android.os.Parcel;
import android.os.Parcelable;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;

public class AcalAlarm implements Serializable, Parcelable, Comparable<AcalAlarm> {
	private static final long	serialVersionUID	= 1L;
	public final boolean relativeToStart;
	public final String description;
	public final AcalDuration relativeTime;
	public final AcalDateTime timeToFire;
	public final ActionType actionType;
	public boolean isSnooze = false;
	public AcalDateTime snoozeTime = null;
	public boolean hasEventAssociated = false;
	public AcalEvent myEvent = null;

	
	public String toString() {
		return "AcalAlarm: nextTriggerTime: "+this.getNextTimeToFire()+" Snooze: "+(isSnooze ? "Yes" : "No")+
			  " Action: "+actionType+" Relative: "+(relativeToStart ? "Yes" : "No");
	}
	
	@Override
	public boolean equals (Object o) {
		if (! (o instanceof AcalAlarm) || o == null ) return false;
		if (this == o) return true;
		AcalAlarm that = (AcalAlarm)o;
		return 	(this.relativeToStart == that.relativeToStart) &&
				(this.description != null ? (that.description != null) && (this.description.equals(that.description)) : that.description == null )&&
				(this.timeToFire != null ? (that.timeToFire != null) && (this.timeToFire.equals(that.timeToFire)) : that.timeToFire == null )&&
				(this.relativeTime != null ? (that.relativeTime != null) && (this.relativeTime.equals(that.relativeTime)) : that.relativeTime == null )&&
				(this.actionType != null ? (that.actionType != null) && (this.actionType.equals(that.actionType)) : that.actionType == null )&&
				(this.isSnooze == that.isSnooze);
	}
	public enum ActionType {
		DISPLAY, AUDIO, IGNORED;

		public static ActionType fromString( String typeString ) {
			if ( typeString != null ) {
				if ( typeString.equalsIgnoreCase("DISPLAY")) return DISPLAY;
				if ( typeString.equalsIgnoreCase("AUDIO")) return AUDIO;
			}
			return IGNORED;
		}
	};

	public AcalAlarm(boolean relativeToStart, String description,	AcalDuration relativeTime, ActionType actionType, AcalDateTime start, AcalDateTime end) {
		this.relativeToStart = relativeToStart;
		this.description = description;
		this.relativeTime = relativeTime;
		this.actionType = actionType;
		if ( relativeToStart )
			timeToFire = AcalDateTime.addDuration(start, relativeTime);
		else
			timeToFire = AcalDateTime.addDuration(end, relativeTime);
	}
	
	public AcalAlarm( VAlarm component, Masterable parent, AcalDateTime start, AcalDateTime end ) {
		AcalProperty aProperty = component.getProperty("TRIGGER");
		String related = null;
		if ( aProperty != null )
			related = aProperty.getParam("RELATED");
		relativeToStart = (related == null || related.equalsIgnoreCase("START"));
		relativeTime = AcalDuration.fromProperty(aProperty);
		if ( relativeToStart )
			timeToFire = AcalDateTime.addDuration(start, relativeTime);
		else
			timeToFire = AcalDateTime.addDuration(end, relativeTime);
		aProperty = component.getProperty("ACTION");
		actionType = ( aProperty == null ? ActionType.IGNORED : ActionType.fromString(aProperty.getValue()));
		
		aProperty = component.getProperty("DESCRIPTION");
		if ( aProperty == null || aProperty.getValue().equalsIgnoreCase("Default Mozilla Description") ) {
			aProperty = parent.getProperty("SUMMARY");
		}
		description = ( aProperty == null ? "Alarm" : aProperty.getValue());
	}

	public VAlarm getVAlarm( Masterable parent ) {
		VAlarm ret = new VAlarm( parent );
		String triggerValue = relativeTime.toString();;
		
		AcalProperty trigger = new AcalProperty("TRIGGER", triggerValue);
		if ( ! relativeToStart ) trigger.setParam("RELATED", "END");
		ret.addProperty(trigger);

		AcalProperty action = new AcalProperty("ACTION", this.actionType.toString());
		ret.addProperty(action);
		
		return ret;
	}

	@Override
	public int compareTo(AcalAlarm another) {
		if ( this.getNextTimeToFire().before(another.getNextTimeToFire())) return -1;
		if ( this.getNextTimeToFire().after(another.getNextTimeToFire())) return 1;
		return 0;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeByte((byte)(relativeToStart?'S':'F'));
		out.writeString(description);
		if ( relativeTime == null ) {
			throw new NullPointerException("relativeTime may not be null");
		}
		relativeTime.writeToParcel(out,flags);
		if ( timeToFire == null ) {
			throw new NullPointerException("timeToFire may not be null");
		}
		timeToFire.writeToParcel(out,flags);
		out.writeByte((byte)(isSnooze ? 'T' : 'F'));
		if (isSnooze) {
			snoozeTime.writeToParcel(out,flags);
		}
		out.writeByte((byte)(hasEventAssociated ? 'T' : 'F'));
		if (hasEventAssociated) {
			myEvent.writeToParcel(out, flags);
		}
		out.writeString(actionType.toString());
	}

	public AcalAlarm(Parcel in) {
		relativeToStart = in.readByte() == 'S';
		description = in.readString();
		relativeTime = new AcalDuration(in);
		timeToFire = AcalDateTime.unwrapParcel(in);
	
		isSnooze = (in.readByte() == 'T');
		if (isSnooze) {
			this.snoozeTime = AcalDateTime.unwrapParcel(in);
		}
		hasEventAssociated = (in.readByte() == 'T');
		if (hasEventAssociated) {
			myEvent = new AcalEvent(in);
		}
		actionType = ActionType.fromString(in.readString());
	}

	public static final Parcelable.Creator<AcalAlarm> CREATOR = new Parcelable.Creator<AcalAlarm>() {
		public AcalAlarm createFromParcel(Parcel in) {
			return new AcalAlarm(in);
		}

		public AcalAlarm[] newArray(int size) {
			return new AcalAlarm[size];
		}
	};
	
	public long nextAlarmTime() {
		return timeToFire.getMillis();
	}
	
	public String toPrettyString() {
		return relativeTime.toPrettyString((relativeToStart ? "start" : "finish"));
	}
	
	/**
	 * The following relate specifically to AlarmActivity/CDS
	 */
	public void snooze(AcalDuration howLong) {
		isSnooze = true; 
		this.snoozeTime = AcalDateTime.addDuration(new AcalDateTime(), howLong);
	}
	
	public void setEvent(AcalEvent e) {
		this.hasEventAssociated = true;
		this.myEvent = e;
	}
	
	public AcalEvent getEvent() {
		return this.myEvent;
	}
	
	public boolean isSnooze() {
		return this.isSnooze;
	}
	
	public AcalDateTime getNextTimeToFire() {
		if (!isSnooze) return timeToFire;
		return snoozeTime;
	}
	
	public void setToLocalTime() {
		this.timeToFire.applyLocalTimeZone();
		if (this.snoozeTime != null) this.snoozeTime.applyLocalTimeZone();
	}

}

