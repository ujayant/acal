package com.morphoss.acal.dataservice;

import android.content.ContentValues;
import android.os.Parcel;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;

public class DefaultResourceInstance implements Resource {
	
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

	public DefaultResourceInstance (long cid, long rid, String name, String etag, String cType, 
			String data, boolean sync, Long earliestStart, Long latestEnd, String eType) {
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
	}
	
	public DefaultResourceInstance(Parcel in) {
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
	}
	
	@Override
	public long getCollectionId() {
		return this.collectionId;
	}

	@Override
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
	}

	@Override
	public String getBlob() {
		return this.data;
	}

	@Override
	public String getEtag() {
		return this.etag;
	}
	
	public static Resource fromContentValues(ContentValues cv) {
		long rid = -1;
		if (cv.containsKey(ResourceTableManager.RESOURCE_ID)) rid = cv.getAsLong(ResourceTableManager.RESOURCE_ID);
		return new DefaultResourceInstance(
				cv.getAsLong(ResourceTableManager.COLLECTION_ID),
				rid,
				cv.getAsString(ResourceTableManager.RESOURCE_NAME),
				cv.getAsString(ResourceTableManager.ETAG),
				cv.getAsString(ResourceTableManager.CONTENT_TYPE),
				cv.getAsString(ResourceTableManager.RESOURCE_DATA),
				cv.getAsInteger(ResourceTableManager.NEEDS_SYNC) == 1,
				cv.getAsLong(ResourceTableManager.EARLIEST_START),
				cv.getAsLong(ResourceTableManager.LATEST_END),
				cv.getAsString(ResourceTableManager.EFFECTIVE_TYPE)
		);
	
		
		
		
		
		
	}
}
