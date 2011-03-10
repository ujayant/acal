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

import java.util.TimeZone;
import java.util.UUID;

import com.morphoss.acal.acaltime.AcalDateTime;

public class VEvent extends Masterable {
	public static final String TAG = "aCal VEvent";

	public VEvent(ComponentParts splitter, Integer resourceId, AcalCollection collectionObject,VComponent parent) {
		super(splitter, resourceId, collectionObject,parent);
	}

	public VEvent( VCalendar parent ) {
		super( "VEVENT", parent.collectionData, parent );
		try { setPersistentOn(); } catch (YouMustSurroundThisMethodInTryCatchOrIllEatYouException e) { }
		addProperty(new AcalProperty("UID",UUID.randomUUID().toString()));
		AcalDateTime creation = new AcalDateTime();
		creation.setTimeZone(TimeZone.getDefault().getID());
		creation.shiftTimeZone("UTC");
		addProperty(new AcalProperty("DTSTAMP",creation.fmtIcal()));
		addProperty(new AcalProperty("CREATED",creation.fmtIcal()));
		addProperty(new AcalProperty("LAST-MODIFIED",creation.fmtIcal()));
	}

	public VEvent fromMasterEvent( VEvent master ) {
		return (VEvent) VComponent.createComponentFromBlob(master.getCurrentBlob(),
															master.resourceId, master.collectionData);
	}
}
