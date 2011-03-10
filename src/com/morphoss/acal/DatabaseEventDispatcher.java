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

package com.morphoss.acal;

import java.util.ArrayList;

public class DatabaseEventDispatcher {
	
	private ArrayList<DatabaseEventListener> listeners = new ArrayList<DatabaseEventListener>();
	
	public void dispatchEvent(DatabaseChangedEvent event) {
		for (DatabaseEventListener del : listeners) {
			del.databaseChanged(event);
		}
	}
	
	public void addListener(DatabaseEventListener dl) {
		listeners.add(dl);
	}
	public void removeListener(DatabaseEventListener dl) {
		listeners.add(dl);
	}
}
