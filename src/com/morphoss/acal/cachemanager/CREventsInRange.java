package com.morphoss.acal.cachemanager;

import java.util.ArrayList;

import android.content.ContentValues;
import android.os.Parcel;
import android.os.Parcelable;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.cachemanager.CacheManager.EventCacheProcessor;

public class CREventsInRange implements CacheRequest {

	private AcalDateRange range;
	private CacheResponseListener<ArrayList<CacheObject>> callBack = null;
	
	public CREventsInRange(AcalDateRange range, CacheResponseListener<ArrayList<CacheObject>> callBack) {
		this.range = range;
		this.callBack = callBack;
	}
	
	@Override
	public void process(EventCacheProcessor processor)  throws CacheProcessingException{
		final ArrayList<CacheObject> result = new ArrayList<CacheObject>();
		processor.checkWindow(range);

		ArrayList<ContentValues> data = processor.query(null, EventCacheProcessor.FIELD_DTSTART+" > ? AND "+EventCacheProcessor.FIELD_DTSTART+" < ?", 
				new String[]{range.start.getMillis()+"",range.end.getMillis()+""}, null, null, EventCacheProcessor.FIELD_DTSTART);
		for (ContentValues cv : data) 
				result.add(CacheManager.fromContentValues(cv));

		result.add(new CacheObject(
				-1,
				1,
				"Test Event",
				"Test Location",
				range.start.clone().setHour(12).getMillis(),
				range.start.clone().setHour(13).getMillis(),
				CacheObject.EVENT_FLAG));
		
		new Thread(new Runnable() {

			@Override
			public void run() {
				callBack.cacheResponse(new CREventsInRangeResponse<ArrayList<CacheObject>>(result));
			}
		}).start();
	}

	public class CREventsInRangeResponse<E extends ArrayList<CacheObject>> implements CacheResponse<ArrayList<CacheObject>>, Parcelable {
		
		private ArrayList<CacheObject> result;
		
		public CREventsInRangeResponse(ArrayList<CacheObject> result) {
			this.result = result;
		}
		
		public ArrayList<CacheObject> result() {
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
