package com.morphoss.acal.database.resourcesmanager;

import java.util.List;

import com.morphoss.acal.database.DatabaseTableManager.DataChange;

public class ResourceChangedEvent {

	private List<DataChange> changes;
	public ResourceChangedEvent(List<DataChange> changes) {
		this.changes = changes;
	}
	public List<DataChange> getChanges() { return this.changes; }
}
