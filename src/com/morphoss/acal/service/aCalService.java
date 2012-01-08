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

import java.util.ArrayList;
import java.util.PriorityQueue;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.DatabaseChangedEvent;
import com.morphoss.acal.DatabaseEventDispatcher;
import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.activity.AlarmActivity;
import com.morphoss.acal.database.cachemanager.CacheManager;
import com.morphoss.acal.database.resourcesmanager.ResourceManager;
import com.morphoss.acal.davacal.AcalAlarm;

public class aCalService extends Service {

	
	private ServiceRequest.Stub serviceRequest = new ServiceRequestHandler();
	private WorkerClass worker;
	public static final String TAG = "aCalService";
	public static String aCalVersion = "aCal/1.0"; // Updated at start of program.
	public static final DatabaseEventDispatcher databaseDispatcher = new DatabaseEventDispatcher();
	
	private final static long serviceStartedAt = System.currentTimeMillis();
	private ResourceManager rm;
	private CacheManager cm;

	// Alarms stuff in here temporarily but should be moved.
	private static final AcalDateTime MIN_ALARM_AGE = AcalDateTime.addDuration(new AcalDateTime(), new AcalDuration("-PT4H"));	// now -4 hours
	private PriorityQueue<AcalAlarm> alarmQueue = new PriorityQueue<AcalAlarm>();
	private PriorityQueue<AcalAlarm> snoozeQueue = new PriorityQueue<AcalAlarm>();
	private AcalDateTime lastTriggeredAlarmTime = MIN_ALARM_AGE;	//default start
	private PendingIntent alarmIntent = null;
	private AlarmManager alarmManager;
	private long nextTriggerTime = Long.MAX_VALUE;
	
	//TODO remove this line
	public static Context context;
	
	public void onCreate() {
		super.onCreate();
		aCalService.context = this;

		aCalVersion = getString(R.string.appName) + "/";
		try {
			aCalVersion += getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName;
		}
		catch (NameNotFoundException e) {
			Log.e(TAG,"Can't find our good self in the PackageManager!");
			Log.e(TAG,Log.getStackTraceString(e));
		}

		Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
		startService();
	}

	
	private synchronized void startService() {

		rm = ResourceManager.getInstance(this);
		cm = CacheManager.getInstance(this);
		
		worker = WorkerClass.getInstance(this);
				
		// Schedule immediate sync of any changes to the server
		worker.addJobAndWake(new SyncChangesToServer());

		// Start sync running for all active collections
		SynchronisationJobs.startCollectionSync(worker, this);

	}

	
	// This is the old onStart method that will be called on the pre-2.0
	// platform.  On 2.0 or later we override onStartCommand() so this
	// method will not be called.
	@Override
	public void onStart(Intent intent, int startId) {
		handleCommand(intent);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		handleCommand(intent);
		// We want this service to continue running until it is explicitly
		// stopped, so return sticky.
		return Service.START_STICKY;
	}

