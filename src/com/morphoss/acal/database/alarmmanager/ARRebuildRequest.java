package com.morphoss.acal.database.alarmmanager;

import com.morphoss.acal.database.alarmmanager.AlarmQueueManager.AlarmTableManager;

public class ARRebuildRequest implements AlarmRequest {

	@Override
	public void process(AlarmTableManager processor) throws AlarmProcessingException {
		processor.rebuild();
	}

}
