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
	
	/** Default Constructor */
	public WeekViewSideBar(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
	}
	
	/** Default Constructor */
	public WeekViewSideBar(Context context, AttributeSet attrs) {
		super(context,attrs);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
	}
	
	
	/** Default Constructor */
	public WeekViewSideBar(Context context) {
		super(context);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
	}
	
	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		if (this.isInEditMode()) {
			Paint p = new Paint();
			p.setStyle(Paint.Style.FILL);
			p.setColor(0x00ffffff);
			canvas.drawRect(0, 0, this.getWidth(), this.getHeight(), p);
			return;
		}
		if (this.getWidth() == 0) return;
		if (this.getHeight() == 0) return;
		Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);
		p.setColor(context.getResources().getColor(R.color.WeekViewSidebarBG));
		canvas.drawRect(0, 0, this.getWidth(), this.getHeight(), p);
		
		float offset = (context.getSideVerticalOffset()-(context.getScrollY()+(WeekViewActivity.HALF_HOUR_HEIGHT*1.5F)));
		
		canvas.drawBitmap(context.getImageCache().getSideBar(this.getWidth()), 0,offset, p);
		p.setStyle(Paint.Style.STROKE);
		p.setColor(context.getResources().getColor(R.color.WeekViewSidebarBorder));
		canvas.drawRect(0, 0, this.getWidth(), this.getHeight(), p);
	}
}
