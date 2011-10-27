package com.morphoss.acal.widget;

import java.util.ArrayList;
import java.util.TimeZone;

import android.content.Context;
import android.database.DataSetObserver;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

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
	Zone[] commonZones = {
			new Zone("UK & Ireland","Europe/London"),	
			new Zone("Portugal","Europe/Lisbon"),	
			new Zone("Central European time","Europe/Berlin"),	
			new Zone("France, Belgium","Europe/Paris"),	
			new Zone("Spain","Europe/Madrid"),	
			new Zone("Czech Republic","Europe/Prague"),	
			new Zone("Japan","Asia/Tokyo"),	
			new Zone("Beijing","Asia/Beijing"),	
			new Zone("Hong Kong","Asia/Hong_Kong"),	
			new Zone("New Zealand time","Pacific/Auckland"),	
			new Zone("Australia - Queensland","Australia/Brisbane"),	
			new Zone("Australia - NSW, Victoria & Tasmania","Australia/Sydney"),	
			new Zone("Australia - South Australia","Australia/Adelaide"),	
			new Zone("USA Pacific time","America/Los_Angeles"),	
			new Zone("USA Mountain time","America/Denver"),	
			new Zone("USA Central time","America/Chicago"),	
			new Zone("USA Eastern time","America/New_York"),	
	};

	ArrayList<Zone> ourZones;
	
	TimeZoneListAdapter( Context context, TimeZone currentTz ) {
		super();
		this.context = context;
		ourZones = new ArrayList<Zone>(commonZones.length + 10 );
		boolean found = false;
		String currentTzId = (currentTz == null ? null : currentTz.getID());
		for( Zone z : commonZones )	{
			ourZones.add(z);
			if ( currentTzId != null && currentTzId.equals(z.tzid) ) found = true;
		}
		if ( currentTz != null && !found ) {
			// Add the current timezone into the first position in the list
			ourZones.add(0, new Zone(currentTzId,currentTzId) );
		}

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
