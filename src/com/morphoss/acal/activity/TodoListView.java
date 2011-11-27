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

import java.util.ArrayList;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;

import com.morphoss.acal.AcalTheme;
import com.morphoss.acal.Constants;
import com.morphoss.acal.R;
import com.morphoss.acal.dataservice.CalendarDataService;
import com.morphoss.acal.dataservice.DataRequest;
import com.morphoss.acal.dataservice.DataRequestCallBack;
import com.morphoss.acal.davacal.SimpleAcalTodo;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.davacal.VComponentCreationException;
import com.morphoss.acal.service.aCalService;

/**
 * <h1>Todo List View</h1>
 * 
 * <p>
 * This view is split into 2 sections:
 * </p>
 * <ul>
 * <li>Todo List - A grid view controlled by a View Flipper displaying all the
 * outstanding Todo items</li>
 * <li>Buttons - A set of buttons at the bottom of the screen</li>
 * </ul>
 * <p>
 * As well as this, there is a menu accessible through the menu button.
 * </p>
 * 
 * <p>
 * Each of the view flippers listens to gestures, Side swipes on either will
 * result in the content of the flipper moving forward or back. Content for the
 * flippers is provided by Adapter classes that contain the data the view is
 * representing.
 * </p>
 * 
 * <p>
 * At any time there are 2 important pieces of information that make up this
 * views state: The currently selected day, which is highlighted when visible in
 * the month view and determines which events are visible in the event list. The
 * other is the currently displayed date, which determines which month we are
 * looking at in the month view. This state information is written to and read
 * from file when the view loses and gains focus.
 * </p>
 * 
 * 
 * @author Morphoss Ltd
 * @license GPL v3 or later
 * 
 */
public class TodoListView extends AcalActivity implements OnClickListener {

	public static final String TAG = "aCal TodoListView";

	private boolean invokedFromView = false;
	
	private GridView todoList;
	private TodoListAdapter todoListAdapter;

	/* Fields relating to state */
	private boolean showFuture = true;
	private boolean showCompleted = false;

	/* Fields relating to buttons */
	public static final int DUE = 0;
	public static final int TODO = 1;
	public static final int ADD = 3;

	/* Fields relating to calendar data */
	private DataRequest dataRequest = null;

	/********************************************************
	 * Activity Overrides *
	 ********************************************************/

	/**
	 * <p>
	 * Called when Activity is first created. Initialises all appropriate fields
	 * and Constructs the Views for display.
	 * </p>
	 * 
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.todo_list_view);

		Bundle b = this.getIntent().getExtras();
		if ( b != null && b.containsKey("InvokedFromView") )
			invokedFromView = true;

		// make sure aCalService is running
		this.startService(new Intent(this, aCalService.class));

		// Set up buttons
		this.setupButton(R.id.todo_add_button, ADD, "+");
		this.setSelections();

	}

	private void connectToService() {
		try {
			Log.v(TAG,TAG + " - Connecting to service with dataRequest ="+(dataRequest == null? "null" : "non-null"));
			Intent intent = new Intent(this, CalendarDataService.class);
			Bundle b = new Bundle();
			b.putInt(CalendarDataService.BIND_KEY,
					CalendarDataService.BIND_DATA_REQUEST);
			intent.putExtras(b);
			this.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
		}
		catch (Exception e) {
			Log.e(TAG, "Error connecting to service: " + e.getMessage());
		}
	}

	
	private synchronized void serviceIsConnected() {
		if ( this.todoList == null ) createListView(true);
		if ( this.todoListAdapter == null ) {
			this.todoListAdapter = new TodoListAdapter(this, showCompleted, showFuture );
			this.todoList.setAdapter(todoListAdapter);
		}
	}

	private synchronized void serviceIsDisconnected() {
		this.dataRequest = null;
	}

	/**
	 * <p>
	 * Called when Activity regains focus. Try's to load the saved State.
	 * </p>
	 * 
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onResume()
	 */
	@Override
	public void onResume() {
		super.onResume();
		Log.v(TAG,TAG + " - onResume");

		connectToService();
	}

	/**
	 * <p>
	 * Called when activity loses focus or is closed. Try's to save the current
	 * State
	 * </p>
	 * 
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPause()
	 */
	@Override
	public void onPause() {
		super.onPause();

		try {
			if (dataRequest != null) {
				dataRequest.unregisterCallback(mCallback);
			}
			this.unbindService(mConnection);
		}
		catch (RemoteException re) { }
		catch (IllegalArgumentException e) { }
		finally {
			dataRequest = null;
		}

	}

