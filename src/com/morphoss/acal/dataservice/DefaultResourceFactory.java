package com.morphoss.acal.dataservice;

import java.util.HashMap;

public class DefaultResourceFactory implements ResourceFactory {

	private static final HashMap<Long,Resource> resources = 
		new HashMap<Long,Resource>();
	
	
	@Override
	public Resource getInstance(long id) {
		if (resources.containsKey(id)) return resources.get(id);
		Resource instance = new DUMMYResourceInstance(0,id);
		resources.put(id, instance);
		return instance;
	}

}
