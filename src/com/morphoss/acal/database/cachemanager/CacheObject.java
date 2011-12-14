package com.morphoss.acal.database.cachemanager;

import android.content.ContentValues;
import android.os.Parcel;
import android.os.Parcelable;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.database.cachemanager.CacheManager.CacheTableManager;
import com.morphoss.acal.dataservice.EventInstance;
import com.morphoss.acal.davacal.PropertyName;
import com.morphoss.acal.davacal.VEvent;

/**
 * Represents a single row in the cache
 * @author Chris Noldus
 *
 */
public class CacheObject implements Parcelable, Comparable<CacheObject> {
	private final long rid;
	private final String rrid;	//Recurence id, -1 if not recurs.
	private final long cid;
	private final String summary;
	private final String location;
	private final long start;
	private final long end;
	private final boolean startFloating;
	private final boolean endFloating;
	private final int flags;
	
	public static final int EVENT_FLAG = 			1;
	public static final int TODO_FLAG = 			1<<1;
	public static final int HAS_ALARM_FLAG = 		1<<2;
	public static final int RECURS_FLAG =			1<<3;
	public static final int DIRTY_FLAG = 			1<<4;
	public static final int FLAG_ALL_DAY = 			1<<5;
	
	public enum TYPE { EVENT, TODO };
	
	public CacheObject(long rid, String rrid,  long cid, String sum, String loc, long st, long end, boolean sfloat, boolean efloat, int flags) {
		this.rid = rid;
		this.rrid = rrid;
		this.cid = cid;
		this.summary = sum;
		this.location= loc;
		this.start = st;
		this.end = end;
		this.startFloating = sfloat;
		this.endFloating = efloat;
		this.flags = flags;
	}
	
	//Generate a cacheObject from a VEvent
	public CacheObject( VEvent event, AcalDateTime dtstart, AcalDuration duration) {
		this.rid = event.getResourceId();
		this.rrid = dtstart.toPropertyString(PropertyName.RECURRENCE_ID);
		this.cid = event.getCollectionId();
		this.summary = event.getSummary();
		this.location = event.getLocation();
		this.start = dtstart.getMillis();
		this.end = duration.getEndDate(dtstart).getMillis();
		
		int flags = EVENT_FLAG;
		if (!event.getAlarms().isEmpty()) flags+=HAS_ALARM_FLAG;
		if (event.getProperty(PropertyName.RRULE) != null) flags+=RECURS_FLAG;
		if (event.getResource().isPending()) flags+=DIRTY_FLAG;
		if (dtstart.isFloating()) {
			startFloating = true;
			endFloating = true;
		} else {
			startFloating = false;
			endFloating = false;
		}
		if (dtstart.isDate()) flags+= FLAG_ALL_DAY;
		this.flags = flags;
	}
	
	private CacheObject(Parcel in) {
		rid = in.readLong();
		rrid = in.readString();
		cid = in.readLong();
		summary = in.readString();
		location = in.readString();
		start = in.readLong();
		end = in.readLong();
		startFloating = (in.readByte() == 'T' ? true : false);
		endFloating = (in.readByte() == 'T' ? true : false);
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
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(rid);
		dest.writeString(rrid);
		dest.writeLong(cid);
		dest.writeString(summary);
		dest.writeString(location);
		dest.writeByte((byte) (startFloating ? 'T' : 'F'));
		dest.writeByte((byte) (endFloating ? 'T' : 'F'));
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
	
	public ContentValues getCacheCVs() {
		ContentValues cv =  new ContentValues();
		
		cv.put(CacheTableManager.FIELD_RESOURCE_ID,rid);
		cv.put(CacheTableManager.FIELD_RECURRENCE_ID, this.rrid);
		cv.put(CacheTableManager.FIELD_CID,cid);
		cv.put(CacheTableManager.FIELD_SUMMARY,this.summary);
		cv.put(CacheTableManager.FIELD_LOCATION,this.location);
		cv.put(CacheTableManager.FIELD_DTSTART, this.start);
		cv.put(CacheTableManager.FIELD_DTEND, this.end);
		cv.put(CacheTableManager.FIELD_DTSTART_FLOAT, this.startFloating);
		cv.put(CacheTableManager.FIELD_DTEND_FLOAT, this.endFloating);
		cv.put(CacheTableManager.FIELD_FLAGS, this.flags);
		return cv;
	}
	
	public static CacheObject fromContentValues(ContentValues row) {
		return new CacheObject(
					row.getAsLong(CacheTableManager.FIELD_RESOURCE_ID), 
					row.getAsString(CacheTableManager.FIELD_RECURRENCE_ID),
					row.getAsInteger(CacheTableManager.FIELD_CID),
					row.getAsString(CacheTableManager.FIELD_SUMMARY),
					row.getAsString(CacheTableManager.FIELD_LOCATION),
					row.getAsLong(CacheTableManager.FIELD_DTSTART),
					row.getAsLong(CacheTableManager.FIELD_DTEND),
					row.getAsInteger(CacheTableManager.FIELD_DTSTART_FLOAT) ==1,
					row.getAsInteger(CacheTableManager.FIELD_DTEND_FLOAT) ==1,
					row.getAsInteger(CacheTableManager.FIELD_FLAGS)
				);
	}

	@Override
	public int compareTo(CacheObject another) {
		return (int) (this.start - another.start);
	}

	public boolean isEvent() {
		return (flags & EVENT_FLAG) > 0;
	}
	
	
}
