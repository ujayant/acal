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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.GestureDetector.OnGestureListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.morphoss.acal.Constants;
import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.acaltime.AcalRepeatRule;
import com.morphoss.acal.dataservice.CalendarDataService;
import com.morphoss.acal.dataservice.DataRequest;
import com.morphoss.acal.davacal.AcalAlarm;
import com.morphoss.acal.davacal.AcalAlarm.ActionType;
import com.morphoss.acal.davacal.AcalCollection;
import com.morphoss.acal.davacal.SimpleAcalTodo;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.davacal.VTodo;
import com.morphoss.acal.davacal.VTodo.TODO_FIELD;
import com.morphoss.acal.providers.DavCollections;
import com.morphoss.acal.service.aCalService;

public class TodoEdit extends Activity implements OnGestureListener, OnTouchListener, OnClickListener, OnCheckedChangeListener {

	public static final String TAG = "aCal TodoEdit";
	public static final int APPLY = 0;
	public static final int CANCEL = 1;

	private SimpleAcalTodo sat;
	private VTodo todo;

	public static final int ACTION_NONE = -1;
	public static final int ACTION_CREATE = 0;
	public static final int ACTION_MODIFY_SINGLE = 1;
	public static final int ACTION_MODIFY_ALL = 2;
	public static final int ACTION_MODIFY_ALL_FUTURE = 3;
	public static final int ACTION_DELETE_SINGLE = 4;
	public static final int ACTION_DELETE_ALL = 5;
	public static final int ACTION_DELETE_ALL_FUTURE = 6;

	private int action = ACTION_NONE;
	
	
	private static final int FROM_DATE_DIALOG = 0;
	private static final int FROM_TIME_DIALOG = 1;
	private static final int UNTIL_DATE_DIALOG = 2;
	private static final int UNTIL_TIME_DIALOG = 3;
	private static final int SELECT_COLLECTION_DIALOG = 4;
	private static final int ADD_ALARM_DIALOG = 5;
	private static final int SET_REPEAT_RULE_DIALOG = 6;
	private static final int WHICH_TODO_DIALOG = 7;

	private SharedPreferences prefs;
	boolean prefer24hourFormat = false;
	
	private String[] repeatRules;
	private String[] todoChangeRanges; // See strings.xml R.array.TodoChangeAffecting
		
	// Must match R.array.RelativeAlarmTimes (strings.xml)
	private String[] alarmRelativeTimeStrings;
	private static final AcalDuration[] alarmValues = new AcalDuration[] {
		new AcalDuration(),
		new AcalDuration("-PT10M"),
		new AcalDuration("-PT30M"),
		new AcalDuration("-PT1H"),
		new AcalDuration("-PT2H"),
		new AcalDuration("-PT12H"),
		new AcalDuration("-P1D")
	};
	
	private String[] repeatRulesValues;
	
	private DataRequest dataRequest = null;

	//GUI Components
	private TextView fromLabel;
	private TextView untilLabel;
	private Button fromDate;
	private Button untilDate;
	private Button fromTime;
	private Button untilTime;	
	private Button applyButton;	
	private LinearLayout sidebar;
	private TextView todoName;
	private TextView locationView;
	private TextView notesView;
	private TableLayout alarmsList;
	private RelativeLayout alarmsLayout;
	private Button repeatsView;
	private Button alarmsView;
	private Button collection;
	private CheckBox allDayTodo;
	
	//Active collections for create mode
	private ContentValues[] activeCollections;
	private ContentValues currentCollection;	//currently selected collection
	private String[] collectionsArray;

	private List<AcalAlarm> alarmList;
	
