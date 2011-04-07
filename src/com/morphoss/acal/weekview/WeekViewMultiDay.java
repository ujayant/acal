package com.morphoss.acal.weekview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.widget.ImageView;
import android.widget.TextView;

import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;

public class WeekViewMultiDay extends ImageView {
	
	private Context context; 
	private AcalDateTime date;
	
	/** Default Constructor */
	public WeekViewMultiDay(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
	}
	
	/** Default Constructor */
	public WeekViewMultiDay(Context context, AttributeSet attrs) {
		super(context,attrs);
		this.context = context;
	}
	
	
	/** Default Constructor */
	public WeekViewMultiDay(Context context) {
		super(context);
		this.context = context;
	}
	

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		if (this.isInEditMode()) {
			Paint p = new Paint();
			p.setStyle(Paint.Style.FILL);
			p.setColor(Color.parseColor("#333333"));
			canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), p);
			return;
		}
		int x = 0; int y = 0;
		int dayWidth = WeekViewActivity.DAY_WIDTH;
		int dayHeight = this.getHeight();
		int totalWidth = this.getWidth();
		
		while(x<totalWidth) {
			drawBox(x,y,dayWidth,dayHeight,canvas);
			x+=dayWidth;
		}
		
		
	}

	public void setDate(AcalDateTime date) {
		this.date = date;
	}
	
	private void drawBox(int x, int y, int w, int h, Canvas c) {
 		
	

	}
	

}
