package com.morphoss.acal.database.resourcesmanager;

public interface ResourceRequest {

	public void process(ResourceManager.ResourceTableManager processor) throws ResourceProcessingException;

}