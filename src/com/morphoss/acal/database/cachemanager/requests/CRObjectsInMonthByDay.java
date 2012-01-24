package com.morphoss.acal.database.cachemanager.requests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TimeZone;

import android.content.ContentValues;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.cachemanager.CacheManager;
import com.morphoss.acal.database.cachemanager.CacheObject;
import com.morphoss.acal.database.cachemanager.CacheProcessingException;
import com.morphoss.acal.database.cachemanager.CacheRequestWithResponse;
import com.morphoss.acal.database.cachemanager.CacheResponse;
import com.morphoss.acal.database.cachemanager.CacheResponseListener;
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
public class CRObjectsInMonthByDay extends CacheRequestWithResponse<HashMap<Short,ArrayList<CacheObject>>> {

	private int month;
	private int year;
	
	public static final String TAG = "aCal CRObjectsInMonthByDay";
	
	//metrics
	private long construct =-1;
	private long pstart=-1;
	private long qstart=-1;
	private long qend=-1;
	private long pend=-1;
	
	
	/**
	 * Request all for the month provided. Pass the result to the callback provided
	 * @param range
	 * @param callBack
	 */
	public CRObjectsInMonthByDay(int month, int year, CacheResponseListener<HashMap<Short,ArrayList<CacheObject>>> callBack) {
		super(callBack);
		construct = System.currentTimeMillis();
		this.month = month;
		this.year = year;
	}
	
	@Override
	public void process(CacheTableManager processor) throws CacheProcessingException {
		pstart = System.currentTimeMillis();
		final HashMap<Short,ArrayList<CacheObject>> result = new HashMap<Short,ArrayList<CacheObject>>();
		AcalDateTime start = new AcalDateTime( year, month, 1, 0, 0, 0, TimeZone.getDefault().getID()); 
		AcalDateTime end = start.clone().addMonths(1).applyLocalTimeZone();
		
		if (!processor.checkWindow(new AcalDateRange(start,end))) {
			//Wait give up - caller can decide to rerequest or waitf for cachechanged notification
			this.postResponse(new CREventsInMonthByDayResponse<HashMap<Short,ArrayList<CacheObject>>>(result));
			pend = System.currentTimeMillis();
			printMetrics();
			return;
		}
		
		String dtStart = start.getMillis()+"";
		String dtEnd = end.getMillis()+"";
		String offset = TimeZone.getDefault().getOffset(start.getMillis())+"";
		
		qstart  = System.currentTimeMillis();
		ArrayList<ContentValues> data = processor.query(null, 
				"( " + 
					"( "+CacheTableManager.FIELD_DTEND+" > ? AND NOT "+CacheTableManager.FIELD_DTEND_FLOAT+" )"+
						" OR "+
						"( "+CacheTableManager.FIELD_DTEND+" + ? > ? AND "+CacheTableManager.FIELD_DTEND_FLOAT+" )"+
						" OR "+
					"( "+CacheTableManager.FIELD_DTEND+" ISNULL )"+
				" ) AND ( "+
					"( "+CacheTableManager.FIELD_DTSTART+" < ? AND NOT "+CacheTableManager.FIELD_DTSTART_FLOAT+" )"+
						" OR "+
					"( "+CacheTableManager.FIELD_DTSTART+" + ? < ? AND "+CacheTableManager.FIELD_DTSTART_FLOAT+" )"+
						" OR "+
					"( "+CacheTableManager.FIELD_DTSTART+" ISNULL )"+
				")",
				new String[] {dtStart , offset, dtStart, dtEnd, offset, dtEnd},
				null,null,CacheTableManager.FIELD_DTSTART+" ASC");
		qend  = System.currentTimeMillis();
		for (ContentValues value : data ) {
			CacheObject co = CacheObject.fromContentValues(value);
			AcalDateTime dt = AcalDateTime.fromMillis(value.getAsLong(CacheTableManager.FIELD_DTSTART)).shiftTimeZone(TimeZone.getDefault().getID());
			if (!result.containsKey(dt.getMonthDay())) result.put(dt.getMonthDay(), new ArrayList<CacheObject>());
			result.get(dt.getMonthDay()).add(co);
		}
		
		this.postResponse(new CREventsInMonthByDayResponse<HashMap<Short,ArrayList<CacheObject>>>(result));
		pend = System.currentTimeMillis();
		printMetrics();
	}
	
	private void printMetrics() {
		long total = pend-construct;
		long process = pend-pstart;
		long queuetime = pstart-construct;
		long query = qend-qstart;
		if ( CacheManager.DEBUG ) Log.println(Constants.LOGD, TAG,
				String.format("Metrics: Queue Time:%5d, Process Time:%5d,  Query Time:%4d,  Total Time:%6d", queuetime, process, query, total) );
	}
	

	/**
	 * This class represents the response from a CRObjectsInMonthByDay Request. It will be passed to the callback if one was provided.
	 * @author Chris Noldus
	 *
	 * @param <E>
	 */
public class CREventsInMonthByDayResponse<E extends HashMap<Short,ArrayList<CacheObject>>> implements CacheResponse<HashMap<Short,ArrayList<CacheObject>>> {
		
		private HashMap<Short,ArrayList<CacheObject>> result;
		
		public CREventsInMonthByDayResponse(HashMap<Short,ArrayList<CacheObject>> result) {
			this.result = result;
		}
		
		public HashMap<Short,ArrayList<CacheObject>> result() {
			return this.result;
		}
	}
	
}
