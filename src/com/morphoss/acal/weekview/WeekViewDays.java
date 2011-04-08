package com.morphoss.acal.weekview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.morphoss.acal.acaltime.AcalDateTime;

public class WeekViewDays extends ImageView {
	
	private WeekViewActivity context; 
	private AcalDateTime date;
	
	/** Default Constructor */
	public WeekViewDays(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
		this.date = this.context.getCurrentDate();
	}
	
	/** Default Constructor */
	public WeekViewDays(Context context, AttributeSet attrs) {
		super(context,attrs);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
		this.date = this.context.getCurrentDate();
	}
	
	
	/** Default Constructor */
	public WeekViewDays(Context context) {
		super(context);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
		this.date = this.context.getCurrentDate();
	}
	

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);
		p.setColor(Color.parseColor("#AAf0f0f0"));
		canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), p);
		
		//first draw the grid
		float dayWidth = WeekViewActivity.DAY_WIDTH;
		float halfHourHeight = WeekViewActivity.HALF_HOUR_HEIGHT;
		float width = this.getWidth();
		float height = this.getHeight();
		float dayHeight = halfHourHeight*24;
		
		DashPathEffect dashes = new DashPathEffect(new float[]{5,5},0);
		for (float x = 0; x<=width; x+=dayWidth) {
			//draw box for whole day
			p.reset();
			p.setStyle(Paint.Style.STROKE);
			p.setColor(Color.parseColor("#AAAAAA"));
			canvas.drawRect(x,0,dayWidth,(halfHourHeight*2)*9,p);
			p.setColor(Color.parseColor("#f6f6d0"));
			canvas.drawRect(x,(halfHourHeight*2)*9,dayWidth,(halfHourHeight*2)*8,p);
			p.setColor(Color.parseColor("#AAAAAA"));
			canvas.drawRect(x,(halfHourHeight*2)*17,dayWidth,(halfHourHeight*2)*7,p);
			boolean half = false;
			for (float y=0;y<=height;y+=halfHourHeight) {
				if (half) {
					p.setStyle(Paint.Style.STROKE);
					p.setColor(Color.parseColor("#AAAAAA"));
					//draw dotted
					p.setPathEffect(dashes);
					canvas.drawLine(x,y, x+dayWidth, y, p);
				}
				else{
					p.setStyle(Paint.Style.STROKE);
					p.setColor(Color.parseColor("#AAAAAA"));
					//draw solid
					p.reset();
					p.setStyle(Paint.Style.STROKE);
					p.setColor(Color.parseColor("#AAAAAA"));
					canvas.drawLine(x,y, x+dayWidth, y, p);
				}
				half=!half;
			}
		}
		
		
	}

	public void setDate(AcalDateTime date) {
		this.date = date;
	}
	

}
