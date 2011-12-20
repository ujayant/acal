package com.morphoss.acal.database.cachemanager;

import android.util.Log;

import com.morphoss.acal.Constants;
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
		if ( CacheManager.DEBUG ) Log.println(Constants.LOGD, TAG, "Processing query set");
		queries.process(processor);
		if ( CacheManager.DEBUG ) Log.println(Constants.LOGD, TAG,"Done");
	}

}
