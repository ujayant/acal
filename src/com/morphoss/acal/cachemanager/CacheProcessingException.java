package com.morphoss.acal.cachemanager;

public class CacheProcessingException extends Exception {
	
	public CacheProcessingException() {
		super();
	}
	
	public CacheProcessingException(String string) {
		super(string);
	}
	
	public CacheProcessingException(String string, Throwable cause) {
		super(cause);
	}
	
	public CacheProcessingException(Throwable cause) {
		super(cause);
	}

	private static final long serialVersionUID = 1L;
}
