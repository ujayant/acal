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

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.GestureDetector.OnGestureListener;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.morphoss.acal.Constants;
import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.acaltime.AcalRepeatRule;
import com.morphoss.acal.davacal.AcalAlarm;
import com.morphoss.acal.davacal.AcalEventAction;
import com.morphoss.acal.davacal.SimpleAcalEvent;
import com.morphoss.acal.davacal.AcalEventAction.EVENT_FIELD;
import com.morphoss.acal.service.aCalService;

public class EventView extends Activity implements OnGestureListener, OnTouchListener, OnClickListener{

	public static final String TAG = "aCal EventView";
	public static final int TODAY = 0;
	public static final int EDIT = 1;
	public static final int ADD = 2;
	public static final int SHOW_ON_MAP = 3;
	
	public static final int EDIT_EVENT = 0;
	
	//private GestureDetector gestureDetector;
	
	//private AcalDateTime currentDate;
	private AcalEventAction event;
	private SharedPreferences prefs;	
	
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.event_view);

		//Ensure service is actually running
		this.startService(new Intent(this, aCalService.class));
		//gestureDetector = new GestureDetector(this);

		// Get preferences
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		//Set up buttons
		this.setupButton(R.id.event_today_button, TODAY);
		this.setupButton(R.id.event_edit_button, EDIT);
		this.setupButton(R.id.event_add_button, ADD);
		
		
		Bundle b = this.getIntent().getExtras();
		try {
			if (b.containsKey("Event")) {
				this.event = ((AcalEventAction) b.getParcelable("Event"));
			}
			else if ( b.containsKey("SimpleAcalEvent") ) {
				this.event = new AcalEventAction(this,(SimpleAcalEvent) b.getParcelable("SimpleAcalEvent"));
			}
			this.populateLayout();
		}
		catch (Exception e) {
			if (Constants.LOG_DEBUG)Log.d(TAG, "Error getting data from caller: "+e.getMessage());
		}
		
		Button map = (Button) this.findViewById(R.id.EventFindOnMapButton);
		map.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				
				String loc = (String)event.getField(EVENT_FIELD.location);
				//replace whitespaces with '+'
				loc.replace("\\s", "+");
				Uri target = Uri.parse("geo:0,0?q="+loc);
				startActivity(new Intent(android.content.Intent.ACTION_VIEW, target)); 
				//start map view
				return;

			}
		});
		
	}
	
	private void populateLayout() {
		AcalDateTime start = (AcalDateTime)event.getField(EVENT_FIELD.startDate);
		String title = (String)event.getField(EVENT_FIELD.summary);
		String location = (String)event.getField(EVENT_FIELD.location);
		String description = (String)event.getField(EVENT_FIELD.description);
		StringBuilder alarms = new StringBuilder();
		List<?> alarmList = (List<?>) event.getField(EVENT_FIELD.alarmList);
		for (Object alarm : alarmList) {
			if ( alarm instanceof AcalAlarm ) {
				if ( alarms.length() > 0 ) alarms.append('\n');
				alarms.append(((AcalAlarm)alarm).toPrettyString());
			}
		}
		
		String repetition = (String) event.getField(EVENT_FIELD.repeatRule);
		int colour = (Integer)event.getField(EVENT_FIELD.colour);
		LinearLayout sidebar = (LinearLayout)this.findViewById(R.id.EventViewColourBar);
		sidebar.setBackgroundColor(colour);
		
		TextView name = (TextView) this.findViewById(R.id.EventName);
		name.setText(title);
		name.setTextColor(colour);
		
		AcalDateTime viewDate = new AcalDateTime();
		viewDate.applyLocalTimeZone();
		viewDate.setDaySecond(0);
		TextView time = (TextView) this.findViewById(R.id.EventTimeContent);
		time.setText(event.getTimeText(viewDate, AcalDateTime.addDays(viewDate, 1),prefs.getBoolean(getString(R.string.prefTwelveTwentyfour), false)));
		time.setTextColor(colour);

		TextView titlebar = (TextView)this.findViewById(R.id.EventViewTitle);
		titlebar.setText(time.getText());
		

		TextView locationView = (TextView) this.findViewById(R.id.EventLocationContent);
		if ( location != null && ! location.equals("") ) {
			locationView.setText(location);
		}
		else {
			RelativeLayout locationLayout = (RelativeLayout) this.findViewById(R.id.EventLocationLayout);
			locationLayout.setVisibility(View.GONE);
		}

		TextView notesView = (TextView) this.findViewById(R.id.EventNotesContent);
		if ( description != null && ! description.equals("") ) {
			notesView.setText(description);
		}
		else {
			RelativeLayout notesLayout = (RelativeLayout) this.findViewById(R.id.EventNotesLayout);
			notesLayout.setVisibility(View.GONE);
		}
		
		TextView alarmsView = (TextView) this.findViewById(R.id.EventAlarmsContent);
		if ( alarms != null && ! alarms.equals("") ) {
			alarmsView.setText(alarms);
			if ( !event.event.getAlarmEnabled() ) {
				TextView alarmsWarning = (TextView) this.findViewById(R.id.CalendarAlarmsDisabled);
				alarmsWarning.setVisibility(View.VISIBLE);
			}
		}
		else {
			RelativeLayout alarmsLayout = (RelativeLayout) this.findViewById(R.id.EventAlarmsLayout);
			alarmsLayout.setVisibility(View.GONE);
		}
		
		TextView repeatsView = (TextView) this.findViewById(R.id.EventRepeatsContent);
		AcalRepeatRule RRule = new AcalRepeatRule(start, repetition); 
		String rr = RRule.repeatRule.toPrettyString(this);
		if (rr == null || rr.equals("")) rr = getString(R.string.OnlyOnce);
		repeatsView.setText(rr);
		
		
	}
	
	private void setupButton(int id, int val) {
		Button button = (Button) this.findViewById(id);
		button.setOnClickListener(this);
		button.setTag(val);
	}

	@Override
	public boolean onDown(MotionEvent arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onFling(MotionEvent arg0, MotionEvent arg1, float arg2,
			float arg3) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onLongPress(MotionEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean onScroll(MotionEvent arg0, MotionEvent arg1, float arg2,
			float arg3) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onShowPress(MotionEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean onSingleTapUp(MotionEvent arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onTouch(View arg0, MotionEvent arg1) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onClick(View arg0) {
		int button = (int)((Integer)arg0.getTag());
		switch (button) {
			case EDIT: {
				//start event activity
				Bundle bundle = new Bundle();
				bundle.putParcelable("Event", event);
				Intent eventEditIntent = new Intent(this, EventEdit.class);
				eventEditIntent.putExtras(bundle);
				this.startActivityForResult(eventEditIntent,EDIT_EVENT);
				break;
			}
			case ADD: {
				Bundle bundle = new Bundle();
				bundle.putParcelable("DATE", (AcalDateTime)event.getField(EVENT_FIELD.startDate));
				Intent eventEditIntent = new Intent(this, EventEdit.class);
				eventEditIntent.putExtras(bundle);
				this.startActivity(eventEditIntent);
				break;
			}
			case TODAY: {
				AcalDateTime selectedDate = new AcalDateTime();
				Intent res = new Intent();
				res.putExtra("selectedDate", (Parcelable) selectedDate);
				this.setResult(RESULT_OK, res);
				this.finish();
				break;
			}
		}
		
	}
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (requestCode == EDIT_EVENT && resultCode == RESULT_OK) {
    		if (data.hasExtra("changedEvent")) {
    			event  = (AcalEventAction)data.getParcelableExtra("changedEvent");
    			populateLayout();
    		}
    	}
    }
	
}
