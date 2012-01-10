package com.morphoss.acal.database.alarmmanager;

import java.util.ArrayList;

import com.morphoss.acal.database.DataChangeEvent;
import com.morphoss.acal.database.alarmmanager.AlarmQueueManager.AlarmTableManager;
import com.morphoss.acal.database.alarmmanager.requesttypes.AlarmRequest;
import com.morphoss.acal.database.resourcesmanager.ResourceChangedEvent;

public class ARResourceChanged implements AlarmRequest {

	private ResourceChangedEvent event;
	
	public ARResourceChanged(ResourceChangedEvent event) {
		this.event = event;
	}

	@Override
	public void process(AlarmTableManager processor) throws AlarmProcessingException {
		processor.processChanges(event.getChanges());
	}

}
