package com.morphoss.acal.dataservice;

import android.os.Parcelable;


public abstract class Resource implements Parcelable {

	public abstract long getResourceId();
	public abstract Collection getCollection();
	
	public static final Resource getInstance(ResourceFactory rf, long id) {
		return rf.getInstance(id);
	}
}
