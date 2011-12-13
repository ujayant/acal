package com.morphoss.acal.database.cachemanager;

import android.util.Log;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.database.DatabaseTableManager.DMQueryList;
import com.morphoss.acal.database.cachemanager.CacheManager.CacheTableManager;


public class CRAddRangeResult implements CacheRequest {

	private DMQueryList queries;
	private AcalDateRange range;
	public static final String TAG = "aCal CRAddRangeResult";
	
	public CRAddRangeResult(DMQueryList queries, AcalDateRange range) {
		this.queries = queries;
		this.range = range;
	}
	
	@Override
	public void process(CacheTableManager processor) throws CacheProcessingException {
		Log.d(TAG, "Processing query set and updating window");
		queries.process(processor);
		processor.updateWindowToInclude(range);
		Log.d(TAG,"Done");
	}

	

}
