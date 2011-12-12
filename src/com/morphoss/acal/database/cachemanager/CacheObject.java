package com.morphoss.acal.database.cachemanager;

import android.content.ContentValues;
import android.os.Parcel;
import android.os.Parcelable;

import com.morphoss.acal.database.cachemanager.CacheManager.CacheTableManager;
import com.morphoss.acal.dataservice.EventInstance;

/**
 * Represents a single row in the cache
 * @author Chris Noldus
 *
 */
public class CacheObject implements Parcelable {
	private final long rid;
	private final long cid;
	private final String summary;
	private final String location;
	private final long start;
	private final long end;
	private final int flags;
	
	public static final int EVENT_FLAG = 			1;
	public static final int TODO_FLAG = 			1<<1;
	public static final int HAS_ALARM_FLAG = 		1<<2;
	public static final int RECURS_FLAG =			1<<3;
	public static final int DIRTY_FLAG = 			1<<4;
	public static final int START_IS_FLOATING_FLAG =1<<5;
	public static final int END_IS_FLOATING_FLAG = 	1<<6;
	public static final int FLAG_ALL_DAY = 			1<<7;
	
	public enum TYPE { EVENT, TODO };
	
	public CacheObject(long rid, long cid, String sum, String loc, long st, long end, int flags) {
		this.rid = rid;
		this.cid = cid;
		this.summary = sum;
		this.location= loc;
		this.start = st;
		this.end = end;
		this.flags = flags;;
	}
	
	private CacheObject(Parcel in) {
		rid = in.readLong();
		cid = in.readLong();
		summary = in.readString();
		location = in.readString();
		start = in.readLong();
		end = in.readLong();
		flags = in.readInt();

	}
	
	/**
	 * 
	 * @return
	 */
	public EventInstance getFullEventInstance() {
		//TODO
		return null;
	}

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(rid);
		dest.writeLong(cid);
		dest.writeString(summary);
		dest.writeString(location);
		dest.writeLong(start);
		dest.writeLong(end);
		dest.writeInt(flags);
	}
	
	public static final Parcelable.Creator<CacheObject> CREATOR = new Parcelable.Creator<CacheObject>() {
		public CacheObject createFromParcel(Parcel in) {
			return new CacheObject(in);
		}

		public CacheObject[] newArray(int size) {
			return new CacheObject[size];
		}
	};

	/**
	 * @return whether this resource is marked as pending
	 */
	public boolean isPending() {
		return (flags&DIRTY_FLAG)>0;
	}

	/**
	 * The summary of this resource
	 * @return
	 */
	public String getSummary() {
		return this.summary;
	}

	/**
	 * Whether this resource has associated alarms
	 * @return
	 */
	public boolean hasAlarms() {
		return (flags&HAS_ALARM_FLAG)>0;
	}
	
	/**
	 * An instance of the collection this resource belongs to
	 * @return
	 */
	public long getCollectionId() {
		return cid;
	}

	/**
	 * Whether this resource has recurrences
	 * @return
	 */
	public boolean isRecuring() {
		return (flags&RECURS_FLAG)>0;
	}

	/**
	 * The location associated with this resource
	 * @return
	 */
	public String getLocation() {
		return this.location;
	}
	
	/**
	 * The start time (in millis) as UTC of this resource
	 * @return
	 */
	public long getStart() {
		return start;
	}
	
	/**
	 * The end time as UTC of this resource
	 * @return
	 */
	public long getEnd() {
		return end;
	}

	/**
	 * Whether this resource has an all day date range.
	 * @return
	 */
	public boolean isAllDay() {
		return (flags&FLAG_ALL_DAY)>0;
	}
	
	public static CacheObject fromEventInstance(EventInstance ei) {
	/**	public static final int EVENT_FLAG = 			1;
		public static final int TODO_FLAG = 			1<<1;
		public static final int HAS_ALARM_FLAG = 		1<<2;
		public static final int RECURS_FLAG =			1<<3;
		public static final int DIRTY_FLAG = 			1<<4;
		public static final int START_IS_FLOATING_FLAG =1<<5;
		public static final int END_IS_FLOATING_FLAG = 	1<<6;
		public static final int FLAG_ALL_DAY = 			1<<7;*/
		
		int flag = 1;
		//if (ei.getStart().isFloating()) flag+=1<<5;
		//if (ei.getEnd().isFloating()) flag+=1<<6;
		//if (ei.isAllDay()) flag+= 1<<7;
		
		
		return new CacheObject(ei.getResource().getResourceId(), ei.getCollectionId(), ei.getSummary(), ei.getLocation(), 
				ei.getStart().getMillis(), ei.getEnd().getMillis(), flag);
	}
	
	public ContentValues getCacheCVs() {
		ContentValues cv =  new ContentValues();
		
		cv.put(CacheTableManager.FIELD_RESOURCE_ID,rid);
		cv.put(CacheTableManager.FIELD_CID,cid);
		cv.put(CacheTableManager.FIELD_SUMMARY,this.summary);
		cv.put(CacheTableManager.FIELD_LOCATION,this.location);
		cv.put(CacheTableManager.FIELD_DTSTART, this.start);
		cv.put(CacheTableManager.FIELD_DT_END, this.end);
		cv.put(CacheTableManager.FIELD_FLAGS, this.flags);
		return cv;
	}
	
	
}
