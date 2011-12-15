package com.morphoss.acal.database.resourcesmanager;

import java.util.ArrayList;

import android.content.ContentValues;
import android.util.Log;

import com.morphoss.acal.database.cachemanager.CacheObject;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import com.morphoss.acal.dataservice.CalendarInstance;
import com.morphoss.acal.dataservice.Resource;

public class RRRequestInstance extends ResourceRequestWithResponse<CalendarInstance> {

	public static final String TAG = "aCal RRRequestInstance";
	
	private long rid;
	private String rrid;
	
	public RRRequestInstance(ResourceResponseListener<CalendarInstance> callBack, CacheObject co) {
		super(callBack);
		this.rid = co.getResourceId();
		this.rrid = co.getRecurrenceId();
	}
	
	public RRRequestInstance(ResourceResponseListener<CalendarInstance> callBack, long resourceId, String recurrenceId) {
		super(callBack);
		this.rid = resourceId;
		this.rrid = recurrenceId;
	}
	

	@Override
	public void process(ResourceTableManager processor) throws ResourceProcessingException {
		ArrayList<ContentValues> cv = processor.query(null, ResourceTableManager.RESOURCE_ID+" = ?", new String[]{rid+""}, null,null,null);
		
		try {
			Resource res = Resource.fromContentValues(cv.get(0));
			CalendarInstance ci = CalendarInstance.fromResourceAndRRId(res, rrid);
			this.postResponse(new RRRequestInstanceResponse<CalendarInstance>(ci));
		} catch (Exception e) {
			Log.e(TAG,e.getMessage()+Log.getStackTraceString(e));
			this.postResponse(new RRRequestInstanceResponse<CalendarInstance>(e));
		}
		
	}

	public class RRRequestInstanceResponse<CalendarIntstance> extends ResourceResponse<CalendarInstance> {

		private CalendarInstance result = null;
		public RRRequestInstanceResponse(CalendarInstance ci) { this.result = ci; }
		public RRRequestInstanceResponse(Exception e) { super(e); }
		
		@Override
		public CalendarInstance result() {
			return this.result;
		}
		
	}
}
