package com.morphoss.acal.resources;

public interface ResourcesRequest {

	public void process(ResourcesManager.RequestProcessor processor) throws ResourceProccessingException;

}