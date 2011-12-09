package com.morphoss.acal.database.cachemanager;

import java.util.ArrayList;
import java.util.List;

import com.morphoss.acal.database.DatabaseTableManager.DMQueryList;
import com.morphoss.acal.database.DatabaseTableManager.DataChange;
import com.morphoss.acal.database.cachemanager.CacheManager.CacheTableManager;



public class CRDoResourceChanges implements CacheRequest {

	private List<DataChange> changes;
	
	public CRDoResourceChanges(List<DataChange> changes) {
		this.changes = changes;
	}
	
	@Override
	public void process(CacheTableManager processor) throws CacheProcessingException {
		DMQueryList queryList = processor.new DMQueryList();

		//step 1 - extract all resource id's and  create where clause that deletes all rows with given rid;
		ArrayList<Long> resourceIds = new ArrayList<Long>();
		
		//step 2 - ask ResourceManager for all resources in window range with given rids - BLOCK
		
		//step 3 - insert all provided resources in range
		
		//step 4 execute list
	}



}
