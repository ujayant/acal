package com.morphoss.acal.widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.morphoss.acal.R;
import com.morphoss.acal.davacal.ZoneData;

public class TimeZoneListAdapter implements SpinnerAdapter {

	private final static String TAG = "aCal TimeZoneListAdapter";
	Context context;

	private class Zone {
		final String displayName;
		final String tzid;
		
		Zone( String name, String id ) {
			displayName = name;
			tzid = id;
		}
	}

	/**
	 * Get a list of countries of people using aCal and apply to this
	 */
	ArrayList<Zone> ourZones;
	
	TimeZoneListAdapter( Context context, TimeZone currentTz ) {
		super();
		this.context = context;
		String[] zoneNames = context.getResources().getStringArray(R.array.timezoneNameList);
		ourZones = new ArrayList<Zone>(ZoneData.zones.length + 10 );
		String currentTzId = (currentTz == null ? null : currentTz.getID());
		
		Map<String,Zone> zoneMap = new HashMap<String,Zone>(ZoneData.zones.length);
		Zone aZone;
		for( int i=0; i< ZoneData.zones.length; i++ ) {
			aZone = new Zone(zoneNames[i], ZoneData.zones[i][0]);
			zoneMap.put(ZoneData.zones[i][0],aZone);
		}
		if ( currentTzId != null ) {
			aZone = zoneMap.get(currentTzId); 
			if ( aZone == null ) aZone = new Zone(currentTzId,currentTzId);
			else zoneMap.remove(currentTzId);
			ourZones.add(aZone);
		}
		String floatingZone = context.getString(R.string.floatingTime);
		ourZones.add(new Zone(floatingZone, null));
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String recentZone;
		for( int i=0; i<10; i++ ) {
			recentZone = prefs.getString("recentTimeZone-"+i, null);
			if ( recentZone != null ) {
				aZone = zoneMap.get(recentZone); 
				if ( aZone != null && !recentZone.equals(currentTzId) && !recentZone.equals(floatingZone) ) {
					zoneMap.remove(recentZone);
					ourZones.add(aZone);
				}
			}
		}
		
		for( int i=0; i< ZoneData.zones.length; i++ ) {
			aZone = zoneMap.get(ZoneData.zones[i][0]);
			if ( aZone != null ) ourZones.add(aZone);
		}

		ourZones.add(new Zone("UTC", "UTC"));
	}

	public int getPositionOf(String tzid) {
		Zone z;
		for( int i=0; i<ourZones.size(); i++ ) {
			z = ourZones.get(i);
			if ( z.tzid == null ) {
				if ( tzid == null) return i;
			}
			else if ( z.tzid.equals(tzid) )
				return i; 
		}
		return 0;
	}

	public String getTzId(int position) {
		return ourZones.get(position).tzid;
	}

	@Override
	public int getCount() {
		return ourZones.size();
	}

	@Override
	public Object getItem(int position) {
		return ourZones.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public int getItemViewType(int position) {
		return 0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		TextView rowLayout;

		if ( convertView != null && convertView instanceof TextView )
			rowLayout = (TextView) convertView;
		else {
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowLayout = (TextView) inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
		}

		try {
			rowLayout.setText(ourZones.get(position).displayName);
		}
		catch ( Exception e ) {
			Log.e(TAG, "Problem setting zone name", e);
		}
		return rowLayout;
	}

	@Override
	public int getViewTypeCount() {
		return 1;
	}

	@Override
	public boolean hasStableIds() {
		return true;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public void registerDataSetObserver(DataSetObserver observer) {
		// @todo Auto-generated method stub

	}

	@Override
	public void unregisterDataSetObserver(DataSetObserver observer) {
		// @todo Auto-generated method stub

	}

	@Override
	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		CheckedTextView rowLayout;

		if ( convertView != null && convertView instanceof CheckedTextView )
			rowLayout = (CheckedTextView) convertView;
		else {
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowLayout = (CheckedTextView) inflater.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
		}

		try {
			rowLayout.setText(ourZones.get(position).displayName);
		}
		catch ( Exception e ) {
			Log.e(TAG, "Problem setting zone name in dropdown list", e);
		}
		return rowLayout;
	}

}
