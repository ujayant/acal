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


import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.davacal.AcalEvent;
import com.morphoss.acal.views.MonthDayBox;

/**
 * TODO This class needs some DOC!
 * @author Morphoss Ltd
 *
 */
public class MonthAdapter extends BaseAdapter {

	private MonthView context;
	private AcalDateTime prevMonth;
	private AcalDateTime nextMonth;
	private AcalDateTime displayDate;
	private AcalDateTime selectedDate;
	private int daysInLastMonth;
	private int daysInThisMonth;
	private int firstOffset;
	private int firstCol;
	private HashMap<Integer,View> views;

	public MonthAdapter(MonthView monthview, AcalDateTime displayDate, AcalDateTime selectedDate) {
		this.displayDate = displayDate;
		this.selectedDate = selectedDate;
		this.context = monthview;
		
		getFirstDay(monthview);

		//Get next and previous months
		this.prevMonth = (AcalDateTime) displayDate.clone();
		this.prevMonth.set(AcalDateTime.DAY_OF_MONTH, 1);
		this.nextMonth = (AcalDateTime) this.prevMonth.clone();
		int curMonth = displayDate.get(AcalDateTime.MONTH);
		int curYear = displayDate.get(AcalDateTime.YEAR);
		if (curMonth > AcalDateTime.JANUARY) this.prevMonth.set(AcalDateTime.MONTH, curMonth-1);
		else {
			this.prevMonth.set(AcalDateTime.MONTH, AcalDateTime.DECEMBER);
			this.prevMonth.set(AcalDateTime.YEAR, curYear-1);
		}


		if (curMonth < AcalDateTime.DECEMBER) this.nextMonth.set(AcalDateTime.MONTH, curMonth+1);
		else {
			this.nextMonth.set(AcalDateTime.MONTH, AcalDateTime.JANUARY);
			this.nextMonth.set(AcalDateTime.YEAR, curYear+1);
		}

		//How many days in prev month?
		this.daysInLastMonth = this.prevMonth.getActualMaximum(AcalDateTime.DAY_OF_MONTH);

		//How many days in this month?
		this.daysInThisMonth = displayDate.getActualMaximum(AcalDateTime.DAY_OF_MONTH);

		//what day does the first fall on?
		int curDay = displayDate.get(AcalDateTime.DAY_OF_MONTH);
		displayDate.set(AcalDateTime.DAY_OF_MONTH, 1);
		this.firstOffset = displayDate.get(AcalDateTime.DAY_OF_WEEK);
		displayDate.set(AcalDateTime.DAY_OF_MONTH, curDay);
	}


	private void getFirstDay(Context context) {
		//check preferred first day
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String day = prefs.getString(context.getString(R.string.firstDayOfWeek), context.getString(R.string.Monday));
		if (day.equalsIgnoreCase(context.getString(R.string.Monday)))
			this.firstCol = AcalDateTime.MONDAY;
		else if (day.equalsIgnoreCase(context.getString(R.string.Tuesday)))
			this.firstCol = AcalDateTime.TUESDAY;
		else if (day.equalsIgnoreCase(context.getString(R.string.Wednesday)))
			this.firstCol = AcalDateTime.WEDNESDAY;
		else if (day.equalsIgnoreCase(context.getString(R.string.Thursday)))
			this.firstCol = AcalDateTime.THURSDAY;
		else if (day.equalsIgnoreCase(context.getString(R.string.Friday)))
			this.firstCol = AcalDateTime.FRIDAY;
		else if (day.equalsIgnoreCase(context.getString(R.string.Saturday)))
			this.firstCol = AcalDateTime.SATURDAY;
		else if (day.equalsIgnoreCase(context.getString(R.string.Sunday)))
			this.firstCol = AcalDateTime.SUNDAY;

	}
	
	public int getCount() {
		return 7*7;	//number of rows needed * 7
	}

	public Object getItem(int position) { return null; }

	public long getItemId(int position) { return 0; }

