package com.morphoss.acal.dataservice;

import java.util.HashMap;

import android.os.Parcel;

public class DefaultResourceInstance extends Resource {
	
	private final long collectionId;
	private final long resourceId;

	public DefaultResourceInstance (long cid, long rid) {
		this.collectionId = cid;
		this.resourceId = rid;
	}
	
	@Override
	public Collection getCollection() {
		return Collection.getInstance(new DefaultCollectionFactory(), this.collectionId);
	}

	@Override
	public long getResourceId() {
		return this.resourceId;
	}

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(collectionId);
		dest.writeLong(resourceId);
	}
}
