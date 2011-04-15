/*
 * Copyright (C) 2011 Morphoss Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.morphoss.acal.activity;

import android.content.ContentValues;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.provider.MediaStore;
import android.util.Log;

import com.morphoss.acal.R;
import com.morphoss.acal.providers.DavCollections;

public class AcalPreferences extends PreferenceActivity {

	public static final String TAG = "AcalPreferences";

	@Override 
	public void onCreate(Bundle savedInstanceState) { 
	     try {
		     super.onCreate(savedInstanceState); 
		     addPreferencesFromResource(R.xml.main_preferences);
		     this.addDefaultCollectionPreference();
		     this.addDefaultAlarmTonePreference();
	     }
	     catch( Exception e ) {
	    	 Log.d(TAG,Log.getStackTraceString(e));
	     }
	 }

	/**
	 * This is a good example of how to programatically alter a preference.
	 * All preferences should be at least partly defined in the XML.
	 */
	private void addDefaultCollectionPreference() {
		ContentValues[] collections = DavCollections.getCollections(getContentResolver(),
																DavCollections.INCLUDE_EVENTS);
		if ( collections.length == 0 ) return;
	    ListPreference defaultCollection = (ListPreference)this.getPreferenceScreen().getPreferenceManager().findPreference(getString(R.string.DefaultCollection_PrefKey));
	     
    	//auth
		String names[] = new String[collections.length];
		String ids[] = new String[collections.length];
		int count = 0;
		for (ContentValues cv : collections) {
			names[count] = cv.getAsString(DavCollections.DISPLAYNAME);
			ids[count++] = cv.getAsString(DavCollections._ID);
		}
    	defaultCollection.setEntries(names);
    	defaultCollection.setEntryValues(ids);
   		defaultCollection.setDefaultValue(ids[0]);
   		defaultCollection.setSelectable(true);
   		defaultCollection.setEnabled(true);
	}

	
	//Alarm Tones
	private void addDefaultAlarmTonePreference() {
		//List<ContentValues> alarmTones = getSelectableAlarmTones();
		//if (alarmTones == null || alarmTones.isEmpty()) return;
	    ListPreference defaultAlarm = (ListPreference)this.getPreferenceScreen().getPreferenceManager().findPreference(getString(R.string.DefaultAlarmTone_PrefKey));
	    RingtoneManager rm = new RingtoneManager(this);
		Cursor cursor = rm.getCursor();
		int count = cursor.getCount();
		if (count < 1) {
			return;
		}
		int titleColumn =  cursor.getColumnIndex(MediaStore.MediaColumns.TITLE);
		if ( titleColumn < 0 ) {
			titleColumn =  cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
			if ( titleColumn < 0 ) {
				return;
			}
		}
		String names[] = new String[count+1];
		String uris[] = new String[count+1];
		names[0] = getString(R.string.DefaultAlarmTone);
		uris[0] = "null";
		cursor.moveToFirst();
	    for (int i = 0; i < count; i++) {
	    	names[i+1] = cursor.getString(titleColumn);
	    	uris[i+1] = rm.getRingtoneUri(i).toString();
	    	cursor.moveToNext();
	    }
    	defaultAlarm.setEntries(names);
    	defaultAlarm.setEntryValues(uris);
   		defaultAlarm.setDefaultValue(uris[0]);
   		defaultAlarm.setSelectable(true);
   		defaultAlarm.setEnabled(true);
	}
	
	
	
	@Override
	public void onResume() {
		super.onResume();
	}
	
}
