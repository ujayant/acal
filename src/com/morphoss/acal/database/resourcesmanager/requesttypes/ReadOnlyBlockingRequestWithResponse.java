package com.morphoss.acal.database.resourcesmanager.requesttypes;

import com.morphoss.acal.database.resourcesmanager.ResourceResponse;


public abstract class ReadOnlyBlockingRequestWithResponse<E> extends ReadOnlyResourceRequestWithResponse<E>   {

	private boolean processed = false;
	private ResourceResponse<E> response;
	
	public ReadOnlyBlockingRequestWithResponse() {
		super(null);
	}
	
	public ReadOnlyBlockingRequestWithResponse(int priority) {
		super(null,priority);
	}
	
	protected void postResponse(ResourceResponse<E> r) {
		this.response = r;
		this.processed = true;
	}
	
	public boolean isProcessed() { return this.processed; }
	
	public ResourceResponse<E> getResponse() {
		return this.response;
	}
}
