package com.morphoss.acal.database.cachemanager;

import java.util.TimeZone;

import android.content.ContentValues;
import android.os.Parcel;
import android.os.Parcelable;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.database.cachemanager.CacheManager.CacheTableManager;
import com.morphoss.acal.davacal.PropertyName;
import com.morphoss.acal.davacal.VEvent;
import com.morphoss.acal.davacal.VTodo;

/**
 * Represents a single row in the cache
 * @author Chris Noldus
 *
 */
public class CacheObject implements Parcelable, Comparable<CacheObject> {
	private final long rid;
	private final String resourceType;
	private final String rrid;	//Recurence id, -1 if not recurs.
	private final long cid;
	private final String summary;
	private final String location;
	private final long start;
	private final long end;
	private final boolean startFloating;
	private final boolean endFloating;
	private final boolean completeFloating;
	private final long completed;
	private final int flags;
	
	public static final int HAS_ALARM_FLAG = 		1;
	public static final int RECURS_FLAG =			1<<1;
	public static final int DIRTY_FLAG = 			1<<2;
	public static final int FLAG_ALL_DAY = 			1<<3;
	

	public CacheObject(long rid, String resourceType, String rrid,  long cid, String sum, String loc, long st, long end, long completed, boolean sfloat, boolean efloat, boolean cfloat, int flags) {
		this.rid = rid;
		this.resourceType = resourceType;
		this.rrid = rrid;
		this.cid = cid;
		this.summary = sum;
		this.location= loc;
		this.start = st;
		this.end = end;
		this.completed = completed;
		this.startFloating = sfloat;
		this.endFloating = efloat;
		this.completeFloating = cfloat;
		this.flags = flags;
	}
	
