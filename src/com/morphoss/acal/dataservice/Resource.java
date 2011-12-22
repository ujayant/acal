package com.morphoss.acal.dataservice;

import android.content.ContentValues;
import android.os.Parcel;
import android.os.Parcelable;

import com.morphoss.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;


public class Resource implements Parcelable {

	//This class is immutable!
	private final long collectionId;
	private final long resourceId;
	private final String name;
	private final String etag;
	private String contentType;
	private String data;
	private boolean needsSync;
	private final long earliestStart;
	private final long latestEnd;
	private final String effectiveType;
	private final boolean pending;
	
	public Resource(long cid, long rid, String name, String etag, String cType, 
			String data, boolean sync, Long earliestStart, Long latestEnd, String eType, boolean pending) {
		this.collectionId = cid;
		this.resourceId = rid;
		this.name = name;
		this.etag = etag;
		this.contentType = cType;
		this.data = data;
		this.needsSync = sync;
		if (earliestStart == null) earliestStart = Long.MIN_VALUE;
		this.earliestStart = earliestStart;
		if (latestEnd == null) latestEnd = Long.MAX_VALUE;
		this.latestEnd = latestEnd;
		this.effectiveType = eType;
		this.pending = pending;
	}
	
	public Resource(Parcel in) {
		collectionId = in.readLong();
		resourceId = in.readLong();
		this.name = in.readString();
		this.etag = in.readString();
		this.contentType = in.readString();
		this.data = in.readString();
		this.needsSync = in.readByte() == 'T';
		this.earliestStart = in.readLong();
		this.latestEnd = in.readLong();
		this.effectiveType = in.readString();
		this.pending = in.readByte() == 'T';
	}
	
	public long getCollectionId() {
		return this.collectionId;
	}

	public long getResourceId() {
		return this.resourceId;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(collectionId);
		dest.writeLong(resourceId);
		dest.writeString(name);
		dest.writeString(etag);
		dest.writeString(contentType);
		dest.writeString(data);
		dest.writeByte(this.needsSync ? (byte)'T' : (byte)'F');
		dest.writeLong(earliestStart);
		dest.writeLong(latestEnd);
		dest.writeString(effectiveType);
		dest.writeByte(this.pending ? (byte)'T' : (byte)'F');
	}

	public String getBlob() {
		return this.data;
	}

	public String getEtag() {
		return this.etag;
	}
	
	public static Resource fromContentValues(ContentValues cv) {
		long rid = -1;
		if (cv.containsKey(ResourceTableManager.RESOURCE_ID)) rid = cv.getAsLong(ResourceTableManager.RESOURCE_ID);
		boolean pending = false;
		if (cv.containsKey(ResourceTableManager.IS_PENDING)) pending = cv.getAsBoolean(ResourceTableManager.IS_PENDING);
		return new Resource(
				cv.getAsLong(ResourceTableManager.COLLECTION_ID),
				rid,
				cv.getAsString(ResourceTableManager.RESOURCE_NAME),
				cv.getAsString(ResourceTableManager.ETAG),
				cv.getAsString(ResourceTableManager.CONTENT_TYPE),
				cv.getAsString(ResourceTableManager.RESOURCE_DATA),
				cv.getAsInteger(ResourceTableManager.NEEDS_SYNC) == 1,
				cv.getAsLong(ResourceTableManager.EARLIEST_START),
				cv.getAsLong(ResourceTableManager.LATEST_END),
				cv.getAsString(ResourceTableManager.EFFECTIVE_TYPE),
				pending
		);
		
	}

	public boolean isPending() {
		return this.pending;
	}

	public Long getEarliestStart() {
		return earliestStart;
	}

	public Long getLatestEnd() {
		return latestEnd;
	}
}
