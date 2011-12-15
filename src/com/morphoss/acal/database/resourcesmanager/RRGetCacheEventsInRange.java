package com.morphoss.acal.database.resourcesmanager;

import java.util.ArrayList;

import android.content.ContentValues;
import android.util.Log;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.database.cachemanager.CacheObject;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import com.morphoss.acal.dataservice.Resource;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.davacal.VComponentCreationException;

public class RRGetCacheEventsInRange extends ResourceRequestWithResponse<ArrayList<CacheObject>> {

	private final AcalDateRange range;
	public static final String TAG = "aCal RRGetCacheEventsInRange";
	
	
	public RRGetCacheEventsInRange(AcalDateRange range, ResourceResponseListener<ArrayList<CacheObject>> callback) {
		super(callback);
		Log.d(TAG,"Instatiated");
		this.range = range;
	}
	
	@Override
	public void process(ResourceTableManager processor) {
		// TODO At the moment this algorithm ignores pending resources nor does it handle floating events.
		Log.d(TAG,"Process Called...");
		
		//step 1 --> convert range to long UTC start 
		long start = range.start.getMillis();
		long end = range.end.getMillis();
		
		Log.d(TAG,"Getting Resource rows");
		//step 2 query db for reesources in range
		ArrayList<ContentValues> rValues = processor.query(null, 
				
				"( "+ResourceTableManager.LATEST_END+" ISNULL OR " + ResourceTableManager.LATEST_END+" >= ? )" +
				" AND "+ResourceTableManager.EARLIEST_START+" <= ? "
				,
				new String[]{  start+"", end+""},
				null,null,null);
		Log.d(TAG,rValues.size()+" Rows retreived. Converting into Resource Objects");
		ArrayList<Resource> resources = new ArrayList<Resource>();
		for (ContentValues cv : rValues) resources.add(Resource.fromContentValues(cv));
		Log.d(TAG, "Conversion complete. Populating VCalendars and appedning events.");
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
					((VCalendar)comp).appendCacheEventInstancesBetween(events, range, false);
				}
			} catch (VComponentCreationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		Thread.currentThread().setPriority(currentPri);
		Log.d(TAG,events.size()+"Event Instances obtained. Posting Response.");
			
		//post response
		super.postResponse(new RREventsInRangeResponse<ArrayList<CacheObject>>(events, range));

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

}
