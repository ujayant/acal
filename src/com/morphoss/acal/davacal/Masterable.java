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

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.acaltime.AcalRepeatRule;

public abstract class Masterable extends VComponent {
	
	protected Masterable(ComponentParts splitter, Integer resourceId, AcalCollection collectionObject, VComponent parent) {
		super(splitter,resourceId,collectionObject,parent);
	}

	protected Masterable(String typeName, AcalCollection collectionObject, VComponent parent) {
		super(typeName,collectionObject,parent);
	}

	public void addAlarmTimes( List<AcalAlarm> alarmList, AcalDateRange instanceRange ) {
		for( VComponent child : this.getChildren() ) {
			if ( child instanceof VAlarm )
				alarmList.add(new AcalAlarm((VAlarm) child, this, instanceRange.start, instanceRange.end));
		}
	}
	
	public AcalDuration getDuration() {
		AcalDuration ret = null;

		AcalProperty dProp = getProperty("DTSTART");
		if ( dProp == null ) return new AcalDuration();

		AcalDateTime dtstart = AcalDateTime.fromIcalendar(dProp.getValue(),
													dProp.getParam("VALUE"), dProp.getParam("TZID"));
		dProp = getProperty("DURATION");
		if ( dProp != null ) {
			ret = AcalDuration.fromProperty(dProp);
			if ( Constants.debugRepeatRule && Constants.LOG_VERBOSE ) Log.v(AcalRepeatRule.TAG,"Event Duration from DURATION is " + ret.toString() );
		}
		else {
			dProp = getProperty("DTEND");
			if ( dProp == null ) dProp = getProperty("DUE"); // VTodo
			if ( dProp != null ) {
				ret = dtstart.getDurationTo(AcalDateTime.fromIcalendar(dProp.getValue(),
							dProp.getParam("VALUE"), dProp.getParam("TZID")));
				if ( Constants.debugRepeatRule && Constants.LOG_VERBOSE ) Log.v(AcalRepeatRule.TAG,"Event Duration from DTEND/DUE is " + ret.toString() );
			}
			else {
				ret = new AcalDuration();
				ret.setDuration( (dtstart.isDate() ? 1 : 0), 0 );
			}
		}

		return ret;
	}


	/**
	 * Given a List of AcalAlarm's, make this Masterable have those as child components.
	 * @param alarmList the list of alarms.
	 */
	public void updateAlarmComponents( List<?> alarmList ) {
		if ( alarmList != null && alarmList.size() > 0 ) {
			try {
				this.setPersistentOn();
			}
			catch (YouMustSurroundThisMethodInTryCatchOrIllEatYouException e) {
				// What a silly name.  We're ignoring it because we are actually
				// modifying the event here, presumably ultimately to save it,
				// but regardless, we will never decrement that persistence counter,
				// so there, mister!
			}
			populateChildren();
			List<VComponent> children = getChildren();
			Iterator<VComponent> it = children.iterator();
			while( it.hasNext() ) {
				VComponent child = it.next();
				if ( child instanceof VAlarm ) it.remove();
			}
			for( Object alarm : alarmList ) {
				if ( alarm instanceof AcalAlarm )
					addChild(((AcalAlarm) alarm).getVAlarm(this));
			}
		}
	}
}
