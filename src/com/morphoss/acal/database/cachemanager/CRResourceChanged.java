package com.morphoss.acal.database.cachemanager;

import android.util.Log;

import com.morphoss.acal.database.DatabaseTableManager.DMQueryList;
import com.morphoss.acal.database.cachemanager.CacheManager.CacheTableManager;

public class CRResourceChanged implements CacheRequest {

	private DMQueryList queries;
	public static final String TAG = "aCal CRResourceChanged";
	
	public CRResourceChanged(DMQueryList queries) {
		this.queries = queries;
	}
	
	@Override
	public void process(CacheTableManager processor) throws CacheProcessingException {
		Log.d(TAG, "Processing query set");
		queries.process(processor);
		Log.d(TAG,"Done");
	}

}