	//Generate a cacheObject from a VEvent
	public CacheObject( VEvent event, AcalDateTime dtstart, AcalDuration duration) {
		this.rid = event.getResourceId();
		this.resourceType = CacheTableManager.RESOURCE_TYPE_VEVENT;
		this.completed = Long.MAX_VALUE;
		this.completeFloating = false;
		this.rrid = dtstart.toPropertyString(PropertyName.RECURRENCE_ID);
		this.cid = event.getCollectionId();
		this.summary = event.getSummary();
		this.location = event.getLocation();
		this.start = dtstart.getMillis();
		this.end = duration.getEndDate(dtstart).getMillis();
		
		int flags = 0;
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

	
	//Generate a cacheObject from a VTodo
	public CacheObject( VTodo task, AcalDateTime dtstart, AcalDateTime due, AcalDateTime completed) {
		this.rid = task.getResourceId();
		this.resourceType = CacheTableManager.RESOURCE_TYPE_VTODO;
		this.rrid = dtstart.toPropertyString(PropertyName.RECURRENCE_ID);
		this.cid = task.getCollectionId();
		this.summary = task.getSummary();
		this.location = task.getLocation();
		this.start = dtstart.getMillis();
		this.end = due.getMillis();
		this.completed = completed.getMillis();
		
		int flags = 0;
		if (!task.getAlarms().isEmpty()) flags+=HAS_ALARM_FLAG;
		if (task.getProperty(PropertyName.RRULE) != null) flags+=RECURS_FLAG;
		if (task.getResource().isPending()) flags+=DIRTY_FLAG;
		startFloating = dtstart.isFloating();
		endFloating = due.isFloating();
		completeFloating = completed.isFloating();

		if (dtstart.isDate()) flags+= FLAG_ALL_DAY;
		this.flags = flags;
	}
	
	private CacheObject(Parcel in) {
		rid = in.readLong();
		resourceType = in.readString();
		rrid = in.readString();
		cid = in.readLong();
		summary = in.readString();
		location = in.readString();
		startFloating = (in.readByte() == 'T' ? true : false);
		endFloating = (in.readByte() == 'T' ? true : false);
		completeFloating = (in.readByte() == 'T' ? true : false);
		start = in.readLong();
		end = in.readLong();
		completed = in.readLong();
		flags = in.readInt();

	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(rid);
		dest.writeString(resourceType);
		dest.writeString(rrid);
		dest.writeLong(cid);
		dest.writeString(summary);
		dest.writeString(location);
		dest.writeByte((byte) (startFloating ? 'T' : 'F'));
		dest.writeByte((byte) (endFloating ? 'T' : 'F'));
		dest.writeByte((byte) (completeFloating ? 'T' : 'F'));
		dest.writeLong(start);
		dest.writeLong(end);
		dest.writeLong(completed);
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
	 * The collection id
	 * @return
	 */
	public long getCollectionId() {
		return cid;
	}

	/**
	 * The resource id
	 * @return
	 */
	public long getResourceId() {
		return rid;
	}

	
	/**
	 * Whether this resource has recurrences
	 * @return
	 */
	public boolean isRecuring() {
		return (flags&RECURS_FLAG)>0;
	}

	/**
	 * Whether this task is overdue
	 * @return
	 */
	public boolean isOverdue() {
		return CacheTableManager.RESOURCE_TYPE_VTODO.equals(resourceType) &&
				(completed == Long.MAX_VALUE) &&
				(end - (endFloating?TimeZone.getDefault().getOffset(end) : 0)) < System.currentTimeMillis();
	}

	/**
	 * Whether this task is completed
	 * @return
	 */
	public boolean isCompleted() {
		return CacheTableManager.RESOURCE_TYPE_VTODO.equals(resourceType) && (completed != Long.MAX_VALUE);
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
		cv.put(CacheTableManager.FIELD_RESOURCE_TYPE, this.resourceType);
		cv.put(CacheTableManager.FIELD_RECURRENCE_ID, this.rrid);
		cv.put(CacheTableManager.FIELD_CID,cid);
		cv.put(CacheTableManager.FIELD_SUMMARY,this.summary);
		cv.put(CacheTableManager.FIELD_LOCATION,this.location);
		cv.put(CacheTableManager.FIELD_DTSTART, this.start);
		cv.put(CacheTableManager.FIELD_DTEND, this.end);
		cv.put(CacheTableManager.FIELD_COMPLETED, this.completed);
		cv.put(CacheTableManager.FIELD_DTSTART_FLOAT, this.startFloating);
		cv.put(CacheTableManager.FIELD_DTEND_FLOAT, this.endFloating);
		cv.put(CacheTableManager.FIELD_COMPLETE_FLOAT, this.completeFloating);
		cv.put(CacheTableManager.FIELD_FLAGS, this.flags);
		return cv;
	}
	
	public static CacheObject fromContentValues(ContentValues row) {
		return new CacheObject(
					row.getAsLong(CacheTableManager.FIELD_RESOURCE_ID), 
					row.getAsString(CacheTableManager.FIELD_RESOURCE_TYPE), 
					row.getAsString(CacheTableManager.FIELD_RECURRENCE_ID),
					row.getAsInteger(CacheTableManager.FIELD_CID),
					row.getAsString(CacheTableManager.FIELD_SUMMARY),
					row.getAsString(CacheTableManager.FIELD_LOCATION),
					row.getAsLong(CacheTableManager.FIELD_DTSTART),
					row.getAsLong(CacheTableManager.FIELD_DTEND),
					row.getAsLong(CacheTableManager.FIELD_COMPLETED),
					row.getAsInteger(CacheTableManager.FIELD_DTSTART_FLOAT) ==1,
					row.getAsInteger(CacheTableManager.FIELD_DTEND_FLOAT) ==1,
					row.getAsInteger(CacheTableManager.FIELD_COMPLETE_FLOAT) ==1,
					row.getAsInteger(CacheTableManager.FIELD_FLAGS)
				);
	}

	@Override
	public int compareTo(CacheObject another) {
		return (int) (this.start - another.start);
	}

	public boolean isEvent() {
		return CacheTableManager.RESOURCE_TYPE_VEVENT.equals(resourceType);
	}

	public boolean isTodo() {
		return CacheTableManager.RESOURCE_TYPE_VTODO.equals(resourceType);
	}

	public String getRecurrenceId() {
		return this.rrid;
	}

	public AcalDateTime getStartDateTime() {
		return AcalDateTime.localTimeFromMillis(start,startFloating);
	}

	public AcalDateTime getEndDateTime() {
		return AcalDateTime.localTimeFromMillis(end,endFloating);
	}

	public AcalDateTime getCompletedDateTime() {
		return AcalDateTime.localTimeFromMillis(completed,completeFloating);
	}
	
	
}
