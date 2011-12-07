package com.morphoss.acal.resources;


public class ResourceProccessingException extends Exception {

	public ResourceProccessingException() {
		super();
	}
	
	public ResourceProccessingException(String string) {
		super(string);
	}
	
	public ResourceProccessingException(String string, Throwable cause) {
		super(cause);
	}
	
	public ResourceProccessingException(Throwable cause) {
		super(cause);
	}

	private static final long serialVersionUID = 1L;

}
