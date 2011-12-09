package com.morphoss.acal.database.resourcesmanager;

import java.util.ArrayList;

import com.morphoss.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;

public class RRDeleteByCollectionId implements ResourceRequest {

	private ArrayList<Integer> ids;
	
	public RRDeleteByCollectionId(ArrayList<Integer> ids) {
		this.ids =ids;
	}
	
	@Override
	public void process(ResourceTableManager processor)	throws ResourceProcessingException {
		for (int id : ids) processor.deleteByCollectionId(id);
	}

}
