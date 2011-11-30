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

package com.morphoss.acal.dataservice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import android.os.RemoteException;
import android.util.Log;

import com.morphoss.acal.aCal;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.davacal.AcalEvent;
import com.morphoss.acal.davacal.SimpleAcalEvent;

/**
 * This class is responsible for maintaining the collection of events to be used by month view
 * @author Morphoss Ltd
 *
 */
public class EventCache {

	//things we need
	//A list or array of events for a given day
	//A set of events for the currently displayed Month
	//A set of events for the currently displayed Day
	//A refresh directive
	//A delete directive
	
	//events go into large array in order of occurrence (NB, we could have up to MAX_DAYS+31 in the cache)
	final static int MAX_DAYS = 360;
	
	//Maps dates to events. each event list must remain sorted.
	static final Map<Integer,ArrayList<SimpleAcalEvent>> cachedEvents = new TreeMap<Integer,ArrayList<SimpleAcalEvent>>();
	
	//A queue of requested dates, lets us know which dates to remove (oldest first);
	static final LinkedList<Integer> dateQueue = new LinkedList<Integer>();

	static final String currentTimeZoneName = TimeZone.getDefault().getID();
	private static final ArrayList<SimpleAcalEvent>	emptyList	= new ArrayList<SimpleAcalEvent>(0);
	
	//Converts a datetime to a day hash
	static private int getDateHash(AcalDateTime day) {
		AcalDateTime d = day.clone();
		d.applyLocalTimeZone();
		return SimpleAcalEvent.getDateHash( day.getMonthDay(), day.getMonth(), day.getYear() );
	}
	
	
	/**
	 * This is the meat of the cache, its job is to add events in the most efficient manner possible.
	 * 	1) If the days events are already in the map, we are done.
	 *  2) Try to load a months worth of events as RR is more efficient if used this way. 
	 *  	2a) Reduce the scope of the query to exclude days we already have events for
	 *  3) Convert single list result into a set of lists, one for each day
	 *  4) Update the queue to keep track of which days should be wiped from the cache first
	 * @param day
	 */
	public synchronized void addDay(AcalDateTime day, DataRequest dataRequest) {
		if (cachedEvents.containsKey(getDateHash(day))) return;	//already in collection
		
		//only ensure size when adding, otherwise if MAX_DAYS is too small we could run into problems.
		ensureSize();

		// We get a month at a time, since RRule calculations are more efficient over longer periods
		AcalDateTime workingStart = day.clone();
		workingStart.applyLocalTimeZone().setDaySecond(0);
		workingStart.setMonthDay(1);
		AcalDateTime workingEnd = workingStart.clone().addMonths(1);
		AcalDateRange rangeToFetch = new AcalDateRange(workingStart, workingEnd);
		int year = workingStart.getYear();
		int month = workingStart.getMonth();
		int lastOfMonth = AcalDateTime.monthDays(year, month); 

		//We need to convert a list with several days worth of events to a set of lists each with exactly one days events.
		try {
			List<AcalEvent> sourceEvents = dataRequest.getEventsForDateRange(rangeToFetch);
			ArrayList<SimpleAcalEvent> result = new ArrayList<SimpleAcalEvent>(sourceEvents.size());
			for (AcalEvent se : sourceEvents) {
				result.add( SimpleAcalEvent.getSimpleEvent(se));
			}
			
			int i=0;
			if (result.isEmpty()) { 
				//put blanks in for days where there was no events
				for (i = 1; i<= lastOfMonth; i++) {
					int hash = SimpleAcalEvent.getDateHash(i, month, year);
					if (!cachedEvents.containsKey(hash)) cachedEvents.put(hash, emptyList);
				}
				return;
			}	//No events
			
			//using a special comparator to avoid events being shuffled around because of timezone
			Collections.sort(result); //, new EventComparator());
			
			int currentPointer = 0;
			
			//The current event we are processing
			SimpleAcalEvent currentEvent = null;
			Iterator<SimpleAcalEvent> si = null;

			//temp storage for multiday events
			ArrayList<SimpleAcalEvent> multiDayEvents = new ArrayList<SimpleAcalEvent>();

			int curHash = SimpleAcalEvent.getDateHash(1,month,year);
			int lastHash = SimpleAcalEvent.getDateHash(lastOfMonth,month,year);
			if ( currentPointer < result.size() )  currentEvent = result.get(currentPointer);
			while ( curHash <= lastHash) {

				//The list for the current day
				ArrayList<SimpleAcalEvent> todaysEvents = new ArrayList<SimpleAcalEvent>();

				// add any previous days multi day events
				si = multiDayEvents.iterator();
				while ( si.hasNext()) {
					SimpleAcalEvent event = si.next();
					todaysEvents.add(event);

					//remove multiday events that expire today.
					// since event end time is non-inclusive.
					if ( event.endDateHash == curHash ) si.remove();
				}
				
				// while the current item has this or an earlier hash (i.e. the same day or earlier)
				// add it to the current list.  We need to consider earlier events since we're looking
				// at a month boundary and the first event(s) might start before that.
				while (currentEvent != null && currentEvent.startDateHash <= curHash ) {
					//check to see if multi day event, if so add to multi day list
					if ( currentEvent.endDateHash > curHash ) multiDayEvents.add(currentEvent);
					todaysEvents.add(currentEvent);
					currentPointer++;
					if (currentPointer >= result.size()) {
						currentEvent = null;
						break;
					}
					currentEvent = result.get(currentPointer);
				}

				// We use the same emptyList constant everywhere
				cachedEvents.put(curHash, (todaysEvents.isEmpty() ? emptyList : todaysEvents) );

				if (dateQueue.contains((Integer)curHash))
					dateQueue.remove((Integer)curHash);	//remove item from queue if its already in there

				dateQueue.offer(curHash);	//add item to queue

				curHash++;
			}

		}
		catch (RemoteException e) {
			Log.w(aCal.TAG,"EventCache: Unable to fetch events in "+rangeToFetch.start.fmtIcal()+" - " + rangeToFetch.end.fmtIcal());
			Log.d(aCal.TAG, Log.getStackTraceString(e));
		}
		
	}
	
