package com.morphoss.acal.davacal;

public enum PropertyName {
	UID, DTSTAMP, CREATED, LAST_MODIFIED, DTSTART, DTEND, DUE, DURATION, LOCATION, SUMMARY,
	DESCRIPTION, RRULE, RDATE, EXDATE, PERCENT_COMPLETE, COMPLETED;
	
	public String toString() {
		if ( this == LAST_MODIFIED ) return "LAST-MODIFIED";
		else if ( this == PERCENT_COMPLETE ) return "PERCENT-COMPLETE";
		return super.toString();
	}
};

