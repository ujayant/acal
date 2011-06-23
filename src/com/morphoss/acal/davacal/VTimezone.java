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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalRepeatRuleParser;



public class VTimezone extends VComponent {

	public static final String TAG = "aCal VTimezone";
	protected final String name = "VTimezone";

	protected TimeZone tz = null;
	protected String tzid = null;
	
	public VTimezone(ComponentParts splitter, Integer resourceId, AcalCollection collectionObject,VComponent parent) {
		super(splitter, resourceId, collectionObject, parent);
	}

	public String getTZID() {
		if ( tzid == null ) {
			if ( !guessOlsonTimeZone() ) {
				this.makeTimeZone(getProperty("TZID").getValue());
			}
		}
		return tzid;
	}

	public TimeZone getTZ() {
		if ( tz == null ) {
			if ( !guessOlsonTimeZone() ) {
				this.makeTimeZone(getProperty("TZID").getValue());
			}
		}
		return tz;
	}

	private boolean guessOlsonTimeZone() {
		if ( tryTz(getProperty("TZID")) ) return true;
		if ( tryTz(getProperty("TZNAME")) ) return true;
		if ( tryTz(getProperty("X-LIC-LOCATION")) ) return true;
		
		String[] matchingZones = getMatchingZones();
		if ( matchingZones != null && matchingZones.length == 1 ) {
			tzid = matchingZones[0];
			tz = TimeZone.getTimeZone(tzid); 
			return true;
		}
		return false;
	}

