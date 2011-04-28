package com.morphoss.acal.davacal;

import java.util.TimeZone;



public class SimpleAcalEvent implements Comparable<SimpleAcalEvent> {
	//Start and end times are in UTC
	public final long start;
	public final long end;
	public final long resourceId;
	public final String summary;
	public final int colour;
	
	//Special Fields for WeekView
	private int lastWidth;
	
	public SimpleAcalEvent(long start, long end, long resourceId, String summary, int colour) {
		long st = start;
		long en = end;
		st+= (TimeZone.getDefault().getRawOffset()/1000f);
		en+= (TimeZone.getDefault().getRawOffset()/1000f);		//make sure we store time in 'floating time'
		this.start = st;
		this.end = en;
		this.resourceId = resourceId;
		this.summary = summary;
		this.colour = colour;
	}
	
	//Returns a simple representation of an AcalEvent
	public static SimpleAcalEvent getSimpleEvent(AcalEvent event) {
		return new SimpleAcalEvent(event.getStart().getEpoch(), event.getEnd().getEpoch(), event.resourceId, event.summary, event .colour);
	}
	
	//Returns true iff this event overlaps the one given
	public boolean overlaps(SimpleAcalEvent e) {
		return this.end>=e.start && this.start<=e.end;
	}
	
	
	/**
	 * Special Methods for WeekView
	 * TODO try and refactor these out
	 */
	public void setLastWidth(int val) {
		this.lastWidth = val;
	}
	
	public int getLastWidth() {
		return this.lastWidth;
	}
	
	
	@Override
	public int compareTo(SimpleAcalEvent seo) {
		return (int)(this.end - this.start);
	}
}
