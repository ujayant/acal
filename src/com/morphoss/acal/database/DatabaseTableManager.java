package com.morphoss.acal.database;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteMisuseException;
import android.os.Process;
import android.util.Log;

import com.morphoss.acal.Constants;


/**
 * Some useful code for DB managers. Extend this and database state can be maintained.
 * call beginQuery before starting any internal queries and end query when finished any internal querys.
 * transactions are exposed and maintained.
 * 
 * @author Chris Noldus
 *
 */
public abstract class DatabaseTableManager {

	protected boolean inTx = false;

	public static final int OPEN_READ = 1;
	public static final int OPEN_WRITE = 3;
	public static final int CLOSE = 5;
	
	private ArrayList<DataChangeEvent> changes;
	
	private int initialPriority;
	private static final int preferredPriority = Process.THREAD_PRIORITY_DISPLAY + (2*Process.THREAD_PRIORITY_LESS_FAVORABLE);

	protected SQLiteDatabase db = null;
	protected AcalDBHelper dbHelper;
	protected Context context;

	private boolean	readOnlyDb = true;
	private boolean inReadQuerySet = false;
	
	private long dbOpened;
	private long dbYielded;
	
	public enum QUERY_ACTION { INSERT, UPDATE, DELETE, PENDING_RESOURCE };
	
	private static final String TAG = "aCal DatabaseManager";

	public abstract void dataChanged(ArrayList<DataChangeEvent> changes);
	protected abstract String getTableName();
	
	protected DatabaseTableManager(Context context) {
		this.context = context;
	}

	private String openStackTraceInfo = null;
	
	protected void saveStackTraceInfo() {
		int base = 3;
		int depth = 12;
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		openStackTraceInfo = "\t"+stack[base].toString();
		for (int i = base+1; i < stack.length && i< base+depth; i++)
			openStackTraceInfo += "\n\t\t"+stack[i].toString(); 
	}
	
	protected void printStackTraceInfo(int logLevel) {
			int base = 3;
			int depth = 10;
			StackTraceElement[] stack = Thread.currentThread().getStackTrace();
			String info = "\t"+stack[base].toString();
			for (int i = base+1; i < stack.length && i< base+depth; i++)
				info += "\n\t\t"+stack[i].toString(); 
			Log.println(logLevel, TAG, info);
	}
	
	protected void addChange(DataChangeEvent e) {
		if (changes != null) changes.add(e);
		else throw new IllegalStateException("Can not add change when db is closed!");
	}

	protected void openDB(final int type) {
		if ( inReadQuerySet && type == OPEN_READ ) return;

		if (db != null) {
			if ( openStackTraceInfo != null ) {
				Log.e(TAG,"Tried to open DB when already open.  Database was previously opened here:\n"+openStackTraceInfo);
			}
			throw new SQLiteMisuseException("Tried to open DB when already open");
		}
		this.initialPriority = Process.getThreadPriority(Process.myTid());
		Process.setThreadPriority(preferredPriority);
		dbHelper = new AcalDBHelper(context);
		changes = new ArrayList<DataChangeEvent>();
		this.dbOpened = System.currentTimeMillis();
		this.dbYielded = dbOpened;
		if (Constants.debugDatabaseManager && Constants.LOG_DEBUG) Log.println(Constants.LOGD,TAG,"DB:"+this.getTableName()+" OPEN_READ:");
		if (Constants.debugDatabaseManager && Constants.LOG_VERBOSE) printStackTraceInfo(Log.VERBOSE);
		switch (type) {
			case OPEN_READ:
				saveStackTraceInfo();
				db = dbHelper.getReadableDatabase();
				readOnlyDb = true;
				break;
			case OPEN_WRITE:
				if (Constants.debugDatabaseManager && Constants.LOG_DEBUG) Log.println(Constants.LOGD,TAG,"DB:"+this.getTableName()+" OPEN_WRITE:");
				if (Constants.debugDatabaseManager && Constants.LOG_VERBOSE) printStackTraceInfo(Log.VERBOSE);
				saveStackTraceInfo();
				db = dbHelper.getWritableDatabase();
				readOnlyDb = false;
				break;
/*
			case OPEN_READTX:
			if (Constants.debugDatabaseManager && Constants.LOG_DEBUG) Log.println(Constants.LOGD,TAG,"DB:"+this.getTableName()+" OPEN_READTX:");
			saveStackTraceInfo();
			if (Constants.debugDatabaseManager && Constants.LOG_VERBOSE) printStackTraceInfo(Log.VERBOSE);
			inTx = true;
			inReadTx = true;
			db = dbHelper.getReadableDatabase();
			break;
		case OPEN_WRITETX:
			if (Constants.debugDatabaseManager && Constants.LOG_DEBUG) Log.println(Constants.LOGD,TAG,"DB:"+this.getTableName()+" OPEN_WRITETX:");
			saveStackTraceInfo();
			if (Constants.debugDatabaseManager && Constants.LOG_VERBOSE) printStackTraceInfo(Log.VERBOSE);
			inTx = true;
			db = dbHelper.getWritableDatabase();
			db.beginTransaction();
			break;
*/
		default:
			dbHelper.close();
			dbHelper = null;
			changes = null;
			throw new IllegalArgumentException("Invalid argument provided for openDB");		
		}
	}

