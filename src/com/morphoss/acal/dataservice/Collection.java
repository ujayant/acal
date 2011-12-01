package com.morphoss.acal.dataservice;

import android.os.Parcelable;

public interface Collection extends Parcelable {

	public int getColour();
	public long getCollectionId();
	public boolean alarmsEnabled();
}
