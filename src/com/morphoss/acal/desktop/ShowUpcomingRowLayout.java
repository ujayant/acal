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
	public static float WIDTH;
	public static float HEIGHT;
	
	public ShowUpcomingRowLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		WIDTH=context.getResources().getDimension(R.dimen.widgetRowWidth);
		HEIGHT=context.getResources().getDimension(R.dimen.widgetRowHeight);
	}
	
	public ShowUpcomingRowLayout(Context context) {
		super(context);
		WIDTH=context.getResources().getDimension(R.dimen.widgetRowWidth);
		HEIGHT=context.getResources().getDimension(R.dimen.widgetRowHeight);
	}
	
	public Bitmap setData(ContentValues cv, String dateTimeText) {
		int colour = cv.getAsInteger(ShowUpcomingWidgetProvider.FIELD_COLOUR);
		float dip = this.getResources().getDisplayMetrics().density;
		TextView timeView =(TextView)this.findViewById(R.id.upcoming_time);
		TextView sumView =(TextView)this.findViewById(R.id.upcoming_summary);
		
		
		timeView.setText(dateTimeText); timeView.setTextColor(AcalTheme.pickForegroundForBackground(colour&0x33FFFFFF));
		sumView.setText(cv.getAsString(ShowUpcomingWidgetProvider.FIELD_SUMMARY));
			sumView.setTextColor(AcalTheme.pickForegroundForBackground(colour&0x33FFFFFF));
		
		
		GradientDrawable shape = new BackgroundShape(new int[] {(0x33FFFFFF&colour), (0x88000000|colour)}, (int)(HEIGHT/2*dip));
		this.setBackgroundDrawable(shape);
		
//		this.measure((int)(WIDTH*dip),(int)(HEIGHT*dip));
//		this.layout(0, 0, (int)(WIDTH*dip),(int)(HEIGHT*dip));
		this.measure((int)WIDTH,(int)HEIGHT);
		this.layout(0, 0, (int)WIDTH,(int)HEIGHT);
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
