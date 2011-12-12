package com.morphoss.acal.database.cachemanager;

import java.util.List;

import com.morphoss.acal.database.DatabaseTableManager.DataChangeEvent;

public class CacheChangedEvent {

	private List<DataChangeEvent> changes;
	
	public CacheChangedEvent(List<DataChangeEvent> changes) {
		this.changes = changes;
	}
	
	public List<DataChangeEvent> getChanges() { return this.changes; }
}
