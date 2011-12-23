package com.morphoss.acal.database.cachemanager;

import com.morphoss.acal.database.cachemanager.CacheManager.CacheTableManager;

public class CRClearCacheRequest implements CacheRequest {

	@Override
	public void process(CacheTableManager processor) throws CacheProcessingException {
		processor.rebuildCache();
	}

}
