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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;

import javax.net.ssl.SSLException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import android.os.Build;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.service.aCalService;

public class Connector {

	private String server;
	private int port;
	private String protocol;
	private String URL;
	private HttpAuthProvider auth;
	private Header responseHeaders[];
	private int statusCode;
	private int connectionTimeOut = 60000;
	public static final String TAG = "aCal Connector";

	public String userAgent = "aCal/0.3 (Wheels off the Bus)";
	
	public Header[] getResponseHeaders() {
		return this.responseHeaders;
	}
	
	public int getStatusCode() {
		return this.statusCode;
	}

	public String getURL() {
		return this.URL;
	}

	public Connector (String protocol, int port, String server, HttpAuthProvider auth) {
		this.protocol = protocol;
		this.port = port;
		this.server = server;
		this.auth = auth;
		this.URL = this.protocol+"://"+this.server+":"+this.port;
		try {
			this.userAgent = aCalService.aCalVersion;
		}
		catch ( Exception e ){
			if ( Constants.LOG_DEBUG ) Log.d(TAG, "Couldn't assign userAgent from aCalService.aCalVersion");
			if ( Constants.LOG_DEBUG ) Log.d(TAG,Log.getStackTraceString(e));
		}

// User-Agent: aCal/0.3 (google; Nexus One; passion; HTC; passion; FRG83D)  Android/2.2.1 (75603)
		this.userAgent += " (" + Build.BRAND + "; " + Build.MODEL + "; " + Build.PRODUCT + "; "
					+ Build.MANUFACTURER + "; " + Build.DEVICE + "; " + Build.DISPLAY + "; " + Build.BOARD + ") "
					+ " Android/" + Build.VERSION.RELEASE + " (" + Build.VERSION.INCREMENTAL + ")";

	}

	public void setServer(String protocol, int port, String server) {
		this.protocol = protocol;
		this.port = port;
		this.server = server;
		this.URL = this.protocol+"://"+this.server+":"+this.port;
	}

	public void setAuth(HttpAuthProvider auth) {
		this.auth = auth;
	}

	public InputStream sendRequest(String method, String path, Header[] headers, Object data) throws SendRequestFailedException, SSLException {
		long down = 0;
		long up = 0;
		long start = System.currentTimeMillis();
		try {

			// Set parameters, schema connection manager and create client and add auth
			HttpParams params = getParams();
			SchemeRegistry schemeRegistry = getSchemeRegistry();
			
			ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
			DefaultHttpClient httpclient = new DefaultHttpClient(cm, params);
			
			if (auth != null)
				this.auth.setAuth(httpclient);

			if ( Constants.LOG_DEBUG && Constants.debugDavCommunication )
				Log.d(TAG, method+" "+URL+path);

			// Create request and add headers and entity
			DavRequest request = new DavRequest(method, URL+path);
			request.addHeader(new BasicHeader("User-Agent", userAgent));
			for (Header h : headers) {
				request.addHeader(h);
				if ( Constants.LOG_DEBUG && Constants.debugDavCommunication ) {
					Log.d(TAG,"H>  "+h.getName()+":"+h.getValue() );
				}
			}
			if (data != null) {
				request.setEntity(new StringEntity(data.toString(),"UTF-8"));
				if ( Constants.LOG_DEBUG && Constants.debugDavCommunication ) {
					Log.d(TAG, "----------------------- vvv Request Body vvv -----------------------" );
					for( String line : data.toString().split("\n") ) {
						if ( line.length() == data.toString().length() ) {
							int end;
							int length = line.length();
							for( int pos=0; pos < length; pos += 120 ) {
								end = pos+120;
								if ( end > length ) end = length;
								Log.d(TAG, "R>  " + line.substring(pos, end) );
							}
						}
						else {
							Log.d(TAG, "R>  " + line.replaceAll("\r$", "") );
						}
					}
					Log.d(TAG, "----------------------- ^^^ Request Body ^^^ -----------------------" );
				}
			}

			up = request.getEntity().getContentLength();
			// Create host, send request and get response 

			try {
				InetAddress.getByName(this.server);
			}
			catch (UnknownHostException e1) {
//				if ( Constants.LOG_DEBUG )
					Log.d(TAG,"Caught & ignored UnknownHostException for " + this.server );
			}
			
			int requestPort = -1;
			String requestProtocol = this.protocol;
			if ( (this.protocol.equals("http") && this.port != 80 )
						|| ( this.protocol.equals("https") && this.port != 443 )
				) {
				requestPort = this.port;
			}
			HttpHost host = new HttpHost(this.server, requestPort, requestProtocol);
			
			HttpResponse response = null;
			response = httpclient.execute(host,request);
			HttpEntity entity = response.getEntity();
			
			down = (entity == null ? 0 : entity.getContentLength());
			
			this.responseHeaders = response.getAllHeaders();
			this.statusCode = response.getStatusLine().getStatusCode();
			long finish = System.currentTimeMillis();
			double timeTaken = ((double)(finish-start))/1000.0;

			if ( Constants.LOG_DEBUG && Constants.debugDavCommunication ) {
				Log.d(TAG, "Response: "+statusCode+", Sent: "+up+", Received: "+down+", Took: "+timeTaken+" seconds");
				for (Header h : responseHeaders) {
					Log.d(TAG,"H<  "+h.getName()+": "+h.getValue() );
				}
				if (entity != null) {
					if ( Constants.LOG_DEBUG && Constants.debugDavCommunication ) {
						Log.d(TAG, "----------------------- vvv Response Body vvv -----------------------" );
						BufferedReader r = new BufferedReader(new InputStreamReader(entity.getContent()));
						StringBuilder total = new StringBuilder();
						String line;
						while ((line = r.readLine()) != null) {
						    total.append(line);
						    total.append("\n");
							if ( line.length() > 180 ) {
								int end;
								int length = line.length();
								for( int pos=0; pos < length; pos += 120 ) {
									end = pos+120;
									if ( end > length ) end = length;
									Log.d(TAG, "R<  " + line.substring(pos, end) );
								}
							}
							else {
								Log.d(TAG, "R<  " + line.replaceAll("\r$", "") );
							}
						}
						Log.d(TAG, "----------------------- ^^^ Response Body ^^^ -----------------------" );
						return new ByteArrayInputStream( total.toString().getBytes() );
					}
				}
			}
			if (entity != null)
				return entity.getContent();

			return null;
		}
		catch (SSLException e) {
			if ( Constants.LOG_DEBUG && Constants.debugDavCommunication )
				Log.d(TAG,Log.getStackTraceString(e));
			throw e;
		}
		catch (Exception e) {
			Log.d(TAG,Log.getStackTraceString(e));
			throw new SendRequestFailedException();
		}

	}

	private HttpParams getParams() {
		HttpParams params = new BasicHttpParams();
		params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		params.setParameter(CoreProtocolPNames.HTTP_CONTENT_CHARSET, HTTP.UTF_8);
		params.setParameter(CoreProtocolPNames.USER_AGENT, userAgent );
		params.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, connectionTimeOut);
		params.setParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false);
		params.setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);
		
		return params;
	}

	
	public void setTimeOut( int  newTimeOut ) {
		connectionTimeOut = newTimeOut;
	}

	
	private SchemeRegistry getSchemeRegistry() {
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));

		Scheme httpsScheme = new Scheme("https",  new EasySSLSocketFactory(), 443);
		schemeRegistry.register(httpsScheme);

		return schemeRegistry;
	}


}
