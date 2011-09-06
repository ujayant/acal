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



public class VTodo extends Masterable {
	public static final String TAG = "aCal VTodo";

	public static enum TODO_FIELD {
			resourceId,
			summary,
			collectionId,
			repeatRule,
			alarmList,
			dueDate,
			percentComplete,
			status
	}
	
	public VTodo(ComponentParts splitter, Integer resourceId, AcalCollection collectionObject,VComponent parent) {
		super(splitter, resourceId, collectionObject,parent);
	}

	public VTodo( VCalendar parent ) {
		super(VComponent.VTODO, parent );
	}

	public VTodo( AcalCollection collection ) {
		this( new VCalendar(collection) );
	}

}
