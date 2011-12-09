package com.morphoss.acal.database.resourcesmanager;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;

public class RRGetResourcesInRange implements ResourceRequest {

	private final AcalDateRange range;
	
	public RRGetResourcesInRange(AcalDateRange range) {
		this.range = range;
	}
	
	@Override
	public void process(ResourceTableManager processor) {
		// TODO Auto-generated method stub

	}

}
