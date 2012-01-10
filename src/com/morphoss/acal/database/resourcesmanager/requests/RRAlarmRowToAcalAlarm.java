package com.morphoss.acal.database.resourcesmanager.requests;

import com.morphoss.acal.database.alarmmanager.AlarmRow;
import com.morphoss.acal.database.resourcesmanager.ResourceProcessingException;
import com.morphoss.acal.database.resourcesmanager.ResourceResponse;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import com.morphoss.acal.database.resourcesmanager.requesttypes.BlockingResourceRequestWithResponse;
import com.morphoss.acal.davacal.AcalAlarm;

public class RRAlarmRowToAcalAlarm extends BlockingResourceRequestWithResponse<AcalAlarm> {

	private AlarmRow row;
	public RRAlarmRowToAcalAlarm(AlarmRow row) {
		this.row = row;
	}
	
	@Override
	public void process(WriteableResourceTableManager processor) throws ResourceProcessingException {
		// TODO Auto-generated method stub
		
	}
	
	public class RRAlarmRowToAcalAlarmResponse extends ResourceResponse<AcalAlarm> {

		private AcalAlarm result;
		
		public RRAlarmRowToAcalAlarmResponse (AcalAlarm result) {
			this.result = result;
		}
		
		@Override
		public AcalAlarm result() {
			return result;
		}
		
	}

}
