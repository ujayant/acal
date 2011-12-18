package com.morphoss.acal.database.resourcesmanager.requests;

import java.util.ArrayList;

import android.content.ContentValues;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.database.cachemanager.CacheObject;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.ReadOnlyResourceTableManager;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import com.morphoss.acal.database.resourcesmanager.ResourceResponse;
import com.morphoss.acal.database.resourcesmanager.ResourceResponseListener;
import com.morphoss.acal.database.resourcesmanager.requesttypes.ReadOnlyResourceRequestWithResponse;
import com.morphoss.acal.dataservice.Resource;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.davacal.VComponentCreationException;

public class RRGetCacheEventsInRange extends ReadOnlyResourceRequestWithResponse<ArrayList<CacheObject>> {

	private final AcalDateRange range;
	public static final String TAG = "aCal RRGetCacheEventsInRange";
	private boolean processed = false;
	
	
	public RRGetCacheEventsInRange(AcalDateRange range, ResourceResponseListener<ArrayList<CacheObject>> callback) {
		super(callback);
		Log.println(Constants.LOGD,TAG,"Instantiated for range of "+range.toString());
		this.range = range;
	}
	
	@Override
	public void process(ReadOnlyResourceTableManager processor) {
		// TODO At the moment this algorithm ignores pending resources nor does it handle floating events.
		Log.println(Constants.LOGD,TAG,"Process Called...");
		
		//step 1 --> convert range to long UTC start 
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

		ArrayList<Resource> resources = new ArrayList<Resource>();
		for (ContentValues cv : rValues) resources.add(Resource.fromContentValues(cv));
		Log.println(Constants.LOGD,TAG, "Conversion complete. Populating VCalendars and appending events.");
		ArrayList<CacheObject> events = new ArrayList<CacheObject>();
		//step 3 - foreach resource, Vcomps
		//This is very CPU intensive, so lower our priority to prevent interfering with other parts of the app.
		int currentPri = Thread.currentThread().getPriority();
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		for (Resource r : resources) {
			//if VComp is VCalendar
			try {
				VComponent comp = VComponent.createComponentFromResource(r);
				if (comp instanceof VCalendar) {
					((VCalendar)comp).appendCacheEventInstancesBetween(events, range);
				}
			} catch (VComponentCreationException e) {
				Log.i(TAG,Log.getStackTraceString(e));
			}
		}
		Thread.currentThread().setPriority(currentPri);
		Log.println(Constants.LOGD,TAG,events.size()+"Event Instances obtained. Posting Response.");
			
		//post response
		super.postResponse(new RREventsInRangeResponse<ArrayList<CacheObject>>(events, range));

		this.processed = true;
	}
	
	/**
	 * This class represents the response from a RREventsInRangeResponse Request. It will be passed to the callback if one was provided.
	 * @author Chris Noldus
	 *
	 * @param <E>
	 */
	public class RREventsInRangeResponse<E extends ArrayList<CacheObject>> extends ResourceResponse<ArrayList<CacheObject>> {
		
		private ArrayList<CacheObject> result;
		private AcalDateRange requestedRange;
		
		private RREventsInRangeResponse(ArrayList<CacheObject> result, AcalDateRange requestedRange) {
			this.result = result;
			this.requestedRange = requestedRange;
		}
		
		/**
		 * Returns the result of the original Request.
		 */
		public ArrayList<CacheObject> result() {
			return this.result;
		}
		
		public AcalDateRange requestedRange() {
			return this.requestedRange;
		}
	}

	@Override
	public boolean isProcessed() {
		return this.processed;
	}

}
