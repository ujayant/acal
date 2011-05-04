package com.morphoss.acal.davacal;

import java.util.TimeZone;



public class SimpleAcalEvent implements Comparable<SimpleAcalEvent> {
	//Start and end times are in UTC
	public final long start;
	public final long end;
	public final long resourceId;
	public final String summary;
	public final int colour;
	
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
		event.dtstart.applyLocalTimeZone();
		return new SimpleAcalEvent(event.getStart().getEpoch(), event.getEnd().getEpoch(), event.resourceId, event.summary, event .colour);
	}
	
	//Returns true iff this event overlaps the one given
	public boolean overlaps(SimpleAcalEvent e) {
		return this.end>=e.start && this.start<=e.end;
	}
	
	
	/**
	 * Special Fields/Methods for WeekView
	 */
	
	private int maxWidth;
	private int actualWidth;
	private int lastWidth;
	
	public void calulateMaxWidth(int screenWidth, int HSPP) {
		this.actualWidth = (int)(end-start)/HSPP;
		maxWidth = (int) Math.min(actualWidth,screenWidth);
	}
	
	public int getMaxWidth() {
		return this.maxWidth;
	}
	
	public int getActualWidth() {
		return this.actualWidth;
	}
	
	public int getLastWidth() {
		return this.lastWidth;
	}
	public void setLastWidth(int w) {
		this.lastWidth = w;
	}
	
	@Override
	public int compareTo(SimpleAcalEvent seo) {
		return (int)(this.end - this.start);
	}
}
