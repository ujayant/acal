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
import java.util.Map;
import java.util.TreeMap;

import android.os.RemoteException;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.davacal.AcalEvent;

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
	final static int MAX_DAYS = 180;
	
	//Maps dates to events. each event list must remain sorted.
	static final Map<Integer,ArrayList<AcalEvent>> cachedEvents = new TreeMap<Integer,ArrayList<AcalEvent>>();
	
	//A queue of requested dates, lets us know which dates to remove (oldest first);
	static final LinkedList<Integer> dateQueue = new LinkedList<Integer>();
		
	//Converts a datetime to a day hash
	static private int getDateHash(AcalDateTime day) {
		return day.getMonthDay() + (day.getMonth()*32) + (day.getYear()*32*13);
	}
	
	static private int getDateHash(int day, int month, int year) {
		return day + (month*32) + (year*32*13);
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
	@SuppressWarnings("unchecked")
	public synchronized void addDay(AcalDateTime day, DataRequest dataRequest) {
		if (cachedEvents.containsKey(getDateHash(day))) return;	//already in collection
		
		//only ensure size when adding, otherwise if MAX_DAYS is to small we could run into problems.
		ensureSize();
		//we want to get the largest 'chunk' possible. We will start with a 1 month range, but will reduce it until we get a first and last date that we haven't got.
		AcalDateTime workingDate = day.clone();
		int first = 1;
		int month = day.getMonth();
		int year = day.getYear();
		int last = workingDate.getActualMaximum(AcalDateTime.DAY_OF_MONTH);
		while (first<last) if (cachedEvents.containsKey(getDateHash(first,month,year))) first++; else break;
		while (first<last) if (cachedEvents.containsKey(getDateHash(last,month,year))) last--; else break;
		if (first > last) return;	//This should be impossible
		int originalFirst = first;
		AcalDateRange rangeToFetch = new AcalDateRange(new AcalDateTime(year,month,first,0,0,0,null), new AcalDateTime(year,month,last,23,59,59,null));
		
		//We need to convert a list with several days worth of events to a set of lists each with exactly one days events.
		try {
			ArrayList<AcalEvent> result = (ArrayList<AcalEvent>) dataRequest.getEventsForDateRange(rangeToFetch);
			for (AcalEvent re : result)
				re.dtstart.applyLocalTimeZone();
			
			if (result.isEmpty()) { 
				//put blanks in for days where there was no events
				for (int i = originalFirst; i<= last; i++) {
					int hash = this.getDateHash(i, month, year);
					if (!cachedEvents.containsKey(hash)) cachedEvents.put(hash, new ArrayList<AcalEvent>());
				}
				return;
			}	//No events
			
			//using a special comparator to avoid events being shuffled around because of timezone
			Collections.sort(result); //, new EventComparator());
			
			//For some reason we sometimes get events before the requested range.
			//int minHash = getDateHash(first,month,year);
			//for (int i = 0; i< result.size(); i++) {
			//	if (getDateHash(result.get(i).dtstart)<minHash){
			//		result.remove(i);
			//		i--;
			//	}
			//}
			
			int currentPointer = 0;
			//temp storage for multiday events
			ArrayList<AcalEvent> multiDayEvents = new ArrayList<AcalEvent>();
			
			while (currentPointer < result.size() && first <= last) {
				int curHash = getDateHash(first++,month,year);
				//The current event we are processing
				AcalEvent currentEvent = result.get(currentPointer);
				
				//The list for the current day
				ArrayList<AcalEvent> todaysEvents = new ArrayList<AcalEvent>();
				//add any previous days multi day events
				Iterator<AcalEvent> i = multiDayEvents.iterator();
				while (i.hasNext()) {
					AcalEvent event = i.next();
					todaysEvents.add(event);

					//remove multiday events that expire today. We need to subtract the 1 second
					// since event end time is non-inclusive.
					if (getDateHash(event.getEnd().addSeconds(-1)) == curHash) i.remove();
				}
				
				//while the current item has the same hash (i.e. the same day)	add it to the current list
				while (getDateHash(currentEvent.dtstart) == curHash ) {
					//check to see if multi day event, if so add to multi day list
					if ( getDateHash(currentEvent.getEnd().addSeconds(-1)) > curHash) multiDayEvents.add(currentEvent);
					todaysEvents.add(currentEvent);
					currentPointer++;
					if (currentPointer >= result.size()) break;
					currentEvent = result.get(currentPointer);
				}
				cachedEvents.put(curHash,todaysEvents);
				if (dateQueue.contains((Integer)curHash)) dateQueue.remove((Integer)curHash);	//remove item from queue if its already in there
				dateQueue.offer(curHash);	//add item to queue
			}
			//put blanks in for days where there was no events
			for (int i = originalFirst; i<= last; i++) {
				int hash = this.getDateHash(i, month, year);
				if (!cachedEvents.containsKey(hash)) cachedEvents.put(hash, new ArrayList<AcalEvent>());
			}
			
			
		} catch (RemoteException e) {
			
		}
		
	}
	
	//private class EventComparator implements Comparator<AcalEvent> {

		//@Override
		//public int compare(AcalEvent arg0, AcalEvent arg1) {
			//return arg0.dtstart- getDateHash(arg1.dtstart);
		//}
		
	//}
	
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
	public synchronized ArrayList<AcalEvent> getEventsForDays(AcalDateRange range,DataRequest dr) {
		//we need to start at 00:00 and end at 23:59:59 to ensure we include everything
		AcalDateTime current = range.start.clone();
		current.setDaySecond(0);
		AcalDateTime end = range.end.clone();
		end.addDays(1);
		end.setDaySecond(0);
		ArrayList<AcalEvent> ret = new ArrayList<AcalEvent>();
		while (!(current.after(range.end)) ) {
			this.addDay(current, dr);
			ArrayList<AcalEvent> curList = getEventsForDay(current);
			for (AcalEvent e : curList) {
				if (!ret.contains(e))ret.add(e);
			}
			current.addDays(1);
		}
		
		//we need to remove events that occured on same day as range.start but ended before range.start
		//and visaversa for range.end
		Iterator<AcalEvent> i = ret.iterator();
		while (i.hasNext()) {
			AcalEvent e = i.next();
			AcalDateRange eventRange = new AcalDateRange(e.getStart(), e.getEnd());
			if (!range.overlaps(eventRange)) i.remove();
		}
		
		return ret;
	}
	
	/** Methods required by month view */
	public synchronized ArrayList<AcalEvent> getEventsForDay(AcalDateTime day) {
		if (!cachedEvents.containsKey(getDateHash(day))) return null;
		return cachedEvents.get(getDateHash(day));
	}

	public synchronized int getNumberEventsForDay(AcalDateTime day) {
		if (!cachedEvents.containsKey(getDateHash(day))) return 0;
		return cachedEvents.get(getDateHash(day)).size();
	}

	public synchronized AcalEvent getNthEventForDay(AcalDateTime day, int n) {
		if (!cachedEvents.containsKey(getDateHash(day))) return null;
		return cachedEvents.get(getDateHash(day)).get(n);
	}

	public synchronized void deleteEvent(AcalDateTime day, int n) {
		if (!cachedEvents.containsKey(getDateHash(day))) return;
		cachedEvents.get(getDateHash(day)).remove(n);
	}
	
}
