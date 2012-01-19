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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.morphoss.acal.AcalTheme;
import com.morphoss.acal.Constants;
import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDateTimeFormatter;
import com.morphoss.acal.database.cachemanager.CacheObject;
import com.morphoss.acal.database.resourcesmanager.ResourceChangedEvent;
import com.morphoss.acal.database.resourcesmanager.ResourceChangedListener;
import com.morphoss.acal.database.resourcesmanager.ResourceManager;
import com.morphoss.acal.database.resourcesmanager.ResourceResponse;
import com.morphoss.acal.database.resourcesmanager.ResourceResponseListener;
import com.morphoss.acal.database.resourcesmanager.requests.RRRequestInstance;
import com.morphoss.acal.dataservice.CalendarInstance;
import com.morphoss.acal.dataservice.Collection;
import com.morphoss.acal.dataservice.JournalInstance;
import com.morphoss.acal.dataservice.MethodsRequired;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VJournal;
import com.morphoss.acal.providers.DavCollections;
import com.morphoss.acal.service.aCalService;
import com.morphoss.acal.widget.DateTimeDialog;
import com.morphoss.acal.widget.DateTimeSetListener;

public class JournalEdit extends AcalActivity
	implements OnCheckedChangeListener,
				ResourceChangedListener, ResourceResponseListener<CalendarInstance> {

	public static final String TAG = "aCal JournalEdit";

	private VJournal journal;
	public static final int ACTION_NONE = -1;
	public static final int ACTION_CREATE = 0;
	public static final int ACTION_MODIFY_ALL = 2;
	public static final int ACTION_DELETE_ALL = 5;
	public static final int ACTION_EDIT = 8;
	public static final int ACTION_COPY = 9;

	private int action = ACTION_NONE;
	private static final int FROM_DIALOG = 10;
	private static final int LOADING_DIALOG = 0xfeed;

	boolean prefer24hourFormat = false;


	public static final String	KEY_CACHE_OBJECT	= "CacheObject";
	public static final String	KEY_OPERATION		= "Operation";
	public static final String	KEY_RESOURCE		= "Resource";
	public static final String	KEY_VCALENDAR_BLOB	= "VCalendar";
	

	
	private MethodsRequired dataRequest = new MethodsRequired();

	//GUI Components
	private Button btnStartDate;
	private LinearLayout sidebar;
	private TextView journalName;
	private LinearLayout collectionsLayout;
	private Spinner spinnerCollection;
	private Button btnSaveChanges;	
	private Button btnCancelChanges;

	
	//Active collections for create mode
	private Collection currentCollection;	//currently selected collection
	private CollectionForArrayAdapter[] collectionsArray;

	private int	currentOperation;
	private static final int REFRESH = 0;
	private static final int FAIL = 1;
	private static final int CONFLICT = 2;
	private static final int SHOW_LOADING = 3;
	private static final int GIVE_UP = 4;
	
	private Dialog loadingDialog = null;
	private ResourceManager	resourceManager;

	
	private Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			if ( msg.what == REFRESH ) {
				if ( loadingDialog != null ) {
					loadingDialog.dismiss();
					loadingDialog = null;
				}
				updateLayout();
			}
			else if ( msg.what == CONFLICT ) {
				Toast.makeText(
						JournalEdit.this,
						"The resource you are editing has been changed or deleted on the server.",
						5).show();
			}
			else if ( msg.what == SHOW_LOADING ) {
				if ( journal == null ) showDialog(LOADING_DIALOG);
			}
			else if ( msg.what == FAIL ) {
				Toast.makeText(JournalEdit.this,
						"Error loading data.", 5)
						.show();
				finish();
				return;
			}
			else if ( msg.what == GIVE_UP ) {
				if ( loadingDialog != null ) {
					loadingDialog.dismiss();
					Toast.makeText(
							JournalEdit.this,
							"Error loading event data.",
							Toast.LENGTH_LONG).show();
					finish();
					return;
				}
			}
			
		}
	};

	private TextView journalContent;

	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.journal_edit);

		//Ensure service is actually running
		startService(new Intent(this, aCalService.class));

		resourceManager = ResourceManager.getInstance(this,this);
		requestJournalResource();

		// Get time display preference
		prefer24hourFormat = prefs.getBoolean(getString(R.string.prefTwelveTwentyfour), false);

		ContentValues[] taskCollections = DavCollections.getCollections( getContentResolver(), DavCollections.INCLUDE_TASKS );
		if ( taskCollections.length == 0 ) {
			Toast.makeText(this, getString(R.string.errorMustHaveActiveCalendar), Toast.LENGTH_LONG);
			this.finish();	// can't work if no active collections
			return;
		}

		this.collectionsArray = new CollectionForArrayAdapter[taskCollections.length];
		int count = 0;
		long collectionId;
		for (ContentValues cv : taskCollections ) {
			collectionId = cv.getAsLong(DavCollections._ID);
			collectionsArray[count++] = new CollectionForArrayAdapter(this,collectionId);
		}

		this.populateLayout();
	}

	
	private void requestJournalResource() {
		currentOperation = ACTION_EDIT;
		try {
			Bundle b = this.getIntent().getExtras();
			if ( b != null && b.containsKey(KEY_OPERATION) ) {
				currentOperation = b.getInt(KEY_OPERATION);
			}
			if ( b != null && b.containsKey(KEY_CACHE_OBJECT) ) {
				CacheObject cacheJournal = (CacheObject) b.getParcelable(KEY_CACHE_OBJECT);
				resourceManager.sendRequest(new RRRequestInstance(this, cacheJournal.getResourceId(), cacheJournal.getRecurrenceId()));
				mHandler.sendMessageDelayed(mHandler.obtainMessage(SHOW_LOADING), 50);
				mHandler.sendMessageDelayed(mHandler.obtainMessage(GIVE_UP), 10000);
			}
		}
		catch (Exception e) {
			Log.e(TAG, "No bundle from caller.", e);
		}

		if ( this.journal == null && currentOperation == ACTION_CREATE ) {
			long preferredCollectionId = prefs.getLong(getString(R.string.DefaultCollection_PrefKey), -1);
			if ( Collection.getInstance(preferredCollectionId, this) == null )
				preferredCollectionId = collectionsArray[0].getCollectionId();

			this.action = ACTION_CREATE;
			setJournal(new VJournal());
			this.journal.setSummary(getString(R.string.NewJournalTitle));
		}
	}

	
	private void setJournal( VJournal newJournal ) {
		this.journal = newJournal;
		long collectionId = -1;		
		if ( currentOperation == ACTION_EDIT ) {
			this.action = ACTION_MODIFY_ALL;
		}
		else if ( currentOperation == ACTION_COPY ) {
			this.action = ACTION_CREATE;
		}

		if ( Collection.getInstance(collectionId,this) != null )
			currentCollection = Collection.getInstance(collectionId,this);

	}

	
	/**
	 * The ArrayAdapter needs something which can return a displayed value on toString() and it's
	 * not really reasonable to add that sort of oddity to Collection itself.
	 */
	private class CollectionForArrayAdapter {
		Collection c;
		public CollectionForArrayAdapter(Context cx, long id) {
			c = Collection.getInstance(id, cx);
		}

		public long getCollectionId() {
			return c.getCollectionId();
		}

		public String toString() {
			return c.getDisplayName();
		}
	}

	
	/**
	 * Populate the screen initially.
	 */
	private void populateLayout() {

		//Sidebar
		sidebar = (LinearLayout)this.findViewById(R.id.JournalEditColourBar);

		//Title
		this.journalName = (TextView) this.findViewById(R.id.JournalName);
		journalName.setSelectAllOnFocus(action == ACTION_CREATE);

		//Title
		this.journalContent = (TextView) this.findViewById(R.id.JournalNotesContent);
		journalContent.setSelectAllOnFocus(action == ACTION_CREATE);
		
		//Collection
		collectionsLayout = (LinearLayout)this.findViewById(R.id.JournalCollectionLayout);
		spinnerCollection = (Spinner) this.findViewById(R.id.JournalEditCollectionSelect);
		if (collectionsArray.length < 2) {
			spinnerCollection.setEnabled(false);
			collectionsLayout.setVisibility(View.GONE);
		}

		//date/time fields
		btnStartDate = (Button) this.findViewById(R.id.JournalDateTime);

		btnSaveChanges = (Button) this.findViewById(R.id.journal_apply_button);
		btnSaveChanges.setText((isModifyAction() ? getString(R.string.Apply) : getString(R.string.Add)));
		btnCancelChanges = (Button) this.findViewById(R.id.journal_cancel_button);
		
	
		// Button listeners
		setButtonDialog(btnStartDate, FROM_DIALOG);

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
		
		
	}


	/**
	 * Update the screen whenever something has changed.
	 */
	private void updateLayout() {
		AcalDateTime start = journal.getStart();


		String title = journal.getSummary();
		journalName.setText(title);

		String description = journal.getDescription();
		journalContent.setText(description);
		
		Integer colour = currentCollection.getColour();
		if ( colour == null ) colour = 0x70a0a0a0;
		sidebar.setBackgroundColor(colour);
		AcalTheme.setContainerColour(spinnerCollection,colour);

		try {
			// Attempt to set text colour that works with (hopefully) background colour. 
			((TextView) spinnerCollection
						.getSelectedView())
						.setTextColor(AcalTheme.pickForegroundForBackground(colour));
		}
		catch( Exception e ) {
			// Oh well.  Some other way then... @journal.
			Log.i(TAG,"Think of another solution...",e);
		}
		journalName.setTextColor(colour);

		ArrayAdapter<CollectionForArrayAdapter> collectionAdapter = new ArrayAdapter(this,android.R.layout.select_dialog_item, collectionsArray);
		int spinnerPosition = 0;
		while( spinnerPosition < collectionsArray.length && collectionsArray[spinnerPosition].getCollectionId() != currentCollection.getCollectionId())
			spinnerPosition++;

		spinnerCollection.setAdapter(collectionAdapter);
		if ( spinnerPosition < collectionsArray.length )
			//set the default according to value
			spinnerCollection.setSelection(spinnerPosition);
		
		
		btnStartDate.setText( AcalDateTimeFormatter.fmtFull( start, prefer24hourFormat) );

				
		
		
	}
	
	public boolean isModifyAction() {
		return (action >= ACTION_CREATE && action <= ACTION_MODIFY_ALL);
	}


	private void setSelectedCollection(long collectionId) {

		if ( Collection.getInstance(collectionId,this) != null )
			currentCollection = Collection.getInstance(collectionId,this);

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
		String oldSum = journal.getSummary();
		String newSum = this.journalName.getText().toString() ;
		String oldDesc = journal.getDescription();
		String newDesc = this.journalContent.getText().toString() ;
		
		if (!oldSum.equals(newSum)) journal.setSummary(newSum);
		if (!oldDesc.equals(newDesc)) journal.setDescription(newDesc);

		
		if (action == ACTION_CREATE || action == ACTION_MODIFY_ALL ) {
			if ( !this.saveChanges() ){
				Toast.makeText(this, "Save failed: retrying!", Toast.LENGTH_LONG).show();
				this.saveChanges();
			}
			return; 
		}
		

	}

	private boolean saveChanges() {
		
		try {
			VCalendar vc = (VCalendar) journal.getTopParent();

			// @journal This call will also need to send collectionId (and resourceId for updates) 
			this.dataRequest.journalChanged(vc, action);

			Log.i(TAG,"Saving journal to collection "+currentCollection.getCollectionId()+" with action " + action );
			if ( action == ACTION_CREATE )
				Toast.makeText(this, getString(R.string.TaskSaved), Toast.LENGTH_LONG).show();
			else if (action == ACTION_MODIFY_ALL)
				Toast.makeText(this, getString(R.string.TaskModifiedAll), Toast.LENGTH_LONG).show();

			Intent ret = new Intent();
			Bundle b = new Bundle();
			b.putString(KEY_VCALENDAR_BLOB, vc.getCurrentBlob());
			/*
			b.putLong(KEY_RESOURCE, currentCollection.getAsInteger(DavCollections._ID));
			*/
			ret.putExtras(b);
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
		switch ( id ) {
			case LOADING_DIALOG:
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("Loading...");
				builder.setCancelable(false);
				loadingDialog = builder.create();
				return loadingDialog;
		}
		if ( journal == null ) return null;

		// Any dialogs after this point depend on journal having been initialised
		AcalDateTime start = journal.getStart();
		
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

		switch ( id ) {
			case FROM_DIALOG:
				return new DateTimeDialog( this, start, prefer24hourFormat, true, true,
						new DateTimeSetListener() {
							public void onDateTimeSet(AcalDateTime newDateTime) {
								journal.setStart( newDateTime );
								updateLayout();
							}
						});



			default:
				return null;
		}
	}

	@Override
	public void resourceChanged(ResourceChangedEvent event) {
		// @journal Auto-generated method stub
		
	}


	@Override
	public void resourceResponse(ResourceResponse<CalendarInstance> response) {
		int msg = FAIL;
		if (response.wasSuccessful()) {
			setJournal( new VJournal((JournalInstance) response.result()) );
			msg = REFRESH;
		}
		mHandler.sendMessage(mHandler.obtainMessage(msg));
	}

}
