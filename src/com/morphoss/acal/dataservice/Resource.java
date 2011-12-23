package com.morphoss.acal.dataservice;

import android.content.ContentValues;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.morphoss.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;


public class Resource implements Parcelable {

	private static final String TAG = "acal Resource";
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
	private boolean pending;
	
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
	
	public ContentValues toContentValues() {
		ContentValues cv = new ContentValues();
		cv.put(ResourceTableManager.RESOURCE_ID,resourceId);
		cv.put(ResourceTableManager.COLLECTION_ID,collectionId);
		cv.put(ResourceTableManager.RESOURCE_NAME,name);
		cv.put(ResourceTableManager.ETAG,etag);
		cv.put(ResourceTableManager.CONTENT_TYPE,contentType);
		cv.put(ResourceTableManager.RESOURCE_DATA,data);
		cv.put(ResourceTableManager.NEEDS_SYNC,needsSync);
		cv.put(ResourceTableManager.EARLIEST_START, earliestStart);
		cv.put(ResourceTableManager.LATEST_END, latestEnd);
		cv.put(ResourceTableManager.EFFECTIVE_TYPE, effectiveType);
		return cv;
	}
	public static Resource fromContentValues(ContentValues cv) {
		long rid = -1;
		boolean pending = false;
		String blob = null;
		long es = 0;
		long le = 0;
		boolean needsSync = false;
		String effectiveType = "";
		if (cv.containsKey(ResourceTableManager.PEND_RESOURCE_ID)) {
			rid = cv.getAsLong(ResourceTableManager.PEND_RESOURCE_ID);
			blob = cv.getAsString(ResourceTableManager.NEW_DATA);
			if (blob == null || blob.equals("")) throw new IllegalArgumentException("Can not create resource out of pending deleted.");
			pending = true;
		}
		else if (cv.containsKey(ResourceTableManager.RESOURCE_ID)) {
			try {
				rid = cv.getAsLong(ResourceTableManager.RESOURCE_ID);
				blob = cv.getAsString(ResourceTableManager.RESOURCE_DATA);
				if (cv.containsKey(ResourceTableManager.EARLIEST_START));
					es = cv.getAsLong(ResourceTableManager.EARLIEST_START);
				if (cv.containsKey(ResourceTableManager.LATEST_END) && cv.getAsLong(ResourceTableManager.LATEST_END) != null);
					le = cv.getAsLong(ResourceTableManager.LATEST_END);
				needsSync = cv.getAsBoolean(ResourceTableManager.NEEDS_SYNC);// == 1;
				effectiveType = cv.getAsString(ResourceTableManager.EFFECTIVE_TYPE);
			} catch (Exception e) { Log.d(TAG,"Error in Resource: "+e+Log.getStackTraceString(e)); }
		} else throw new IllegalArgumentException("Resource ID Required");
				
		
		
		return new Resource(
				cv.getAsLong(ResourceTableManager.COLLECTION_ID),
				rid,
				cv.getAsString(ResourceTableManager.RESOURCE_NAME),
				cv.getAsString(ResourceTableManager.ETAG),
				cv.getAsString(ResourceTableManager.CONTENT_TYPE),
				blob,
				needsSync,
				es,
				le,
				effectiveType,
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

	public void setPending(boolean b) {
		this.pending = b;
	}
}
