package com.morphoss.acal.database.cachemanager.requests;

import com.morphoss.acal.database.cachemanager.CacheManager;
import com.morphoss.acal.database.cachemanager.CacheProcessingException;
import com.morphoss.acal.database.cachemanager.CacheRequest;
import com.morphoss.acal.database.cachemanager.CacheManager.CacheTableManager;

public class CRClearCacheRequest implements CacheRequest {

	@Override
	public void process(CacheTableManager processor) throws CacheProcessingException {
		processor.rebuildCache();
	}

}
