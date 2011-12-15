package com.morphoss.acal.desktop;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.RemoteViews;

import com.morphoss.acal.Constants;
import com.morphoss.acal.DatabaseChangedEvent;
import com.morphoss.acal.R;
import com.morphoss.acal.StaticHelpers;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.AcalDBHelper;
import com.morphoss.acal.dataservice.Collection;
import com.morphoss.acal.dataservice.EventInstance;
import com.morphoss.acal.service.aCalService;

public class ShowUpcomingWidgetProvider extends AppWidgetProvider {
	
	public static final String TAG = "acal ShowUpcomingWidgetProvider";
	
	public static final int NUMBER_OF_EVENTS_TO_SHOW = 4;
	public static final int NUM_DAYS_TO_LOOK_AHEAD = 7;
	
	public static final String TABLE = "show_upcoming_widget_data";
	
	public static final String FIELD_ID = "_id";
	public static final String FIELD_RESOURCE_ID = "resource_id";
	public static final String FIELD_ETAG = "etag";
	public static final String FIELD_COLOUR = "colour";
	public static final String FIELD_DTSTART = "dtstart";
	public static final String FIELD_DTEND = "dtend";
	public static final String FIELD_SUMMARY = "summary";

	public static final String SHOW_UPCOMING_WIDGET_IDS_KEY ="acalshowupcomingwidgetids";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.hasExtra(SHOW_UPCOMING_WIDGET_IDS_KEY)) {
			int[] ids = intent.getExtras().getIntArray(SHOW_UPCOMING_WIDGET_IDS_KEY);
			this.onUpdate(context, AppWidgetManager.getInstance(context), ids);
		} else super.onReceive(context, intent);
	}
	
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

		if (Constants.LOG_DEBUG) Log.d(TAG, "onUpdate Called...");
		for (int widgetId : appWidgetIds) {

			Intent updateIntent = new Intent();	
			updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
			updateIntent.putExtra(SHOW_UPCOMING_WIDGET_IDS_KEY, appWidgetIds);

			PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, updateIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			
			if (Constants.LOG_DEBUG) Log.d(TAG, "Processing for widget id: "+widgetId);
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			boolean prefer24Hour = prefs.getBoolean(context.getString(R.string.prefTwelveTwentyfour),false);
			
			
			RemoteViews views = new RemoteViews(context.getPackageName(),R.layout.show_upcoming_widget_layout);
			views.removeAllViews(R.id.upcoming_container);
			
			//Used to calculate when we should trigger an update.
			long timeOfNextEventEnd = Long.MAX_VALUE;
			long timeOfNextEventStart = Long.MAX_VALUE;

			//Remove events that have finished
			cleanOld(context);
			
			//Get Data
			ContentValues[] cvs = getCurrentData(context);
			for (int  i = 0; i<NUMBER_OF_EVENTS_TO_SHOW; i++) {
				if (cvs[i] != null) {
					if (Constants.LOG_VERBOSE) Log.v(TAG, "Processing event "+i);
					AcalDateTime dtstart = AcalDateTime.fromMillis(cvs[i].getAsLong(FIELD_DTSTART)).shiftTimeZone(TimeZone.getDefault().getID());
					AcalDateTime dtend = AcalDateTime.fromMillis(cvs[i].getAsLong(FIELD_DTEND)).shiftTimeZone(TimeZone.getDefault().getID());
					int rid = cvs[i].getAsInteger(FIELD_RESOURCE_ID);
					
					//set up on click intent
					//Intent viewEvent = new Intent(context, EventView.class);
				//	viewEvent.putExtra(EventView.EVENT_INSTANCE_KEY, DefaultEventInstance.fromDB(rid, cvs[i].getAsLong(FIELD_DTSTART)));
				//	PendingIntent onClickIntent = PendingIntent.getActivity(context, i, viewEvent, PendingIntent.FLAG_UPDATE_CURRENT);
					
					//inflate row
					RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.show_upcoming_widget_base_row);

					LayoutInflater lf = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					ShowUpcomingRowLayout rowLayout = (ShowUpcomingRowLayout)lf.inflate(R.layout.show_upcoming_widget_custom_row, null);

					String dateTimeText = getNiceDate(context,dtstart);
					if ( !dateTimeText.equals("") ) {
						dateTimeText = getNiceTime(context,dtstart,dtend,prefer24Hour) + " ("+dateTimeText+")";
					}
					row.setImageViewBitmap(R.id.upcoming_row_image, rowLayout.setData(cvs[i], dateTimeText ));
					//row.setOnClickPendingIntent(R.id.upcoming_row, onClickIntent);
					//row.setOnClickPendingIntent(R.id.upcoming_row_image, onClickIntent);
					

					if (timeOfNextEventEnd > cvs[i].getAsLong(FIELD_DTEND))
						timeOfNextEventEnd = cvs[i].getAsLong(FIELD_DTEND);
					
					if (timeOfNextEventStart > cvs[i].getAsLong(FIELD_DTSTART))
						timeOfNextEventStart = cvs[i].getAsLong(FIELD_DTSTART);
					
					//addview
					views.addView(R.id.upcoming_container, row);
					
				} else break;
			}
			
			//set on click
			//views.setOnClickPendingIntent(R.id.upcoming_container, pendingIntent);
			
			if (Constants.LOG_DEBUG) Log.d(TAG, "Processing widget "+widgetId+" completed.");
		
			appWidgetManager.updateAppWidget(widgetId, views);
			
			//schedule alarm to wake us if next event starts/ends within refresh period (30 mins);
			//ignore start if negative
			long now = new AcalDateTime().getMillis();
			long start = timeOfNextEventStart - now;
			long end = timeOfNextEventEnd - now;
			long timeTillNextAlarm = end;
			if (start > 0 && start<end) timeTillNextAlarm = start;
			
			if (Constants.LOG_DEBUG) Log.d(TAG, "Next Event start/finsih = "+timeTillNextAlarm);
			if (timeTillNextAlarm< 1800000L) {
				if (Constants.LOG_DEBUG)  Log.d(TAG, "Setting update alarm for "+(timeTillNextAlarm/1000)+" seconds from now. due to event starting or ending");
				// Get the AlarmManager service
				 AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
				 am.set(AlarmManager.RTC, System.currentTimeMillis()+timeTillNextAlarm, pendingIntent);
			} else {
				if (Constants.LOG_DEBUG)  Log.d(TAG, "No Events starting or ending in the next 30 mins, not setting alarm.");
			}
		}
	}
	
	//Clean any events from the DB that have ended
	public static void cleanOld(Context context) {
		long endTime = new AcalDateTime().getMillis();
		AcalDBHelper dbhelper = new AcalDBHelper(context);
		SQLiteDatabase db = dbhelper.getReadableDatabase();
		int res = db.delete(TABLE, FIELD_DTEND+" <= ?", new String[]{""+endTime});
		db.close();
		dbhelper.close();
		if (res >  0) aCalService.databaseDispatcher.dispatchEvent(new DatabaseChangedEvent(DatabaseChangedEvent.DATABASE_SHOW_UPCOMING_WIDGET_UPDATE,null,null));
		if (Constants.LOG_DEBUG)  Log.d(TAG, "Deleted "+res+" event(s) that have ended.");
	}
	
	/**
	 * Get the current contents of the DB table and return as an array of ContentValues
	 * Array Size is ALWAYS NUMBER_POF_EVENTS_TO_SHOW, however there maybe NULL values if the 
	 * number of rows in the DB is different. Returned array is in order of the _ID field, which should
	 * be the same order in which events were added.
	 * 
	 * @param context
	 * @return
	 */
	public synchronized static ContentValues[] getCurrentData(Context context) {
		if (Constants.LOG_DEBUG) Log.d(TAG, "Retreiveing current data");
		ContentValues[] cvs = new ContentValues[NUMBER_OF_EVENTS_TO_SHOW];
		AcalDBHelper dbhelper = new AcalDBHelper(context);
		SQLiteDatabase db = dbhelper.getReadableDatabase();
		
		Cursor cursor = db.query(TABLE, null, null, null, null, null, FIELD_ID+" ASC");
		if (cursor.getCount() > 0) {
			cursor.moveToFirst();
			for (int i=0; !cursor.isAfterLast() && i<NUMBER_OF_EVENTS_TO_SHOW; cursor.moveToNext(), i++) {
				cvs[i] = new ContentValues();
				DatabaseUtils.cursorRowToContentValues(cursor, cvs[i]);
				if (Constants.LOG_VERBOSE) Log.v(TAG, "Loaded event "+i+" from db");
			}
		}
		cursor.close();
		db.close();
		dbhelper.close();
		return cvs;
	}

	public synchronized static void checkIfUpdateRequired(Context context, List<EventInstance> currentEvents) {
		if (Constants.LOG_DEBUG) Log.d(TAG, "Checking to see if widget update is required...");

		//Turn events into content vals for easier processing
		ContentValues[] cvs = new ContentValues[currentEvents.size()];
		for (int i = 0; i< currentEvents.size(); i++) {
			ContentValues cv = getCVFromEvent(context, currentEvents.get(i));
			if (cv != null)	cvs[i] = cv;
			else break;
		}
		
		if (hasDataChanged(context, cvs)){
			if (Constants.LOG_DEBUG) Log.d(TAG, "Data change detected, Updating DB");
			updateData(context, cvs);
			if (Constants.LOG_DEBUG) Log.d(TAG, "Sending update broadcast");
			StaticHelpers.updateWidgets(context, ShowUpcomingWidgetProvider.class);
		}
	}
	
	/**
	 * Returns true if the list of events provided does not match the contents of the database
	 * @param context
	 * @param currentEvents
	 * @return
	 */
	public synchronized static boolean hasDataChanged(Context context, ContentValues[] newData) {
		ContentValues[] oldData = getCurrentData(context);
		if (Constants.LOG_DEBUG) Log.d(TAG, "Comparing current data to db");
		
		if (oldData.length != newData.length) return true;
		
		for (int i = 0; i< NUMBER_OF_EVENTS_TO_SHOW && i< oldData.length; i++) {
			if (oldData[i] == null && newData[i] == null) continue; //both are null
			if (oldData[i] == null || newData[i] == null) return true; //only one is null

			//check all fields for change
			if (		oldData[i].getAsInteger(FIELD_RESOURCE_ID) != newData[i].getAsInteger(FIELD_RESOURCE_ID) ||  
						!oldData[i].getAsString(FIELD_ETAG).equals(newData[i].getAsString(FIELD_ETAG)) ||
						oldData[i].getAsInteger(FIELD_COLOUR) != newData[i].getAsInteger(FIELD_COLOUR) ||
						oldData[i].getAsLong(FIELD_DTSTART) != newData[i].getAsLong(FIELD_DTSTART) ||
						oldData[i].getAsLong(FIELD_DTEND) != newData[i].getAsLong(FIELD_DTEND) ||
						!oldData[i].getAsString(FIELD_SUMMARY).equals(newData[i].getAsString(FIELD_SUMMARY)))
				return true;
			
			//check to see if this event has ended
			long now = new AcalDateTime().getMillis();
			if (oldData[i].getAsLong(FIELD_DTEND) <= now) return true;
		}
		if (Constants.LOG_DEBUG) Log.d(TAG, "Data does not appear to have changed.");
		return false;
	}
	
	/**
	 * Overwrites the current database table with the list of events
	 * @param context
	 * @param currentEvents
	 */
	public static synchronized void updateData(Context context, ContentValues[] cvs) {
		if (Constants.LOG_DEBUG) Log.d(TAG, "Writing new values to DB");
		AcalDBHelper dbhelper = new AcalDBHelper(context);
		SQLiteDatabase db = dbhelper.getWritableDatabase();
		db.beginTransaction();
		
		//1st, clear existing data
		db.delete(TABLE, null, null);
			db.yieldIfContendedSafely();
		//2nd Add each event
		for (ContentValues cv : cvs) {
			db.insert(TABLE, null, cv);
			db.yieldIfContendedSafely();
		}
		
		db.setTransactionSuccessful();
		db.endTransaction(); 
		
		db.close(); 
	}

	public synchronized static ContentValues getCVFromEvent(Context context, EventInstance event) {
		ContentValues cv = new ContentValues();
		cv.put(FIELD_RESOURCE_ID, event.getResourceId());
		cv.put(FIELD_ETAG, event.getEtag());
		cv.put(FIELD_COLOUR, Collection.getInstance(event.getCollectionId(), context).getColour());
		cv.put(FIELD_DTSTART, event.getStart().getMillis());
		cv.put(FIELD_DTEND, event.getEnd().getMillis());
		cv.put(FIELD_SUMMARY, event.getSummary());
		
		return cv;
	}
	
	
	
	public String getNiceDate(Context context, AcalDateTime dateTime) {
		AcalDateTime now = new AcalDateTime().applyLocalTimeZone();
		if (dateTime.getMillis() <= now.getMillis()) return context.getString(R.string.ends); ///Event is occuring now
		if (now.getEpochDay() == dateTime.getEpochDay()) {
//			String today = context.getString(R.string.Today);
			return "";
		}
		int dow = dateTime.getWeekDay();
		switch (dow) {
			case AcalDateTime.MONDAY: return context.getString(R.string.Mon);
			case AcalDateTime.TUESDAY: return context.getString(R.string.Tue);
			case AcalDateTime.WEDNESDAY: return context.getString(R.string.Wed);
			case AcalDateTime.THURSDAY: return context.getString(R.string.Thu);
			case AcalDateTime.FRIDAY: return context.getString(R.string.Fri);
			case AcalDateTime.SATURDAY: return context.getString(R.string.Sat);
			case AcalDateTime.SUNDAY: return context.getString(R.string.Sun);
		}
		
		//Shouldn't really be able to get here - note localisation settings don't seem to work properly.
		//See http://code.google.com/p/android/issues/detail?id=12679
		DateFormat shortDate = DateFormat.getDateInstance(DateFormat.SHORT);
		return shortDate.format(dateTime.toJavaDate());
	}
	
	public String getNiceTime(Context context, AcalDateTime start, AcalDateTime end, boolean use24HourFormat) {
		AcalDateTime now = new AcalDateTime().applyLocalTimeZone();
		DateFormat format;
		if (use24HourFormat) format = new SimpleDateFormat("HH:mm");
		else format = new SimpleDateFormat("hh:mmaa");
		if (start.getMillis() <= now.getMillis()) {
			if (end.getYear() == now.getYear() && end.getYearDay() == now.getYearDay())
				return format.format(end.toJavaDate()).toLowerCase();
			else 
				//multiday event
				return now.getDurationTo(end).getDays()+" "+context.getString(R.string.days);
		}
		else return format.format(start.toJavaDate()).toLowerCase();
	}
	
}
