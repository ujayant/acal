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

package com.morphoss.acal.service.connector;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.auth.AuthScope;

import android.content.ContentValues;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.activity.serverconfig.AuthenticationFailure;
import com.morphoss.acal.providers.Servers;

public class ConnectUri {

	final private static String TAG = "aCal ConnectUri";

	// Basic URI components
	protected String hostName = null;
	protected String path = "/";
	protected String protocol = "http";
	protected int port = 0;

	// Authentication credentials
	protected boolean authRequired = false;
	protected int authType  = 0; 
	protected String authRealm = AuthScope.ANY_REALM;

	private String	username;
	private String	password;
	private HttpAuthProvider auth = null;

	public ConnectUri( String hostIn, Integer proto, Integer portIn ) {
		hostName = hostIn;
		protocol = (proto == null || proto != 1 ? "http" : "https");
		if ( portIn == null || portIn < 1 || portIn > 65535 || portIn == 80 || portIn == 443 )
			port = (protocol.equals("http") ? 80 : 443);
		else
			port = portIn;
	}

	public ConnectUri( String hostIn, Integer proto, Integer portIn, String pathIn, String user, String pass ) {
		hostName = hostIn;
		protocol = (proto == null || proto != 1 ? "http" : "https");
		if ( portIn == null || portIn < 1 || portIn > 65535 || portIn == 80 || portIn == 443 )
			port = (protocol.equals("http") ? 80 : 443);
		else
			port = portIn;

		setPath(pathIn);
		
		username = user;
		password = pass;

	}

	public void applySettings(ContentValues cvServerData) {
		cvServerData.put(Servers.HOSTNAME, hostName);
		cvServerData.put(Servers.USE_SSL, (protocol.equals("https")?1:0));
		cvServerData.put(Servers.PORT, port);
		cvServerData.put(Servers.PRINCIPAL_PATH, path);
		cvServerData.put(Servers.AUTH_TYPE, authType );
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


	/**
	 * When a request fails with a 401 Unauthorized you can call this with the content
	 * of the WWW-Authenticate header in the response and it will modify the URI so that
	 * if you repeat the request the correct authentication should be used.
	 * 
	 * If you then get a 401, and this gets called again on that same Uri, it will throw
	 * an AuthenticationFailure exception rather than continue futilely.
	 * 
	 * @param authRequestHeader
	 * @throws AuthenticationFailure
	 */
	public void interpretRequestedAuth( Header authRequestHeader ) throws AuthenticationFailure {
		// Adjust our authentication setup so the next request will be able
		// to send the correct authentication headers...

		final Pattern authHeaderMatcher = Pattern.compile("(Basic|Digest)\\s+realm\\s*=\"(.*?)\"(,\\s*)?(.*)?"
	    			  ,Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

		int newAuthType = 0;
		String newAuthRealm;

		Matcher m = authHeaderMatcher.matcher(authRequestHeader.getValue());
		if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Interpreting '"+authRequestHeader+"'");

		if ( !m.matches() || m.group(1) == null )
			throw new AuthenticationFailure("Unknown authentication type: " +authRequestHeader);

		newAuthRealm = m.group(2);
		if ( m.group(1).equals("Basic") ) {
			// The simple way...
			newAuthType = Servers.AUTH_BASIC;
			auth = new BasicAuth(username, password);
		}
		else if ( m.group(1).equals("Digest") ) {
			newAuthType = Servers.AUTH_DIGEST;
			getAuthObject();
			DigestAuth newAuth = new DigestAuth(username, password);
			newAuth.saveChallengeHeader(authRequestHeader);
			auth = newAuth;
		}
		else {
			// We can't actually get here unless that authHeaderMatch regex changes.
			throw new AuthenticationFailure("Unknown authentication type: " +authRequestHeader);
		}

		if ( authRequired && authType == newAuthType && authRealm == newAuthRealm ) {
			throw new AuthenticationFailure("This authentication type & realm are being reapplied!");
		}

		authRequired = true;
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

	
	public void setAuthType(int newType, String newRealm) {
		authType = newType;
		authRealm = newRealm;
	}

	
	public void setUserCredentials(String user, String pass) {
		username = user;
		password = pass;
	}

	
	public HttpAuthProvider getAuthObject() {
		if ( auth == null ) {
			switch (authType) {
				case Servers.AUTH_BASIC:
					auth = new BasicAuth(username, password);
					break;
				case Servers.AUTH_DIGEST:
					auth = new DigestAuth(username, password);
					break;
				default:
					break;
			}
		}
		return auth;
	}

	
	public String toString() {
		return protocol
				+ "://"
				+ hostName
				+ ((protocol.equals("http") && port == 80) || (protocol.equals("https") && port == 443) ? "" : ":"+Integer.toString(port))
				+ path;
	}
}
