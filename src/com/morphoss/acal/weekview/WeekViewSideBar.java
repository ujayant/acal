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
	

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);
		p.setColor(Color.parseColor("#ffffff"));
		canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), p);
		
		
		if (this.isInEditMode()) {
			return;
		}
		float hour = 0;
		float h = this.getHeight();
		float width = this.getWidth()-1;
		float boxHeight = WeekViewActivity.HALF_HOUR_HEIGHT;
		float y = 0-boxHeight/2;
		float x = 0;
		boolean half = false;
		while(y<=h) {
			drawBox(x,y,width,boxHeight,canvas,half,hour,p);
			half=!half;
			if (!half) hour++;
			y+=boxHeight;
		}
		p.setStyle(Paint.Style.STROKE);
		p.setColor(Color.parseColor("#333333"));
		canvas.drawRect(0, 0, width, canvas.getHeight(), p);

	}
	
	public void drawBox(float x, float y, float width, float height, Canvas c, boolean half, float hour, Paint p) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = (View) inflater.inflate(R.layout.week_view_assets, null);
		TextView box;
		if (!half) box= ((TextView) v.findViewById(R.id.WV_side_box));
		else box= ((TextView) v.findViewById(R.id.WV_side_box_half));
		box.setVisibility(View.VISIBLE);
		String text = "";
		if (half) text="30";
		else if (hour == 0)text="12 "+(hour<12?"a":"p");
		else text=(int)hour+" "+(hour<12?"a":"p");
		box.setText(text);
		box.measure(MeasureSpec.makeMeasureSpec((int) width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) height, MeasureSpec.EXACTLY));
		box.layout(0,0, (int)width, (int)height);
		Bitmap returnedBitmap = Bitmap.createBitmap((int)width, (int)height,Bitmap.Config.ARGB_8888);
		Canvas tempCanvas = new Canvas(returnedBitmap);
		box.draw(tempCanvas);
		c.drawBitmap(returnedBitmap,x, y,p);
	}

	public void setDate(AcalDateTime date) {
		this.date = date;
	}
	

}
