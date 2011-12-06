package com.morphoss.acal.cachemanager;


public interface CacheResponseListener {

	public void cacheResponse(CacheResponse response);
	
	public class CacheResponse {
		public final Object data;
		public final int requestType;
		
		public CacheResponse(Object data, int requestType) {
			this.data = data;
			this.requestType = requestType;
		}
	}
}
