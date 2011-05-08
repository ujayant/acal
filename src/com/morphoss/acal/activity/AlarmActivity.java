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

package com.morphoss.acal.activity;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.morphoss.acal.Constants;
import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.dataservice.CalendarDataService;
import com.morphoss.acal.dataservice.DataRequest;
import com.morphoss.acal.davacal.AcalAlarm;
import com.morphoss.acal.davacal.AcalEvent;

public class AlarmActivity extends Activity implements OnClickListener  {

	public static final String TAG = "aCal AlarmActivity";
	private AcalAlarm currentAlarm;
	private PowerManager.WakeLock wl;
	private boolean isBound = false;
	private DataRequest dataRequest;

	//GUI Components
	private TextView header;
	private TextView title;
	private TextView location;
	private TextView time;
	private ImageView mapButton;
	private ImageView snoozeButton;
	private ImageView dismissButton;
	private MediaPlayer mp;
	private AudioManager am;
	private Vibrator v;
	private static final int NOTIFICATION_ID = 1;
	private String ns;
	private NotificationManager mNotificationManager;
	private SharedPreferences prefs;	
	
	private static final int DIMISS = 0;
	private static final int SNOOZE = 1;
	private static final int MAP = 2;

	// These values are not defined until Android 2.0 or later, so we have
	// to define them ourselves.  They won't work unless you're on a 2.x or
	// later device either, of course...
//	private static final int WINDOW_FLAG_DISMISS_KEYGUARD = 0x00400000;
	private static final int WINDOW_FLAG_SHOW_WHEN_LOCKED = 0x00080000;
	private static final int WINDOW_FLAG_TURN_SCREEN_ON   = 0x00200000;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
		wl = pm.newWakeLock(
					PowerManager.FULL_WAKE_LOCK
					| PowerManager.ACQUIRE_CAUSES_WAKEUP
					| PowerManager.ON_AFTER_RELEASE
					, "aCal Alarm"
			);
		wl.acquire();	

		
		getWindow().addFlags( WINDOW_FLAG_SHOW_WHEN_LOCKED
//					| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
//					| WINDOW_FLAG_DISMISS_KEYGUARD
					| WINDOW_FLAG_TURN_SCREEN_ON
				);