	protected void closeDB() {
		if ( inReadQuerySet ) return;
		if (inTx) {
			Log.println(Constants.LOGE, TAG, Log.getStackTraceString(new Exception("Transaction still open during database close!")));
		}

		if (db == null) throw new SQLiteMisuseException("Tried to close a DB that wasn't opened");
		db.close();
		while (db.isOpen()) {
			try {
				Thread.sleep(10); 
			} catch (Exception e) {}
		}
		db = null;
		openStackTraceInfo = null;
		dbHelper.close();
		dbHelper = null;
		Process.setThreadPriority(this.initialPriority);

//		switch (type) {
//		case CLOSE:
			if (Constants.debugDatabaseManager && Constants.LOG_DEBUG) Log.println(Constants.LOGD,TAG,"DB:"+this.getTableName()+" CLOSE:");
			if (Constants.debugDatabaseManager && Constants.LOG_VERBOSE) printStackTraceInfo(Log.VERBOSE);
//			break;
//		case CLOSE_TX:
//			if (Constants.debugDatabaseManager && Constants.LOG_DEBUG) Log.println(Constants.LOGD,TAG,"DB:"+this.getTableName()+" CLOSETX:");
//			if (Constants.debugDatabaseManager && Constants.LOG_VERBOSE) printStackTraceInfo(Log.VERBOSE);
//			if (!inTx) throw new IllegalStateException("Tried to close a db transaction when not in one!");
//			inTx = false;
//			inReadTx = false;
//			openStackTraceInfo = null;
//			break;
//		default:
//			throw new IllegalArgumentException("Invalid argument provided for openDB");
//		}

		long time = System.currentTimeMillis()-this.dbYielded;
		//Metric checking to make sure that the database is being used correctly.
		if ( (Constants.DEBUG_MODE && time > 700) || time > 2000 ) {
			Log.w(TAG, "Database opened for excessive period of time ("+time+"ms) Without yield!:");
			this.printStackTraceInfo(Log.WARN);
		}
		if (Constants.debugDatabaseManager && Constants.LOG_DEBUG ) {
			time = System.currentTimeMillis()- this.dbOpened;
			Log.println(Constants.LOGD, TAG, "Database opened for "+time+"ms");
		}
		this.dataChanged(changes);
		changes = null;
	}

	public synchronized void openReadQuerySet() {
		if ( db != null ) throw new IllegalStateException("Tried to open database for read query set but it is already open!");
		openDB(OPEN_READ);
		inReadQuerySet = true;
	}
	
