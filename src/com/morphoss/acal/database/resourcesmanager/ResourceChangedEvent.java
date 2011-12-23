package com.morphoss.acal.database.resourcesmanager;

import java.util.ArrayList;
import java.util.HashMap;

import com.morphoss.acal.database.DataChangeEvent;
import com.morphoss.acal.database.DatabaseTableManager.QUERY_ACTION;
import com.morphoss.acal.dataservice.Resource;

public class ResourceChangedEvent {

	private ArrayList<DataChangeEvent> changes;
	private HashMap<DataChangeEvent,Resource> changedResources = new HashMap<DataChangeEvent,Resource>();
	
	
	public ResourceChangedEvent(ArrayList<DataChangeEvent> changes) {
		this.changes = changes;
		for (DataChangeEvent change : changes) {
			Resource r = Resource.fromContentValues(change.getData());
			if (change.action == QUERY_ACTION.PENDING_RESOURCE) r.setPending(true);
			changedResources.put(change,r);
		}
	}
	public ArrayList<DataChangeEvent> getChanges() { return this.changes; }
	public Resource getResource(DataChangeEvent change) {
		return changedResources.get(change);
	}
}