	private boolean tryTz(AcalProperty testProperty) {
		if ( testProperty != null ) {
			tzid = testProperty.getValue(); 
			if ( tzid != null ) {
				tz = TimeZone.getTimeZone(tzid);
				if ( tz != null ) {
					tzid = tz.getID();
					return true;
				}
				tzid = AcalDateTime.getOlsonName(tzid);
				if ( tzid != null ) {
					tz = TimeZone.getTimeZone(tzid);
					if ( tz != null ) {
						tzid = tz.getID();
						return true;
					}
					tzid = null;
				}
			}
		}
		return false;
	}

	
	private String[] getMatchingZones() {
		List<VComponent> subComponents = this.getChildren();
		List<Integer> offsets = new ArrayList<Integer>();
		List<String> onsets = new ArrayList<String>();
		List<Boolean> types = new ArrayList<Boolean>();
		int offset;
		boolean isDaylight;
		int dstMillis = 2000000000;
		int stdMillis = 2000000000; // An out of range value that will match nothing
		for( VComponent child : subComponents ) {
			isDaylight = child.getName().equalsIgnoreCase("daylight"); 
			types.add(isDaylight);
			onsets.add(child.getProperty("RRULE").getValue());
			offset = Integer.parseInt(child.getProperty("TZOFFSETTO").getValue());
			offset = (((int)(offset/100)*3600) + (offset%100)) * 1000;
			offsets.add(offset);
			if ( isDaylight )
				dstMillis = offset;
			else
				stdMillis = offset;
		}
		if ( stdMillis > 864000000 ) return null;

		String[] ids = TimeZone.getAvailableIDs(stdMillis);
		List<String> zoneList = new ArrayList<String>();
		SimpleTimeZone zone = null;
		for (String id : ids) {
			 zone = (SimpleTimeZone) TimeZone.getTimeZone(id);
			 if ( zone.getDSTSavings() == dstMillis ) zoneList.add(zone.getID());
		 }
		
		return (String[]) zoneList.toArray();

	}

	
	private void makeTimeZone( String id ) {
		int offset;

		int stdOffset = 2000000000; // An out of range value that will match nothing
		AcalDateTime stdStart = null;
		AcalRepeatRuleParser stdRule = null;

		int dstOffset = 2000000000;
		AcalDateTime dstStart = null;
		AcalRepeatRuleParser dstRule = null;

		for( VComponent child : this.getChildren() ) {
			offset = Integer.parseInt(child.getProperty("TZOFFSETTO").getValue());
			offset = (((int)(offset/100)*3600) + (offset%100)) * 1000;

			if ( child.getName().equalsIgnoreCase("daylight") ) {
				dstOffset = offset;
				dstRule = AcalRepeatRuleParser.parseRepeatRule(child.getProperty("RRULE").getValue());
				dstStart = AcalDateTime.fromAcalProperty(child.getProperty("DTSTART"));
			}
			else {
				stdOffset = offset;
				stdRule = AcalRepeatRuleParser.parseRepeatRule(child.getProperty("RRULE").getValue());
				stdStart = AcalDateTime.fromAcalProperty(child.getProperty("DTSTART"));
			}
		}
		if ( stdOffset > 864000000 || stdStart == null || dstStart == null
					) return;

		int startDay, endDay, startDoW = 0, endDoW = 0;

		if ( stdRule.bymonth.length == 1 ) stdStart.setMonth(stdRule.bymonth[0]);
		if ( stdRule.bymonthday.length == 1 ) startDay = stdRule.bymonthday[0];
		else startDay = stdStart.getMonthDay();
		if ( stdRule.byday.length == 1 ) startDoW = stdRule.byday[0].wDay;
		
		if ( dstRule.bymonth.length == 1 ) dstStart.setMonth(dstRule.bymonth[0]);
		if ( dstRule.bymonthday.length == 1 ) endDay = dstRule.bymonthday[0];
		else endDay = dstStart.getMonthDay();
		if ( dstRule.byday.length == 1 ) endDoW = dstRule.byday[0].wDay;

		
		SimpleTimeZone sTz = new SimpleTimeZone(stdOffset, id, 
					stdStart.getMonth() - 1, startDay, startDoW, stdStart.getDaySecond() * 1000, 
					dstStart.getMonth() - 1, endDay, endDoW, dstStart.getDaySecond() * 1000, dstOffset);

		tz = sTz;
		tzid = id;
	}
	/*
       TZID:America/New_York
       LAST-MODIFIED:20050809T050000Z
       BEGIN:DAYLIGHT
       DTSTART:19670430T020000
       RRULE:FREQ=YEARLY;BYMONTH=4;BYDAY=-1SU;UNTIL=19730429T070000Z
       TZOFFSETFROM:-0500
       TZOFFSETTO:-0400
       TZNAME:EDT
       END:DAYLIGHT
       BEGIN:STANDARD
       DTSTART:19671029T020000
       RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU;UNTIL=20061029T060000Z
       TZOFFSETFROM:-0400
       TZOFFSETTO:-0500
       TZNAME:EST
       END:STANDARD       
	 */
	/**
	 * TODO: Completely inadequate.  We're really going to have to (a) build them,
	 * (b) fetch them or (c) steal them from a passing appointment.  And in actuality (b)
	 * and (c) probably won't work too well :-(
	 */
	public VTimezone( VCalendar parent, String tzName ) {
		super( "VTIMEZONE", parent.collectionData, parent );
		try { setPersistentOn(); } catch (YouMustSurroundThisMethodInTryCatchOrIllEatYouException e) { }

		if ( tzName != null )
			tz = TimeZone.getTimeZone(tzName);

		if ( tz == null )
			tz = TimeZone.getDefault();

		int stdOffset = (int) tz.getRawOffset()/1000;
		int dstOffset = (int) tz.getDSTSavings()/1000;
		tzid = tz.getID();
		addProperty(new AcalProperty("TZID",tzid));
		if ( tz.useDaylightTime() ) {
			addProperty(new AcalProperty("BEGIN", "DAYLIGHT"));
			addProperty(new AcalProperty("TZNAME", tz.getDisplayName(true, TimeZone.SHORT, Locale.US)));
			addProperty(new AcalProperty("TZOFFSETFROM", (stdOffset<0?"-":"")+String.format("%02d%02d", (int) (stdOffset/3600), stdOffset%3600)));
			addProperty(new AcalProperty("TZOFFSETTO", (dstOffset<0?"-":"")+String.format("%02d%02d", (int) (dstOffset/3600), dstOffset%3600)));
			addProperty(new AcalProperty("RRULE",""));
			addProperty(new AcalProperty("DTSTART",""));
			addProperty(new AcalProperty("END","DAYLIGHT"));
		}
		addProperty(new AcalProperty("BEGIN", "STANDARD"));
		addProperty(new AcalProperty("TZNAME", tz.getDisplayName(false, TimeZone.SHORT, Locale.US)));
		addProperty(new AcalProperty("TZOFFSETFROM", (dstOffset<0?"-":"")+String.format("%02d%02d", (int) (dstOffset/3600), dstOffset%3600)));
		addProperty(new AcalProperty("TZOFFSETTO", (stdOffset<0?"-":"")+String.format("%02d%02d", (int) (stdOffset/3600), stdOffset%3600)));
		addProperty(new AcalProperty("RRULE",""));
		addProperty(new AcalProperty("DTSTART",""));
		addProperty(new AcalProperty("END","STANDARD"));
	}

	
}
