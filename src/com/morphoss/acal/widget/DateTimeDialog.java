/*
 * Copyright (C) 2011 Morphoss Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
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

package com.morphoss.acal.widget;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.DatePicker;
import android.widget.DatePicker.OnDateChangedListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.TimePicker.OnTimeChangedListener;

import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDateTimeFormatter;

/**
 * @author Morphoss Ltd
 */

public class DateTimeDialog extends Dialog
		implements	OnClickListener, OnTimeChangedListener, OnDateChangedListener,
					OnCheckedChangeListener, OnItemSelectedListener
{

	private Context context;
	private DateTimeSetListener dialogListener;

	private Button setButton;
	private Button cancelButton;
	private TextView dateTimeText;
	private DatePicker datePicker;
	private TimePicker timePicker;
	private CheckBox dateOnlyCheckBox;
	private Spinner timeZoneSpinner; 
	private TimeZoneListAdapter tzListAdapter;

	private AcalDateTime currentDateTime;
	private final boolean use24HourTime;

	public DateTimeDialog(Context context, String dialogTitle, AcalDateTime dateTimeValue, boolean twentyFourHourTime, DateTimeSetListener listener )  {
    	super(context);
    	this.context = context;
        this.dialogListener = listener;
        use24HourTime = twentyFourHourTime;
        setContentView(R.layout.datetime_dialog);

        this.setTitle(dialogTitle);
        
        currentDateTime = (dateTimeValue == null ? new AcalDateTime() : dateTimeValue.clone());
        
        setButton = (Button)this.findViewById(R.id.DateTimeSetButton);
        cancelButton = (Button)this.findViewById(R.id.DateTimeCancelButton);
        setButton.setOnClickListener(this);
        cancelButton.setOnClickListener(this);

        dateTimeText = (TextView) this.findViewById(R.id.DateTimeText);

        datePicker = (DatePicker) this.findViewById(R.id.datePicker);
        datePicker.init(currentDateTime.getYear(), currentDateTime.getMonth() - 1, currentDateTime.getMonthDay(),this);

        timePicker = (TimePicker) this.findViewById(R.id.timePicker);
        timePicker.setIs24HourView(twentyFourHourTime);
        timePicker.setCurrentHour((int) currentDateTime.getHour());
        timePicker.setCurrentMinute((int) currentDateTime.getMinute());
        timePicker.setOnTimeChangedListener(this);

        dateOnlyCheckBox = (CheckBox) this.findViewById(R.id.DateTimeIsDate);
        dateOnlyCheckBox.setChecked(currentDateTime.isDate());
        dateOnlyCheckBox.setOnCheckedChangeListener(this);

        timeZoneSpinner = (Spinner) this.findViewById(R.id.DateTimeZoneSelect);
        tzListAdapter = new TimeZoneListAdapter(this.context, currentDateTime.getTimeZone());
        timeZoneSpinner.setAdapter(tzListAdapter);
        timeZoneSpinner.setSelection(tzListAdapter.getPositionOf(currentDateTime.getTimeZoneId()));
        timeZoneSpinner.setOnItemSelectedListener(this);

        updateLayout();
    }

	
	private void updateLayout() {
		timePicker.setEnabled(!dateOnlyCheckBox.isChecked());
    	dateTimeText.setText(AcalDateTimeFormatter.fmtFull(currentDateTime,use24HourTime));
	}


	private void toggleIsDate( boolean isDate ) {
		currentDateTime.setAsDate(isDate);
		updateLayout();
	}

	
	@Override
	public void onClick(View v) {
		if (v == setButton)
			dialogListener.onDateTimeSet(currentDateTime);

		this.dismiss();
	}

	
	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if ( buttonView == dateOnlyCheckBox ) 		toggleIsDate(isChecked);
	}


	@Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
		currentDateTime.setTimeZone(tzListAdapter.getTzId(timeZoneSpinner.getSelectedItemPosition()));
		updateLayout();
    }

	
	@Override
    public void onNothingSelected(AdapterView<?> parent) {
      // Do nothing.
    }




	@Override
	public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
		currentDateTime.setHour(hourOfDay);
		currentDateTime.setMinute(minute);
		updateLayout();
	}


	@Override
	public void onDateChanged(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
		currentDateTime.setYearMonthDay(year, monthOfYear+1, dayOfMonth);
		updateLayout();
	}
}