	// The actual start command, regardless of whether we're running under
	// 1.x or 2.x
	private void handleCommand( Intent inRequest ) {
		if ( inRequest == null ) return;
		if ( inRequest.hasExtra("UISTARTED") ) {
			// The UI is currently starting, so we might schedule some stuff
			// to happen soon.
			long uiStarted = inRequest.getLongExtra("UISTARTED", System.currentTimeMillis());
			if ( serviceStartedAt > uiStarted ) return; // Not if everything just started!

			// Tell the dataService to rebuild it's caches, just to be sure.
			if ( Constants.LOG_DEBUG )
				Log.i(TAG,"UI Started, requesting internal cache revalidation.");

			ServiceJob job = new SynchronisationJobs(SynchronisationJobs.CACHE_RESYNC);
			job.TIME_TO_EXECUTE = 15000L;
			worker.addJobAndWake(job);
			
		}
	}

	

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (Constants.LOG_DEBUG) Log.println(Constants.LOGD,TAG, "On destroy called. Killing worker thread.");
		//Ensure database is closed properly and worker is terminated.
		if ( worker != null ) worker.killWorker();
		worker = null;
		rm.close();
		cm.close();
		cm = null;
		rm = null;
		if (Constants.LOG_DEBUG) Log.println(Constants.LOGD,TAG, "Worker killed.");
	}
	


	private synchronized void scheduleServiceRestart() {
		long restartTime = System.currentTimeMillis() + 60000;
		 
		Intent serviceIntent = new Intent(this, aCalService.class);
		serviceIntent.putExtra("RESTARTED", System.currentTimeMillis());

		PendingIntent ourFutureSelf = PendingIntent.getService(getApplicationContext(), 0, serviceIntent, 0);
		AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		alarmManager.set(AlarmManager.RTC_WAKEUP, restartTime, ourFutureSelf);
		Log.e(TAG, "Scheduling aCalService restart in 60 seconds.");
		this.stopSelf();
	}

	
	//@Override
	public IBinder onBind(Intent arg0) {
		return serviceRequest;
	}
	
	public void addWorkerJob(ServiceJob s) {
		Runtime r = Runtime.getRuntime();
		if ( ((r.totalMemory() * 100) / r.maxMemory()) > 115 ) {
			scheduleServiceRestart();
		}
		else {
			if ( worker == null ) startService();
			this.worker.addJobAndWake(s);
		}
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
		public void fullResync() throws RemoteException {
			databaseDispatcher.dispatchEvent(new DatabaseChangedEvent(DatabaseChangedEvent.DATABASE_INVALIDATED,null,null));
			ServiceJob[] jobs = new ServiceJob[2];
			jobs[0] = new SynchronisationJobs(SynchronisationJobs.HOME_SET_DISCOVERY);
			jobs[1] = new SynchronisationJobs(SynchronisationJobs.HOME_SETS_UPDATE);
			worker.addJobsAndWake(jobs);
			SynchronisationJobs.startCollectionSync(worker, aCalService.this);
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
	
	/** Calculates the next time an alarm will go off AFTER the lastTriggeredAlarmtime 
	 * If no alarm is found alarms are disabled. Otherwise, set the alarm trigger for this time
	 */
	private void updateAlarms( ArrayList<AcalAlarm> updatedAlarms) {
		if (Constants.LOG_DEBUG)Log.d(TAG, "Rebuilding alarm queue. Last triggered Time: "+lastTriggeredAlarmTime);

		//Avoid concurrent modification of active queue
		synchronized(alarmQueue) {
			PriorityQueue<AcalAlarm> newQueue = new PriorityQueue<AcalAlarm>();

			//move all alarms that have the same time as lastTriggeredAlarmTime to this queue
			while (!alarmQueue.isEmpty() && alarmQueue.peek().getNextTimeToFire().equals(lastTriggeredAlarmTime)) newQueue.offer(alarmQueue.poll());
			if (Constants.LOG_DEBUG)Log.d(TAG,"Transferred "+newQueue.size()+" alarms from original queue that had timetofire=lasttrggeredtime");

			//add All alarms for triggeredTime+1 to now+7D
			AcalDateTime rangeStart = lastTriggeredAlarmTime.clone();
			rangeStart.applyLocalTimeZone();  // Ensure we have the correct timezone applied.
			rangeStart.addSeconds(1);
			AcalDateTime currentTime = new AcalDateTime();
			currentTime.applyLocalTimeZone();  // Ensure we have the correct timezone applied.
			if ( rangeStart.after(currentTime) ) rangeStart = currentTime;
			currentTime.addDays(7);
			AcalDateRange alarmRange = new AcalDateRange( rangeStart, currentTime );	//Only look forward 1 day

			ArrayList<AcalAlarm> alarms = updatedAlarms;
			if (Constants.LOG_DEBUG) Log.d(TAG, "Found "+alarms.size()+" new alarms for range"+alarmRange);
			for (AcalAlarm alarm : alarms) {  newQueue.offer(alarm); }
			alarmQueue = newQueue;

		}	//end synchronisation block
		if (Constants.LOG_DEBUG)Log.d(TAG,"Setting next alarm trigger");
		setNextAlarmTrigger();
	}

	private void setNextAlarmTrigger() {
		if ( Constants.LOG_DEBUG ) Log.d(TAG, "Set next alarm trigger called");
		synchronized( alarmQueue ) {
			AcalAlarm nextReg = alarmQueue.peek();
			AcalAlarm nextSnooze = snoozeQueue.peek();
			if ( nextReg == null && nextSnooze == null ) {
				if ( Constants.LOG_DEBUG ) Log.d(TAG, "No alarms in alarm queues. Not setting any alarm trigger.");
				// Stop existing alarm trigger
				if ( alarmIntent != null ) alarmManager.cancel(alarmIntent);
				alarmIntent = null;
				return;
			}
			if ( nextReg == null ) {
				if ( Constants.LOG_DEBUG ) Log.d(TAG, "No regular alarms, just snoozes.");
				createAlarmIntent(nextSnooze);
				return;
			}
			else if ( nextSnooze == null ) {
				if ( Constants.LOG_DEBUG ) Log.d(TAG, "No snooze alarms, just regulars.");
				createAlarmIntent(nextReg);
				return;
			}
			else {
				if ( Constants.LOG_DEBUG ) Log.d(TAG, "Both regular and Snooze alarms queued.");
				if ( nextReg.getNextTimeToFire().before(nextSnooze.getNextTimeToFire()) ) createAlarmIntent(nextReg);
				else
					createAlarmIntent(nextSnooze);
				return;
			}
		}
	}

	// Changes the next alarm trigger time IFF its changed.
	private synchronized void createAlarmIntent(AcalAlarm alarm) {
		if ( Constants.LOG_DEBUG ) Log.d(TAG, "Creating Alarm Intent for alarm: " + alarm);
		AcalDateTime now = new AcalDateTime();
		now.applyLocalTimeZone();
		long timeOfNextAlarm = (now.getDurationTo(alarm.getNextTimeToFire())).getTimeMillis()
		+ now.getMillis();
		if ( this.alarmIntent != null ) {
			if ( timeOfNextAlarm == nextTriggerTime ) {
				if ( Constants.LOG_DEBUG ) Log.d(TAG, "Alarm trigger time hasn't changed. Aborting.");
				return; // no change
			}
			else {
				alarmManager.cancel(alarmIntent);
			}
		}
		nextTriggerTime = timeOfNextAlarm;
		Intent intent = new Intent(this, AlarmActivity.class);
		alarmIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT);
		alarmManager.set(AlarmManager.RTC_WAKEUP, timeOfNextAlarm, alarmIntent);
		//if ( Constants.LOG_DEBUG )
		Log.d(TAG,
				"Set alarm trigger for: " + timeOfNextAlarm + "/" + alarm.getNextTimeToFire());
	}

	
}

