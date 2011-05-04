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

import com.morphoss.acal.acaltime.AcalDateTime;


/**
 * 
 * @author Morphoss Ltd
 *
 */
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
//		st+= (TimeZone.getDefault().getRawOffset()/1000f);
//		en+= (TimeZone.getDefault().getRawOffset()/1000f);		//make sure we store time in 'floating time'
		this.start = st;
		this.end = en;
		this.resourceId = resourceId;
		this.summary = summary;
		this.colour = colour;
	}
	
	/**
	 * Factory method to generate a SimpleAcalEvent from a real AcalEvent.  Since we don't have to worry
	 * about repetition from here on in, we ensure the events are localised to the user's current
	 * timezone before we go any further.
	 * 
	 * @param event The AcalEvent to be finely ground
	 * @return Essence of SimpleAcalEvent which we have ground from the AcalEvent.
	 */
	public static SimpleAcalEvent getSimpleEvent(AcalEvent event) {
		AcalDateTime start = event.getStart(); 
		start.applyLocalTimeZone();
		return new SimpleAcalEvent(start.getEpoch(), start.getEpoch()+(event.getDuration().getDurationMillis()/1000),
					event.resourceId, event.summary, event.colour);
	}

	/**
	 * Identifies whether this event overlaps another.  In this case "Overlap" is defined
	 * in accordance with the spirit of RFC5545 et al, in that the event does *not* include
	 * the end time.
	 * 
	 * @param e Another event we might overlap.
	 * @return true iff this event overlaps the one given, otherwise false
	 */
	public boolean overlaps(SimpleAcalEvent e) {
		return this.end > e.start && this.start < e.end;
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
