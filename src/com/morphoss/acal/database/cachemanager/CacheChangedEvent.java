package com.morphoss.acal.database.cachemanager;

import java.util.List;

import com.morphoss.acal.database.DatabaseTableManager.DataChange;

public class CacheChangedEvent {

	private List<DataChange> changes;
	
	public CacheChangedEvent(List<DataChange> changes) {
		this.changes = changes;
	}
	
	public List<DataChange> getChanges() { return this.changes; }
}
