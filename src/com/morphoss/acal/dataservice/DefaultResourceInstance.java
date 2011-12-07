package com.morphoss.acal.dataservice;

import android.content.ContentValues;
import android.content.Context;
import android.os.Parcel;

public class DefaultResourceInstance implements Resource {
	
	private final long collectionId;
	private final long resourceId;

	public DefaultResourceInstance (long cid, long rid) {
		this.collectionId = cid;
		this.resourceId = rid;
	}
	
	public DefaultResourceInstance(Parcel in) {
		collectionId = in.readLong();
		resourceId = in.readLong();
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

	@Override
	public String getBlob() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getEtag() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public static Resource fromContentValues(ContentValues cv) {
		//TODO
		return null;
	}
}
