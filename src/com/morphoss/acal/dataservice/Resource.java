package com.morphoss.acal.dataservice;

import android.os.Parcelable;


public interface Resource extends Parcelable {

	public abstract long getResourceId();
	public abstract long getCollectionId();
	public abstract String getBlob();
	public abstract String getEtag();
	public abstract boolean isPending();
	
}
