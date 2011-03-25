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

import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

public class DigestAuth implements HttpRequestInterceptor, HttpAuthProvider {

	private String username;
	private String password;
	
	private Header challenge = null;
	
	public DigestAuth(String username, String password) {
		this.username = username;
		this.password = password;
	}

	public void saveChallengeHeader( Header challengeHeader ) {
		challenge = challengeHeader;
	}

	@Override
	public void setAuth(DefaultHttpClient client) {
		Credentials creds = new UsernamePasswordCredentials(this.username, this.password);
		client.getCredentialsProvider().setCredentials(new AuthScope(null, -1, AuthScope.ANY_REALM), creds);
		client.addRequestInterceptor(this,0);
	}

	@Override
	public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
		AuthState authState = (AuthState) context.getAttribute(ClientContext.TARGET_AUTH_STATE);
		CredentialsProvider credsProvider = (CredentialsProvider) context.getAttribute(ClientContext.CREDS_PROVIDER);
		HttpHost targetHost = (HttpHost) context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
		if (authState.getAuthScheme() == null) {
			AuthScope authScope = new AuthScope(targetHost.getHostName(), targetHost.getPort());
			Credentials creds = credsProvider.getCredentials(authScope);
			if (creds != null) {
				DigestScheme digest = new DigestScheme();
				authState.setAuthScheme(digest);
				authState.setCredentials(creds);
				if ( challenge != null ) {
					digest.processChallenge(challenge);
					// Once we've processed it, we're done with it, and we don't want it potentially
					// hanging around to screw with our minds next time!
					challenge = null;
				}
			}
		}
	}

}
