package com.morphoss.acal.activity.serverconfig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.providers.Servers;
import com.morphoss.acal.service.connector.AcalRequestor;
import com.morphoss.acal.service.connector.SendRequestFailedException;
import com.morphoss.acal.xml.DavNode;

public class TestPort {
	private static final String TAG = "aCal TestPort";
	private static final boolean DEBUG = true;
	
	private static final String pPathRequestData = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"+
			"<propfind xmlns=\"DAV:\">"+
				"<prop>"+
					"<principal-collection-set/>"+
					"<current-user-principal/>"+
					"<resourcetype/>"+
				"</prop>"+
			"</propfind>";

	private static final Header[] pPathHeaders = new Header[] {
		new BasicHeader("Depth","0"),
		new BasicHeader("Content-Type","text/xml; charset=UTF-8")
	};

	private final AcalRequestor requestor;
	int port;
	boolean useSSL;
	private String hostName;
	private String path;
	int connectTimeOut;
	int socketTimeOut;
	private Boolean isOpen;
	private Boolean authOK;
	private Boolean hasDAV;
	private Boolean hasCalDAV;

	/**
	 * Construct based on values from the AcalRequestor
	 * @param requestorIn
	 */
	public TestPort(AcalRequestor requestorIn) {
		this.requestor = requestorIn;
		this.path = requestor.getPath();
		this.hostName = requestor.getHostName();
		this.port = requestor.getPort();
		this.useSSL = requestor.getProtocol().equals("https");
		connectTimeOut = 200 + (useSSL ? 300 : 0);
		socketTimeOut = 3000;
		isOpen = null;
		authOK = null;
		hasDAV = null;
		hasCalDAV = null;
	}


	/**
	 * Construct based on values from the AcalRequestor, but overriding port/SSL
	 * @param requestorIn
	 * @param port
	 * @param useSSL
	 */
	TestPort(AcalRequestor requestorIn, int port, boolean useSSL) {
		this(requestorIn);
		this.port = port;
		this.useSSL = useSSL;
	}

	
	/**
	 * <p>
	 * Test whether the port is open.
	 * </p> 
	 * @return
	 */
	boolean isOpen() {
		if ( this.isOpen == null ) {
			requestor.setTimeOuts(connectTimeOut,socketTimeOut);
			requestor.setPath(path);
			requestor.setHostName(hostName);
			requestor.setPortProtocol( port, (useSSL?1:0) );
			if ( DEBUG ) Log.i(TAG, "Checking port open "+requestor.protocolHostPort());
			this.isOpen = false;
			try {
				requestor.doRequest("HEAD", null, null, null);
				if ( DEBUG ) Log.i(TAG, "Probe "+requestor.fullUrl()+" success: status " + requestor.getStatusCode());

				// No exception, so it worked!
				this.isOpen = true;
				if ( requestor.getStatusCode() == 401 ) this.authOK = false;
				checkCalendarAccess(requestor.getResponseHeaders());

				this.socketTimeOut = 15000;
				this.connectTimeOut = 15000;
				requestor.setTimeOuts(connectTimeOut,socketTimeOut);
			}
			catch (Exception e) {
				Log.d(TAG, "Probe "+requestor.fullUrl()+" failed: " + e.getMessage());
			}
		}
		if ( DEBUG ) Log.i(TAG, "Port "+(isOpen ?"":"not")+" open on "+requestor.protocolHostPort() );
		return this.isOpen;
	}


	/**
	 * Increases the connection timeout and attempts another probe.
	 * @return
	 */
	boolean reProbe() {
		connectTimeOut += 1000;
		connectTimeOut *= 2;
		isOpen = null;
		return isOpen();
	}


	/**
	 * <p>
	 * Checks whether the calendar supports CalDAV by looking through the headers for a "DAV:" header which
	 * includes "calendar-access". Appends to the successMessage we will return to the user, as well as
	 * setting the hasCalendarAccess for later update to the DB.
	 * </p>
	 * 
	 * @param headers
	 * @return true if the calendar does support CalDAV.
	 */
	private boolean checkCalendarAccess(Header[] headers) {
		if ( headers != null ) {
			for (Header h : headers) {
				if (h.getName().equalsIgnoreCase("DAV")) {
					if (h.getValue().toLowerCase().contains("calendar-access")) {
						if ( DEBUG ) Log.i(TAG, "Discovered server supports CalDAV on URL "+requestor.fullUrl());
						hasCalDAV = true;
						hasDAV = true; // by implication
						return true;
					}
				}
			}
		}
		return false;
	}


