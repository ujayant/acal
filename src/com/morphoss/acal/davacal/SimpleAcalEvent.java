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
import java.util.Date;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;


/**
 * 
 * @author Morphoss Ltd
 *
 */
public class SimpleAcalEvent implements Comparable<SimpleAcalEvent> {

	private static final String TAG = "SimpleAcalEvent"; 

	//Start and end times are in UTC
	public final long start;
	public final long end;
	public final long resourceId;
	public final String summary;
	public final int colour;
	public final String location;
	public final boolean hasRepeat;
	public final boolean hasAlarm;
	public final boolean isAllDay;
	public final boolean isPending;
	
	private SimpleAcalEvent(long start, long end, long resourceId, String summary, String location, int colour,
				boolean isAlarming, boolean isRepetitive, boolean allDayEvent, boolean isPending ) {
		long st = start;
		long en = end;
		this.start = st;
		this.end = en;
		this.resourceId = resourceId;
		this.summary = summary;
		this.location = location;
		this.colour = colour;
		this.hasAlarm = isAlarming;
		this.hasRepeat = isRepetitive;
		this.isAllDay = allDayEvent;
		this.isPending = isPending;
	}

	
	/**
	 * Construct a SimpleAcalEvent from bits of VComponent, with overrides for
	 * startDate & duration so we can build it inside a repeat rule calculation 
	 * @param event The parsed VComponent which is the event
	 * @param startDate To override the startDate in the event.
	 * @param duration To override the duration or end date in the event 
	 */
	public SimpleAcalEvent(VComponent event, AcalDateTime startDate, AcalDuration duration, boolean isPending ) {
		this.resourceId = event.getResourceId();
		
		this.isPending = isPending;

		boolean allDayEvent = startDate.isDate();
		boolean floating = startDate.isFloating();
		start = startDate.applyLocalTimeZone().getEpoch();

		
		long en = start - 1; // illegal value to test for...
		if ( duration != null ) {
			en = duration.getEndDate(startDate).getEpoch();
			if ( floating && !allDayEvent && duration.getTimeMillis() == 0 && duration.getDays() > 0)
				allDayEvent = true;
		}
		isAllDay = allDayEvent;
		
		if ( en < start ) {
			AcalProperty dtend = event.getProperty("DTEND");  
			if (dtend != null)
				en = AcalDateTime.fromAcalProperty(dtend).applyLocalTimeZone().getEpoch();
		}
		if ( en < start ) {
			if ( startDate.isDate() )
				en = start + AcalDateTime.SECONDS_IN_DAY;
			else
				en = start;
		}
		end = en;

		summary = event.safePropertyValue("SUMMARY");
		location = event.safePropertyValue("LOCATION");
		String repeats = event.safePropertyValue("RRULE") + event.safePropertyValue("RDATE");
		hasRepeat = repeats.length()>0;

		boolean isAlarming = false;
		for( VComponent child : event.getChildren() ) {
			if ( child instanceof VAlarm ) {
				isAlarming = true;
				break;
			}
		}
		hasAlarm = isAlarming;
		
		int aColor = Color.BLUE;
		try {
			aColor = event.getCollectionColour();
		} catch (Exception e) {
			Log.e(TAG,"Error Creating UnModifiableAcalEvent - "+e.getMessage());
			Log.e(TAG,Log.getStackTraceString(e));
		}
		colour = aColor;
		
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
		boolean allDayEvent = start.isDate() ||	(start.isFloating()
														&& event.getDuration().getTimeMillis() == 0
																	&& event.getDuration().getDays() > 0);
		start.applyLocalTimeZone();
		return new SimpleAcalEvent(start.getEpoch(), event.getDuration().getEndDate(start).getEpoch(),
					event.resourceId, event.summary, event.location, event.colour,
					event.hasAlarms(), event.getRepetition().length() > 0, allDayEvent,
					event.isPending);
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

	/**
	 * Return a pretty string indicating the time period of the event.  If the start or end
	 * are on a different date to the view start/end then we also include the date on that
	 * element.  If it is an all day event, we say so. 
	 * @param viewDateStart - the start of the viewed range
	 * @param viewDateEnd - the end of the viewed range
	 * @param as24HourTime - from the pref
	 * @return A nicely formatted string explaining the start/end of the event.
	 */
	public String getTimeText(Context c, long viewDateStart, long viewDateEnd, boolean as24HourTime ) {
		String timeText = "";
		String timeFormatString = (as24HourTime ? "HH:mm" : "hh:mmaa");
		SimpleDateFormat timeFormatter = new SimpleDateFormat(timeFormatString);

		Date st = new Date(start*1000);
		Date en = new Date(end*1000);
		if ( start < viewDateStart || end  > viewDateEnd ){
			if ( isAllDay ) {
				timeFormatter = new SimpleDateFormat("MMM d");
				timeText = c.getString(R.string.AllDaysInPeriod, timeFormatter.format(st), timeFormatter.format(en));
			}
			else {
				SimpleDateFormat startFormatter = timeFormatter;
				SimpleDateFormat finishFormatter = timeFormatter;
				
				if ( start < viewDateStart )
					startFormatter  = new SimpleDateFormat("MMM d, "+timeFormatString);
				if ( end >= viewDateEnd )
					finishFormatter = new SimpleDateFormat("MMM d, "+timeFormatString);
		
				timeText = startFormatter.format(st)+" - " + finishFormatter.format(en);
			}
		}
		else if ( isAllDay ) {
			timeText = c.getString(R.string.ForTheWholeDay);
		}
		else {
			timeText = timeFormatter.format(st) + " - " + timeFormatter.format(en);
		}
		return timeText;
	}

}
