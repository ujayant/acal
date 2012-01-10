package com.morphoss.acal.database.alarmmanager;

import com.morphoss.acal.database.alarmmanager.AlarmQueueManager.AlarmTableManager;
import com.morphoss.acal.database.alarmmanager.requesttypes.AlarmResponse;
import com.morphoss.acal.database.alarmmanager.requesttypes.BlockingAlarmRequestWithResponse;

public class ARGetNextAlarm extends BlockingAlarmRequestWithResponse<AlarmRow> {

	@Override
	public void process(AlarmTableManager processor) throws AlarmProcessingException {
		
		this.postResponse(new ARGetNextAlarmResult(processor.getNextDueAlarm()));
	}
	
	public class ARGetNextAlarmResult extends AlarmResponse<AlarmRow> {

		private AlarmRow result;
		
		public ARGetNextAlarmResult(AlarmRow result) { 
			this.result = result;
		}
		
		@Override
		public AlarmRow result() {return this.result;	}
		
	}
}
