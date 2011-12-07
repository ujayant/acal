package com.morphoss.acal.dataservice;

import java.util.HashMap;

import android.content.Context;

import com.morphoss.acal.davacal.AcalCollection;

public class DefaultCollectionFactory implements CollectionFactory {

	private static final HashMap<Long,Collection> collections = new HashMap<Long,Collection>();

	@Override
	public Collection getInstance(long id) {
		if (collections.containsKey(id)) 
			return collections.get(id);
		//TODO get from DB
		Collection instance = AcalCollection.fromDatabase(id);
		collections.put(id, instance);
		return instance;
	}
	
}
