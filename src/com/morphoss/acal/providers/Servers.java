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

import com.morphoss.acal.DatabaseChangedEvent;
import com.morphoss.acal.database.AcalDBHelper;
import com.morphoss.acal.service.aCalService;

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

/**
 * <P>This ContentProvider interfaces with the dav_server table in the database.</P>
 * 
 * @author Morphoss Ltd
 *
 */
public class Servers extends ContentProvider {

	//Authority must match one defined in manifest!
	public static final String AUTHORITY = "servers";
    public static final Uri CONTENT_URI = Uri.parse("content://"+ AUTHORITY);
    
    //Database + Table
    private SQLiteDatabase AcalDB;
    public static final String DATABASE_TABLE = "dav_server";
    
    //Path definitions
    private static final int ROOT = 0;
    private static final int SERVERS = 1;
    private static final int SERVER_ID = 2;   
       
    //Creates Paths and assigns Path Definition Id's
    public static final UriMatcher uriMatcher = new UriMatcher(ROOT);
    static{
         uriMatcher.addURI(AUTHORITY, null, SERVERS);
         uriMatcher.addURI(AUTHORITY, "#", SERVER_ID);
    }

	//Table Fields - All other classes should use these constants to access fields.
	public static final String _ID = "_id";
	public static final String FRIENDLY_NAME="friendly_name";
	public static final String LAST_CHECKED="last_checked";
	public static final String SUPPLIED_DOMAIN="supplied_domain";
	public static final String SUPPLIED_PATH="supplied_path";
	public static final String HOSTNAME="hostname";
	public static final String PRINCIPAL_PATH="principal_path";
	public static final String USERNAME="username";
	public static final String PASSWORD="password";
	public static final String PORT="port";
	public static final String AUTH_TYPE="auth_type";
	public static final String HAS_SRV="has_srv"; 
	public static final String HAS_WELLKNOWN="has_wellknown";
	public static final String HAS_CALDAV="has_caldav";
	public static final String HAS_MULTIGET="has_multiget";
	public static final String HAS_SYNC="has_sync";
	public static final String ACTIVE="active";
	public static final String USE_SSL="use_ssl";

	/*
	 * 	(non-Javadoc)
	 * @see android.content.ContentProvider#delete(android.net.Uri, java.lang.String, java.lang.String[])
	 */
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		int count=0;
		switch (uriMatcher.match(uri)){
		case SERVERS:
			count = AcalDB.delete(
					DATABASE_TABLE,
					selection, 
					selectionArgs);
			break;
		case SERVER_ID:
			String id = uri.getPathSegments().get(0);
			count = AcalDB.delete( DATABASE_TABLE,
						_ID + " = " + id + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), 
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
		// TODO Auto-generated method stub
		switch (uriMatcher.match(uri)) {
		//Get all Servers
		case SERVERS:
			return "vnd.android.cursor.dir/vnd.morphoss.servers";
		case SERVER_ID:
			return "vnd.android.cursor.item/vnd.morphoss.servers";
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
		//---add a new server---
		long rowID = AcalDB.insert(
				DATABASE_TABLE, "", values);

		//---if added successfully---
		if (rowID>0)
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

		if (uriMatcher.match(uri) == SERVER_ID)
			//---if getting a particular server---
			sqlBuilder.appendWhere(_ID + " = " + uri.getPathSegments().get(0));                

		if (sortOrder==null || sortOrder.equals("") )
			sortOrder = _ID;

		Cursor c = sqlBuilder.query(
				AcalDB, 
				projection, 
				selection, 
				selectionArgs, 
				null, 
				null, 
				sortOrder);

		//---register to watch a content URI for changes---
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}

	/*
	 * (non-Javadoc)
	 * @see android.content.ContentProvider#update(android.net.Uri, android.content.ContentValues, java.lang.String, java.lang.String[])
	 */
	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		int count = 0;
	
		switch (uriMatcher.match(uri)){
		case SERVERS:
			count = AcalDB.update(
					DATABASE_TABLE, 
					values,
					selection, 
					selectionArgs);
			break;
		case SERVER_ID:                
			count = AcalDB.update(
					DATABASE_TABLE, 
					values,
					_ID + " = " + uri.getPathSegments().get(0) + 
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

	
	public static void deleteServer( Context context, int serverId ) {
		AcalDBHelper dbHelper = new AcalDBHelper(context);
		SQLiteDatabase db = dbHelper.getWritableDatabase();

		String[] params = new String[] { Integer.toString(serverId) };
		db.beginTransaction();
		try {
			db.delete(PathSets.DATABASE_TABLE, PathSets.SERVER_ID+"=?", params );
			db.delete(DavResources.DATABASE_TABLE,
						DavResources.COLLECTION_ID
						+" IN (SELECT "+DavCollections._ID+" FROM "+DavCollections.DATABASE_TABLE
															+" WHERE "+DavCollections.SERVER_ID+"=?)",
						params );
			db.delete(PendingChanges.DATABASE_TABLE,
						PendingChanges.COLLECTION_ID
						+" IN (SELECT "+DavCollections._ID+" FROM "+DavCollections.DATABASE_TABLE
															+" WHERE "+DavCollections.SERVER_ID+"=?)",
						params );
			db.delete(DavCollections.DATABASE_TABLE, DavCollections.SERVER_ID+"=?", params );
			db.delete(Servers.DATABASE_TABLE, Servers._ID+"=?", params );
			db.setTransactionSuccessful();
		}
		catch ( Exception e ){
			Log.i(AcalDBHelper.TAG,Log.getStackTraceString(e));
		}
		finally {
			db.endTransaction();
			db.close();

			//FINALLY DISPATCH CHANGE
			aCalService.databaseDispatcher.dispatchEvent(new DatabaseChangedEvent(DatabaseChangedEvent.DATABASE_INVALIDATED,null,null));
		}

	}
}
