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

package com.morphoss.acal.weekview;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.GestureDetector.OnGestureListener;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;

import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.activity.EventEdit;
import com.morphoss.acal.widget.NumberPickerDialog;
import com.morphoss.acal.widget.NumberSelectedListener;

/**
 * This is the activity behind WeekView. It catches all UI Events and user interaction.
 *
 * Valid user input is passed on to the WeekViewLayout, responsible for drawing all the components in this activity.
 *  
 * 
 * @author Morphoss Ltd
 * @license GPL v3 or later
 */
public class WeekViewActivity extends Activity implements OnGestureListener, OnTouchListener, NumberSelectedListener, OnClickListener {
	/* Fields relating to buttons */
	public static final int TODAY = 0;
	public static final int MONTH = 2;
	public static final int ADD = 3;

	public static final String TAG = "aCal YearView";
	
	private WeekViewHeader 	header;
	private WeekViewSideBar sidebar;
	private WeekViewMultiDay multiday;
	private WeekViewDays	days;

	//Size options in pixels
	public static final int DAY_WIDTH = 100;
	public static final int HALF_HOUR_HEIGHT = 20;
	
	private static final int DATE_PICKER = 0;
	
	/* Fields Relating to Gesture Detection */
	private GestureDetector gestureDetector;
	private AcalDateTime selectedDate = new AcalDateTime();
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		gestureDetector = new GestureDetector(this);
		this.selectedDate = this.getIntent().getExtras().getParcelable("StartDay");
		this.setContentView(R.layout.week_view);
		header 	= (WeekViewHeader) 	this.findViewById(R.id.week_view_header);
		sidebar = (WeekViewSideBar) this.findViewById(R.id.week_view_sidebar);
		multiday= (WeekViewMultiDay)this.findViewById(R.id.week_view_multi_day);
		days 	= (WeekViewDays) 	this.findViewById(R.id.week_view_days);
		
		// Set up buttons
		this.setupButton(R.id.year_today_button, TODAY);
		this.setupButton(R.id.year_month_button, MONTH);
		this.setupButton(R.id.year_add_button, ADD);

	}
	
	@Override 
	public void onPause() {
		super.onPause();
	}
	
	@Override
	public void onResume() {
		super.onResume();
	}
	
	/**
	 * <p>
	 * Helper method for setting up buttons
	 * </p>
	 */
	private void setupButton(int id, int val) {
		Button today = (Button) this.findViewById(id);
		if (today == null) {
			Log.e(TAG, "Cannot find button '" + id + "' by ID, to set value '"
					+ val + "'");
			Log.i(TAG, Log.getStackTraceString(new Exception()));
		} else {
			today.setOnClickListener(this);
			today.setTag(val);
		}
	}
	
	private void dateChanged() {
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return gestureDetector.onTouchEvent(event);
	}
	@Override
	public boolean onTouch(View view, MotionEvent touch) {
		return this.gestureDetector.onTouchEvent(touch);
	}
	
	@Override
	public boolean onDown(MotionEvent arg0) {
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
		showDialog(DATE_PICKER);
	}
	@Override
	public boolean onScroll(MotionEvent start, MotionEvent current, float dx, float dy) {
		return false;
	}
	@Override
	public void onShowPress(MotionEvent arg0) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public boolean onSingleTapUp(MotionEvent arg0) {
		return false;
	}
	
	
	@Override 
	public void onNumberSelected(int number) {
		selectedDate = new AcalDateTime(number,1,1,0,0,0,null);
		this.dateChanged();
	}
	
	protected Dialog onCreateDialog(int id) {
		switch(id) {
			case DATE_PICKER:
			NumberPickerDialog dialog = new NumberPickerDialog(this,this,selectedDate.getYear(),1582,3999);
			return dialog;
		}
		return null;
		
	}
	/**
	 * <p>
	 * Handles button Clicks
	 * </p>
	 */
	@Override
	public void onClick(View clickedView) {
		int button = (int) ((Integer) clickedView.getTag());
		switch (button) {
		case TODAY:
			break;
		case ADD:
			Bundle bundle = new Bundle();
			bundle.putParcelable("DATE", this.selectedDate);
			Intent eventEditIntent = new Intent(this, EventEdit.class);
			eventEditIntent.putExtras(bundle);
			this.startActivity(eventEditIntent);
			break;
		case MONTH:
			this.finish();
			break;
		default:
			Log.w(TAG, "Unrecognised button was pushed in MonthView.");
		}
	}
}
