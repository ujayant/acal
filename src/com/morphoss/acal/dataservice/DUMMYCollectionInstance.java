package com.morphoss.acal.dataservice;

import java.util.HashMap;

import android.os.Parcel;

public class DUMMYCollectionInstance extends Collection {


	private final long id;
	private final int color;
	private final boolean alarmsEnabled;

	public DUMMYCollectionInstance(long id, int color, boolean alarmsEnabled) {
		this.id = id;
		this.color = color;
		this.alarmsEnabled = alarmsEnabled;
	}

	@Override
	public long getCollectionId() {
		return this.id;
	}

	@Override
	public int getColour() {
		return this.color;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(id);
		dest.writeInt(color);
		dest.writeInt(alarmsEnabled ? 1 : 2);		
	}

	@Override
	public boolean alarmsEnabled() {
		// TODO Auto-generated method stub
		return false;
	}

	public static DUMMYCollectionInstance getInstance(Object ... params) {
		//TODO - DELETE THIS METHOD
		return new DUMMYCollectionInstance(0,0,false);
	}
}
