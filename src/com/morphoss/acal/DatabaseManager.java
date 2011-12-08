package com.morphoss.acal;

import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteMisuseException;

import com.morphoss.acal.database.AcalDBHelper;

/**
 * Some useful code for DB managers. Extend this and database state can be maintained.
 * call beginQuery before starting any internal queries and end query when finished any internal querys.
 * transactions are exposed and maintained.
 * 
 * @author Chris Noldus
 *
 */
public abstract class DatabaseManager {

	protected SQLiteDatabase db;
	protected AcalDBHelper dbHelper;
	protected Context context;
	
	protected boolean inTx = false;
	private boolean sucTx = false;
	
	public static final int OPEN_READ = 1;
	public static final int OPEN_WRITE = 2;
	public static final int OPEN_WRITETX = 3;
	
	public static final int CLOSE = 4;
	public static final int CLOSE_TX = 5;
	
	protected DatabaseManager(Context context) {
		this.context = context;
	}
	
	private void openDB(final int type) {
		if (inTx || sucTx || db != null) throw new SQLiteMisuseException("Tried to open DB when already open");
		dbHelper = new AcalDBHelper(context);
		switch (type) {
		case OPEN_READ:
			db = dbHelper.getReadableDatabase();
			break;
		case OPEN_WRITE:
			db = dbHelper.getWritableDatabase();
			break;
		case OPEN_WRITETX:
			inTx = true;
			db = dbHelper.getWritableDatabase();
			db.beginTransaction();
			break;
		default:
			dbHelper.close();
			dbHelper = null;
			throw new IllegalArgumentException("Invalid argument provided for openDB");		
		}
	}
	
	private void closeDB(final int type) {
		if (db == null) throw new SQLiteMisuseException("Tried to close a DB that wasn't opened");
		db.close();
		dbHelper.close();
		db = null;
		dbHelper = null;
		switch (type) {
			case CLOSE:
				break;
			case CLOSE_TX:
				if (!inTx) throw new IllegalStateException("Tried to close a db transaction when not in one!");
				inTx = false;
				sucTx = false;
				break;
		default:
			throw new IllegalArgumentException("Invalid argument provided for openDB");
		}
	}
	
	protected void beginReadQuery() {
		if (!inTx) openDB(OPEN_READ);
	}
	
	protected void endQuery() {
		if (!inTx) closeDB(CLOSE);
	}
	
	protected void beginWriteQuery() {
		if (!inTx) openDB(OPEN_WRITE);
	}
	
	
	public void beginTransaction() {
		openDB(OPEN_WRITETX);
	}
	
	public void setTxSuccessful() {
		if (!inTx || db == null || !db.isOpen()) throw new IllegalStateException("Tried to set Tx Successful when not in TX");
		db.setTransactionSuccessful();
		this.sucTx = true;
	}
	
	public void endTransaction() {
		closeDB(CLOSE_TX);
	}
	
	protected abstract String getTableName();
	
	//Some useful generic methods
	
	public int delete(String whereClause, String[] whereArgs) {
		beginWriteQuery();
		int count = db.delete(getTableName(), whereClause, whereArgs);
		endQuery();
		return count;
	}

	public int update(ContentValues values, String whereClause,
			String[] whereArgs) {
		beginWriteQuery();
		int count = db.update(getTableName(), values, whereClause,
				whereArgs);
		endQuery();
		return count;
	}

	public long insert(String nullColumnHack, ContentValues values) {
		beginWriteQuery();
		long count = db.insert(getTableName(), nullColumnHack, values);
		endQuery();
		return count;
	}

	public ArrayList<ContentValues> query(String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {
		beginReadQuery();
		ArrayList<ContentValues> result = new ArrayList<ContentValues>();
		int count = 0;
		Cursor c = db.query(getTableName(), columns, selection, selectionArgs, groupBy, having, orderBy);
		if (c.getCount() > 0) {
			for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
				result.add(new ContentValues());
				DatabaseUtils.cursorRowToContentValues(c, result.get(count++));
			}
		}
		endQuery();
		return result;
	}
	
	public class DMQueryList {
		private ArrayList<DMAction> actions = new ArrayList<DMAction>();
		public void addAction(DMAction action) { actions.add(action); }
		public boolean process(DatabaseManager dm) {
			boolean res = false;
			try {
				dm.beginTransaction();
				for (DMAction action : actions) action.process(dm);
				dm.setTxSuccessful();
				res = true;
			} catch (Exception e) {
			} finally { dm.endTransaction();}
			return res;
		}
	}
	
	
	public interface DMAction {
		public void process(DatabaseManager dm);
	}
	
	public final class DMInsertQuery implements DMAction {
		
		private final String nullColumnHack;
		private final ContentValues values;
		
		public DMInsertQuery(String nullColumnHack, ContentValues values) {
			this.nullColumnHack = nullColumnHack;
			this.values = values;
		}
		
		public void process(DatabaseManager dm) {
			dm.insert(nullColumnHack, values);
		}
	}
	
	public final class DMUpdateQuery implements DMAction {

		private final ContentValues values;
		private final String whereClause;
		private final String[] whereArgs;
		
		public DMUpdateQuery(ContentValues values, String whereClause, String[] whereArgs) {
			this.values = values;
			this.whereClause = whereClause;
			this.whereArgs = whereArgs;
		}
		
		@Override
		public void process(DatabaseManager dm) {
			dm.update(values, whereClause, whereArgs);
		}
	}
	
	public final class DMDeleteQuery implements DMAction {

		private final String whereClause;
		private final String[] whereArgs;
		
		public DMDeleteQuery(String whereClause, String[] whereArgs) {
			this.whereClause = whereClause;
			this.whereArgs = whereArgs;
		}
		
		@Override
		public void process(DatabaseManager dm) {
			dm.delete(whereClause, whereArgs);			
		}
		
	}
}