	/****************************************************
	 * Private Methods *
	 ****************************************************/

	/**
	 * <p>
	 * Helper method for setting up buttons
	 * </p>
	 * @param buttonLabel 
	 */
	private void setupButton(int id, int val, String buttonLabel) {
		Button myButton = (Button) this.findViewById(id);
		if (myButton == null) {
			Log.e(TAG, "Cannot find button '" + id + "' by ID, to set value '" + val + "'");
			Log.i(TAG, Log.getStackTraceString(new Exception()));
		}
		else {
			myButton.setText(buttonLabel);
			myButton.setOnClickListener(this);
			myButton.setTag(val);
			AcalTheme.setContainerFromTheme(myButton, AcalTheme.BUTTON);
		}
	}


	private void setSelections() {
		this.setupButton(R.id.todo_due_button, DUE, (this.showFuture ? getString(R.string.Due) : getString(R.string.All) ));
		this.setupButton(R.id.todo_all_button, TODO, (this.showCompleted ? getString(R.string.Todo) : getString(R.string.All)));

		TextView title = (TextView) this.findViewById(R.id.todo_list_title);
		title.setText( !showFuture ? R.string.dueTasksTitle
								   : (!showCompleted ? R.string.incompleteTasksTitle
										   			 : R.string.allTasksTitle)
					);

		this.todoListAdapter = null;
		if ( dataRequest == null ) connectToService();
		else serviceIsConnected();
	}

	
	/**
	 * <p>
	 * Creates a new GridView object based on this Activities current state. The
	 * GridView created will display this Activities ListView
	 * </p>
	 * 
	 * @param addParent
	 *            <p>
	 *            Whether or not to set the ViewFlipper as the new GridView's
	 *            Parent. if set to false the caller is contracted to add a
	 *            parent to the GridView.
	 *            </p>
	 */
	private void createListView(boolean addParent) {
		try {
			// List
			todoList = (GridView) findViewById(R.id.todo_list);
			todoList.setSelector(R.drawable.no_border);

		} catch (Exception e) {
			Log.e(TAG, "Error occured creating listview: " + e.getMessage());
		}
	}


	/**
	 * <p>
	 * Called when user has selected 'Settings' from menu. Starts Settings
	 * Activity.
	 * </p>
	 */
	private void startSettings() {
		Intent settingsIntent = new Intent();
		settingsIntent.setClassName("com.morphoss.acal",
				"com.morphoss.acal.activity.Settings");
		this.startActivity(settingsIntent);
	}

	
	/**
	 * <p>
	 * Called when user has selected 'Events' from menu. Starts MonthView
	 * Activity.
	 * </p>
	 */
	private void startMonthView() {
		if ( invokedFromView )
			this.finish();
		else {
			Intent monthViewIntent = new Intent();
			Bundle bundle = new Bundle();
			bundle.putInt("InvokedFromView",1);
			monthViewIntent.putExtras(bundle);
			monthViewIntent.setClassName("com.morphoss.acal",
					"com.morphoss.acal.activity.MonthView");
			this.startActivity(monthViewIntent);
		}
	}


	/****************************************************
	 * Public Methods *
	 ****************************************************/

	/**
	 * Methods for managing event structure
	 */
	public ArrayList<SimpleAcalTodo> getTodos(boolean listCompleted, boolean listFuture) {
		if (dataRequest == null) {
			Log.w(TAG,"DataService connection not available!");
			return new ArrayList<SimpleAcalTodo>();
		}
		try {
			return (ArrayList<SimpleAcalTodo>) dataRequest.getTodos(listCompleted,listFuture);
		}
		catch (RemoteException e) {
			if (Constants.LOG_DEBUG) Log.d(TAG,"Remote Exception accessing eventcache: "+e);
			return new ArrayList<SimpleAcalTodo>();
		}
	}

	public int getNumberTodos(boolean listCompleted, boolean listFuture) {
		if (dataRequest == null) return 0;
		try {
			return dataRequest.getNumberTodos(listCompleted,listFuture);
		} catch (RemoteException e) {
			if (Constants.LOG_DEBUG) Log.d(TAG,"Remote Exception accessing eventcache: "+e);
			return 0;
		}
	}

