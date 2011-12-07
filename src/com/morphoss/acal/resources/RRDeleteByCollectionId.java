package com.morphoss.acal.resources;

import java.util.ArrayList;

import com.morphoss.acal.resources.ResourcesManager.RequestProcessor;

public class RRDeleteByCollectionId implements ResourcesRequest {

	private ArrayList<Integer> ids;
	
	public RRDeleteByCollectionId(ArrayList<Integer> ids) {
		this.ids =ids;
	}
	
	@Override
	public void process(RequestProcessor processor)	throws ResourceProccessingException {
		for (int id : ids) processor.deleteByCollectionId(id);
	}

}
