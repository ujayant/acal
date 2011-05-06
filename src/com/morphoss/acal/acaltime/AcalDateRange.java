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

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.morphoss.acal.Constants;

/**
 * @author Morphoss Ltd
 */

public class AcalDateRange implements Parcelable, Cloneable {

	private final static String TAG = "AcalDateRange";

	final public AcalDateTime start;
	final public AcalDateTime end;

	public AcalDateRange(AcalDateTime start, AcalDateTime end) {
		if ( start == null ) throw new IllegalArgumentException();
		this.start = start.clone();
		this.end = (end==null ? null : end.clone());
	}
	
	public static final Parcelable.Creator<AcalDateRange> CREATOR = new Parcelable.Creator<AcalDateRange>() {
        public AcalDateRange createFromParcel(Parcel in) {
            return new AcalDateRange(in);
        }

        public AcalDateRange[] newArray(int size) {
            return new AcalDateRange[size];
        }
    };

    public AcalDateRange(Parcel in) {
		this.start = AcalDateTime.unwrapParcel(in);
		this.end = AcalDateTime.unwrapParcel(in);
    }
    
	@Override
	public int describeContents() {
		return 0;
	}
	
	public AcalDateRange getIntersection(AcalDateRange other) {
		if ( end != null && end.before(other.start) ) return null;

		AcalDateTime newStart = start;
		if ( newStart.before(other.start) ) newStart = other.start;

		AcalDateTime newEnd = (end == null ? other.end : end );
		if ( newEnd != null && other.end != null && newEnd.after(other.end) ) newEnd = other.end;
		if ( newEnd != null && newEnd.before(newStart)) return null;

		if ( Constants.debugDateTime && Constants.LOG_VERBOSE )	Log.v(TAG,"Intersection of ("+start.fmtIcal()+","+(end==null?"null":end.fmtIcal())+") & ("
						+other.start.fmtIcal()+","+(other.end==null?"null":other.end.fmtIcal())+") is ("
						+newStart.fmtIcal()+","+(newEnd==null?"null":newEnd.fmtIcal())+")"
					);
		
		return new AcalDateRange(newStart,newEnd);
		
	}

	/**
	 * Test whether this range overlaps the test range
	 * @param start
	 * @param finish
	 * @return true, if the ranges overlap.
	 */
	public boolean overlaps( AcalDateRange dTest ) {
		if ( dTest == null ) return false;
		return overlaps(dTest.start,dTest.end);
	}

	/**
	 * Test whether this range overlaps the period from start (inclusive) to end (non-inclusive)
	 * @param start
	 * @param finish
	 * @return true, if the ranges overlap.
	 */
	public boolean overlaps( AcalDateTime start, AcalDateTime finish ) {
		if ( start == null && finish == null) return false;
		boolean answer;
		if ( end == null ) {
			answer = (!start.before(start) && (finish == null || finish.after(start) ) );
		}
		else if ( finish == null ) {
			answer = start.before(end);
		}
		else {
			answer = ( !finish.before(start) && start.before(end) );
		}
		if ( Constants.debugDateTime && Constants.LOG_VERBOSE )
			Log.v(TAG,"Overlap of ("+start.fmtIcal()+","+(end==null?"null":end.fmtIcal())+") & ("
						+ start.fmtIcal()+","+(finish==null?"null":finish.fmtIcal())+") is: "
						+ (answer? "yes":"no")
					);
		return answer;
	}

	public String toString() {
		return "range("+start.fmtIcal()+","+(end==null?">>>":end.fmtIcal());
	}

	public AcalDateRange clone() {
		return new AcalDateRange(start,end);
	}
	
	@Override
	public void writeToParcel(Parcel out, int flags) {
		start.writeToParcel(out, flags);
		end.writeToParcel(out, flags);
	}
}
