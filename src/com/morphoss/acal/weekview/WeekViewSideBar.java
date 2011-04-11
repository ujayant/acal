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

public class WeekViewSideBar extends ImageView {
	
	private WeekViewActivity context; 
	private AcalDateTime date;
	private float verticalOffset = 0;
	
	/** Default Constructor */
	public WeekViewSideBar(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
		this.date = this.context.getCurrentDate();
	}
	
	/** Default Constructor */
	public WeekViewSideBar(Context context, AttributeSet attrs) {
		super(context,attrs);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
		this.date = this.context.getCurrentDate();
	}
	
	
	/** Default Constructor */
	public WeekViewSideBar(Context context) {
		super(context);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
		this.date = this.context.getCurrentDate();
	}
	
	public void setVerticalOffset(int offset) {
		int[] location = new int[2];
		this.getLocationOnScreen(location);
		if (offset-location[1] != this.verticalOffset) {
			this.verticalOffset = offset-location[1]; 
			this.invalidate(); //force redraw
		}
	}
	
	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		
		if (this.getWidth() == 0) return;
		if (this.getHeight() == 0) return;
		Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);
		p.setColor(Color.parseColor("#f0f0f0"));
		canvas.drawRect(0, 0, this.getWidth(), this.getHeight(), p);
		if (this.isInEditMode()) {
			return;
		}
		
		float offset = verticalOffset+(WeekViewActivity.HALF_HOUR_HEIGHT/2);
		
		canvas.drawBitmap(context.getImageCache().getSideBar(this.getWidth()), 0,offset, p);
		p.setStyle(Paint.Style.STROKE);
		p.setColor(Color.parseColor("#333333"));
		canvas.drawRect(0, 0, this.getWidth(), this.getHeight(), p);

	}

	public void setDate(AcalDateTime date) {
		this.date = date;
	}
}
