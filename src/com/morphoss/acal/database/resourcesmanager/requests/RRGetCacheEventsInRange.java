package com.morphoss.acal.database.resourcesmanager.requests;

import java.util.ArrayList;

import android.content.ContentValues;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.database.cachemanager.CacheObject;
import com.morphoss.acal.database.cachemanager.CacheWindow;
import com.morphoss.acal.database.resourcesmanager.ResourceResponse;
import com.morphoss.acal.database.resourcesmanager.ResourceResponseListener;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.ReadOnlyResourceTableManager;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import com.morphoss.acal.database.resourcesmanager.requesttypes.ReadOnlyResourceRequestWithResponse;
import com.morphoss.acal.dataservice.Resource;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.davacal.VComponentCreationException;

public class RRGetCacheEventsInRange extends ReadOnlyResourceRequestWithResponse<ArrayList<Resource>> {

	public static final String TAG = "aCal RRGetCacheEventsInRange";
	private boolean processed = false;
	private CacheWindow window;
	
	
	public RRGetCacheEventsInRange(CacheWindow window, ResourceResponseListener<ArrayList<Resource>> callback) {
		super(callback);
		Log.println(Constants.LOGD,TAG,"Instantiated for window "+window);
		this.window = window;
	}
	
	@Override
	public void process(ReadOnlyResourceTableManager processor) {
		ArrayList<Resource> result = new ArrayList<Resource>();
		// TODO At the moment this algorithm ignores pending resources nor does it handle floating events.
		Log.println(Constants.LOGD,TAG,"Process Called...");
		AcalDateRange range = window.getRequestedWindow();
		if (window.getRequestedWindow() == null) {
			Log.d(TAG, "Resource request cancelled - cache window already full");
			super.postResponse(new RREventsInRangeResponse<ArrayList<Resource>>(result));
			this.processed = true;
			return;
		}
		
		//step 1 --> convert window range to long UTC start 
		long start = range.start.getMillis();
		long end = range.end.getMillis();
		
		Log.println(Constants.LOGD,TAG,"Getting Resource rows");

		//step 2 query db for resources in range
		ArrayList<ContentValues> rValues = processor.query(null,
				"("+ResourceTableManager.EFFECTIVE_TYPE +"=? OR "+ResourceTableManager.EFFECTIVE_TYPE +"=? )" +
				" AND ("+ResourceTableManager.LATEST_END+" IS NULL OR " + ResourceTableManager.LATEST_END+" >= ? )" +
				" AND ("+ResourceTableManager.EARLIEST_START+" IS NULL OR "+ResourceTableManager.EARLIEST_START+" <= ? )"
				,
				new String[]{ VComponent.VEVENT, VComponent.VTODO, start+"", end+""},
				null,null,null);
		Log.println(Constants.LOGD,TAG,rValues.size()+" Rows retreived. Converting into Resource Objects");
		for (ContentValues cv : rValues) result.add(Resource.fromContentValues(cv));
		Log.println(Constants.LOGD,TAG, "Conversion complete. Populating VCalendars and appending events.");
		
		
		
		
		//post response
		super.postResponse(new RREventsInRangeResponse<ArrayList<Resource>>(result));

		this.processed = true;
	}
	
	@Override
	public boolean isProcessed() {
		return this.processed;
	}
	
	/**
	 * This class represents the response from a RREventsInRangeResponse Request. It will be passed to the callback if one was provided.
	 * @author Chris Noldus
	 *
	 * @param <E>
	 */
	public class RREventsInRangeResponse<E extends ArrayList<Resource>> extends ResourceResponse<ArrayList<Resource>> {
		
		private ArrayList<Resource> result;
		
		private RREventsInRangeResponse(ArrayList<Resource> result) {
			this.result = result;
		}
		
		/**
		 * Returns the result of the original Request.
		 */
		public ArrayList<Resource> result() {
			return this.result;
		}
		
	}



}
