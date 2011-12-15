package com.morphoss.acal.database.resourcesmanager;

import java.util.ArrayList;

import android.content.ContentValues;

import com.morphoss.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import com.morphoss.acal.dataservice.Resource;

public class RRGetResourcesInCollection extends BlockingResourceRequestWithResponse<ArrayList<Resource>> {

	private long collectionId;
	
	public RRGetResourcesInCollection(long collectionId) {
		this.collectionId = collectionId;
	}
	@Override
	public void process(ResourceTableManager processor)	throws ResourceProcessingException {
		ArrayList<Resource> response = new ArrayList<Resource>();
		
		//DO PROCESSING HERE
		ArrayList<ContentValues> cvs = processor.query(null, ResourceTableManager.COLLECTION_ID+" = ?", new String[]{collectionId+""},
														null,null,null);
		
		for (ContentValues cv : cvs) response.add(Resource.fromContentValues(cv));
		
		this.postResponse(new RRGetResourcesInCollectionResult(response));
	}

	public class RRGetResourcesInCollectionResult extends ResourceResponse<ArrayList<Resource>> {

		private ArrayList<Resource> result;
		
		public RRGetResourcesInCollectionResult(ArrayList<Resource> result) { this.result = result; }
		
		@Override
		public ArrayList<Resource> result() {return this.result;	}
		
	}
}
