package com.morphoss.acal.database.alarmmanager;

import com.morphoss.acal.database.alarmmanager.AlarmQueueManager.AlarmTableManager;
import com.morphoss.acal.database.alarmmanager.requesttypes.AlarmRequest;

public class ARRebuildRequest implements AlarmRequest {

	@Override
	public void process(AlarmTableManager processor) throws AlarmProcessingException {
		processor.rebuild();
	}

}
