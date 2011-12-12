package com.morphoss.acal.dataservice;

import android.os.Parcelable;

public abstract class Collection implements Parcelable {

	public abstract int getColour();
	public abstract long getCollectionId();
	public abstract boolean alarmsEnabled();
	public abstract String getDisplayName();
}
