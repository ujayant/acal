package com.morphoss.acal.activity;

import android.content.Context;
import android.graphics.Color;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.morphoss.acal.R;

public class CollectionConfigListItemPreference extends Preference {

	private int collectionColour = 0;
	
	public CollectionConfigListItemPreference(Context context) {
		super(context);
	}

	public CollectionConfigListItemPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public CollectionConfigListItemPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public void setCollectionColour(String newColour ) {
		try {
			collectionColour = Color.parseColor(newColour);
		} catch (IllegalArgumentException iae) { }
	}

	public View getView(View convertView, ViewGroup parent) {
		View v = super.getView(convertView, parent);
		View colourBar = v.findViewById(R.id.CollectionItemColorBar);
		colourBar.setBackgroundColor(collectionColour);
		return v;
	}
}
