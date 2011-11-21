package com.morphoss.acal.activity;

import com.morphoss.acal.StaticHelpers;

import android.app.Activity;

public abstract class AcalActivity extends Activity {
	AcalActivity() {
		super();
		StaticHelpers.setContext(this);
	}
}
