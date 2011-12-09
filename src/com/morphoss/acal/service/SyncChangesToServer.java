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

package com.morphoss.acal.service;

import com.morphoss.acal.database.resourcesmanager.RRSyncChangesToServer;
import com.morphoss.acal.database.resourcesmanager.ResourceManager;

public class SyncChangesToServer extends ServiceJob {

	public static final String	TAG					= "aCal SyncChangesToServer";
	private final RRSyncChangesToServer request = new RRSyncChangesToServer();
	
	
	public SyncChangesToServer() {
		this.TIME_TO_EXECUTE = System.currentTimeMillis();
		
	}

	@Override
	public void run(aCalService context) {
		request.setService(context);
		ResourceManager rm = ResourceManager.getInstance(context);
		//send request
		rm.sendRequest(request);
		//block until response completed
		while (request.running()) {
			Thread.yield();
			try { Thread.sleep(100); } catch (Exception e) {}
		}
		
		if ( request.getUpdateSyncStatus() ) {
			this.TIME_TO_EXECUTE = System.currentTimeMillis() + request.timeToWait();
			context.addWorkerJob(this);
		}

	}

	@Override
	public String getDescription() {
		return "Syncing local changes back to the server.";
	}

}
