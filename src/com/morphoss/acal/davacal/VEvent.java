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

import com.morphoss.acal.dataservice.EventInstance;
import com.morphoss.acal.dataservice.Resource;


public class VEvent extends Masterable {
	public static final String TAG = "aCal VEvent";

	public VEvent(ComponentParts splitter, VComponent parent) {
		super(splitter, parent);
	}

	public VEvent( VCalendar parent ) {
		super( VComponent.VEVENT, parent );
	}
	
	public VEvent() {
		this( new VCalendar() );
	}

	@Deprecated
	public VEvent(ComponentParts splitter, Resource r, VComponent parent) {
		super(splitter, r, parent);
	}

	@Deprecated
	public VEvent( long collectionId ) {
		this( new VCalendar(collectionId) );
	}

	private VEvent fromMasterEvent( VEvent master ) {
		// Isn't this a clone?
		return (VEvent) VComponent.createComponentFromBlob(master.getCurrentBlob());
	}
	
	public static VEvent createComponentFromInstance(EventInstance event) {
		VEvent instance = new VEvent();
		if ( event.getStart() != null ) instance.setStart(event.getStart());
		if ( event.getEnd() != null )	instance.setEnd(event.getEnd());
		if ( event.getSummary() != null ) instance.setSummary(event.getSummary());
		if ( event.getDescription() != null ) instance.setDescription(event.getDescription());
		if ( event.getRRule() != null ) instance.setRepetition(event.getRRule());
		if ( event.getLocation() != null ) instance.setLocation(event.getLocation());
		if ( !event.getAlarms().isEmpty() ) instance.addAlarmTimes(event.getAlarms(), null);

		return instance;
	}
	
}
