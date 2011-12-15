package com.morphoss.acal.dataservice;

import java.util.HashMap;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import com.morphoss.acal.StaticHelpers;
import com.morphoss.acal.providers.DavCollections;

public class Collection {
	
	final private static String TAG = "aCal Collection";
	private ContentValues cv;
	private int collectionColour;
	public boolean alarmsEnabled;
	public final int collectionId;
	public final boolean useForEvents;
	public final boolean useForTasks;
	public final boolean useForJournal;
	public final boolean useForAddressbook;
	
	private static final HashMap<Long,Collection> collections = new HashMap<Long,Collection>();
	
	public synchronized  static Collection getInstance(long id, Context context) {
		if (collections.containsKey(id)) 
			return collections.get(id);
		//TODO get from DB
		Collection instance = fromDatabase(id,context);
		collections.put(id, instance);
		return instance;
	}
	
	//Call this method if there are any changes to Colelctions table.
	public synchronized static void flush() {
		collections.clear();
	}
	public Collection( ContentValues collectionRow ) {
		cv = collectionRow;
		setColour(cv.getAsString(DavCollections.COLOUR));
		alarmsEnabled = (cv.getAsInteger(DavCollections.USE_ALARMS) == 1);
		collectionId = cv.getAsInteger(DavCollections._ID);
		useForEvents = (cv.getAsInteger(DavCollections.ACTIVE_EVENTS) == 1);
		useForTasks = (cv.getAsInteger(DavCollections.ACTIVE_TASKS) == 1);
		useForJournal = (cv.getAsInteger(DavCollections.ACTIVE_JOURNAL) == 1);
		useForAddressbook = (cv.getAsInteger(DavCollections.ACTIVE_ADDRESSBOOK) == 1);
	}

	private static Collection fromDatabase(long collectionId, Context context) {
		ContentValues collectionRow = DavCollections.getRow(collectionId, context.getContentResolver());
		if ( collectionRow == null ) return null;
		return new Collection(collectionRow);
	}
	

	public void updateCollectionRow( ContentValues collectionRow ) {
		if ( cv.getAsInteger(DavCollections._ID) != collectionId ) {
			Log.w(TAG,"Attempt to re-use AcalCollection with different Collection ID");
			try {
				throw new Exception("");
			}
			catch ( Exception e ) {
				Log.w(TAG,Log.getStackTraceString(e));
			}
			return;
		}
		cv.putAll(collectionRow);
		if (cv.containsKey(DavCollections.COLOUR)){
			setColour(cv.getAsString(DavCollections.COLOUR));
		}
		if (cv.containsKey(DavCollections.USE_ALARMS)){
			alarmsEnabled = (cv.getAsInteger(DavCollections.USE_ALARMS) == 1);
		}
	}

	public int getColour() {
		return collectionColour;
	}

	public int setColour( String colourString ) {
		if ( colourString == null ) colourString = StaticHelpers.randomColorString();
		try {
			collectionColour = Color.parseColor(colourString);
		} catch (IllegalArgumentException iae) {
			collectionColour = Color.parseColor("#00f");	//Default blue
		}
		return collectionColour;
	}

	public ContentValues getCollectionRow() {
		return cv;
	}

	public long getCollectionId() {
		return collectionId;
	}

	public String getDisplayName() {
		return cv.getAsString(DavCollections.DISPLAYNAME);
	}

	public boolean alarmsEnabled() {
		return this.alarmsEnabled;
	}
}