	private boolean originalHasOccurrence = false;
	private String originalOccurence = "";
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.todo_edit);

		//Ensure service is actually running
		startService(new Intent(this, aCalService.class));
		connectToService();

		// Get time display preference
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefer24hourFormat = prefs.getBoolean(getString(R.string.prefTwelveTwentyfour), false);

		alarmRelativeTimeStrings = getResources().getStringArray(R.array.RelativeAlarmTimes);
		todoChangeRanges = getResources().getStringArray(R.array.TodoChangeAffecting);
		
		//Set up buttons
		this.setupButton(R.id.todo_apply_button, APPLY);
		this.setupButton(R.id.todo_cancel_button, CANCEL);

		Bundle b = this.getIntent().getExtras();
		getTodoAction(b);
		if ( this.todo == null ) {
			Log.d(TAG,"Unable to create VTodo object");
			return;
		}
		this.populateLayout();
	}

	
	private VTodo getTodoAction(Bundle b) {
		int operation = SimpleAcalTodo.TODO_OPERATION_EDIT;
		if ( b != null && b.containsKey("SimpleAcalTodo") ) {
			this.sat = (SimpleAcalTodo) b.getParcelable("SimpleAcalTodo");
			operation = sat.operation;
			try {
				if (Constants.LOG_DEBUG)
					Log.d(TAG, "Loading Todo: "+sat.summary );
				VComponent vc = VComponent.fromDatabase(this, sat.resourceId);
				this.todo = (VTodo) ((VCalendar) vc).getMasterChild();
			}
			catch( Exception e ) {
				Log.e(TAG,Log.getStackTraceString(e));
			}
		}

		//Get collection data
		activeCollections = DavCollections.getCollections( getContentResolver(), DavCollections.INCLUDE_TASKS );
		int collectionId = -1;
		if ( activeCollections.length > 0 )
			collectionId = activeCollections[0].getAsInteger(DavCollections._ID);
		else {
			Toast.makeText(this, getString(R.string.errorMustHaveActiveCalendar), Toast.LENGTH_LONG);
			this.finish();	// can't work if no active collections
			return null;
		}
		this.collectionsArray = new String[activeCollections.length];
		int count = 0;
		for (ContentValues cv : activeCollections) {
			if (cv.getAsInteger(DavCollections._ID) == collectionId) this.currentCollection = cv;
			collectionsArray[count++] = cv.getAsString(DavCollections.DISPLAYNAME);
		}
		
		if ( operation == SimpleAcalTodo.TODO_OPERATION_EDIT ) {
			try {
				collectionId = (Integer) this.todo.getCollectionId();
				this.action = ACTION_MODIFY_ALL;
				if ( isModifyAction() ) {
					String rr = (String)  this.todo.getRepetition();
					if (rr != null && !rr.equals("") && !rr.equals(AcalRepeatRule.SINGLE_INSTANCE)) {
						this.originalHasOccurrence = true;
						this.originalOccurence = rr;
					}
					if (this.originalHasOccurrence) {
						this.action = ACTION_MODIFY_SINGLE;
					}
					else {
						this.action = ACTION_MODIFY_ALL;
					}
				}
			}
			catch (Exception e) {
				if (Constants.LOG_DEBUG)Log.d(TAG, "Error getting data from caller: "+e.getMessage());
			}
		}
		else if ( operation == SimpleAcalTodo.TODO_OPERATION_COPY ) {
			// Duplicate the todo into a new one.
			try {
				collectionId = todo.getCollectionId();
			}
			catch (Exception e) {
				if (Constants.LOG_DEBUG)Log.d(TAG, "Error getting data from caller: "+e.getMessage());
			}
			this.action = ACTION_CREATE;
		}

		if ( this.todo == null ) {

			Map<TODO_FIELD,Object> defaults = new HashMap<TODO_FIELD,Object>(10);
			defaults.put( TODO_FIELD.summary, getString(R.string.NewTaskTitle) );

			Integer preferredCollectionId = Integer.parseInt(prefs.getString(getString(R.string.DefaultCollection_PrefKey), "-1"));
			if ( preferredCollectionId != -1 ) {
				for( ContentValues aCollection : activeCollections ) {
					if ( preferredCollectionId == aCollection.getAsInteger(DavCollections._ID) ) {
						collectionId = preferredCollectionId;
						break;
					}
				}
			}
			AcalCollection collection = AcalCollection.fromDatabase(this, collectionId);
			defaults.put(TODO_FIELD.collectionId, collectionId);

			this.todo = new VTodo(collection);
			this.action = ACTION_CREATE;

		}
		
		return todo;
	}

	
	private void setSelectedCollection(String name) {
		for (ContentValues cv : activeCollections) {
			if (cv.getAsString(DavCollections.DISPLAYNAME).equals(name)) {
				this.currentCollection = cv; break;
			}
		}
		AcalCollection collection = new AcalCollection(currentCollection);
		VCalendar vc = (VCalendar) this.todo.getTopParent();
		vc.setCollection(collection);

		this.collection.setText(name);
		sidebar.setBackgroundColor(collection.getColour());
		this.updateLayout();
	}

	
	private void populateLayout() {

		//Todo Colour
		sidebar = (LinearLayout)this.findViewById(R.id.TodoEditColourBar);

		//Title
		this.todoName = (TextView) this.findViewById(R.id.TodoName);
		if ( todo == null || action == ACTION_CREATE ) {
			todoName.setSelectAllOnFocus(true);
		}

		//Collection
		this.collection = (Button) this.findViewById(R.id.TodoEditCollectionButton);
		if (this.activeCollections.length < 2) {
			this.collection.setEnabled(false);
			this.collection.setHeight(0);
			this.collection.setPadding(0, 0, 0, 0);
		}
		else {
			//set up click listener for collection dialog
			setListen(this.collection, SELECT_COLLECTION_DIALOG);
		}
		
		
		//date/time fields
		fromLabel = (TextView) this.findViewById(R.id.TodoFromLabel);
		untilLabel = (TextView) this.findViewById(R.id.TodoUntilLabel);
		allDayTodo = (CheckBox) this.findViewById(R.id.TodoAllDay);
		fromDate = (Button) this.findViewById(R.id.TodoFromDate);
		fromTime = (Button) this.findViewById(R.id.TodoFromTime);
		untilDate = (Button) this.findViewById(R.id.TodoUntilDate);
		untilTime = (Button) this.findViewById(R.id.TodoUntilTime);

		applyButton = (Button) this.findViewById(R.id.todo_apply_button);

		locationView = (TextView) this.findViewById(R.id.TodoLocationContent);
		

		notesView = (TextView) this.findViewById(R.id.TodoNotesContent);
		
		alarmsLayout = (RelativeLayout) this.findViewById(R.id.TodoAlarmsLayout);
		alarmsList = (TableLayout) this.findViewById(R.id.alarms_list_table);
		alarmsView = (Button) this.findViewById(R.id.TodoAlarmsButton);
		
		repeatsView = (Button) this.findViewById(R.id.TodoRepeatsContent);
		
		
		//Button listeners
		setListen(fromDate,FROM_DATE_DIALOG);
		setListen(fromTime,FROM_TIME_DIALOG);
		setListen(untilDate,UNTIL_DATE_DIALOG);
		setListen(untilTime,UNTIL_TIME_DIALOG);
		setListen(alarmsView,ADD_ALARM_DIALOG);
		setListen(repeatsView,SET_REPEAT_RULE_DIALOG);
		allDayTodo.setOnCheckedChangeListener(this);
		if ( this.todo.getDuration().getDurationMillis() == 60L*60L*24L*1000L ){
			allDayTodo.setChecked(true);
		}

		
		String title = todo.getSummary();
		todoName.setText(title);

		String location = todo.getLocation();
		locationView.setText(location);

		String description = todo.getDescription();
		notesView.setText(description);
		
		updateLayout();
	}

	
	private void updateLayout() {
		AcalDateTime start = todo.getStart();
		if ( start != null) start.applyLocalTimeZone();
		AcalDateTime end = todo.getEnd();
		if ( end != null) end.applyLocalTimeZone();
		AcalDateTime due = todo.getDue();
		if ( due != null) due.applyLocalTimeZone();

		Integer colour = todo.getCollectionColour();
		if ( colour == null ) colour = getResources().getColor(android.R.color.black);
		sidebar.setBackgroundColor(colour);
		todoName.setTextColor(colour);
		
		this.collection.setText(this.currentCollection.getAsString(DavCollections.DISPLAYNAME));
		
		this.applyButton.setText((isModifyAction() ? getString(R.string.Apply) : getString(R.string.Add)));
		
		boolean allDay = allDayTodo.isChecked();

		if ( start != null )
			fromDate.setText(AcalDateTime.fmtDayMonthYear(start));

		
		if (allDay) {
			fromLabel.setVisibility(View.GONE);
			untilLabel.setVisibility(View.GONE);
			untilDate.setText(""); 
			untilDate.setVisibility(View.GONE);
			fromTime.setText(""); 
			fromTime.setVisibility(View.GONE);
			untilTime.setText(""); 
			untilTime.setVisibility(View.GONE); 
		}
		else {
			fromLabel.setVisibility(View.VISIBLE);
			untilLabel.setVisibility(View.VISIBLE);
			if ( end != null )
				untilDate.setText(AcalDateTime.fmtDayMonthYear(end));
			untilDate.setVisibility(View.VISIBLE);

			DateFormat formatter = new SimpleDateFormat(prefer24hourFormat?"HH:mm":"hh:mmaa");
			if ( start != null )
				fromTime.setText(formatter.format(start.toJavaDate()));
			fromTime.setVisibility(View.VISIBLE);
			if ( end != null )
				untilTime.setText(formatter.format(end.toJavaDate()));
			untilTime.setVisibility(View.VISIBLE);
		}
		

		if ( todo.getStart() == null ) {
			alarmList = todo.getAlarms();
			this.alarmsList.removeAllViews();
			alarmsLayout.setVisibility(View.GONE);
		}
		else {
			//Display Alarms
			alarmList = todo.getAlarms();
			this.alarmsList.removeAllViews();
			for (AcalAlarm alarm : alarmList) {
				this.alarmsList.addView(this.getAlarmItem(alarm, alarmsList));
			}
			alarmsLayout.setVisibility(View.VISIBLE);
		}
		
		//set repeat options
		if ( start == null && due == null ) {
			repeatsView.setVisibility(View.GONE);
		}
		else {
			AcalDateTime relativeTo = (start == null ? due : start);
			int dow = relativeTo.getWeekDay();;
			int weekNum = relativeTo.getMonthWeek();

			String dowStr = "";
			String dowLongString = "";
			String everyDowString = "";
			switch (dow) {
				case 0:
					dowStr="MO";
					dowLongString = getString(R.string.Monday);
					everyDowString = getString(R.string.EveryMonday);
					break;
				case 1:
					dowStr="TU";
					dowLongString = getString(R.string.Tuesday);
					everyDowString = getString(R.string.EveryTuesday);
					break;
				case 2:
					dowStr="WE";
					dowLongString = getString(R.string.Wednesday);
					everyDowString = getString(R.string.EveryWednesday);
					break;
				case 3:
					dowStr="TH";
					dowLongString = getString(R.string.Thursday);
					everyDowString = getString(R.string.EveryThursday);
					break;
				case 4:
					dowStr="FR";
					dowLongString = getString(R.string.Friday); 
					everyDowString = getString(R.string.EveryFriday);
					break;
				case 5:
					dowStr="SA";
					dowLongString = getString(R.string.Saturday); 
					everyDowString = getString(R.string.EverySaturday);
					break;
				case 6:
					dowStr="SU";
					dowLongString = getString(R.string.Sunday); 	
					everyDowString = getString(R.string.EverySunday);
					break;
			}
			String dailyRepeatName = getString(R.string.EveryWeekday);
			String dailyRepeatRule = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;COUNT=260";
			if (relativeTo.get(AcalDateTime.DAY_OF_WEEK) == AcalDateTime.SATURDAY || relativeTo.get(AcalDateTime.DAY_OF_WEEK) == AcalDateTime.SUNDAY) {
				dailyRepeatName = getString(R.string.EveryWeekend);
				dailyRepeatRule = "FREQ=WEEKLY;BYDAY=SA,SU;COUNT=104";
			}
	
			this.repeatRules = new String[] {
						getString(R.string.OnlyOnce),
						getString(R.string.EveryDay),
						dailyRepeatName,
						everyDowString,
						String.format(this.getString(R.string.EveryNthOfTheMonth),
								relativeTo.getMonthDay()+AcalDateTime.getSuffix(relativeTo.getMonthDay())),
						String.format(this.getString(R.string.EveryMonthOnTheNthSomeday),
									weekNum+AcalDateTime.getSuffix(weekNum)+" "+dowLongString),
						getString(R.string.EveryYear)
			};
			this.repeatRulesValues = new String[] {
					"FREQ=DAILY;COUNT=400",
					dailyRepeatRule,
					"FREQ=WEEKLY;BYDAY="+dowStr,
					"FREQ=MONTHLY;COUNT=60",
					"FREQ=MONTHLY;COUNT=60;BYDAY="+weekNum+dowStr,
					"FREQ=YEARLY"
			};
			String repeatRuleString = todo.getRepetition();
			if (repeatRuleString == null) repeatRuleString = "";
			AcalRepeatRule RRule;
			try {
				RRule = new AcalRepeatRule(relativeTo, repeatRuleString); 
			}
			catch( IllegalArgumentException  e ) {
				Log.i(TAG,"Illegal repeat rule: '"+repeatRuleString+"'");
				RRule = new AcalRepeatRule(relativeTo, null ); 
			}
			String rr = RRule.repeatRule.toPrettyString(this);
			if (rr == null || rr.equals("")) rr = getString(R.string.OnlyOnce);
			repeatsView.setText(rr);
		}
	}

	private void setListen(Button b, final int dialog) {
		b.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				showDialog(dialog);

			}
		});
	}

	
	public void applyChanges() {
		//check if text fields changed
		//summary
		String oldSum = todo.getSummary();
		String newSum = this.todoName.getText().toString() ;
		String oldLoc = todo.getLocation();
		String newLoc = this.locationView.getText().toString();
		String oldDesc = todo.getDescription();
		String newDesc = this.notesView.getText().toString() ;
		
		if (!oldSum.equals(newSum)) todo.setSummary(newSum);
		if (!oldLoc.equals(newLoc)) todo.setLocation(newLoc);
		if (!oldDesc.equals(newDesc)) todo.setDescription(newDesc);
		
		AcalDateTime start = todo.getStart();
		if ( start != null ) {
			//check if all day
			if (allDayTodo.isChecked()) {
				start.setDaySecond(0);
				start.setAsDate(true);
				todo.setStart(start);
				todo.setDuration(new AcalDuration("P1D"));
			}
	
			AcalDuration duration = todo.getDuration();
			// Ensure end is not before start
			if ( duration.getDays() < 0 || duration.getTimeMillis() < 0 ) {
				start = todo.getStart();
				AcalDateTime end = AcalDateTime.addDuration(start, duration);
				while( end.before(start) ) end.addDays(1);
				duration = start.getDurationTo(end);
				todo.setDuration(duration);
			}
		}
		
		if (action == ACTION_CREATE || action == ACTION_MODIFY_ALL ) {
			if ( !this.saveChanges() ){
				Toast.makeText(this, "Save failed: retrying!", Toast.LENGTH_LONG).show();
				this.saveChanges();
			}
			return; 
		}
		
		//ask the user which instance(s) to apply to
		this.showDialog(WHICH_TODO_DIALOG);

	}

	private boolean saveChanges() {
		
		try {
			this.dataRequest.todoChanged(todo.getTopParent(), action);

			Log.i(TAG,"Saving todo with action " + action );
			if ( action == ACTION_CREATE )
				Toast.makeText(this, getString(R.string.TaskSaved), Toast.LENGTH_LONG).show();
			else if (action == ACTION_MODIFY_ALL)
				Toast.makeText(this, getString(R.string.TaskModifiedAll), Toast.LENGTH_LONG).show();
			else if (action == ACTION_MODIFY_SINGLE)
				Toast.makeText(this, getString(R.string.TaskModifiedOne), Toast.LENGTH_LONG).show();
			else if (action == ACTION_MODIFY_ALL_FUTURE)
				Toast.makeText(this, getString(R.string.TaskModifiedThisAndFuture), Toast.LENGTH_LONG).show();

			Intent ret = new Intent();
			ret.putExtra("changedTodo", sat);
			this.setResult(RESULT_OK, ret);

			this.finish();
		}
		catch (Exception e) {
			if ( e.getMessage() != null ) Log.d(TAG,e.getMessage());
			if (Constants.LOG_DEBUG)Log.d(TAG,Log.getStackTraceString(e));
			Toast.makeText(this, getString(R.string.TaskErrorSaving), Toast.LENGTH_LONG).show();
		}
		return true;
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
		case APPLY: applyChanges(); break;
		case CANCEL: finish();
		}
	}
	
	@Override
	public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
		this.updateLayout();
	}

	//Dialogs
	protected Dialog onCreateDialog(int id) {
		AcalDateTime start = todo.getStart();
		AcalDateTime end = todo.getEnd();
		switch (id) {
		case FROM_DATE_DIALOG:
			return new DatePickerDialog(this,fromDateListener,
					start.get(AcalDateTime.YEAR),
					start.get(AcalDateTime.MONTH)-1,
					start.get(AcalDateTime.DAY_OF_MONTH)
			);
		case UNTIL_DATE_DIALOG:
			return new DatePickerDialog(this,untilDateListener,
					end.get(AcalDateTime.YEAR),
					end.get(AcalDateTime.MONTH)-1,
					end.get(AcalDateTime.DAY_OF_MONTH)
			);
		case FROM_TIME_DIALOG:
			return new TimePickerDialog(this, fromTimeListener,
					start.getHour(), 
					start.getMinute(),
					prefer24hourFormat);
		case UNTIL_TIME_DIALOG:
			return new TimePickerDialog(this, untilTimeListener,
					end.getHour(), 
					end.getMinute(),
					prefer24hourFormat);
		case SELECT_COLLECTION_DIALOG:
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(getString(R.string.ChooseACollection));
				builder.setItems(this.collectionsArray, new DialogInterface.OnClickListener() {
				    public void onClick(DialogInterface dialog, int item) {
				    	setSelectedCollection(collectionsArray[item]);
				    }
				});
				return builder.create();
		case ADD_ALARM_DIALOG:
			builder = new AlertDialog.Builder(this);
			builder.setTitle(getString(R.string.ChooseAlarmTime));
			builder.setItems(alarmRelativeTimeStrings, new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog, int item) {
			    	//translate item to equal alarmValue index
			    	if ( item < 0 || item >= alarmValues.length || todo.getStart() == null ) return;
			    	alarmList.add(
			    			new AcalAlarm(
			    					true, 
			    					todo.getDescription(), 
			    					alarmValues[item], 
			    					ActionType.AUDIO, 
			    					todo.getStart(), 
			    					AcalDateTime.addDuration( todo.getStart(), alarmValues[item] )
			    			)
			    	);
			    	todo.updateAlarmComponents(alarmList);
			    	updateLayout();
			    }
			});
			return builder.create();
		case WHICH_TODO_DIALOG:
			builder = new AlertDialog.Builder(this);
			builder.setTitle(getString(R.string.ChooseInstancesToChange));
			builder.setItems(todoChangeRanges, new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog, int item) {
			    	switch (item) {
			    		case 0: action = ACTION_MODIFY_SINGLE; saveChanges(); return;
			    		case 1: action = ACTION_MODIFY_ALL; saveChanges(); return;
			    		case 2: action = ACTION_MODIFY_ALL_FUTURE; saveChanges(); return;
			    	}
			    }
			});
			return builder.create();
		case SET_REPEAT_RULE_DIALOG:
			builder = new AlertDialog.Builder(this);
			builder.setTitle(getString(R.string.ChooseRepeatFrequency));
			builder.setItems(this.repeatRules, new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog, int item) {
			    	String newRule = "";
			    	if (item != 0) {
			    		item--;
			    		newRule = repeatRulesValues[item];
			    	}
			    	if ( isModifyAction() ) {
				    	if (TodoEdit.this.originalHasOccurrence && !newRule.equals(TodoEdit.this.originalOccurence)) {
				    		action = ACTION_MODIFY_ALL;
				    	} else if (TodoEdit.this.originalHasOccurrence) {
				    		action = ACTION_MODIFY_SINGLE;
				    	}
			    	}
			    	todo.setRepetition(newRule);
			    	updateLayout();
			    	
			    }
			});
			return builder.create();
		default: return null;
		}
	}
	// the callback received when the user "sets" the start date in the dialog
	private DatePickerDialog.OnDateSetListener fromDateListener =
		new DatePickerDialog.OnDateSetListener() {

		public void onDateSet(DatePicker view, int year, 
				int monthOfYear, int dayOfMonth) {

			AcalDateTime start = todo.getStart().clone().applyLocalTimeZone();
			start.setYearMonthDay(year, monthOfYear + 1, dayOfMonth);
			todo.setStart(start);
			updateLayout();
		}
	};
	
	

	// the callback received when the user "sets" the end date in the dialog
	private DatePickerDialog.OnDateSetListener untilDateListener =
		new DatePickerDialog.OnDateSetListener() {

		public void onDateSet(DatePicker view, int year, 
				int monthOfYear, int dayOfMonth) {

			AcalDateTime start = todo.getStart().clone().applyLocalTimeZone();
			AcalDateTime end = todo.getEnd().clone().applyLocalTimeZone();
			end.setYearMonthDay(year, monthOfYear + 1, dayOfMonth);
			todo.setDuration(start.getDurationTo(end));
			updateLayout();
		}
	};

	// the callback received when the user "sets" the start time in the dialog
	private TimePickerDialog.OnTimeSetListener fromTimeListener =
		new TimePickerDialog.OnTimeSetListener() {

		public void onTimeSet(TimePicker view, int hour, int minute) {

			AcalDateTime start = todo.getStart().clone().applyLocalTimeZone();
			start.setDaySecond(hour*3600 + minute*60);
			todo.setStart(start);

			SimpleDateFormat formatter = new SimpleDateFormat("hh:mma");
			fromTime.setText(formatter.format(start.toJavaDate()));
			formatter = new SimpleDateFormat("hh:mma");
			updateLayout();
		}
	};
	
	private TimePickerDialog.OnTimeSetListener untilTimeListener =
		new TimePickerDialog.OnTimeSetListener() {

		public void onTimeSet(TimePicker view, int hour, int minute) {

			AcalDateTime start = todo.getStart().clone().applyLocalTimeZone();
			AcalDateTime end = todo.getEnd().clone().applyLocalTimeZone();
			end.setDaySecond(hour*3600 + minute*60);
			AcalDuration duration = start.getDurationTo(end);
			todo.setDuration(duration);
			SimpleDateFormat formatter = new SimpleDateFormat("hh:mma");
			untilTime.setText(formatter.format(start.toJavaDate()));
			updateLayout();
		}
	};

	private void connectToService() {
		try {
			Intent intent = new Intent(this, CalendarDataService.class);
			Bundle b  = new Bundle();
			b.putInt(CalendarDataService.BIND_KEY, CalendarDataService.BIND_DATA_REQUEST);
			intent.putExtras(b);
			this.bindService(intent,mConnection,Context.BIND_AUTO_CREATE);
		}
		catch (Exception e) {
			Log.e(TAG, "Error connecting to service: "+e.getMessage());
		}
	}

	private synchronized void serviceIsConnected() {
	}

	private synchronized void serviceIsDisconnected() {
		this.dataRequest = null;
	}



	@Override
	public void onResume() {
		super.onResume();
		connectToService();
	}

	@Override
	public void onPause() {
		super.onPause();
		try {
			this.unbindService(mConnection);
		}
		catch (IllegalArgumentException re) { }
		finally {
			dataRequest = null;
		}
	}
	
	public View getAlarmItem(final AcalAlarm alarm, ViewGroup parent) {
		LinearLayout rowLayout;

		LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		TextView title = null; //, time = null, location = null;
		rowLayout = (TableRow) inflater.inflate(R.layout.alarm_list_item, parent, false);

		title = (TextView) rowLayout.findViewById(R.id.AlarmListItemTitle);
		title.setText(alarm.toPrettyString());
		
		ImageView cancel = (ImageView) rowLayout.findViewById(R.id.delete_button);

		rowLayout.setTag(alarm);
		cancel.setOnClickListener(new OnClickListener(){
			public void onClick(View v) {
				alarmList.remove(alarm);
				updateLayout();
			}
		});
		return rowLayout;
	}
	
	/************************************************************************
	 * 					Service Connection management						*
	 ************************************************************************/

	
	private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            dataRequest = DataRequest.Stub.asInterface(service);
			serviceIsConnected();
		}
		public void onServiceDisconnected(ComponentName className) {
			serviceIsDisconnected();
		}
	};


	public boolean isModifyAction() {
		return (action >= ACTION_CREATE && action <= ACTION_MODIFY_ALL_FUTURE);
	}

}
