package com.morphoss.acal.desktop;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.morphoss.acal.AcalTheme;
import com.morphoss.acal.R;

public class ShowUpcomingRowLayout extends LinearLayout {
	
	public static final String BGCOLORMETHOD = "setBGColour";
	public static final int WIDTH = 298;
	public static final int HEIGHT = 23;
	
	public ShowUpcomingRowLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public ShowUpcomingRowLayout(Context context) {
		super(context);
	}
	
	public Bitmap setData(ContentValues cv, String date, String time) {
		int colour = cv.getAsInteger(ShowUpcomingWidgetProvider.FIELD_COLOUR);
		float dip = this.getResources().getDisplayMetrics().density;
		TextView dateView =(TextView)this.findViewById(R.id.upcoming_date); 
		TextView timeView =(TextView)this.findViewById(R.id.upcoming_time);
		TextView sumView =(TextView)this.findViewById(R.id.upcoming_summary);
		
		
		dateView.setText(date); dateView.setTextColor(AcalTheme.pickForegroundForBackground(colour&0x33FFFFFF));
		timeView.setText(time); timeView.setTextColor(AcalTheme.pickForegroundForBackground(colour&0x33FFFFFF));
		sumView.setText(cv.getAsString(ShowUpcomingWidgetProvider.FIELD_SUMMARY));
			sumView.setTextColor(AcalTheme.pickForegroundForBackground(colour&0x33FFFFFF));
		
		
		GradientDrawable shape = new BackgroundShape(new int[] {(0x33FFFFFF&colour), (0x88000000|colour)}, (int)(HEIGHT/2*dip));
		this.setBackgroundDrawable(shape);
		
		
		
		
		this.measure((int)(WIDTH*dip),(int)(HEIGHT*dip));
		this.layout(0, 0, (int)(WIDTH*dip),(int)(HEIGHT*dip));
		this.setDrawingCacheEnabled(true);
		
		//Bitmap returnedBitmap = Bitmap.createBitmap(this.getWidth(), this.getHeight(),Bitmap.Config.ARGB_4444);
		//Canvas tempCanvas = new Canvas(returnedBitmap);
		//draw(tempCanvas);
		//return returnedBitmap;
		
		return this.getDrawingCache();
	}
	
	public class BackgroundShape extends GradientDrawable {
 
		public BackgroundShape(int[] colors, int cornerRadius) {
            super(GradientDrawable.Orientation.TOP_BOTTOM, colors);

            try {
                this.setShape(GradientDrawable.RECTANGLE);
                this.setGradientType(GradientDrawable.LINEAR_GRADIENT);
                this.setCornerRadius(cornerRadius);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
	}

}