	public synchronized void closeReadQuerySet() {
		if ( db == null ) throw new IllegalStateException("Tried to close read query set but database is not open!");
		if ( !inReadQuerySet ) throw new IllegalStateException("Cannot close a readQuerySet that was not started!");
		inReadQuerySet = false;
		closeDB();
	}
	
	public void yield() {
		if (Constants.debugDatabaseManager) Log.d(TAG, "Yield Called.");
		if (db != null && inTx ) {
			this.dbYielded = System.currentTimeMillis();
			db.yieldIfContendedSafely();
		}
	}

/*
	protected void beginReadQuery() {
		if ( db == null ) 
			openDB(OPEN_READ);
		else if (!inTx && db != null) throw new IllegalStateException("BeginReadQuery called in invalid state");
	}

	protected void endQuery() {
		if (db == null) throw new IllegalStateException("End query called on closed db");
		if (!inTx && !inReadTx) closeDB(CLOSE);
	}

	protected void beginWriteQuery() {
		if (!inTx && db == null) openDB(OPEN_WRITE);
		else if (inReadTx) throw new IllegalStateException("Can not begin write query while in ReadOnly tx");
		else if (!inTx && db != null) throw new IllegalStateException("BeginWrite called when db already open and not in tx");
	}
*/

	public synchronized void beginTx() {
		if ( db == null ) throw new IllegalStateException("Tried to start Tx when database is not open!");
		if ( inTx) throw new IllegalStateException("Tried to start Tx when already in TX");
		inTx = true;
		db.beginTransaction();
	}

	public void setTxSuccessful() {
		if ( db == null ) throw new IllegalStateException("Tried to set Tx successful when database is not open!");
		if ( !inTx ) throw new IllegalStateException("Tried to set Tx Successful when not in TX");
		try {
			db.setTransactionSuccessful();
		}
		catch( IllegalStateException e ) {
			Log.println(Constants.LOGE, TAG,Log.getStackTraceString(e));
		}
	}

	public synchronized void endTx() {
		if ( db == null ) throw new IllegalStateException("Tried to end Tx when database is not open!");
		if (!inTx)  throw new IllegalStateException("Tried to end Tx when not in TX");
		try {
			db.endTransaction();
		}
		catch( Exception e ) {
			Log.println(Constants.LOGE, TAG,Log.getStackTraceString(e));
		}
		inTx = false;
	}

/*	
	private void beginTransactionX() {
		openDB(OPEN_WRITETX);
	}

	private void beginReadTransactionX() {
		openDB(OPEN_READTX);
	}

	private void endTransactionX() {
		if (!inTx)  throw new IllegalStateException("Tried to end Tx when not in TX");
		if (!inReadTx) db.endTransaction();
		closeDB(CLOSE_TX);
	}
*/
	
	//Some useful generic methods

