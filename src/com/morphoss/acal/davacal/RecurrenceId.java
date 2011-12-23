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

import java.util.Comparator;

import com.morphoss.acal.acaltime.AcalDateTime;

public class RecurrenceId extends AcalProperty implements Comparable<RecurrenceId>{
	
	public final AcalDateTime	when;
	public final boolean		thisAndFuture;

	protected RecurrenceId(String name, String value, String[] paramsBlob) {
		super(name,value,paramsBlob);
		when = AcalDateTime.fromIcalendar(getValue(),getParam("VALUE"), getParam("TZID"));
		thisAndFuture = (getParam("RANGE") != null && getParam("RANGE").equalsIgnoreCase("THISANDFUTURE"));
	}

	public static RecurrenceId fromString(String blob) {
		AcalProperty ret = AcalProperty.fromString(blob);
		if ( ret instanceof RecurrenceId ) return (RecurrenceId) ret;
		throw new IllegalArgumentException();
	}

	public int compareTo(RecurrenceId another) {
		if ( when.before(another.when) ) return -1;
		else if ( when.after(another.when) ) return 1;
		return 0;
	}
	
	public static Comparator<VComponent> getVComponentComparatorByRecurrenceId() {
		return new VComponentComparatorByRecurrenceId();
	}
	
	public boolean equals(RecurrenceId rid) {
		if ( when == null && rid.when == null ) return true;
		return when.equals(rid.when);
	}

	/**
	 * Check whether this overrides an instance at rid, by checking if this has a "RANGE=THISANDFUTURE" property and is not after rid.
	 * 
	 * @param rid
	 * @return
	 */
	public boolean overrides(RecurrenceId rid) {
		return ( (thisAndFuture && !when.after(rid.when)) || when.equals(rid.when) );
	}
	
	public static class VComponentComparatorByRecurrenceId implements Comparator<VComponent> {
		
		public int compare(VComponent a, VComponent b) {
			RecurrenceId recA = (RecurrenceId)a.getProperty("RECURRENCE-ID");
			RecurrenceId recB = (RecurrenceId)b.getProperty("RECURRENCE-ID");
			return recA.compareTo(recB);
		}
	}

}
