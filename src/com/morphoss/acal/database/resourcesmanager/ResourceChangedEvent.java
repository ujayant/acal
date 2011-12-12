package com.morphoss.acal.database.resourcesmanager;

import java.util.HashMap;
import java.util.List;

import com.morphoss.acal.database.DatabaseTableManager.DataChangeEvent;
import com.morphoss.acal.dataservice.DefaultResourceInstance;
import com.morphoss.acal.dataservice.Resource;

public class ResourceChangedEvent {

	private List<DataChangeEvent> changes;
	private HashMap<DataChangeEvent,Resource> changedResources = new HashMap<DataChangeEvent,Resource>();
	public ResourceChangedEvent(List<DataChangeEvent> changes) {
		this.changes = changes;
		for (DataChangeEvent change : changes) {
			changedResources.put(change, DefaultResourceInstance.fromContentValues(change.getData()));
		}
	}
	public List<DataChangeEvent> getChanges() { return this.changes; }
	public Resource getResource(DataChangeEvent change) {
		return changedResources.get(change);
	}
}
