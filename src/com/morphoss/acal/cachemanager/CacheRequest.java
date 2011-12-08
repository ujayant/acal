package com.morphoss.acal.cachemanager;

import com.morphoss.acal.cachemanager.CacheManager.EventCacheProcessor;

/**
 * This is the generic interface for CacheRequests.
 * @author Chris Noldus
 *
 */
public interface CacheRequest {
	
	public void process(EventCacheProcessor processor) throws CacheProcessingException;
	
}