	/**
	 * Does a PROPFIND request on the given path.
	 * @param requestPath
	 * @return
	 */
	private boolean doPropfindPrincipal( String requestPath ) {
		if ( requestPath != null ) requestor.setPath(requestPath);
		if ( DEBUG ) Log.i(TAG, "Doing PROPFIND for current-user-principal on " + requestor.fullUrl() );
		try {
			DavNode root = requestor.doXmlRequest("PROPFIND", null, pPathHeaders, pPathRequestData);
			
			int status = requestor.getStatusCode();
			Log.d(TAG, "PROPFIND request " + status + " on " + requestor.fullUrl() );

			checkCalendarAccess(requestor.getResponseHeaders());

			if ( status == 207 ) {
				if ( Constants.LOG_DEBUG ) Log.d(TAG, "Checking for principal path in response...");
				List<DavNode> unAuthenticated = root.getNodesFromPath("multistatus/response/propstat/prop/current-user-principal/unauthenticated");
				if ( ! unAuthenticated.isEmpty() ) {
					if ( Constants.LOG_DEBUG ) Log.d(TAG, "Found unauthenticated principal");
					requestor.setAuthRequired();
					if ( Constants.LOG_DEBUG ) Log.d(TAG, "We are unauthenticated, so try forcing authentication on");
					if ( requestor.getAuthType() == Servers.AUTH_NONE ) {
						requestor.setAuthType(Servers.AUTH_BASIC);
						if ( Constants.LOG_DEBUG ) Log.d(TAG, "Guessing Basic Authentication");
					}
					else if ( requestor.getAuthType() == Servers.AUTH_BASIC ) {
						requestor.setAuthType(Servers.AUTH_DIGEST);
						if ( Constants.LOG_DEBUG ) Log.d(TAG, "Guessing Digest Authentication");
					}
					return doPropfindPrincipal(requestPath);
				}
				
				String principalCollectionHref = null;
				for ( DavNode response : root.getNodesFromPath("multistatus/response") ) {
					String responseHref = response.getFirstNodeText("href"); 
					if ( Constants.LOG_DEBUG ) Log.println(Constants.LOGD, TAG, "Checking response for "+responseHref);
					for ( DavNode propStat : response.getNodesFromPath("propstat") ) {
						if ( Constants.LOG_DEBUG ) Log.println(Constants.LOGD, TAG, "Checking in propstat for "+responseHref);
						if ( propStat.getFirstNodeText("status").equalsIgnoreCase("HTTP/1.1 200 OK") ) {
							if ( Constants.LOG_DEBUG ) Log.println(Constants.LOGD, TAG, "Found propstat 200 OK response for "+responseHref);
							for ( DavNode prop : propStat.getNodesFromPath("prop/*") ) {
								String thisTag = prop.getTagName(); 
								if ( Constants.LOG_DEBUG ) Log.println(Constants.LOGD, TAG, "Examining tag "+thisTag);
								if ( thisTag.equals("resourcetype") && ! prop.getNodesFromPath("principal").isEmpty() ) {
									if ( Constants.LOG_DEBUG ) Log.println(Constants.LOGD, TAG, "This is a principal URL :-)");
									requestor.interpretUriString(responseHref);
									return true;
								}
								else if ( thisTag.equals("current-user-principal") ) {
									if ( Constants.LOG_DEBUG ) Log.println(Constants.LOGD, TAG, "Found the principal URL :-)");
									requestor.interpretUriString(prop.getFirstNodeText("href"));
									return true;
								}
								else if ( thisTag.equals("principal-collection-set") ) {
									principalCollectionHref = prop.getFirstNodeText("href");
								}
							}
						}
					}
				}
				
				if ( principalCollectionHref != null ) {
					
				}
			}
			if ( status < 300 ) authOK = true;
		}
		catch (Exception e) {
			Log.e(TAG, "PROPFIND Error: " + e.getMessage());
			Log.println(Constants.LOGD,TAG, Log.getStackTraceString(e));
		}
		return false;
	}

	
	/**
	 * Probes for whether the server has DAV support.  It seems odd to use the PROPFIND
	 * for this, rather than OPTIONS which was intended for the purpose, but every working
	 * DAV server will support PROPFIND on every URL which supports DAV, whereas OPTIONS
	 * may only be available on some specific URLs in weird cases.
	 */
	boolean hasDAV() {
		if ( DEBUG ) Log.i(TAG, "Starting DAV discovery on "+requestor.fullUrl());
		if ( !isOpen() ) return false;
		if ( hasDAV == null ) {
			hasDAV = false;
			if ( doPropfindPrincipal(this.path) ) 								hasDAV = true;
			else if ( !hasDAV && doPropfindPrincipal("/.well-known/caldav") )	hasDAV = true;
			else if ( !hasDAV && doPropfindPrincipal("/") )						hasDAV = true;
		}
		if ( DEBUG ) Log.i(TAG, "DAV "+(hasDAV?"":"not")+" found on "+requestor.fullUrl());
		return hasDAV;
	}

	
	/**
	 * Probes for CalDAV support on the server using previous path used for DAV.
	 */
	boolean hasCalDAV() {
		requestor.setTimeOuts(connectTimeOut,socketTimeOut);
		requestor.setPath(path);
		requestor.setHostName(hostName);
		requestor.setPortProtocol( port, (useSSL?1:0) );

		if ( DEBUG ) Log.i(TAG, "Starting CalDAV dependency discovery on "+requestor.fullUrl());
		if ( !isOpen() || !hasDAV() || !authOK() ) return false;
		if ( DEBUG ) Log.i(TAG, "All CalDAV dependencies are present.");
		if ( hasCalDAV == null ) {
			if ( DEBUG ) Log.i(TAG, "Still discovering actual CalDAV support.");
			hasCalDAV = false;
			try {
				if ( DEBUG ) Log.i(TAG, "Starting OPTIONS on "+requestor.fullUrl());
				requestor.doRequest("OPTIONS", path, null, null);
				int status = requestor.getStatusCode();
				Log.d(TAG, "OPTIONS request " + status + " on " + requestor.fullUrl() );
				checkCalendarAccess(requestor.getResponseHeaders());  // Updates 'hasCalDAV' if it finds it
			}
			catch (SendRequestFailedException e) {
				Log.d(TAG, "OPTIONS Error connecting to server: " + e.getMessage());
			}
			catch (Exception e) {
				Log.e(TAG,"OPTIONS Error: " + e.getMessage());
				Log.d(TAG,Log.getStackTraceString(e));
			}
		}
		if ( DEBUG ) Log.i(TAG, "CalDAV "+(hasCalDAV?"":"not")+" found on "+requestor.fullUrl());
		return hasCalDAV;
	}


