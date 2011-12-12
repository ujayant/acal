package com.morphoss.acal.dataservice;

import java.util.HashMap;

import android.content.Context;

public class DefaultCollectionFactory implements CollectionFactory {

	private static final HashMap<Long,Collection> collections = new HashMap<Long,Collection>();

	@Override
	public Collection getInstance(long id, Context context) {
		if (collections.containsKey(id)) 
			return collections.get(id);
		//TODO get from DB
		Collection instance = DefaultCollectionInstance.fromDatabase(id,context);
		collections.put(id, instance);
		return instance;
	}
	
}
