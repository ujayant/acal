package com.morphoss.acal.resources;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.resources.ResourcesManager.RequestProcessor;

public class RRGetResourcesInRange implements ResourcesRequest {

	private final AcalDateRange range;
	
	public RRGetResourcesInRange(AcalDateRange range) {
		this.range = range;
	}
	
	@Override
	public void process(RequestProcessor processor) {
		// TODO Auto-generated method stub

	}

}