		this.setContentView(R.layout.alarm_activity);
		
		
		connectToService();
		ns = Context.NOTIFICATION_SERVICE;
		am = (AudioManager)this.getSystemService(Context.AUDIO_SERVICE);
		v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		mNotificationManager = (NotificationManager) getSystemService(ns);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);


		//prepare gui elements
		header = (TextView) this.findViewById(R.id.AlarmTitle);
		title = (TextView) this.findViewById(R.id.AlarmContentTitleTextView);
		location = (TextView) this.findViewById(R.id.AlarmContentLocationTextView1);
		time = (TextView) this.findViewById(R.id.AlarmContentTimeTextView1);
		mapButton = (ImageView) this.findViewById(R.id.map_button);
		snoozeButton = (ImageView) this.findViewById(R.id.snooze_button);
		dismissButton = (ImageView) this.findViewById(R.id.dismiss_button);

	}

	@Override
	public void onNewIntent(Intent i) {
		super.onNewIntent(i);
	}
	
	private void serviceConnected() {
		isBound = true;
		setupButton(mapButton, MAP);
		setupButton(snoozeButton, SNOOZE);
		setupButton(dismissButton, DIMISS);
		showNextAlarm();
	}

	private void showNextAlarm() {
		if (Constants.LOG_DEBUG)Log.d(TAG, "Showing next alarm....");
		try {
			this.currentAlarm = dataRequest.getCurrentAlarm();
			if (this.currentAlarm == null) {
				if (Constants.LOG_DEBUG)Log.d(TAG,"Next alarm is null. Finishing");
				mNotificationManager.cancelAll();
				finish();
				return;
			}
			this.updateAlarmView();
		} catch (RemoteException e) {
			Log.e(TAG, " Error retrieving alarm data from dataRequest.");
			this.finish();
		}
	}

	private void updateAlarmView() {
		try {
			AcalDateTime now = new AcalDateTime();
			int minute = now.getMinute();
			String min = (minute < 10 ? "0"+minute : minute+"");
			header.setText((now.getHour())+":"+min);
			title.setText(currentAlarm.description);
			createNotification(currentAlarm.description);
			AcalEvent event = currentAlarm.getEvent();
			if (event == null)
				throw new IllegalStateException("Alarms passed to AlarmActivity MUST have an associated event");
			location.setText(event.getLocation());

			AcalDateTime viewDate = new AcalDateTime();
			viewDate.applyLocalTimeZone();
			viewDate.setDaySecond(0);
			time.setText(event.getTimeText(viewDate, AcalDateTime.addDays(viewDate,1),prefs.getBoolean(getString(R.string.prefTwelveTwentyfour), false)));
			
			playAlarm();
		}
		catch ( Exception e ) {
			// TODO This needs fixing!
			Log.e(TAG,"!!!ERROR UPDATING ALARM VIEW!!! - "+e.getMessage() );
			Log.e(TAG,Log.getStackTraceString(e));
		}
	}

	private void playAlarm() {
		if (mp != null && mp.isPlaying()) return;
		v.cancel();
		
		if ( am.getRingerMode() == AudioManager.RINGER_MODE_SILENT ) {
			long[] pattern = { 0,1000, 2000,1000, 2000,1000, 2000,1000, 2000,1000, 2000,1000, 2000,1000, 2000,1000, 2000,1000, 2000,1000, 2000};
			v.vibrate(pattern, -1);
		}
		else {
			String uri = prefs.getString(getString(R.string.DefaultAlarmTone_PrefKey), "null" );
			if (uri.equals("null")) {
				mp  = MediaPlayer.create(this, R.raw.assembly);
			} else {
				mp  = MediaPlayer.create(this, Uri.parse(uri));
			}
			if ( mp == null ) {
				long[] pattern = { 0,1000, 2000,1000, 2000,1000, 2000,1000, 2000,1000, 2000,1000, 2000,1000, 2000,1000, 2000,1000, 2000,1000, 2000};
				v.vibrate(pattern, -1);
			}
			else {
				mp.start();
			}
		}
	}

	
	private void createNotification(String text) {

		int icon = R.drawable.icon;
		long when = System.currentTimeMillis();

		Notification notification = new Notification(icon, text, when);
		
		CharSequence contentTitle = "aCal Alarm";
		CharSequence contentText = text;
		Intent notificationIntent = new Intent(this, AlarmActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

		notification.setLatestEventInfo(this, contentTitle, contentText, contentIntent);

		mNotificationManager.notify(NOTIFICATION_ID, notification);
	}

	
	private void connectToService() {
		try {
			if (this.isBound) return;
			Intent intent = new Intent(this, CalendarDataService.class);
			//			Bundle b  = new Bundle();
			//			b.putInt(CalendarDataService.BIND_KEY, CalendarDataService.BIND_ALARM_TRIGGER);
			//			intent.putExtras(b);
			this.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
			this.isBound = true;
		} catch (Exception e) {
			Log.e(TAG, "Error connecting to service: "+e.getMessage());
		}
	}

	private void setupButton(View v, int val) {
		v.setOnClickListener(this);
		v.setTag(val);
	}

	//We have been closed for some reason. Give up on any remaining alarms and notify cds of last triggered.
	//If there are any alarms left in our list, they will be re-triggered by cds immediately.
	@Override
	public void onPause() {
		super.onPause();
		if (mp != null && mp.isPlaying()) mp.stop();
		v.cancel();
		if (isBound)
			this.unbindService(mConnection);
		this.isBound = false;
		dataRequest = null;
		if (wl.isHeld())
			wl.release();
	}

	@Override
	public void onResume() {
		super.onResume();
		connectToService();
	}

	@Override
	public void onClick(View arg0) {
		if (arg0 == mapButton) {
			if (Constants.LOG_DEBUG) Log.d(TAG, "Starting Map");
			String loc = location.getText().toString();
			//replace whitespaces with '+'
			loc.replace("\\s", "+");
			Uri target = Uri.parse("geo:0,0?q="+loc);
			startActivity(new Intent(android.content.Intent.ACTION_VIEW, target)); 
			//start map view
			return;
		}
		if (arg0 == snoozeButton) {
			if (Constants.LOG_DEBUG)Log.d(TAG, "Snoozing Alarm");
			try {
				if ( dataRequest == null ) connectToService();
				this.dataRequest.snoozeAlarm(currentAlarm);
			} catch (Exception e) {
				if (Constants.LOG_DEBUG)Log.e(TAG, "ERROR: Can't snooze alarm: "+e);
			}
		}
		if (arg0 == dismissButton) {
			if (Constants.LOG_DEBUG)Log.d(TAG, "Dismissing alarm.");
			try {
				if ( dataRequest == null ) connectToService();
				this.dataRequest.dismissAlarm(currentAlarm);
			} catch (Exception e) {
				if (Constants.LOG_DEBUG)Log.e(TAG, "ERROR: Can't dismiss alarm: "+e);
			}

		}
		this.showNextAlarm();
	}

	/************************************************************************
	 * 					Service Connection management						*
	 ************************************************************************/


	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service.  We are communicating with our
			// service through an IDL interface, so get a client-side
			// representation of that from the raw service object.
			dataRequest = DataRequest.Stub.asInterface(service);
			serviceConnected();
		}
		public void onServiceDisconnected(ComponentName className) {
			dataRequest = null;
			isBound=false;
		}
	};
}
