package com.morphoss.acal.dataservice;

import android.os.Parcelable;


public interface Resource extends Parcelable {

	public long getResourceId();
	public Collection getCollection();
}
