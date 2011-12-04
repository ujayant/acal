package com.morphoss.acal.dataservice;

import android.os.Parcelable;

public abstract class Collection implements Parcelable {

	public abstract int getColour();
	public abstract long getCollectionId();
	public abstract boolean alarmsEnabled();
	
	public static Collection getInstance(CollectionFactory cf, long id) {
		return cf.getInstance(id);
	}
}
