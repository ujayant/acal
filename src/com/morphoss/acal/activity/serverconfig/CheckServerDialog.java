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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;

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
import com.morphoss.acal.ServiceManager;
import com.morphoss.acal.providers.Servers;
import com.morphoss.acal.service.connector.BasicAuth;
import com.morphoss.acal.service.connector.Connector;
import com.morphoss.acal.service.connector.HttpAuthProvider;
import com.morphoss.acal.service.connector.SendRequestFailedException;
import com.morphoss.acal.xml.DavNode;
import com.morphoss.acal.xml.DavXmlTreeBuilder;


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
	private final ContentValues serverData;
	private ProgressDialog dialog;
	private ServerConfiguration sc;
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
		this.sc = serverConfiguration;
		

		//we must remove any values that may have leaked through from XML that are not part of the DB table
		ServerConfigData.removeNonDBFields(serverData);
		this.serverData = serverData;
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
			checkInternetConnected();

			String hostName = serverData.getAsString(Servers.HOSTNAME);
			if ( hostName == null || hostName.equals("") ) {
				hostName = serverData.getAsString(Servers.SUPPLIED_DOMAIN);
			}

			// Step 2, check we can connect to the server on the given port
			if ( probeServerPort() ) {
				successMessages.add("Found a server on port "+serverData.getAsString(Servers.PORT));
			}
			else {
				throw new CheckServerFailedError("Cound not find server port");
			}

			// Step 2, do OPTIONS with given credentials.
			boolean discovered = false;
			SearchUri searchUri = new SearchUri( hostName, serverData.getAsInteger(Servers.USE_SSL), serverData.getAsInteger(Servers.PORT) );
			SearchUri searchResult = null;
			try {
				if ( serverData.getAsString(Servers.PRINCIPAL_PATH) != null ) {
					searchUri.setPath(serverData.getAsString(Servers.PRINCIPAL_PATH));
					searchResult = doOptions(2, searchUri);
				}
				if ( searchResult == null ) {
					searchUri.setPath("/.well-known/caldav");
					searchResult = propfindPrincipalRequest(3, searchUri);
					if ( searchResult != null ) {
						searchResult.applySettings(serverData);
						discovered = true;
					}
				}
				if ( searchResult == null && serverData.getAsString(Servers.SUPPLIED_PATH) != null ) {
					searchUri.setPath(serverData.getAsString(Servers.SUPPLIED_PATH));
					searchResult = doOptions(2, searchUri);
				}
				if ( searchResult == null  ) {
					String[] optionsPaths = {
								"/",
//								"/principals/users/" + URLEncoder.encode(serverData.getAsString(Servers.USERNAME),Constants.URLEncoding),
								"/.well-known/caldav",
							};
					for (int i = 0; searchResult == null && i < optionsPaths.length; i++) {
						searchUri.setPath(optionsPaths[i]);
						searchResult = doOptions(2, searchUri);
					}
				}
			}
			catch( Exception e ) {
				Log.d(TAG,"Options request failed everywhere and exception thrown.");
				Log.d(TAG,Log.getStackTraceString(e));
			}

			if ( !discovered ) {
				// Try a PROPFIND from the supplied PRINCIPAL PATH
				if ( serverData.getAsString(Servers.PRINCIPAL_PATH) != null ) {
					searchUri.setPath(serverData.getAsString(Servers.PRINCIPAL_PATH));
					searchResult = propfindPrincipalRequest(3,searchUri);
					if ( searchResult != null ) {
						searchResult.applySettings(serverData);
						discovered = true;
					}
				}
			}

			if ( !discovered ) {
				// Try a PROPFIND from the supplied SIMPLE PATH
				if ( serverData.getAsString(Servers.SUPPLIED_PATH) != null ) {
					searchUri.setPath(serverData.getAsString(Servers.SUPPLIED_PATH));
					searchResult = propfindPrincipalRequest(3,searchUri);
					if ( searchResult != null ) {
						searchResult.applySettings(serverData);
						discovered = true;
					}
				}
			}

			if ( !discovered ) {
				successMessages.add("Could not discover principal path - using supplied values.");
			}
			else {
				successMessages.add("Server provided principal path: " + searchResult);
			}

			if ( !has_calendar_access  && discovered ) {
				// Try one last options request to see if we can get calendar-access
				doOptions(3,searchResult);
			}

			// Step 5, Update serverData
			if ( has_calendar_access ) {
				serverData.put(Servers.HAS_CALDAV, 1);
				successMessages.add("Server supports CalDAV.");
			}
			else {
				serverData.put(Servers.HAS_CALDAV, 0);
				successMessages.add("Server does not support CalDAV.");
			}

			// Step 6, Exit with success message
			Message m = Message.obtain();
			Bundle b = new Bundle();
			b.putInt(TYPE, CHECK_COMPLETE);

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
		builder.setMessage("There was an error validating the server:\n\n" + msg
					+ "\n\nWould you still like to save settings anyway?");
		builder.setPositiveButton("Yes", dialogClickListener);
		builder.setNegativeButton("No", dialogClickListener);
		builder.show();
	}

	private void showSuccessDialog(String msg) {

		// Before we display the success dialog and especially before we start syncing it. 
		sc.saveData(); 

		// Display the success dialog
		AlertDialog.Builder builder = new AlertDialog.Builder(sc);
		builder.setMessage("Server validation complete:\n" + msg);
		
		// We don't set a positive button here since we already saved, above, and
		// the background actions may have already updated the server table further!
		builder.setNeutralButton("O.K.", dialogClickListener);
		// builder.setNegativeButton("No", dialogClickListener);
		builder.show();
	}

	DialogInterface.OnClickListener	dialogClickListener	= new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					sc.saveData();
				case DialogInterface.BUTTON_NEUTRAL:
					sc.finish();
					break;
				case DialogInterface.BUTTON_NEGATIVE:
					// Do
					// nothing
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
	private void checkInternetConnected() throws CheckServerFailedError {
		try {
			ConnectivityManager connec = (ConnectivityManager) sc.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo netInfo = connec.getActiveNetworkInfo();
			if ( ! netInfo.isConnected() ) {
				// No connection available abort
				throw new CheckServerFailedError("Unable to connect with server - No internet connection.");
			}
		}
		catch (Exception e) {
			throw new CheckServerFailedError("Unable to connect with server - No internet connection.");
		}

	}


	/**
	 * <p>
	 * Nothing fancy here.  We just quickly try a bunch of ports, and if we manage a successful request
	 * then we set that as our port / protocol starting point for the next stage.  We don't even follow
	 * redirects at this stage...
	 * </p> 
	 * @param port
	 * @param protocol
	 * @param timeOutMillis
	 * @return
	 * @throws SSLException 
	 */
	private boolean tryPort( int port, String protocol, int timeOutMillis ) {
		try {
			String requestPath = serverData.getAsString(Servers.PRINCIPAL_PATH);
			if ( requestPath == null || requestPath.equals("") )
				requestPath = serverData.getAsString(Servers.SUPPLIED_PATH);
			if ( requestPath == null || requestPath.equals("") )
				requestPath = "/";

			String hostName = serverData.getAsString(Servers.HOSTNAME);
			if ( hostName == null || hostName.equals("") )
				hostName = serverData.getAsString(Servers.SUPPLIED_DOMAIN);

			if ( Constants.LOG_VERBOSE ) Log.v(TAG, "Starting probe of "+hostName+" with "+protocol+" on port "+port);

			Connector con = new Connector(protocol, port, hostName, null);
			con.setTimeOut(timeOutMillis);
			con.sendRequest("OPTIONS", requestPath, new Header[0], "");
	
			// No exception, so it worked?
			if ( Constants.LOG_VERBOSE ) Log.v(TAG, "Probe "+protocol+" on port "+port+" returned status " + con.getStatusCode());

			serverData.put(Servers.PORT, port);
			serverData.put(Servers.USE_SSL, (protocol.equals("http") ? 0 : 1) );

			return true;
		}
		catch ( Exception e) {
			if ( Constants.LOG_DEBUG ) Log.d(TAG, "Probe "+protocol+" on port "+port+" threw exception: " + e.getMessage());
//			if ( Constants.LOG_DEBUG ) Log.d(TAG, Log.getStackTraceString(e));
			// ... and ignore.
		}
		return false;
	}

	
	private String getLocationHeader(Header[] responseHeaders) {
		for( Header h : responseHeaders ) {
			if (Constants.LOG_DEBUG)
				Log.d(TAG, "Header: " + h.getName() + ":" + h.getValue());
			if (h.getName().equalsIgnoreCase("Location"))
				return h.getValue();
		}
		return "";
	}

	private boolean probeServerPort() throws CheckServerFailedError {
		// TODO Be nice to do an SRV lookup in here, like:
		//                 http://stackoverflow.com/questions/2695085
		// As a hack, we *could* do this through a web request to a page that
		// does an SRV lookup.
		//

		// First attempt the port / SSL state we have been given, if possible
		Integer p = serverData.getAsInteger(Servers.PORT);
		String protocol = serverData.getAsString(Servers.USE_SSL); 
		protocol = (protocol != null && protocol.equals("1") ? "https" : "http");

		// If the port wasn't given use standard ports as first probe
		if ( p == null || p < 1 || p > 65535 ) p = (protocol.equals("http") ? 80 : 443);
		if ( tryPort(p, protocol, 10000 ) ) return true;

		// Fall back to probing various other commonly used ports.  It would be
		// *really* nice to do these in parallel and collect a matrix of which
		// ones are open since CalDAV might not be the *first* open one of these :-(
		p = null;
		int[] portsToTrySSL = { 8443, 8843, 4443, 8043, 443 };
		int[] portsToTryHttp = { 8008, 8800, 8888, 7777, 80 }; 
		
		// Try initially with a very short timeout
		if ( p == null || p == 1 ) {
			for( int port : portsToTrySSL )
				if ( tryPort(port, "https", 2500 ) ) return true;
		}

		if ( p == null || p == 0 ) {
			for( int port : portsToTryHttp )
				if ( tryPort(port, "http", 1500 ) ) return true;
		}

		// Try with longer timeouts.
		if ( p == null || p == 1 ) {
			for( int port : portsToTrySSL )
				if ( tryPort(port, "https", 5000 ) ) return true;
		}

		if ( p == null || p == 0 ) {
			for( int port : portsToTryHttp )
				if ( tryPort(port, "http", 5000 ) ) return true;
		}

		return false;
	}

	
	private SearchUri propfindPrincipalRequest( int redirects, SearchUri uri ) {

		if ( Constants.LOG_DEBUG && Constants.debugDavCommunication )
			Log.d(TAG, "Doing PROPFIND for current-user-principal on " + uri.toString() );

		Connector con = new Connector(uri.protocol, uri.port, uri.hostName, authProvider());
		con.setTimeOut(10000);
		try {
			InputStream in = con.sendRequest("PROPFIND", uri.path, pPathHeaders, pPathRequestData);
			int status = con.getStatusCode();
			switch (status) {
				case 207: // Status O.K.
					checkCalendarAccess(con.getResponseHeaders());
					String newPath = processPropFindResponse(in);
					uri.interpretUriString(newPath);
					return uri;

				case 300: // Multiple choices, but we take the one in the Location header anyway
				case 301: // Moved permanently
				case 302: // Found (was 'temporary redirect' once in prehistory)
				case 303: // See other
				case 307: // Temporary redirect. Meh.
					// Other than 301/302 these are all pretty unlikely
					if (Constants.LOG_DEBUG)
						Log.d(TAG, "PROPFIND "+uri+" is being redirected.");
					uri.interpretUriString(getLocationHeader(con.getResponseHeaders()));
					if ( redirects > 0 ) {
						// Follow redirect
						return propfindPrincipalRequest( redirects - 1, uri );
					}
					return null;

				default: // Unknown code
					Log.w(TAG, "Status of "+status+" for PROPFIND "+uri+" - giving up.");
					return null;
			}
		}
		catch (SendRequestFailedException e) {
			return null;
		}
		catch (Exception e) {
			if (Constants.LOG_DEBUG)
				Log.d(TAG,"Error connecting to server: " + e.getMessage());
//			Log.e(TAG,Log.getStackTraceString(e));
		}

		return null;
	}

	
	SearchUri doOptions( int redirects, SearchUri uri ) throws CheckServerFailedError {
		try {
			if ( Constants.LOG_DEBUG && Constants.debugDavCommunication )
				Log.d(TAG, "Starting OPTIONS on "+uri.toString());
			
			Connector con = new Connector(uri.protocol, uri.port, uri.hostName, authProvider());
			try {
				con.sendRequest("OPTIONS", uri.path, new Header[] {}, "");
			}
			catch (SendRequestFailedException e) {
				Log.e(TAG,"Error connecting to server: " + e.getMessage());
				Log.e(TAG,Log.getStackTraceString(e));
				return null;
			}
			
			int status = con.getStatusCode();
			switch (status) {
				case 200: // Status O.K.
					if (Constants.LOG_VERBOSE)	Log.v(TAG, "OPTIONS request 200 OK on " + uri.path);
					if ( checkCalendarAccess(con.getResponseHeaders()) ) {
						successMessages.add("Server Connection Successful.");
						return uri;
					}
					return null;

				case 300: // Multiple choices, but we take the one in the Location header anyway
				case 301: // Moved permanently
				case 302: // Found (was 'temporary redirect' once in prehistory)
				case 303: // See other
				case 307: // Temporary redirect. Meh.
					// Other than 301/302 these are all pretty unlikely
					if (Constants.LOG_DEBUG)
						Log.d(TAG, "OPTIONS "+(serverData.getAsInteger(Servers.USE_SSL)==1?"https":"http")
									+" request " + status + " being redirected from " + uri.path);
					uri.interpretUriString(getLocationHeader(con.getResponseHeaders()));
					if ( redirects > 0 ) {
						// Follow redirect
						return doOptions( redirects - 1, uri );
					}
					return null;

				case 403: // Forbidden
				case 404: // Not Found
				case 405: // Method not allowed
					if (Constants.LOG_VERBOSE)	Log.v(TAG, "OPTIONS request " + status + " on " + uri.path);
					// We check for calendar-access anyway, though we still try other URLs
					checkCalendarAccess(con.getResponseHeaders());
					return null;

				case 401: // Unauthorised
					throw new CheckServerFailedError("Authentication Failed");

				default: // Unknown code
					Log.w(TAG, "Unknown Status code returned from server: " + status);
					throw new CheckServerFailedError("Server returned an unrecognized response.");
			}
			
		}
		catch ( CheckServerFailedError e ) {
			return null;
		}
		catch (Exception e) {
			if (Constants.LOG_DEBUG)
				Log.d(TAG,"Error connecting to server: " + e.getMessage());
//			Log.e(TAG,Log.getStackTraceString(e));
		}
		return null;
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
		for (Header h : headers) {
			if (h.getName().equalsIgnoreCase("DAV")) {
				if (h.getValue().toLowerCase().contains("calendar-access")) {
					has_calendar_access = true;
					return true;
				}
			}
		}
		return false;
	}

	
	private String processPropFindResponse(InputStream in) {
		DavNode root = DavXmlTreeBuilder.buildTreeFromXml(in);
		for ( DavNode href : root.getNodesFromPath("multistatus/response/propstat/prop/current-user-principal/href") )
			return href.getText();
		return null;
	}


	private HttpAuthProvider authProvider() {
		HttpAuthProvider auth = null;
	
		int authType = serverData.getAsInteger(Servers.AUTH_TYPE);
		switch (authType) {
			case 1:
				auth = new BasicAuth(serverData.getAsString(Servers.USERNAME),
									 serverData.getAsString(Servers.PASSWORD));
				break;
			default:
				break;
		}
		return auth;
	}

	
	private String findCalendarHome() {
		try {
			String protocol = "http";
			if (serverData.getAsInteger(Servers.USE_SSL) == 1) protocol = "https";

			String hostName = serverData.getAsString(Servers.HOSTNAME);

			Connector con = new Connector(protocol, serverData.getAsInteger(Servers.PORT), hostName, authProvider());

			String requestPath = serverData.getAsString(Servers.PRINCIPAL_PATH);

			
			InputStream in = con.sendRequest("PROPFIND", requestPath, pPathHeaders, pPathRequestData);
			int status = con.getStatusCode();
			switch (status) {
				case 207: // Status O.K.
					checkCalendarAccess(con.getResponseHeaders());
					return processPropFindResponse(in);
				default: // Unknown code
					Log.w(TAG, "Unexpected status of "+status+" returned from server for PROPFIND request on "+Servers.SUPPLIED_PATH);
					return null;
			}
		}
		catch (Exception e) {
			return null;
		}
	}

	
	private class SearchUri {
		public String hostName = null;
		public String path = "/";
		public String protocol = "http";
		public int port = 0;
		
		public SearchUri( String hostIn, Integer proto, Integer portIn) {
			hostName = hostIn;
			protocol = (proto == null || proto != 1 ? "http" : "https");
			if ( portIn == null || portIn < 1 || portIn > 65535 || portIn == 80 || portIn == 443 )
				port = (protocol.equals("http") ? 80 : 443);
			else
				port = portIn;
		}

		public void applySettings(ContentValues cvServerData) {
			cvServerData.put(Servers.PRINCIPAL_PATH, path);
			cvServerData.put(Servers.HOSTNAME, hostName);
			cvServerData.put(Servers.PORT, port);
			cvServerData.put(Servers.USE_SSL, (protocol.equals("https")?1:0));
		}

		public void interpretUriString(String uriString) {
			// Match a URL, including an ipv6 address like http://[DEAD:BEEF:CAFE:F00D::]:8008/
			final Pattern uriMatcher = Pattern.compile(
						"^(?:(https?)://)?" + // Protocol
						"(" + // host spec
						"(?:(?:[a-z0-9-]+[.]){2,7}(?:[a-z0-9-]+))" +  // Hostname or IPv4 address
						"|(?:\\[(?:[0-9a-f]{0,4}:)+(?:[0-9a-f]{0,4})?\\])" + // IPv6 address
						")" +
						"(?:[:]([0-9]{2,5}))?" + // Port number
						"(/.*)?$" // Path bit.
						,Pattern.CASE_INSENSITIVE | Pattern.DOTALL );  

			final Pattern pathMatcher = Pattern.compile("^(/.*)$");
			
			Matcher m = uriMatcher.matcher(uriString);
			if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Interpreting '"+uriString+"'");
			if ( m.matches() ) {
				if ( m.group(1) != null && !m.group(1).equals("") ) {
					if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Found protocol '"+m.group(1)+"'");
					protocol = m.group(1);
					if ( m.group(3) == null || m.group(3).equals("") ) {
						port = (protocol.equals("http") ? 80 : 443);
					}
				}
				if ( m.group(2) != null ) {
					if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Found hostname '"+m.group(2)+"'");
					hostName = m.group(2);
				}
				if ( m.group(3) != null && !m.group(3).equals("") ) {
					if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Found port '"+m.group(3)+"'");
					port = Integer.parseInt(m.group(3));
					if ( m.group(1) != null && (port == 0 || port == 80 || port == 443) ) {
						port = (protocol.equals("http") ? 80 : 443);
					}
				}
				if ( m.group(4) != null && !m.group(4).equals("") ) {
					if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Found redirect path '"+m.group(4)+"'");
					setPath(m.group(4));
				}
			}
			else {
				m = pathMatcher.matcher(uriString);
				if (m.find()) {
					if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Found simple redirect path '"+m.group(1)+"'");
					setPath( m.group(1) );
				}
			}
		}

		public void setPath(String newPath) {
			if ( newPath == null || newPath.equals("") ) {
				path = "/";
				return;
			}
			if ( !newPath.substring(0, 1).equals("/") ) {
				path = "/" + newPath;
			}
			else
				path = newPath;
		}

		public String toString() {
			return protocol
					+ "://"
					+ hostName
					+ ((protocol.equals("http") && port == 80) || (protocol.equals("https") && port == 443) ? "" : ":"+Integer.toString(port))
					+ path;
		}
	}

	
}