	/**
	 * Return whether the auth was OK.  If nothing's managed to tell us it failed
	 * then we give it the benefit of the doubt.
	 * @return
	 */
	public boolean authOK() {
		if ( DEBUG ) Log.i(TAG, "Checking authOK which was: "+(authOK == null ? "uncertain, assumed OK" : (authOK ? "OK" : "bad")));
		return (authOK == null || authOK ? true : false);
	}
	
	/**
	 * Returns a default ArrayList<TestPort> which can be used for probing a server to try
	 * and discover where the CalDAV / CardDAV server is hiding.  
	 * @param requestor The requestor which will be used for probing.
	 * @return The ArrayList of default ports.
	 */
	private static ArrayList<TestPort> testPortSet = null;
	public static Iterator<TestPort> defaultIterator(AcalRequestor requestor) {
		if ( testPortSet == null )
			testPortSet = new ArrayList<TestPort>(10);
		else
			testPortSet.clear();

		testPortSet.add( new TestPort(requestor,443,true) );
		testPortSet.add( new TestPort(requestor,8443,true) );
		testPortSet.add( new TestPort(requestor,80,false) );
		testPortSet.add( new TestPort(requestor,8008,false) );
		testPortSet.add( new TestPort(requestor,8843,true) );
		testPortSet.add( new TestPort(requestor,4443,true) );
		testPortSet.add( new TestPort(requestor,8043,true) );
		testPortSet.add( new TestPort(requestor,8800,false) );
		testPortSet.add( new TestPort(requestor,8888,false) );
		testPortSet.add( new TestPort(requestor,7777,false) );

		return testPortSet.iterator();
	}


	/**
	 * Return a URL Prefix like 'https://'
	 * @return
	 */
	public String getProtocolUrlPrefix() {
		return "http" + (useSSL?"s":"") + "://";
	}
	
}
