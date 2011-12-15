package com.morphoss.acal.database.resourcesmanager.requests;

import java.util.ArrayList;

import com.morphoss.acal.database.resourcesmanager.ResourceManager;
import com.morphoss.acal.database.resourcesmanager.ResourceProcessingException;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import com.morphoss.acal.database.resourcesmanager.requesttypes.ResourceRequest;

public class RRDeleteByCollectionId implements ResourceRequest {

	private ArrayList<Integer> ids;
	
	public RRDeleteByCollectionId(ArrayList<Integer> ids) {
		this.ids =ids;
	}
	
	@Override
	public void process(WriteableResourceTableManager processor)	throws ResourceProcessingException {
		for (int id : ids) processor.deleteByCollectionId(id);
	}

}