	public SimpleAcalTodo getNthTodo(boolean listCompleted, boolean listFuture, int n) {
		if (dataRequest == null) return null;
		try {
			return dataRequest.getNthTodo(listCompleted,listFuture, n);
		} catch (RemoteException e) {
			if (Constants.LOG_DEBUG) Log.d(TAG,"Remote Exception accessing todolist from dataservice: "+e);
			return null;
		}
	}

	public void deleteTodo(boolean listCompleted, boolean listFuture, int n, int action ) {
		if (dataRequest == null) return;
		try {
			SimpleAcalTodo sat = dataRequest.getNthTodo(listCompleted,listFuture, n);
			this.dataRequest.todoChanged((VCalendar) VComponent.fromDatabase(this, sat.resourceId), action);
		}
		catch (RemoteException e) {
			Log.e(TAG,"Error deleting task: "+e);
		}
		catch (VComponentCreationException e) {
			Log.e(TAG,"Error reading task from database: "+e);
		}
	}

	public void completeTodo(boolean listCompleted, boolean listFuture, int n, int action ) {
		if (dataRequest == null) return;
		try {
			SimpleAcalTodo sat = dataRequest.getNthTodo(listCompleted,listFuture, n);
			this.dataRequest.todoChanged((VCalendar) VComponent.fromDatabase(this, sat.resourceId), action);
		}
		catch (RemoteException e) {
			Log.e(TAG,"Error marking task completed: "+e);
		}
		catch (VComponentCreationException e) {
			Log.e(TAG,"Error reading task from database: "+e);
		}
	}

	
	
	/********************************************************************
	 * Implemented Interface Overrides *
	 ********************************************************************/

	/**
	 * <p>
	 * Responsible for handling the menu button push.
	 * </p>
	 * 
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.tasks_options_menu, menu);
		return true;
	}

	/**
	 * <p>
	 * Called when the user selects an option from the options menu. Determines
	 * what (if any) Activity should start.
	 * </p>
	 * 
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
			case R.id.settingsMenuItem:
				startSettings();
				return true;
			case R.id.eventsMenuItem:
				startMonthView();
				return true;
		default:
			return super.onOptionsItemSelected(item);
		}
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
			case DUE:
				this.showFuture = !this.showFuture;
				this.setSelections();
				break;
			case TODO:
				this.showCompleted = !this.showCompleted;
				this.setSelections();
				break;
			case ADD:
				Intent todoEditIntent = new Intent(this, TodoEdit.class);
				this.startActivity(todoEditIntent);
				break;
			default:
				Log.w(TAG, "Unrecognised button was pushed in TodoListView.");
		}
	}

	/************************************************************************
	 * Service Connection management *
	 ************************************************************************/

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. We are communicating with our
			// service through an IDL interface, so get a client-side
			// representation of that from the raw service object.
			dataRequest = DataRequest.Stub.asInterface(service);
			try {
				dataRequest.registerCallback(mCallback);
				
			} catch (RemoteException re) {
				Log.d(TAG,Log.getStackTraceString(re));
			}
			serviceIsConnected();
		}

		public void onServiceDisconnected(ComponentName className) {
			serviceIsDisconnected();
		}
	};

	/**
	 * This implementation is used to receive callbacks from the remote service.
	 */
	private DataRequestCallBack mCallback = new DataRequestCallBack.Stub() {
		/**
		 * This is called by the remote service regularly to tell us about new
		 * values. Note that IPC calls are dispatched through a thread pool
		 * running in each process, so the code executing here will NOT be
		 * running in our main thread like most other things -- so, to update
		 * the UI, we need to use a Handler to hop over there.
		 */
		public void statusChanged(int type, boolean value) {
			mHandler.sendMessage(mHandler.obtainMessage(BUMP_MSG, type,
					(value ? 1 : 0)));
		}
	};

	private static final int BUMP_MSG = 1;

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			int type = msg.arg1;
			switch (type) {
			case CalendarDataService.UPDATE:
				if ( Constants.LOG_DEBUG ) Log.i(TAG,"Received update notification from CalendarDataService.");
				break;
			}

		}

	};

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		return this.todoListAdapter.contextClick(item);
	}


	/************************************************************************
	 * Required Overrides that aren't used *
	 ************************************************************************/

}