	public ArrayList<ContentValues> query(String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {
		ArrayList<ContentValues> result = new ArrayList<ContentValues>();
		int count = 0;
		if (Constants.debugDatabaseManager && Constants.LOG_DEBUG) Log.println(Constants.LOGD,TAG,"DB: "+this.getTableName()+" query:");
		if (Constants.debugDatabaseManager && Constants.LOG_VERBOSE) printStackTraceInfo(Log.VERBOSE);
	
		boolean openedInternally = false;
		if ( db == null ) {
			openedInternally = true;
			openDB(OPEN_READ);
		}
		Cursor c = db.query(getTableName(), columns, selection, selectionArgs, groupBy, having, orderBy);
		try {
			if (c.getCount() > 0) {
				for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
					result.add(new ContentValues());
					DatabaseUtils.cursorRowToContentValues(c, result.get(count++));
				}
			}
		}
		catch( Exception e ) {
			Log.e(TAG,Log.getStackTraceString(e));
		}
		finally {
			if ( c != null ) c.close();
			while (c!=null) {
				if (c.isClosed()) c = null;
				try { Thread.sleep(10); } catch (Exception e) {}
			}
			if ( openedInternally ) closeDB();
		}
		
		return result;
	}

	
	public int delete(String whereClause, String[] whereArgs) {
		boolean openedInternally = false;
		if ( db == null ) {
			openedInternally = true;
			openDB(OPEN_WRITE);
		}

		if ( readOnlyDb ) throw new IllegalStateException("Cannot delete when DB is read-only!");
		if (Constants.debugDatabaseManager && Constants.LOG_DEBUG) Log.println(Constants.LOGD,TAG,
				"Deleting Row on "+this.getTableName()+":\n\tWhere: "+whereClause);

		int count = 0;
		try {
			//First select or the row id's
			ArrayList<ContentValues> rows = this.query(null, whereClause, whereArgs, null,null,null);
			count = db.delete(getTableName(), whereClause, whereArgs);
	
			if (count != rows.size()) {
				if (Constants.debugDatabaseManager) Log.w(TAG, "Inconsistent number of rows deleted!");
			}
			if (count == 0) {
				if (Constants.debugDatabaseManager) Log.w(TAG, "No rows deleted for '"+whereClause+"' args: "+whereArgs);
			}
			else {
				for (ContentValues cv : rows) {
					changes.add(new DataChangeEvent(QUERY_ACTION.DELETE,cv));
				}
			}
		}
		catch( Exception e ) {
			Log.e(TAG,Log.getStackTraceString(e));
		}
		finally {
			if ( openedInternally ) closeDB();
		}
		return count;
	}

	public int update(ContentValues values, String whereClause, String[] whereArgs) {
		boolean openedInternally = false;
		if ( db == null ) {
			openedInternally = true;
			openDB(OPEN_WRITE);
		}

		if ( readOnlyDb ) throw new IllegalStateException("Cannot update when DB is read-only!");
		if (Constants.debugDatabaseManager && Constants.LOG_VERBOSE) Log.println(Constants.LOGV,TAG,
				"Updating Row on "+this.getTableName()+":\n\t"+values.toString());

		int count = 0;
		try {
			count = db.update(getTableName(), values, whereClause, whereArgs);
			changes.add(new DataChangeEvent(QUERY_ACTION.UPDATE, new ContentValues(values)));
		}
		catch( Exception e ) {
			Log.e(TAG,Log.getStackTraceString(e));
		}
		finally {
			if ( openedInternally ) closeDB();
		}
		return count;
	}

	public long insert(String nullColumnHack, ContentValues values) {
		boolean openedInternally = false;
		if ( db == null ) {
			openedInternally = true;
			openDB(OPEN_WRITE);
		}

		if ( readOnlyDb ) throw new IllegalStateException("Cannot insert when DB is read-only!");
		if (Constants.debugDatabaseManager && Constants.LOG_VERBOSE) Log.println(Constants.LOGV, TAG, 
				"Inserting Row on "+this.getTableName()+":\n\t"+values.toString());

		Long newId = null;
		try {
			newId = db.insert(getTableName(), nullColumnHack, values);
			values.put("_id", newId);
			changes.add(new DataChangeEvent(QUERY_ACTION.INSERT, new ContentValues(values)));
		}
		finally {
			if ( openedInternally ) closeDB();
		}
		return newId;
	}

	
	public boolean processActions(DMQueryList queryList) {
		boolean openedInternally = false;
		if ( db == null ) {
			openedInternally = true;
			openDB(OPEN_WRITE);
		}
		if ( readOnlyDb  ) throw new IllegalStateException("Can not insert when DB is read-only!");

		List<DMAction> actions = queryList.getActions();
		boolean res = false;
		boolean transactionInternally = false;
		if ( !inTx ) {
			beginTx();
			transactionInternally = true;
		}
		try {
			for (DMAction action : actions) {
				action.process(this);
				this.yield();
			}
			res = true;
		}
		catch ( Exception e ) {
			Log.e(TAG, "Exception processing request: " + e + Log.getStackTraceString(e));
		}
		finally {
			if ( transactionInternally ) {
				if ( res ) setTxSuccessful();
				endTx();
			}
			if ( openedInternally ) closeDB();
		}
		return res;
	}
}
