package com.morphoss.acal.database.cachemanager;

import java.util.ArrayList;
import java.util.HashMap;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.cachemanager.CacheManager.CacheTableManager;

/**
 * A CacheRequest that returns a Map CacheObjects that occur in the specified month.
 * The Map Keys are Days of the Month, the values are lists of events.
 * 
 * To get the result you should pass in a CacheResponseListenr of the type ArrayList&lt;CacheObject&gt;
 * If you don't care about the result (e.g. your forcing a window size change) you may pass a null callback.
 * 
 * @author Chris Noldus
 *
 */
public class CRObjectsInMonthByDay extends CacheRequestWithResponse<HashMap<Integer,ArrayList<CacheObject>>> {

	private int month;
	private int year;
	
	/**
	 * Request all for the month provided. Pass the result to the callback provided
	 * @param range
	 * @param callBack
	 */
	public CRObjectsInMonthByDay(int month, int year, CacheResponseListener<HashMap<Integer,ArrayList<CacheObject>>> callBack) {
		super(callBack);
		this.month = month;
		this.year = year;
	}
	
	@Override
	public void process(CacheTableManager processor) throws CacheProcessingException {
		final HashMap<Integer,ArrayList<CacheObject>> result = new HashMap<Integer,ArrayList<CacheObject>>();
		AcalDateTime start = new AcalDateTime( year, month, 1, 0, 0, 0, null).applyLocalTimeZone(); 
		AcalDateTime end = start.clone().addMonths(1).applyLocalTimeZone();
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
		this.postResponse(new CREventsInMonthByDayResponse<HashMap<Integer,ArrayList<CacheObject>>>(result));
	}

	/**
	 * This class represents the response from a CRObjectsInMonthByDay Request. It will be passed to the callback if one was provided.
	 * @author Chris Noldus
	 *
	 * @param <E>
	 */
public class CREventsInMonthByDayResponse<E extends HashMap<Integer,ArrayList<CacheObject>>> implements CacheResponse<HashMap<Integer,ArrayList<CacheObject>>> {
		
		private HashMap<Integer,ArrayList<CacheObject>> result;
		
		public CREventsInMonthByDayResponse(HashMap<Integer,ArrayList<CacheObject>> result) {
			this.result = result;
		}
		
		public HashMap<Integer,ArrayList<CacheObject>> result() {
			return this.result;
		}
	}
	
}
