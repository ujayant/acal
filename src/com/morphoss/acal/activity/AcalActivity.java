package com.morphoss.acal.activity;

import com.morphoss.acal.StaticHelpers;

import android.app.Activity;
import android.os.Bundle;

public abstract class AcalActivity extends Activity {

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		StaticHelpers.setContext(this);
	}	
}
