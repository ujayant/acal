package com.morphoss.acal.cachemanager;

import java.util.ArrayList;
import java.util.HashMap;

import android.os.Parcel;
import android.os.Parcelable;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.cachemanager.CREventsInRange.CREventsInRangeResponse;
import com.morphoss.acal.cachemanager.CacheManager.EventCacheProcessor;

public class CREventsInRangeByDay implements CacheRequest {

	private AcalDateRange range;
	private CacheResponseListener<HashMap<Integer,ArrayList<CacheObject>>> callBack = null;
	
	public CREventsInRangeByDay(AcalDateRange range, CacheResponseListener<HashMap<Integer,ArrayList<CacheObject>>> callBack) {
		this.range = range;
		this.callBack = callBack;
	}
	
	@Override
	public void process(EventCacheProcessor processor) throws CacheProcessingException {
		final HashMap<Integer,ArrayList<CacheObject>> result = new HashMap<Integer,ArrayList<CacheObject>>();
		AcalDateTime start = range.start.clone();
		AcalDateTime end = range.end.clone();
		while (start.before(end)) {
			ArrayList<CacheObject> day = new ArrayList<CacheObject>();
			day.add(new CacheObject(
					-1,
					1,
					"Test Event",
					"Test Location",
					start.clone().setHour(12).getMillis(),
					start.clone().setHour(13).getMillis(),
					CacheObject.EVENT_FLAG));
			result.put((int)start.getMonthDay(), day);
			start.addDays(1);
		}
		new Thread(new Runnable() {

			@Override
			public void run() {
				callBack.cacheResponse(new CREventsInRangeByDayResponse<HashMap<Integer,ArrayList<CacheObject>>>(result));
			}
		}).start();
	}

public class CREventsInRangeByDayResponse<E extends HashMap<Integer,ArrayList<CacheObject>>> implements CacheResponse<HashMap<Integer,ArrayList<CacheObject>>>, Parcelable {
		
		private HashMap<Integer,ArrayList<CacheObject>> result;
		
		public CREventsInRangeByDayResponse(HashMap<Integer,ArrayList<CacheObject>> result) {
			this.result = result;
		}
		
		public HashMap<Integer,ArrayList<CacheObject>> result() {
			return this.result;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			// TODO Auto-generated method stub
			
		}
		
	}
	
}
