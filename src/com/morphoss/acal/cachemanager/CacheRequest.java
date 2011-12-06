package com.morphoss.acal.cachemanager;

import android.os.Parcelable;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;

/**
 * This class should be used by activities wishing to request data from the cache
 * @author Chris Noldus
 *
 */
public class CacheRequest {

	private final int code;
	private final Parcelable data;
	private final CacheResponseListener callBack;

	public final static int REQUEST_OBJECTS_FOR_DATARANGE = 0;

	private CacheRequest(int code, Parcelable data, CacheResponseListener callBack) {
		this.code = code;
		this.data = data;
		this.callBack = callBack;
	}
	
	public static CacheRequest requestObjectsForDateRange(AcalDateRange range, CacheResponseListener callback) {
		return new CacheRequest(REQUEST_OBJECTS_FOR_DATARANGE, range, callback);
	}
	
	public int getCode() {
		return code;
	}

	public CacheResponseListener getCallBack() {
		return callBack;
	}

	public Object getData() {
		return data;
	}
	
}