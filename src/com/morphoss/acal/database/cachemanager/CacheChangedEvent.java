package com.morphoss.acal.database.cachemanager;

import java.util.ArrayList;

import com.morphoss.acal.database.DataChangeEvent;

public class CacheChangedEvent {

	private ArrayList<DataChangeEvent> changes;
	
	public CacheChangedEvent(ArrayList<DataChangeEvent> changes) {
		this.changes = changes;
	}
	
	public ArrayList<DataChangeEvent> getChanges() { return this.changes; }
}
