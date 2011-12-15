package com.morphoss.acal.database.resourcesmanager;

import java.util.ArrayList;
import java.util.HashMap;

import com.morphoss.acal.database.DataChangeEvent;
import com.morphoss.acal.dataservice.Resource;

public class ResourceChangedEvent {

	private ArrayList<DataChangeEvent> changes;
	private HashMap<DataChangeEvent,Resource> changedResources = new HashMap<DataChangeEvent,Resource>();
	public ResourceChangedEvent(ArrayList<DataChangeEvent> changes) {
		this.changes = changes;
		for (DataChangeEvent change : changes) {
			changedResources.put(change, Resource.fromContentValues(change.getData()));
		}
	}
	public ArrayList<DataChangeEvent> getChanges() { return this.changes; }
	public Resource getResource(DataChangeEvent change) {
		return changedResources.get(change);
	}
}
