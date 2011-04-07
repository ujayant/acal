package com.morphoss.acal.weekview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.morphoss.acal.acaltime.AcalDateTime;

public class WeekViewDays extends ImageView {
	
	private Context context; 
	
	/** Default Constructor */
	public WeekViewDays(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
	}
	
	/** Default Constructor */
	public WeekViewDays(Context context, AttributeSet attrs) {
		super(context,attrs);
		this.context = context;
	}
	
	
	/** Default Constructor */
	public WeekViewDays(Context context) {
		super(context);
		this.context = context;
	}
	private AcalDateTime date;

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);
		p.setColor(Color.parseColor("#00ffff"));
		canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), p);
	}

	public void setDate(AcalDateTime date) {
		this.date = date;
	}
	

}