	public View getView(int position, View contentView, ViewGroup parent) {

		if (position <7) {
			//Column headers
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			TextView dayBox = null;
			View v = (View) inflater.inflate(R.layout.month_view_assets, null);
			dayBox = (TextView) v.findViewById(R.id.DayBoxColumnHeader);
			int day = (position+firstCol)%7;
			String colText = "";
			switch (day) {
				case AcalDateTime.MONDAY: colText=(context.getString(R.string.Mon)); break;
				case AcalDateTime.TUESDAY: colText=(context.getString(R.string.Tue)); break;
				case AcalDateTime.WEDNESDAY: colText=(context.getString(R.string.Wed)); break;
				case AcalDateTime.THURSDAY: colText=(context.getString(R.string.Thu)); break;
				case AcalDateTime.FRIDAY: colText=(context.getString(R.string.Fri)); break;
				case AcalDateTime.SATURDAY: colText=(context.getString(R.string.Sat)); break;
				case AcalDateTime.SUNDAY: colText=(context.getString(R.string.Sun)); break;
			}
			dayBox.setText(colText);
			dayBox.setVisibility(View.VISIBLE);
			return v;
		}
		position -=7;
		//we need to correct for offset
		int offset = this.firstOffset-this.firstCol;
		if (offset<0) offset+=7;




		AcalDateTime bDate = null;
		boolean in_month = false;


		//What day of the month are we?
		if (position < offset) {
			//previous month
			bDate = (AcalDateTime)this.prevMonth.clone();
			bDate.set(AcalDateTime.DAY_OF_MONTH, this.daysInLastMonth-((offset-1)-position)); 
		} else if (position > offset+this.daysInThisMonth-1) {
			//next month
			bDate = (AcalDateTime)this.nextMonth.clone();
			bDate.set(AcalDateTime.DAY_OF_MONTH,  (position+1) - (offset+this.daysInThisMonth));
		} else {
			//this month
			in_month = true;
			bDate = (AcalDateTime) displayDate.clone();
			bDate.set(AcalDateTime.DAY_OF_MONTH, position-offset+1);
		}
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = (View) inflater.inflate(R.layout.month_view_assets, null);
		
		MonthDayBox mDayBox = null; 
	
		if ( in_month && bDate.get(AcalDateTime.DAY_OF_YEAR) == this.selectedDate.get(AcalDateTime.DAY_OF_YEAR) && this.selectedDate.getYear() == this.displayDate.getYear() ) {
			mDayBox = (MonthDayBox) v.findViewById(R.id.DayBoxHighlightDay);
			mDayBox.setEvents(context.getEventsForDay(bDate));
		} else if ( in_month ) {
			mDayBox = (MonthDayBox) v.findViewById(R.id.DayBoxInMonth);
			mDayBox.setEvents(context.getEventsForDay(bDate));
		} else if   ((bDate.get(AcalDateTime.DAY_OF_YEAR) == this.selectedDate.get(AcalDateTime.DAY_OF_YEAR))&&
				(bDate.get(AcalDateTime.YEAR) == this.selectedDate.get(AcalDateTime.YEAR))) {
			mDayBox = (MonthDayBox) v.findViewById(R.id.DayBoxOutMonthHighlighted);
		} else {
			mDayBox = (MonthDayBox) v.findViewById(R.id.DayBoxOutMonth);
		}
		
		mDayBox.setVisibility(View.VISIBLE);
		mDayBox.setText(bDate.get(AcalDateTime.DAY_OF_MONTH)+"");
		
		v.setTag(bDate);
		v.setOnClickListener(new MonthButtonListener());
		v.setOnTouchListener(this.context);
		return v;
	}
	
			

	private class MonthButtonListener implements OnClickListener {

		@Override
		public void onClick(View arg0) {
			AcalDateTime date = (AcalDateTime)arg0.getTag();
			if ( !AcalDateTime.isWithinMonth(date, displayDate)) {
				context.changeDisplayedMonth(date);
			}
			context.changeSelectedDate(date);
		}

	}

	public void updateSelectedDay(AcalDateTime selectedDate) {
		this.selectedDate = selectedDate;
		this.notifyDataSetChanged();
	}

}
