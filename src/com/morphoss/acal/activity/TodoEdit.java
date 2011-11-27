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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.morphoss.acal.AcalTheme;
import com.morphoss.acal.Constants;
import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDateTimeFormatter;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.acaltime.AcalRepeatRule;
import com.morphoss.acal.dataservice.CalendarDataService;
import com.morphoss.acal.dataservice.DataRequest;
import com.morphoss.acal.davacal.AcalAlarm;
import com.morphoss.acal.davacal.AcalAlarm.ActionType;
import com.morphoss.acal.davacal.AcalAlarm.RelateWith;
import com.morphoss.acal.davacal.AcalCollection;
import com.morphoss.acal.davacal.SimpleAcalTodo;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.davacal.VTodo;
import com.morphoss.acal.providers.DavCollections;
import com.morphoss.acal.service.aCalService;
import com.morphoss.acal.widget.AlarmDialog;
import com.morphoss.acal.widget.DateTimeDialog;
import com.morphoss.acal.widget.DateTimeSetListener;

public class TodoEdit extends AcalActivity
	implements OnCheckedChangeListener, OnSeekBarChangeListener {

	public static final String TAG = "aCal TodoEdit";

	public static final String activityResultName = "changedTodo";
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
	public static final int ACTION_COMPLETE = 7;
	public static final int ACTION_EDIT = 8;
	public static final int ACTION_COPY = 9;

	private int action = ACTION_NONE;

	private static final int FROM_DIALOG = 10;
	private static final int DUE_DIALOG = 11;
	private static final int COMPLETED_DIALOG = 12;
	private static final int ADD_ALARM_DIALOG = 20;
	private static final int SET_REPEAT_RULE_DIALOG = 21;
	private static final int SELECT_COLLECTION_DIALOG = 22;
	private static final int INSTANCES_TO_CHANGE_DIALOG = 30;

	boolean prefer24hourFormat = false;
	
	private String[] repeatRules;
	private String[] todoChangeRanges; // See strings.xml R.array.TodoChangeAffecting
		
	private String[] alarmRelativeTimeStrings;
	// Must match R.array.RelativeAlarmTimes (strings.xml)
	public static final AcalDuration[] alarmValues = new AcalDuration[] {
		new AcalDuration(),
		new AcalDuration("-PT10M"),
		new AcalDuration("-PT15M"),
		new AcalDuration("-PT30M"),
		new AcalDuration("-PT1H"),
		new AcalDuration("-PT2H"),
		//** Custom **//
	};
	
	private String[] repeatRulesValues;
	
	private DataRequest dataRequest = null;

	//GUI Components
	private Button btnStartDate;
	private Button btnDueDate;
	private Button btnCompleteDate;
	private LinearLayout sidebar;
	private LinearLayout sidebarBottom;
	private TextView todoName;
	private TextView locationView;
	private TextView notesView;
	private TableLayout alarmsList;
	private LinearLayout repeatsLayout;
	private Button btnAddRepeat;
	private RelativeLayout alarmsLayout;
	private Button btnAddAlarm;
	private LinearLayout collectionsLayout;
	private Button btnCollection;
	private Button btnSaveChanges;	
	private Button btnCancelChanges;
	

	private int percentComplete = 0;
	private SeekBar percentCompleteBar;
	private TextView percentCompleteText;
	
	//Active collections for create mode
	private ContentValues[] activeCollections;
	private ContentValues currentCollection;	//currently selected collection
	private String[] collectionsArray;

	private List<AcalAlarm> alarmList;
	
	private boolean originalHasOccurrence = false;
	private String originalOccurence = "";

	private int	currentOperation;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.todo_edit);

		//Ensure service is actually running
		startService(new Intent(this, aCalService.class));
		connectToService();

		// Get time display preference
		prefer24hourFormat = prefs.getBoolean(getString(R.string.prefTwelveTwentyfour), false);

		alarmRelativeTimeStrings = getResources().getStringArray(R.array.RelativeAlarmTimes);
		todoChangeRanges = getResources().getStringArray(R.array.TodoChangeAffecting);
		
		Bundle b = this.getIntent().getExtras();
		getTodoAction(b);
		if ( this.todo == null ) {
			Log.d(TAG,"Unable to create VTodo object");
			return;
		}
		this.populateLayout();
	}

	
	private VTodo getTodoAction(Bundle b) {
		currentOperation = ACTION_EDIT;
		if ( b != null && b.containsKey("SimpleAcalTodo") ) {
			this.sat = (SimpleAcalTodo) b.getParcelable("SimpleAcalTodo");
			currentOperation = sat.operation;
			try {
				if (Constants.LOG_DEBUG)
					Log.d(TAG, "Loading Todo: "+sat.summary );
				VCalendar vc = (VCalendar) VComponent.fromDatabase(this, sat.resourceId);
				vc.setEditable();
				this.todo = (VTodo) ((VCalendar) vc).getMasterChild();
			}
			catch( Exception e ) {
				Log.e(TAG,Log.getStackTraceString(e));
			}
		}

		//Get collection data
		currentCollection = null;
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
		
		if ( currentOperation == ACTION_EDIT ) {
			try {
				collectionId = (Integer) todo.getCollectionId();
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
				if (Constants.LOG_DEBUG) Log.d(TAG, "No data from caller ");
			}
		}
		else if ( currentOperation == ACTION_COPY ) {
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

			Integer preferredCollectionId = Integer.parseInt(prefs.getString(getString(R.string.DefaultCollection_PrefKey), "-1"));
			if ( preferredCollectionId != -1 ) {
				for( ContentValues aCollection : activeCollections ) {
					if ( preferredCollectionId == aCollection.getAsInteger(DavCollections._ID) ) {
						collectionId = preferredCollectionId;
						break;
					}
				}
			}
			this.todo = new VTodo(AcalCollection.fromDatabase(this, collectionId));
			this.todo.setSummary(getString(R.string.NewTaskTitle));
			this.action = ACTION_CREATE;

		}

		int count = 0;
		for (ContentValues cv : activeCollections) {
			if (cv.getAsInteger(DavCollections._ID) == collectionId) currentCollection = cv;
			collectionsArray[count++] = cv.getAsString(DavCollections.DISPLAYNAME);
		}
		if ( todo.getCompleted() != null ) todo.setPercentComplete( 100 );
		
		return todo;
	}


	/**
	 * Populate the screen initially.
	 */
	private void populateLayout() {

		//Sidebar
		sidebar = (LinearLayout)this.findViewById(R.id.TodoEditColourBar);
		sidebarBottom = (LinearLayout)this.findViewById(R.id.EventEditColourBarBottom);

		//Title
		this.todoName = (TextView) this.findViewById(R.id.TodoName);
		todoName.setSelectAllOnFocus(action == ACTION_CREATE);

		//Collection
		collectionsLayout = (LinearLayout)this.findViewById(R.id.TodoCollectionLayout);
		btnCollection = (Button) this.findViewById(R.id.TodoEditCollectionButton);
		if (activeCollections.length < 2) {
			btnCollection.setEnabled(false);
			collectionsLayout.setVisibility(View.GONE);
		}
		if ( action != ACTION_CREATE ) {
			btnCollection.setEnabled(false);
		}
		
		
		//date/time fields
		btnStartDate = (Button) this.findViewById(R.id.TodoFromDateTime);
		btnDueDate = (Button) this.findViewById(R.id.TodoDueDateTime);
		btnCompleteDate = (Button) this.findViewById(R.id.TodoCompletedDateTime);

		btnSaveChanges = (Button) this.findViewById(R.id.todo_apply_button);
		btnSaveChanges.setText((isModifyAction() ? getString(R.string.Apply) : getString(R.string.Add)));
		btnCancelChanges = (Button) this.findViewById(R.id.todo_cancel_button);
		

		locationView = (TextView) this.findViewById(R.id.TodoLocationContent);
		notesView = (TextView) this.findViewById(R.id.TodoNotesContent);
		
		alarmsLayout = (RelativeLayout) this.findViewById(R.id.TodoAlarmsLayout);
		alarmsList = (TableLayout) this.findViewById(R.id.alarms_list_table);
		btnAddAlarm = (Button) this.findViewById(R.id.TodoAlarmsButton);
		
		repeatsLayout = (LinearLayout) this.findViewById(R.id.TodoRepeatsLayout);
		btnAddRepeat = (Button) this.findViewById(R.id.TodoRepeatsContent);
		
		// Button listeners
		setButtonDialog(btnStartDate, FROM_DIALOG);
		setButtonDialog(btnDueDate, DUE_DIALOG);
		setButtonDialog(btnCompleteDate, COMPLETED_DIALOG);
		setButtonDialog(btnAddAlarm, ADD_ALARM_DIALOG);
		setButtonDialog(btnAddRepeat, SET_REPEAT_RULE_DIALOG);
		setButtonDialog(btnCollection, SELECT_COLLECTION_DIALOG);

		AcalTheme.setContainerFromTheme(btnSaveChanges, AcalTheme.BUTTON);
		AcalTheme.setContainerFromTheme(btnCancelChanges, AcalTheme.BUTTON);
		
		btnSaveChanges.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				applyChanges();
			}
		});

		btnCancelChanges.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				finish();
			}
		});
		

		percentCompleteText = (TextView) this.findViewById(R.id.TodoPercentCompleteText);
		percentCompleteBar = (SeekBar) this.findViewById(R.id.TodoPercentCompleteBar);
		percentComplete = todo.getPercentComplete();
		percentComplete = (percentComplete < 0 ? 0 : (percentComplete > 100 ? 100 : percentComplete));
		percentCompleteBar.setIndeterminate(false);
		percentCompleteBar.setMax(100);
		percentCompleteBar.setKeyProgressIncrement(5);
		percentCompleteBar.setOnSeekBarChangeListener(this);
		percentCompleteText.setText(Integer.toString(percentComplete)+"%");
		percentCompleteBar.setProgress(percentComplete);
		
		String title = todo.getSummary();
		todoName.setText(title);

		String location = todo.getLocation();
		locationView.setText(location);

		String description = todo.getDescription();
		notesView.setText(description);
		
		updateLayout();
	}


	/**
	 * Update the screen whenever something has changed.
	 */
	private void updateLayout() {
		AcalDateTime start = todo.getStart();
		AcalDateTime due = todo.getDue();
		AcalDateTime completed = todo.getCompleted();

		Integer colour = todo.getTopParent().getCollectionColour();
		if ( colour == null ) colour = 0x70a0a0a0;
		sidebar.setBackgroundColor(colour);
		sidebarBottom.setBackgroundColor(colour);
		AcalTheme.setContainerColour(btnCollection,colour);
		btnCollection.setTextColor(AcalTheme.pickForegroundForBackground(colour));
		todoName.setTextColor(colour);
		btnCollection.setText(todo.getTopParent().getCollectionName());
		
		btnStartDate.setText( AcalDateTimeFormatter.fmtFull( start, prefer24hourFormat) );
		btnDueDate.setText( AcalDateTimeFormatter.fmtFull( due, prefer24hourFormat) );
		btnCompleteDate.setText( AcalDateTimeFormatter.fmtFull( completed, prefer24hourFormat) );

		if ( start != null && due != null && due.before(start) ) {
			AcalTheme.setContainerColour(btnStartDate,0xffff3030);
		}
		else {
			AcalTheme.setContainerFromTheme(btnStartDate, AcalTheme.BUTTON);
		}
		
		if ( start == null && due == null ) {
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
			repeatsLayout.setVisibility(View.GONE);
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
			btnAddRepeat.setText(rr);
			repeatsLayout.setVisibility(View.VISIBLE);
		}
	}

	private void setSelectedCollection(String name) {
		for (ContentValues cv : activeCollections) {
			if (cv.getAsString(DavCollections.DISPLAYNAME).equals(name)) {
				this.currentCollection = cv; break;
			}
		}
		VCalendar vc = (VCalendar) this.todo.getTopParent();
		vc.setCollection(new AcalCollection(currentCollection));

		this.updateLayout();
	}

	
	private void setButtonDialog(Button myButton, final int dialogIndicator) {
		myButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				showDialog(dialogIndicator);
			}
		});
		AcalTheme.setContainerFromTheme(myButton, AcalTheme.BUTTON);
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

		todo.setPercentComplete(percentComplete);
		
		if (action == ACTION_CREATE || action == ACTION_MODIFY_ALL ) {
			if ( !this.saveChanges() ){
				Toast.makeText(this, "Save failed: retrying!", Toast.LENGTH_LONG).show();
				this.saveChanges();
			}
			return; 
		}
		
		//ask the user which instance(s) to apply to
		this.showDialog(INSTANCES_TO_CHANGE_DIALOG);

	}

	private boolean saveChanges() {
		
		try {
			VCalendar vc = (VCalendar) todo.getTopParent();
			
			this.dataRequest.todoChanged(vc, action);

			Log.i(TAG,"Saving todo to collection "+todo.getCollectionId()+" with action " + action );
			if ( action == ACTION_CREATE )
				Toast.makeText(this, getString(R.string.TaskSaved), Toast.LENGTH_LONG).show();
			else if (action == ACTION_MODIFY_ALL)
				Toast.makeText(this, getString(R.string.TaskModifiedAll), Toast.LENGTH_LONG).show();
			else if (action == ACTION_MODIFY_SINGLE)
				Toast.makeText(this, getString(R.string.TaskModifiedOne), Toast.LENGTH_LONG).show();
			else if (action == ACTION_MODIFY_ALL_FUTURE)
				Toast.makeText(this, getString(R.string.TaskModifiedThisAndFuture), Toast.LENGTH_LONG).show();

			Intent ret = new Intent();
			ret.putExtra(activityResultName, sat);
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

	@Override
	public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
		this.updateLayout();
	}

	//Dialogs
	protected Dialog onCreateDialog(int id) {
		AcalDateTime start = todo.getStart();
		AcalDateTime due = todo.getDue();
		AcalDateTime completed = todo.getCompleted();
		
		Boolean dateTypeIsDate = null;
		if ( start == null ) {
			start = new AcalDateTime().applyLocalTimeZone().addDays(1);
			int newSecond = ((start.getDaySecond() / 3600) + 2) * 3600;
			if ( newSecond > 86399 ) start.addDays(1);
			start.setDaySecond(newSecond % 86400);
		}
		else {
			dateTypeIsDate = start.isDate();
		}
		if ( due == null ) {
			due = new AcalDateTime().applyLocalTimeZone().addDays(1);
			int newSecond = start.getDaySecond() + 3600;
			if ( newSecond > 86399 ) due.addDays(1);
			due.setDaySecond(newSecond % 86400);
		}
		else if ( dateTypeIsDate == null ) {
			dateTypeIsDate = due.isDate();
		}
		if ( completed == null ) {
			completed = new AcalDateTime();
			if ( start != null || due != null ) completed.setAsDate((start!=null?start.isDate():due.isDate()));
		}
		else if ( dateTypeIsDate == null ) {
			dateTypeIsDate = completed.isDate();
		}
		if ( dateTypeIsDate == null ) dateTypeIsDate = true;
		start.setAsDate(dateTypeIsDate);
		due.setAsDate(dateTypeIsDate);
		completed.setAsDate(dateTypeIsDate);

		switch ( id ) {
			case FROM_DIALOG:
				return new DateTimeDialog( this, start, prefer24hourFormat, true, true,
						new DateTimeSetListener() {
							public void onDateTimeSet(AcalDateTime newDateTime) {
								todo.setStart( newDateTime );
								updateLayout();
							}
						});

			case DUE_DIALOG:
				return new DateTimeDialog( this, due, prefer24hourFormat, true, true,
						new DateTimeSetListener() {
							public void onDateTimeSet(AcalDateTime newDateTime) {
								todo.setDue( newDateTime );
								updateLayout();
							}
						});

			case COMPLETED_DIALOG:
				return new DateTimeDialog( this, completed, prefer24hourFormat, true, true,
						new DateTimeSetListener() {
							public void onDateTimeSet(AcalDateTime newDateTime) {
								todo.setCompleted( newDateTime );
								todo.setPercentComplete(100);
								todo.setStatus(VTodo.Status.COMPLETED);
								updateLayout();
							}
						});


			case SELECT_COLLECTION_DIALOG:
				AlertDialog.Builder builder = new AlertDialog.Builder( this );
				builder.setTitle( getString( R.string.ChooseACollection ) );
				builder.setItems( this.collectionsArray, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						setSelectedCollection( collectionsArray[item] );
					}
				} );
				return builder.create();
			
			case ADD_ALARM_DIALOG:
				builder = new AlertDialog.Builder( this );
				builder.setTitle( getString( R.string.ChooseAlarmTime ) );
				builder.setItems( alarmRelativeTimeStrings, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						// translate item to equal alarmValue index
						RelateWith relateWith = RelateWith.START;
						AcalDateTime start = todo.getStart();
						if ( start == null ) {
							relateWith = (todo.getDue() == null ? RelateWith.ABSOLUTE : RelateWith.END);
							if ( relateWith == RelateWith.ABSOLUTE ) {
								start = new AcalDateTime();
								start.addDays( 1 );
							}
						}
						if ( item < 0 || item > alarmValues.length ) return;
						if ( item == alarmValues.length ) {
							customAlarmDialog();
						}
						else {
							alarmList.add( new AcalAlarm( relateWith, todo.getDescription(), alarmValues[item],
									ActionType.AUDIO, start, todo.getDue() ) );
							todo.updateAlarmComponents( alarmList );
							updateLayout();
						}
					}
				} );
				return builder.create();
			case INSTANCES_TO_CHANGE_DIALOG:
				builder = new AlertDialog.Builder( this );
				builder.setTitle( getString( R.string.ChooseInstancesToChange ) );
				builder.setItems( todoChangeRanges, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						switch ( item ) {
							case 0:
								action = ACTION_MODIFY_SINGLE;
								saveChanges();
								return;
							case 1:
								action = ACTION_MODIFY_ALL;
								saveChanges();
								return;
							case 2:
								action = ACTION_MODIFY_ALL_FUTURE;
								saveChanges();
								return;
						}
					}
				} );
				return builder.create();
			case SET_REPEAT_RULE_DIALOG:
				builder = new AlertDialog.Builder( this );
				builder.setTitle( getString( R.string.ChooseRepeatFrequency ) );
				builder.setItems( this.repeatRules, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						String newRule = "";
						if ( item != 0 ) {
							item--;
							newRule = repeatRulesValues[item];
						}
						if ( isModifyAction() ) {
							if ( TodoEdit.this.originalHasOccurrence
									&& !newRule.equals( TodoEdit.this.originalOccurence ) ) {
								action = ACTION_MODIFY_ALL;
							}
							else if ( TodoEdit.this.originalHasOccurrence ) {
								action = ACTION_MODIFY_SINGLE;
							}
						}
						todo.setRepetition( newRule );
						updateLayout();

					}
				} );
				return builder.create();
			default:
				return null;
		}
	}

	protected void customAlarmDialog() {

		AlarmDialog.AlarmSetListener customAlarmListener = new AlarmDialog.AlarmSetListener() {

			@Override
			public void onAlarmSet(AcalAlarm alarmValue) {
				alarmList.add( alarmValue );
		    	todo.updateAlarmComponents(alarmList);
		    	updateLayout();
			}
			
		};

		AlarmDialog customAlarm = new AlarmDialog(this, customAlarmListener, null,
				todo.getStart(), todo.getDue(), VComponent.VTODO);
		customAlarm.show();
	}

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


	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		if ( fromUser ) {
			percentComplete = progress;
			percentCompleteText.setText(Integer.toString(percentComplete)+"%");
			if ( progress == 0 ) 							todo.setStatus(VTodo.Status.NEEDS_ACTION);
			else if ( progress > 0 && progress < 100 ) 		todo.setStatus(VTodo.Status.IN_PROCESS);
			else {
				todo.setStatus(VTodo.Status.COMPLETED);
				todo.setCompleted(new AcalDateTime() );
				updateLayout();
			}
		}
	}


	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		if ( false && percentComplete != seekBar.getProgress() ) {
			percentComplete = seekBar.getProgress();
			percentCompleteText.setText(Integer.toString(percentComplete)+"%");
		}
	}


	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		if ( false && percentComplete != seekBar.getProgress() ) {
			percentComplete = seekBar.getProgress();
			percentCompleteText.setText(Integer.toString(percentComplete)+"%");
		}
	}

}
