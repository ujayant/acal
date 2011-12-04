package com.morphoss.acal.dataservice;

import java.util.HashMap;

public class DefaultCollectionFactory implements CollectionFactory {

	private static final HashMap<Long,Collection> collections = new HashMap<Long,Collection>();

	@Override
	public Collection getInstance(long id) {
		if (collections.containsKey(id)) 
			return collections.get(id);
		//TODO get from DB
		Collection instance = new DUMMYCollectionInstance(id, 0, false);
		collections.put(id, instance);
		return instance;
	}
	
}
