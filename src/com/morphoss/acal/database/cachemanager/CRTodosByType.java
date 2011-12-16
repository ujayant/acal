package com.morphoss.acal.database.cachemanager;

import java.util.ArrayList;
import java.util.TimeZone;

import android.content.ContentValues;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.cachemanager.CacheManager.CacheTableManager;

/**
 * A CacheRequest that returns a List of CacheObjects which are tasks matching the specified criteria
 * 
 * To get the result you should pass in a CacheResponseListenr of the type ArrayList&lt;CacheObject&gt;
 * If you don't care about the result (e.g. your forcing a window size change) you may pass a null callback.
 * 
 * @author Andrew McMillan
 *
 */
public class CRTodosByType extends CacheRequestWithResponse<ArrayList<CacheObject>> {

	private boolean includeCompleted;
	private boolean includeFuture;
	
	/**
	 * Request all CacheObjects in the range provided. Pass the result to the callback provided
	 * @param range
	 * @param callBack
	 */
	public CRTodosByType(boolean showCompleted, boolean showFuture, CacheResponseListener<ArrayList<CacheObject>> callBack) {
		super(callBack);
		this.includeCompleted = showCompleted;
		this.includeFuture = showFuture;
	}
	
	@Override
	public void process(CacheTableManager processor)  throws CacheProcessingException{
		final ArrayList<CacheObject> result = new ArrayList<CacheObject>();

		AcalDateTime rangeEnd = AcalDateTime.getInstance().addDays(7);
		if ( includeFuture ) rangeEnd.setMonthStart().addMonths(3);
		AcalDateRange range = new AcalDateRange( AcalDateTime.getInstance().setMonthStart().addMonths(-1), rangeEnd );
		if (!processor.checkWindow(range)) {
			//Wait give up - caller can decide to rerequest or wait for cachechanged notification
			this.postResponse(new CRTodosByTypeResponse<ArrayList<CacheObject>>(result));
			return;
		}

		String dtEnd = rangeEnd.getMillis()+"";
		String offset = TimeZone.getDefault().getOffset(range.start.getMillis())+"";
		
		
		ArrayList<ContentValues> data = processor.query(null, 
				"(" + CacheTableManager.FIELD_RESOURCE_TYPE +"= ?)"+  
				" AND (" + CacheTableManager.FIELD_COMPLETED +"< ? OR ?)"+  
				" AND ( "+
					"( "+CacheTableManager.FIELD_DTSTART+" < ? AND NOT "+CacheTableManager.FIELD_DTSTART_FLOAT+" )"+
						" OR "+
					"( "+CacheTableManager.FIELD_DTSTART+" + ? < ? AND "+CacheTableManager.FIELD_DTSTART_FLOAT+" )"+
						" OR "+
					"( "+CacheTableManager.FIELD_DTSTART+" ISNULL )"+
				")",
				new String[] {
						CacheTableManager.RESOURCE_TYPE_VTODO, Long.MAX_VALUE+"", (includeCompleted ? "1" : "0"),
						dtEnd, offset, dtEnd
						},
				null,null,CacheTableManager.FIELD_DTSTART+" ASC");
		
		for (ContentValues cv : data) 
				result.add(CacheObject.fromContentValues(cv));
		
		this.postResponse(new CRTodosByTypeResponse<ArrayList<CacheObject>>(result));
	}

	/**
	 * This class represents the response from a CRTodosByType Request. It will be passed to the callback if one was provided.
	 * @author Andrew McMillan
	 *
	 * @param <E>
	 */
	public class CRTodosByTypeResponse<E extends ArrayList<CacheObject>> implements CacheResponse<ArrayList<CacheObject>> {
		
		private ArrayList<CacheObject> result;
		
		private CRTodosByTypeResponse(ArrayList<CacheObject> result) {
			this.result = result;
		}
		
		/**
		 * Returns the result of the original Request.
		 */
		public ArrayList<CacheObject> result() {
			return this.result;
		}
	}

}
