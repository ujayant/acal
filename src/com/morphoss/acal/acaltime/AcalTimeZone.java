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

package com.morphoss.acal.acaltime;

import java.io.Serializable;
import java.util.TimeZone;
import java.util.regex.Matcher;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.HashCodeUtil;

/**
 * <h2>A timezone class that handles floating time.</h2>
 * <p>
 * "Floating" is the time you're having when you're not having a timezone.  In fact aCal does almost
 * all of it's date and time calculations in UTC, and only localises those for presentation purposes. In
 * the case of "floating" times no localisation is necessary, except to make sure we don't output a 
 * timezone.
 * </p>
 * <p>
 * We need to implement Serializable & Parcelable here since AcalCalendar needs these to be
 * parceled by AcalVEvent.
 * </p>
 * 
 * @author Morphoss Ltd
 */
public class AcalTimeZone  implements Serializable, Parcelable {

	private static final String TAG = "aCal TimeZone";
	private static final long	serialVersionUID	= 1L;

	private static final TimeZone JAVA_UTC = TimeZone.getTimeZone("UTC");
	
	private String timezoneName;
	private boolean floating;
	private TimeZone tz;

	public AcalTimeZone() {
		floating = true;
		timezoneName = "";
		tz = JAVA_UTC;
	}

	public AcalTimeZone( String tzName ) throws UnrecognisedTimeZone {
		Matcher m = Constants.tzOlsonExtractor.matcher(tzName);
		if ( m.matches() ) {
			tzName = m.group(1);
		}
		tz = TimeZone.getTimeZone(tzName);
		if ( tz == null ) {
			floating = true;
			Log.w(TAG,"Unrecognised timezone name '"+tzName+"' "+m.group(1));
			throw new UnrecognisedTimeZone("Unknown timezone identifier '"+tzName+"'");
		}
		else {
			floating = false;
		}
		timezoneName = tzName;
	}
	
	public boolean isFloating() {
		return floating;
	}

	public boolean isUTC() {
		return timezoneName.equals("UTC");
	}

	public String toString() {
		if ( floating ) return "Floating";
		else return timezoneName;
	}

	public long getOffset(AcalDateTime when) {
		if ( floating || isUTC() ) return 0;
		return tz.getOffset(when.getMillis());
	}

	public static AcalTimeZone getFloatingZone() {
		return new AcalTimeZone();
	}

	public static AcalTimeZone getUTCZone() {
		AcalTimeZone utc = new AcalTimeZone();
		utc.floating = false;
		utc.timezoneName = "UTC";
		return utc;
	}

	public static AcalTimeZone getLocalZone() {
		AcalTimeZone ret = new AcalTimeZone();
		ret.floating = false;
		ret.tz = TimeZone.getDefault();
		ret.timezoneName = ret.tz.getID();
		return ret;
	}

	public boolean equals(Object that) {
		if ( this == that ) return true;
	    if ( !(that instanceof AcalTimeZone) ) return false;
	    AcalTimeZone thatTz = (AcalTimeZone)that;
	    return (
	    			this.floating == thatTz.floating
	    			&& this.timezoneName.equals(thatTz.timezoneName)
	    	);
		
	}
	
	public int hashCode() {
		int result = HashCodeUtil.SEED;
		result = HashCodeUtil.hash( result, this.floating );
	    result = HashCodeUtil.hash( result, this.timezoneName );
	    return result;
	}

    public AcalTimeZone(Parcel in) {
    	super();
		this.floating = (in.readByte() == 'F');
		if ( this.floating ) {
			timezoneName = "";
			tz = TimeZone.getTimeZone("UTC");
		}
		else {
			String tzName = in.readString();
			if ( tzName == null ) tzName = "UTC";
			timezoneName = tzName;
			tz = TimeZone.getTimeZone(tzName);
		}
    }
    
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		if ( this.floating ) {
			out.writeByte((byte) 'F');
		}
		else {
			out.writeByte((byte) 'T');
			out.writeString(timezoneName);
		}
	}

}
