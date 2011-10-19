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

package com.morphoss.acal.activity.serverconfig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.sqlite.SQLiteConstraintException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.morphoss.acal.CheckServerFailedError;
import com.morphoss.acal.Constants;
import com.morphoss.acal.R;
import com.morphoss.acal.ServiceManager;
import com.morphoss.acal.providers.Servers;
import com.morphoss.acal.service.connector.AcalRequestor;


/**
 * <p>
 *  When a server is first configured we run a bunch of tests on it to try and figure
 *  out WTF we are dealing with.  This is where most of that happens.
 *  </p>
 * 
 * @author Morphoss Ltd
 *
 */
public class CheckServerDialog implements Runnable {


	private static final String TAG = "aCal CheckServerDialog";
	private final Context context;
	private final ContentValues serverData;
	private final ServerConfiguration sc;
	private final AcalRequestor requestor;
	private ProgressDialog dialog;

	private static final int SHOW_FAIL_DIALOG = 0;
	private static final int CHECK_COMPLETE = 1;
	private static final int REFRESH_PROGRESS = 2;
	private static final String MESSAGE = "MESSAGE";
	private static final String REFRESH = "REFRESH";
	private static final String TYPE = "TYPE";

	private List<String> successMessages = new ArrayList<String>();
	private boolean advancedMode = false;

	
	//dialog types
	
	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Bundle b = msg.getData();
			int type = b.getInt(TYPE);
			switch (type) {
				case REFRESH_PROGRESS: 	
					if (dialog != null)
						dialog.setMessage(b.getString(REFRESH));
					break;

				case SHOW_FAIL_DIALOG: 	
					if (dialog != null) {
						dialog.dismiss();
						dialog=null;
					}
					showFailDialog(b.getString(MESSAGE));
					break;
				case CHECK_COMPLETE:
					if (dialog != null) {
						dialog.dismiss();
						dialog = null;
					}
					showSuccessDialog(b.getString(MESSAGE));
					break;
			}
		}
	};
	
	
	public CheckServerDialog(ServerConfiguration serverConfiguration, ContentValues serverData, Context cx, ServiceManager sm) {
		this.advancedMode = (serverConfiguration.iface == ServerConfiguration.INTERFACE_ADVANCED);
		this.context = cx;
		this.sc = serverConfiguration;
		

		//we must remove any values that may have leaked through from XML that are not part of the DB table
		ServerConfigData.removeNonDBFields(serverData);
		this.serverData = serverData;
		if ( advancedMode )
			this.requestor = AcalRequestor.fromServerValues(serverData);
		else
			this.requestor = AcalRequestor.fromSimpleValues(serverData);
	}
	
	public void start() {
		dialog = ProgressDialog.show(sc, context.getString(R.string.checkServer), context.getString(R.string.checkServer_Connecting));
		dialog.setIndeterminate(true);
		Thread t = new Thread(this);
		t.start();
	}
	
	public void run() {
		checkServer();
	}

	/**
	 * <p>
	 * Checks the server they have entered for validity. Endeavouring to profile it in some ways that we can
	 * use later to control how we will conduct synchronisations and discovery against it.
	 * </p>
	 */
	private void checkServer() {
		TestPort tester = null;
		try {
			// Step 1, check for internet connectivity
			updateProgress(context.getString(R.string.checkServer_Internet));
			if ( !checkInternetConnected() ) {
				throw new CheckServerFailedError(context.getString(R.string.internetNotAvailable));
			}

			// Step 2, check we can connect to the server on the given port
			if ( advancedMode ) {
				tester = new TestPort(requestor);
				updateProgress(context.getString(R.string.checkServer_SearchingForServer, requestor.fullUrl()));
				if ( !tester.hasCalDAV() ) {
					// Try harder!
					requestor.applyFromServer(serverData, false);
					tester.reProbe();
				}
			}
			else {
				Iterator<TestPort> testers = TestPort.defaultIterator(requestor);

				do {
					tester = testers.next();
					requestor.applyFromServer(serverData, true);
					updateProgress(context.getString(R.string.checkServer_SearchingForServer,
							tester.getProtocolUrlPrefix() + serverData.getAsString(Servers.SUPPLIED_DOMAIN) + ":" + tester.port ));
				} while( !tester.hasCalDAV() && testers.hasNext() );
				if ( !tester.hasCalDAV() ) {
					// Try harder!
					testers = TestPort.defaultIterator(requestor);
					do {
						tester = testers.next();
						requestor.applyFromServer(serverData, true);
						tester.reProbe(); // Extend the timeout
						updateProgress(context.getString(R.string.checkServer_SearchingForServer,
								tester.getProtocolUrlPrefix() + serverData.getAsString(Servers.SUPPLIED_DOMAIN) + ":" + tester.port ));
					} while( !tester.hasCalDAV() && testers.hasNext() );
				}
			}

			if ( tester.hasCalDAV() ) {
				Log.w(TAG, "Found CalDAV: " + requestor.fullUrl());
				serverData.put(Servers.HAS_CALDAV, 1);
				successMessages.add(context.getString(R.string.serverSupportsCalDAV));
				successMessages.add(String.format(context.getString(R.string.foundPrincipalPath), requestor.fullUrl()));
				requestor.applyToServerSettings(serverData);
			}
			else if ( !tester.authOK() ) {
				Log.w(TAG, "Failed Auth: " + requestor.fullUrl());
				successMessages.add(context.getString(R.string.authenticationFailed));
			}
			else {
				Log.w(TAG, "Found no CalDAV");
				serverData.put(Servers.HAS_CALDAV, 0);
				successMessages.add(context.getString(R.string.serverLacksCalDAV));
			}


			// Step 6, Exit with success message
			Message m = Message.obtain();
			Bundle b = new Bundle();
			b.putInt(TYPE, (tester.hasCalDAV() && tester.authOK() ? CHECK_COMPLETE : SHOW_FAIL_DIALOG));

			StringBuilder successMessage = new StringBuilder("");
			for( String msg : successMessages ) {
				successMessage.append("\n");
				successMessage.append(msg);
			}
			b.putString(MESSAGE, successMessage.toString());

			m.setData(b);
			handler.sendMessage(m);

		}
		catch (CheckServerFailedError e) {

			// Getting server details failed
			if (Constants.LOG_DEBUG)
				Log.d(TAG, "Connect failed: " + e.getMessage());
			Message m = Message.obtain();
			Bundle b = new Bundle();
			b.putInt(TYPE, SHOW_FAIL_DIALOG);
			b.putString(MESSAGE, e.getMessage());
			m.setData(b);
			handler.sendMessage(m);

		}
		catch (Exception e) {
			// Something unknown went wrong
			Log.w(TAG, "Unexpected Failure: " + e.getMessage());
			Log.w(TAG, Log.getStackTraceString(e));
			Message m = Message.obtain();
			Bundle b = new Bundle();
			b.putInt(TYPE, SHOW_FAIL_DIALOG);
			b.putString(MESSAGE, "Unknown Error: " + e.getMessage());
			m.setData(b);
			handler.sendMessage(m);
		}
	}

	/**
	 * Update the progress dialog with a friendly string.
	 * @param newMessage
	 */
	private void updateProgress( String newMessage ) {
		Message m = Message.obtain();
		Bundle b = new Bundle();
		b.putInt(TYPE, REFRESH_PROGRESS);
		b.putString(REFRESH, newMessage);
		m.setData(b);
		handler.sendMessage(m);
	}

	private void showFailDialog(String msg) {
		AlertDialog.Builder builder = new AlertDialog.Builder(sc);
		builder.setMessage(
					context.getString(R.string.serverFailedValidation)
					+"\n\n" + msg +"\n\n"
					+ context.getString(R.string.saveSettingsAnyway)
			);
		builder.setPositiveButton(context.getString(android.R.string.yes), dialogClickListener);
		builder.setNegativeButton(context.getString(android.R.string.no), dialogClickListener);
		builder.show();
	}

	private void showSuccessDialog(String msg) {

		AlertDialog.Builder builder = new AlertDialog.Builder(sc);

		try {
			// Before we display the success dialog and especially before we start syncing it.
			sc.saveData();

			builder.setMessage(msg + "\n\n" + context.getString(R.string.serverValidationSuccess));
			
		}
		catch( SQLiteConstraintException e ) {
			builder.setMessage(msg + "\n\n" + context.getString(R.string.serverValidationSuccess)
						+ "\n\n" + context.getString(R.string.serverRecordAlreadyExists)
				);
		}

		// We don't set a positive button here since we already saved, above, and
		// the background actions may have already updated the server table further!
		// Or worse: we couldn't save, so trying again would be futile...
		builder.setNeutralButton(context.getString(android.R.string.ok), dialogClickListener);

		// builder.setNegativeButton("No", dialogClickListener);
		builder.show();
	}

	DialogInterface.OnClickListener	dialogClickListener	= new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					sc.saveData();
					// fall through
				case DialogInterface.BUTTON_NEUTRAL:
					// We already saved before displaying the success dialog to give sync a headstart
					sc.finish();
					break;
				case DialogInterface.BUTTON_NEGATIVE:
					// Do nothing
					break;
			}
		}
	};

	/** Check server methods: Each of these methods represents a different step in the check server process */
	
	/**
	 *	Checks to see if the device has a connection to the internet. Throws CheckServerFailed Error if no connection
	 *	can be established, or if System throws an error while trying to find out 
	 * @throws CheckServerFailedError
	 */
	private boolean checkInternetConnected() {
		try {
			ConnectivityManager connec = (ConnectivityManager) sc.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo netInfo = connec.getActiveNetworkInfo();
			return  (netInfo != null && netInfo.isConnected());
		}
		catch (Exception e) {
			return false;
		}
	}


	
}
