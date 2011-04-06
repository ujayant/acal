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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.DatabaseChangedEvent;
import com.morphoss.acal.DatabaseEventDispatcher;
import com.morphoss.acal.R;

public class aCalService extends Service {

	
	private ServiceRequest.Stub serviceRequest = new ServiceRequestHandler();
	private WorkerClass worker;
	private static final int NOTIFICATION_ID = 0;
	private static Context serviceContext = null;
	public static final String TAG = "aCalService";
	public static String aCalVersion;
	public static final DatabaseEventDispatcher databaseDispatcher = new DatabaseEventDispatcher();
	
	public void onCreate() {
		super.onCreate();
		serviceContext = this;
		startService();
	}

	private void startService() {

		worker = WorkerClass.getInstance(this);
		
		Context context = getApplicationContext();

		if ( Constants.LOG_DEBUG ) {
			/**String ns = Context.NOTIFICATION_SERVICE;
			NotificationManager notificationManager = (NotificationManager) getSystemService(ns);
			
			int icon = R.drawable.icon;
			CharSequence tickerText = "aCal service started.";
			long when = System.currentTimeMillis();
			
			Notification notification = new Notification(icon,tickerText,when);
			
			CharSequence contentTitle = "aCal Calendar Service";
			CharSequence contentText = "The aCal Calendar service was started.";
			Intent notificationIntent = new Intent(this, MonthView.class);
			PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent,0);
			notification.setLatestEventInfo(context,contentTitle,contentText,contentIntent);
			notificationManager.notify(NOTIFICATION_ID, notification);*/
		}

		//start data service
		Intent serviceIntent = new Intent();
		serviceIntent.setAction("com.morphoss.acal.dataservice.CalendarDataService");
		this.startService(serviceIntent);
		
		
		aCalVersion = getString(R.string.appName) + "/";
		try {
			aCalVersion += getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
		}
		catch (NameNotFoundException e) {
			Log.e(TAG,"Can't find our good self in the PackageManager!");
			Log.e(TAG,Log.getStackTraceString(e));
		}

		//initial sync all active collections
		InitialCollectionSync.initialiseAll(worker, this);

		//Schedule sync of changes to server also.
		ServiceJob job = new SyncChangesToServer();
		addWorkerJob(job);
	}
	

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (Constants.LOG_DEBUG) Log.d(TAG, "On destroy called. Killing worker thread.");
		//Ensure database is closed properly and worker is terminated.
		worker.killWorker();
		worker = null;
		if (Constants.LOG_DEBUG) Log.d(TAG, "Worker killed.");
	}
	

	//@Override
	public IBinder onBind(Intent arg0) {
		return serviceRequest;
	}
	
	public void addWorkerJob(ServiceJob s) {
		this.worker.addJobAndWake(s);
	}

	public static String getContextString(int resourceId) {
		if ( serviceContext == null ) {
			return "Context Error";
		}
		return serviceContext.getString(resourceId);
	}

	private class ServiceRequestHandler extends ServiceRequest.Stub {

		@Override
		public void discoverHomeSets() throws RemoteException {
			ServiceJob job = new SynchronisationJobs(SynchronisationJobs.HOME_SET_DISCOVERY);
			job.TIME_TO_EXECUTE = System.currentTimeMillis();
			worker.addJobAndWake(job);
		}

		@Override
		public void updateCollectionsFromHomeSets() throws RemoteException {
			ServiceJob job = new SynchronisationJobs(SynchronisationJobs.HOME_SETS_UPDATE);
			job.TIME_TO_EXECUTE = System.currentTimeMillis();
			worker.addJobAndWake(job);
		}

		@Override
		public void initialCollectionSyncAll() throws RemoteException {
			InitialCollectionSync.initialiseAll(worker, aCalService.this);
		}

		@Override
		public void fullResync() throws RemoteException {
			databaseDispatcher.dispatchEvent(new DatabaseChangedEvent(DatabaseChangedEvent.DATABASE_INVALIDATED,null,null));
			ServiceJob[] jobs = new ServiceJob[2];
			jobs[0] = new SynchronisationJobs(SynchronisationJobs.HOME_SET_DISCOVERY);
			jobs[1] = new SynchronisationJobs(SynchronisationJobs.HOME_SETS_UPDATE);
			worker.addJobsAndWake(jobs);
			InitialCollectionSync.initialiseAll(worker, aCalService.this);
		}

		@Override
		public void revertDatabase() throws RemoteException {
			worker.addJobAndWake(new DebugDatabase(DebugDatabase.REVERT));
		}
		
		public void saveDatabase() throws RemoteException {
			worker.addJobAndWake(new DebugDatabase(DebugDatabase.SAVE));
		}

		@Override
		public void homeSetDiscovery(int server) throws RemoteException {
			HomeSetDiscovery job = new HomeSetDiscovery(server);
			worker.addJobAndWake(job);
		}

		@Override
		public void syncCollectionNow(int collectionId) throws RemoteException {
			SyncCollectionContents job = new SyncCollectionContents(collectionId, true);
			worker.addJobAndWake(job);
		}

		@Override
		public void fullCollectionResync(int collectionId) throws RemoteException {
			InitialCollectionSync job = new InitialCollectionSync(collectionId);
			worker.addJobAndWake(job);
		}
	}
	
	
}