	//prevents the cache from getting too big
	private synchronized void ensureSize() {
		while (dateQueue.size() > MAX_DAYS) {
			cachedEvents.remove(dateQueue.poll());
		}
	}
	
	public synchronized void flushCache() {
		dateQueue.clear();
		cachedEvents.clear();
	}
	
	/** Methods required by month view */
	public synchronized ArrayList<SimpleAcalEvent> getEventsForDays(AcalDateRange range, DataRequest dr) {
		AcalDateTime current = range.start.clone();
		current.applyLocalTimeZone().setDaySecond(0);
		AcalDateTime end = range.end.clone();
		end.applyLocalTimeZone().setDaySecond(0).addDays(1);

		ArrayList<SimpleAcalEvent> ret = new ArrayList<SimpleAcalEvent>();
		while ( current.before(end) ) {
			ArrayList<SimpleAcalEvent> curList = getEventsForDay(current,dr);
			if ( curList == null ) continue;
			for ( SimpleAcalEvent e : curList ) {
				if (!ret.contains(e))ret.add(e);
			}
			current.addDays(1);
		}
		
		//we need to remove events that occured on same day as range.start but ended before range.start
		//and vice-versa for range.end
		Iterator<SimpleAcalEvent> i = ret.iterator();
		while (i.hasNext()) {
			SimpleAcalEvent e = i.next();
			if (e.end < range.start.getEpoch() || e.start > range.end.getEpoch() ) i.remove();
		}
		
		return ret;
	}
	
	/** Methods required by month view */
	public synchronized ArrayList<SimpleAcalEvent> getEventsForDay(AcalDateTime day,DataRequest dr) {
		this.addDay(day, dr);
		if (!cachedEvents.containsKey(getDateHash(day))) return null;
		return cachedEvents.get(getDateHash(day));
	}

	public synchronized int getNumberEventsForDay(AcalDateTime day) {
		if (!cachedEvents.containsKey(getDateHash(day))) return 0;
		return cachedEvents.get(getDateHash(day)).size();
	}

	public synchronized SimpleAcalEvent getNthEventForDay(AcalDateTime day, int n) {
		if ( !cachedEvents.containsKey(getDateHash(day)) || n >= cachedEvents.get(getDateHash(day)).size() )
			return null;
		return cachedEvents.get(getDateHash(day)).get(n);
	}

	public synchronized void deleteEvent(AcalDateTime day, int n) {
		if ( !cachedEvents.containsKey(getDateHash(day)) || n >= cachedEvents.get(getDateHash(day)).size() )
			return;
		cachedEvents.get(getDateHash(day)).remove(n);
	}


	public void flushDay(AcalDateTime day, DataRequest dr ) {
		cachedEvents.remove(getDateHash(day));
		addDay(day,dr);
	}
	
}
