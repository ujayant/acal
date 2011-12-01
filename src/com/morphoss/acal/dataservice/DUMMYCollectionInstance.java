package com.morphoss.acal.dataservice;

import android.os.Parcel;

public class DUMMYCollectionInstance implements Collection {

	@Override
	public long getCollectionId() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getColour() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		// TODO Auto-generated method stub

	}
	
	public static DUMMYCollectionInstance getInstance(Object ... params) {
		return new DUMMYCollectionInstance();
	}

}
