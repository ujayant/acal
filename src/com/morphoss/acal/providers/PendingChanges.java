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

package com.morphoss.acal.providers;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.morphoss.acal.DatabaseChangedEvent;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.AcalDBHelper;
import com.morphoss.acal.service.aCalService;

/**
 * <p>This ContentProvider interfaces with the pending_change table in the database.</p>
 *
 * <p>
 * This class accepts URI specifiers for it's operation in the following forms:
 * </p>
 * <ul>
 * <li>content://pendingchanges</li>
 * <li>content://pendingchanges/begin     - Can only be used with update(), starts a new transaction.</li>
 * <li>content://pendingchanges/approve   - Can only be used with update(), approves all changes since transaction started.</li>
 * <li>content://pendingchanges/commit    - Ends transaction. Changes are only commited if transaction has been approved.</li>
 * <li>content://pendingchanges/#         - A specific pending change</li>
 * </ul>
 *
 * @author Morphoss Ltd
 * @copyright Morphoss Ltd
 *
 */
public class PendingChanges extends ContentProvider {

	// Authority must match one defined in manifest!
	public static final String		AUTHORITY					= "pendingchanges";
	public static final Uri			CONTENT_URI					= Uri.parse("content://" + AUTHORITY);

	// Database + Table
	private SQLiteDatabase			AcalDB;
	public static final String		DATABASE_TABLE				= "pending_change";

	// Path definitions
	private static final int		ROOT						= 0;
	private static final int		ALLSETS						= 1;
	private static final int		BY_RESOURCE_ID				= 2;
	private static final int		BEGIN_TRANSACTION			= 5;
	private static final int		END_TRANSACTION				= 6;
	private static final int		APPROVE_TRANSACTION			= 7;

    //Creates Paths and assigns Path Definition Id's
    public static final UriMatcher uriMatcher = new UriMatcher(ROOT);
    static{
         uriMatcher.addURI(AUTHORITY, null, ALLSETS);
         uriMatcher.addURI(AUTHORITY, "#", BY_RESOURCE_ID);
         uriMatcher.addURI(AUTHORITY, "begin", BEGIN_TRANSACTION);
         uriMatcher.addURI(AUTHORITY, "commit", END_TRANSACTION);
         uriMatcher.addURI(AUTHORITY, "approve", APPROVE_TRANSACTION);
    }

	//Table Fields - All other classes should use these constants to access fields.
	public static final String		_ID							= "_id";
	public static final String		COLLECTION_ID				= "collection_id";
	public static final String		RESOURCE_ID					= "resource_id";
	public static final String		MODIFICATION_TIME			= "modification_time";
	public static final String		AWAITING_SYNC_SINCE			= "awaiting_sync_since";
	public static final String		OLD_DATA					= "old_data";
	public static final String		NEW_DATA					= "new_data";
	public static final String		UID							= "uid";

	/*
	 * 	(non-Javadoc)
	 * @see android.content.ContentProvider#delete(android.net.Uri, java.lang.String, java.lang.String[])
	 */
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		int count=0;
		switch (uriMatcher.match(uri)){
		case ALLSETS:
			count = AcalDB.delete(
					DATABASE_TABLE,
					selection,
					selectionArgs);
			break;
		case BY_RESOURCE_ID:
			String row_id = uri.getPathSegments().get(0);
			count = AcalDB.delete(
					DATABASE_TABLE,
					_ID + " = " + row_id +
					(!TextUtils.isEmpty(selection) ? " AND (" +
							selection + ')' : ""),
							selectionArgs);
			break;
		default: throw new IllegalArgumentException(
				"Unknown URI " + uri);
		}
		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

	/*
	 * (non-Javadoc)
	 * @see android.content.ContentProvider#getType(android.net.Uri)
	 */
	@Override
	public String getType(Uri uri) {
		switch (uriMatcher.match(uri)) {
		//Get all Servers
		case ALLSETS:
		case BY_RESOURCE_ID:
			return "vnd.android.cursor.dir/vnd.morphoss.pending_change";
		default:
			throw new IllegalArgumentException("Unsupported URI: "+uri);
		}

	}

