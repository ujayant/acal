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
import java.util.HashSet;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
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
import com.morphoss.acal.service.connector.SendRequestFailedException;
import com.morphoss.acal.xml.DavNode;


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
	
	private ArrayList<Integer> foundPorts = null;
	
	private static final int SHOW_FAIL_DIALOG = 0;
	private static final int CHECK_COMPLETE = 1;
	private static final String MESSAGE = "MESSAGE";
	private static final String TYPE = "TYPE";

	private List<String> successMessages = new ArrayList<String>();
	private boolean has_calendar_access = false;

	private static final String pPathRequestData = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"+
													"<propfind xmlns=\"DAV:\">"+
														"<prop>"+
															"<principal-collection-set/>"+
															"<current-user-principal/>"+
														"</prop>"+
													"</propfind>";
	private static final Header[] pPathHeaders = new Header[] {
		new BasicHeader("Depth","0"),
		new BasicHeader("Content-Type","text/xml; charset=UTF-8")
	};

	
	//dialog types
	
	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Bundle b = msg.getData();
			int type = b.getInt(TYPE);
			switch (type) {
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
		this.context = cx;
		this.sc = serverConfiguration;
		

		//we must remove any values that may have leaked through from XML that are not part of the DB table
		ServerConfigData.removeNonDBFields(serverData);
		this.serverData = serverData;
		this.requestor = AcalRequestor.fromServerValues(serverData);
	}
	
	public void start() {
		dialog = ProgressDialog.show(sc, "Validating server", "Connecting to server please wait....");
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
		try {
			// Step 1, check for internet connectivity
			if ( !checkInternetConnected() ) {
				throw new CheckServerFailedError(context.getString(R.string.internetNotAvailable));
			}

			// Step 2, check we can connect to the server on the given port
			if ( !probeServerPorts() ) {
				throw new CheckServerFailedError(context.getString(R.string.couldNotDiscoverPort));
			}

			// Step 2, Try some PROPFIND requests to find a principal path
			boolean discovered	= false;
			boolean authOK		= false;
			boolean authFailed	= false;
			if ( serverData.getAsString(Servers.PRINCIPAL_PATH) != null ) {
				discovered = doPropfindPrincipal(serverData.getAsString(Servers.PRINCIPAL_PATH));
				if ( requestor.getStatusCode() < 300 ) authOK = true;
				else if ( requestor.getStatusCode() == 401 ) authFailed = true;
			}
			if ( !discovered && serverData.getAsString(Servers.SUPPLIED_PATH) != null ) {
				discovered = doPropfindPrincipal(serverData.getAsString(Servers.SUPPLIED_PATH));
				if ( requestor.getStatusCode() < 300 ) authOK = true; 
				else if ( requestor.getStatusCode() == 401 ) authFailed = true;
			}
			if ( !discovered ) {
				String[] tryForPaths = {
							"/.well-known/caldav",
							"/",
//								"/principals/users/" + URLEncoder.encode(serverData.getAsString(Servers.USERNAME),Constants.URLEncoding),
						};
				for (int i = 0; !discovered && i < tryForPaths.length; i++) {
					discovered = doPropfindPrincipal(tryForPaths[i]);
					if ( requestor.getStatusCode() < 300 ) authOK = true; 
					else if ( requestor.getStatusCode() == 401 ) authFailed = true;
				}
			}

			if ( !discovered ) {
				successMessages.add(context.getString(R.string.couldNotDiscoverPrincipal));
				if ( authFailed && !authOK )
					successMessages.add(context.getString(R.string.authenticationFailed));
			}
			else {
				successMessages.add(String.format(context.getString(R.string.foundPrincipalPath), requestor.fullUrl()));
				requestor.applyToServerSettings(serverData);
			}

			if ( !has_calendar_access  && discovered ) {
				// Try an options request to see if we can get calendar-access
				doOptions(requestor.getPath());
			}

			// Step 5, Update serverData
			if ( has_calendar_access ) {
				serverData.put(Servers.HAS_CALDAV, 1);
				successMessages.add(context.getString(R.string.serverSupportsCalDAV));
			}
			else {
				serverData.put(Servers.HAS_CALDAV, 0);
				successMessages.add(context.getString(R.string.serverLacksCalDAV));
			}

			// Step 6, Exit with success message
			Message m = Message.obtain();
			Bundle b = new Bundle();
			b.putInt(TYPE, (has_calendar_access && discovered ? CHECK_COMPLETE : SHOW_FAIL_DIALOG));

			StringBuilder successMessage = new StringBuilder("");
			for( String msg : successMessages ) {
				successMessage.append("\n");
				successMessage.append(msg);
			}
			b.putString(MESSAGE, successMessage.toString());

			m.setData(b);
			handler.sendMessage(m);
			return;

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
			Log.w(TAG, "Connect failed: " + e.getMessage());
			Message m = Message.obtain();
			Bundle b = new Bundle();
			b.putInt(TYPE, SHOW_FAIL_DIALOG);
			b.putString(MESSAGE, "Unknown Error: " + e.getMessage());
			m.setData(b);
			handler.sendMessage(m);
		}
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

		// Before we display the success dialog and especially before we start syncing it. 
		sc.saveData(); 

		// Display the success dialog
		AlertDialog.Builder builder = new AlertDialog.Builder(sc);
		builder.setMessage(msg + "\n\n" + context.getString(R.string.serverValidationSuccess));
		
		// We don't set a positive button here since we already saved, above, and
		// the background actions may have already updated the server table further!
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


	private boolean probeServerPorts() {
		// TODO It would be nice to do an SRV lookup in here, like:
		//                 http://stackoverflow.com/questions/2695085
		// As a hack, we *could* do this through a web request to a page that
		// does an SRV lookup.
		//

		HashSet<Integer> workingPorts = new HashSet<Integer>();

		// First attempt the port / SSL state we have been given, if possible
		Integer p = serverData.getAsInteger(Servers.PORT);
		Integer protocol = serverData.getAsInteger(Servers.USE_SSL); 
		protocol = (protocol == null || protocol == 1 ? 1 : 0);

		// If the port wasn't given use standard ports as first probe
		if ( p == null || p < 1 || p > 65535 ) p = (protocol == 0 ? 80 : 443);
		if ( tryPort(p, protocol, 10000 ) ) {
			workingPorts.add(p);
			return true;
		}

		// Fall back to probing various other commonly used ports.  It would be
		// *really* nice to do these in parallel and collect a matrix of which
		// ones are open since CalDAV might not be the *first* open one of these :-(
		p = null;
		int[] portsToTrySSL = { 8443, 8843, 4443, 8043, 443 };
		int[] portsToTryHttp = { 8008, 8800, 8888, 7777, 80 }; 
		
		// Try initially with a very short timeout
		if ( p == null || p == 1 ) {
			for( int port : portsToTrySSL )
				if ( tryPort(port, 1, 2500 ) ) return true;
		}

		if ( p == null || p == 0 ) {
			for( int port : portsToTryHttp )
				if ( tryPort(port, 0, 1500 ) ) return true;
		}

		// Try with longer timeouts.
		if ( p == null || p == 1 ) {
			for( int port : portsToTrySSL )
				if ( tryPort(port, 1, 5000 ) ) return true;
		}

		if ( p == null || p == 0 ) {
			for( int port : portsToTryHttp )
				if ( tryPort(port, 0, 5000 ) ) return true;
		}

		return false;
	}

	
	/**
	 * <p>
	 * Nothing fancy here.  We just quickly try a bunch of ports, and if we manage a successful request
	 * then we set that as our port / protocol starting point for the next stage.
	 * </p> 
	 * @param port
	 * @param protocol
	 * @param timeOutMillis
	 * @return
	 */
	private boolean tryPort( int port, int protocol, int timeOutMillis ) {
		requestor.setTimeOuts(timeOutMillis,3000);
		requestor.setPortProtocol( port, protocol );
		Log.i(TAG, "Starting probe of "+requestor.fullUrl());
		try {
			requestor.doRequest("HEAD", null, null, null);

			// No exception, so it worked?
			if ( Constants.LOG_VERBOSE )
				Log.i(TAG, "Probe "+requestor.fullUrl()+" success: status " + requestor.getStatusCode());

			return true;
		}
		catch (Exception e) {
			Log.d(TAG, "Probe "+requestor.fullUrl()+" failed: " + e.getMessage());
		}
		return false;
	}


	
	private boolean doPropfindPrincipal( String requestPath ) {
		Log.i(TAG, "Doing PROPFIND for current-user-principal on " + requestor.fullUrl() );
		try {
			requestor.setTimeOuts(4000,15000);
			DavNode root = requestor.doXmlRequest("PROPFIND", requestPath, pPathHeaders, pPathRequestData);
			
			int status = requestor.getStatusCode();
			Log.d(TAG, "PROPFIND request " + status + " on " + requestor.fullUrl() );

			checkCalendarAccess(requestor.getResponseHeaders());

			if ( status == 207 ) {
				if ( Constants.LOG_DEBUG ) Log.d(TAG, "Checking for principal path in response...");
				for ( DavNode href : root.getNodesFromPath("multistatus/response/propstat/prop/current-user-principal/unauthenticated") ) {
					if ( Constants.LOG_DEBUG ) Log.d(TAG, "Found unauthenticated principal");
					requestor.setAuthRequired();
					if ( Constants.LOG_DEBUG ) Log.d(TAG, "We are unauthenticated, so try forcing authentication on");
					if ( requestor.getAuthType() == Servers.AUTH_NONE ) {
						requestor.setAuthType(Servers.AUTH_BASIC);
						if ( Constants.LOG_DEBUG ) Log.d(TAG, "Guessing Basic Authentication");
					}
					return doPropfindPrincipal(requestPath);
				}
				for ( DavNode href : root.getNodesFromPath("multistatus/response/propstat/prop/current-user-principal/href") ) {
					if ( Constants.LOG_DEBUG ) Log.d(TAG, "Found principal URL :-)");
					requestor.interpretUriString(href.getText());
					return true;
				}
			}
		}
		catch (Exception e) {
			Log.e(TAG, "PROPFIND Error: " + e.getMessage());
			Log.d(TAG, Log.getStackTraceString(e));
		}
		return false;
	}

	
	private boolean doOptions( String requestPath ) {
		requestor.setTimeOuts(4000,10000);
		try {
			Log.i(TAG, "Starting OPTIONS on "+requestor.fullUrl());
			requestor.doRequest("OPTIONS", requestPath, null, null);
			int status = requestor.getStatusCode();
			Log.d(TAG, "OPTIONS request " + status + " on " + requestor.fullUrl() );
			checkCalendarAccess(requestor.getResponseHeaders());
			if ( status == 200 ) return true;
		}
		catch (SendRequestFailedException e) {
			Log.d(TAG, "OPTIONS Error connecting to server: " + e.getMessage());
		}
		catch (Exception e) {
			Log.e(TAG,"OPTIONS Error: " + e.getMessage());
			Log.d(TAG,Log.getStackTraceString(e));
		}
		return false;
	}
	

	/**
	 * <p>
	 * Checks whether the calendar supports CalDAV by looking through the headers for a "DAV:" header which
	 * includes "calendar-access". Appends to the successMessage we will return to the user, as well as
	 * setting the has_calendar_access for later update to the DB.
	 * </p>
	 * 
	 * @param headers
	 * @return true if the calendar does support CalDAV.
	 */
	public boolean checkCalendarAccess(Header[] headers) {
		if ( headers != null ) {
			for (Header h : headers) {
				if (h.getName().equalsIgnoreCase("DAV")) {
					if (h.getValue().toLowerCase().contains("calendar-access")) {
						Log.i(TAG, "Discovered server supports CalDAV on URL "+requestor.fullUrl());
						has_calendar_access = true;
						return true;
					}
				}
			}
		}
		return false;
	}

	
}
