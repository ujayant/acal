package com.morphoss.acal.database.alarmmanager;

import com.morphoss.acal.database.alarmmanager.AlarmQueueManager.AlarmTableManager;
import com.morphoss.acal.database.alarmmanager.requesttypes.BlockingAlarmRequest;


public class ARUpdateAlarmState implements BlockingAlarmRequest {

	private boolean processed = false;
	private AlarmRow row;
	private ALARM_STATE newState;
	
	public ARUpdateAlarmState(AlarmRow row, ALARM_STATE newState) {
		this.row = row;
		this.newState = newState;
	}
	
	@Override
	public boolean isProcessed() {
		return processed;
	}

	@Override
	public void process(AlarmTableManager processor) throws AlarmProcessingException {
		processor.updateAlarmState(row, newState);
	}

}