	/*
	 * (non-Javadoc)
	 * @see android.content.ContentProvider#insert(android.net.Uri, android.content.ContentValues)
	 */
	@Override
	public Uri insert(Uri uri, ContentValues values) {
		
		String modified = new AcalDateTime().fmtIcal();
		values.put(MODIFICATION_TIME, modified );
		values.putNull(AWAITING_SYNC_SINCE );

		//---add a new server---
		long rowID = AcalDB.insert( DATABASE_TABLE, "", values);

		//---if added successfully---
		if ( rowID > 0 )
		{
			Uri _uri = ContentUris.withAppendedId(CONTENT_URI, rowID);
			getContext().getContentResolver().notifyChange(_uri, null);
			return _uri;
		}
		throw new SQLException("Failed to insert row into " + uri);
	}

	/*
	 * (non-Javadoc)
	 * @see android.content.ContentProvider#onCreate()
	 */
	@Override
	public boolean onCreate() {
		Context context = getContext();
		AcalDBHelper dbHelper = new AcalDBHelper(context);
		AcalDB = dbHelper.getWritableDatabase();
		return (AcalDB == null)?false:true;
	}

	/*
	 * (non-Javadoc)
	 * @see android.content.ContentProvider#query(android.net.Uri, java.lang.String[], java.lang.String, java.lang.String[], java.lang.String)
	 */
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {

		SQLiteQueryBuilder sqlBuilder = new SQLiteQueryBuilder();
		sqlBuilder.setTables(DATABASE_TABLE);
		
		String groupBy = null;

		if (uriMatcher.match(uri) == BY_RESOURCE_ID)
			//---if getting a particular server---
			sqlBuilder.appendWhere(_ID + " = " + uri.getPathSegments().get(0));

		// We always group by resource_id
		groupBy = RESOURCE_ID;

		Cursor c = sqlBuilder.query(
				AcalDB,
				projection,
				selection,
				selectionArgs,
				groupBy,
				null,
				sortOrder);

		//---register to watch a content URI for changes---
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}

	/*
	 * (non-Javadoc)
	 * @see android.content.ContentProvider#update(android.net.Uri, android.content.ContentValues, java.lang.String, java.lang.String[])
	 *
	 */
	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		int count = 0;

		switch (uriMatcher.match(uri)){
		case ALLSETS:
			count = AcalDB.update(
					DATABASE_TABLE,
					values,
					selection,
					selectionArgs);
			break;
		case BY_RESOURCE_ID:
			count = AcalDB.update(
					DATABASE_TABLE,
					values,
					_ID + " = " + uri.getPathSegments().get(0) +
					(!TextUtils.isEmpty(selection) ? " AND (" +
							selection + ')' : ""),
							selectionArgs);
			break;
		case BEGIN_TRANSACTION:	//Return 1 for success or 0 for failure
				//We are beginning a new transaction only (at this time we wont allow nested tx's)
				if (AcalDB.inTransaction()) return 0;
				AcalDB.beginTransaction();
				return 1;

		case END_TRANSACTION:	//Return 1 for success or 0 for failure
			//We are ending an existing transaction only
			if (!AcalDB.inTransaction()) return 0;
			AcalDB.endTransaction();
			return 1;
		case APPROVE_TRANSACTION:	//Return 1 for success or 0 for failure
			//We are ending an existing transaction only
			if (!AcalDB.inTransaction()) return 0;
			AcalDB.setTransactionSuccessful();
			return 1;
		default: throw new IllegalArgumentException(
				"Unknown URI " + uri);
		}
		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

	
	public static void deletePendingChange( Context context, int changeId ) {
		AcalDBHelper dbHelper = new AcalDBHelper(context);
		SQLiteDatabase db = dbHelper.getWritableDatabase();

		String[] params = new String[] { Integer.toString(changeId) };
		db.beginTransaction();
		try {
			db.delete(PendingChanges.DATABASE_TABLE, PendingChanges._ID+"=?", params );
			db.setTransactionSuccessful();
		}
		catch ( Exception e ){
			Log.i(AcalDBHelper.TAG,Log.getStackTraceString(e));
		}
		finally {
			db.endTransaction();
			db.close();
		}

	}
}
