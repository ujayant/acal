package com.morphoss.acal;

import android.graphics.Color;
import android.view.View;
import android.view.ViewParent;

public final class AcalTheme {

	private static int themeDefaultColour = (Constants.DEBUG_MODE ? 0xffff3020 /* red */ : 0xfff0a020 /* orange */ ); 
	private static int themeButtonColour = (Constants.DEBUG_MODE ? 0xffff3020 /* red */ : 0xfff0a020 /* orange */ ); 
	private static int themeBackgroundColour = 0xffffffff; 

	public static final int BUTTON = 1;
	public static final int BACKGROUND = 2;


	final public static View getContainerView(View someView) {
		ViewParent vp;
		do {
			vp = someView.getParent();
		} while ( !(vp instanceof View) );
		return (View) vp;
	}

	/**
	 * Get the colour used for these theme elements
	 * 
	 * @param themeElementID
	 */
	final public static int getElementColour(int themeElementID) {
		switch(themeElementID) {
			case BUTTON:		return themeButtonColour; 
			case BACKGROUND:	return themeBackgroundColour; 
		}
		return themeDefaultColour;
	}

	
	
	/**
	 * The way we are theming some things is to have a LinearLayout with a solid
	 * background assigned, containing (normally) a button with a 9-patch which is
	 * white/transparent overlay.  This utility helps us set the colour on the
	 * relevant object.
	 * 
	 * @param someView
	 * @param themeElementID
	 */
	public static void setContainerFromTheme(View someView, int themeElementID) {
		setContainerColour(someView, getElementColour(themeElementID));
	}

	
	/**
	 * The way we are theming some things is to have a LinearLayout with a solid
	 * background assigned, containing (normally) a button with a 9-patch which is
	 * white/transparent overlay.  This utility helps us set an explicit (unthemed)
	 * colour on the relevant object.
	 * 
	 * @param someView
	 * @param colour
	 */
	public static void setContainerColour(View someView, int colour) {
		getContainerView(someView).setBackgroundColor(colour);
	}

	
	/**
	 * Given the supplied background colour, tries to pick a foreground colour with good
	 * contrast which is not too jarring against it.
	 * 
	 * This initial implementation is simplistic and probably needs refinement.
	 * 
	 * @param backgroundColour
	 * @return
	 */
	public static int pickForegroundForBackground( int backgroundColour ) {
		int r = (backgroundColour & 0xff0000) >> 32;
		int g = (backgroundColour & 0xff00) >> 16;
		int b = (backgroundColour & 0xff);
		
		if ( ((r + g + b) / 3) < 150 ) return Color.WHITE;
		return Color.BLACK;
	}
	
}
