package com.morphoss.acal.cachemanager;

import android.os.Parcel;
import android.os.Parcelable;

import com.morphoss.acal.dataservice.EventInstance;

/**
 * Represents a single row in the cache
 * @author Chris Noldus
 *
 */
public class CacheObject implements Parcelable {
	public final int rid;
	public final String summary;
	public final long start;
	public final long end;
	public final boolean hasAlarms;
	public final boolean recurs;
	public final int colour;
	public final boolean dirty;
	
	public CacheObject(int rid, String sum, long st, long end, boolean alrms, boolean rec, int col, boolean dirt) {
		this.rid = rid;
		this.summary = sum;
		this.start = st;
		this.end = end;
		this.hasAlarms = alrms;
		this.recurs = rec;
		this.colour = col;
		this.dirty = dirt;
	}
	
	private CacheObject(Parcel in) {
		rid = in.readInt();
		summary = in.readString();
		start = in.readLong();
		end = in.readLong();
		hasAlarms = in.readByte() == 'T' ? true : false;
		recurs = in.readByte() == 'T' ? true : false;
		colour = in.readInt();
		dirty = in.readByte() == 'T' ? true : false;
	}
	
	public EventInstance getFullInstance() {
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
		dest.writeInt(rid);
		dest.writeString(summary);
		dest.writeLong(start);
		dest.writeLong(end);
		dest.writeByte(hasAlarms ? (byte)'T' : (byte)'F');
		dest.writeByte(recurs ? (byte)'T' : (byte)'F');
		dest.writeInt(colour);
		dest.writeByte(dirty ? (byte)'T' : (byte)'F');
	}
	
	public static final Parcelable.Creator<CacheObject> CREATOR = new Parcelable.Creator<CacheObject>() {
		public CacheObject createFromParcel(Parcel in) {
			return new CacheObject(in);
		}

		public CacheObject[] newArray(int size) {
			return new CacheObject[size];
		}
	};
}
