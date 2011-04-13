package com.morphoss.acal.weekview;

import java.text.SimpleDateFormat;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;

public class WeekViewHeader extends ImageView {
	
	private WeekViewActivity context; 
	private AcalDateTime date;
	
	/** Default Constructor */
	public WeekViewHeader(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
		this.date = this.context.getCurrentDate();
	}
	
	/** Default Constructor */
	public WeekViewHeader(Context context, AttributeSet attrs) {
		super(context,attrs);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
		this.date = this.context.getCurrentDate();
	}
	
	
	/** Default Constructor */
	public WeekViewHeader(Context context) {
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
		if (this.isInEditMode()) {
			Paint p = new Paint();
			p.setStyle(Paint.Style.FILL);
			p.setColor(Color.parseColor("#333333"));
			canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), p);
			return;
		}
		AcalDateTime startDate = date.clone();
		startDate.addDays(-1);		//we start one day before the current date, current date should be first fully visible date
		int dayWidth = WeekViewActivity.DAY_WIDTH;
		
		int x = (int)(0-dayWidth+context.getScrollX()); int y = 0;
		
		int dayHeight = this.getHeight();
		int totalWidth = this.getWidth();
		while(x<(totalWidth+dayWidth)) {		//continue until we have draw one past screen edge
			drawBox(x,y,dayWidth,dayHeight,canvas,startDate);
			startDate.addDays(1);
			x+=dayWidth;
		}
		
		
	}

	public void setDate(AcalDateTime date) {
		this.date = date;
	}
	
	
	//TODO move to image cache
	private void drawBox(int x, int y, int w, int h, Canvas c, AcalDateTime day) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = (View) inflater.inflate(R.layout.week_view_assets, null);
		TextView title = ((TextView) v.findViewById(R.id.WV_header_day_box));
		title.setVisibility(View.VISIBLE);
		String formatString = "EEE\nMMM d";
		if (day.get(AcalDateTime.DAY_OF_WEEK) == WeekViewActivity.FIRST_DAY_OF_WEEK) {
			formatString+=" (w)";
		}
		SimpleDateFormat formatter = new SimpleDateFormat(formatString);
		title.setText(formatter.format(day.toJavaDate()));
		title.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
		title.layout(0, 0, w, h);
		Bitmap returnedBitmap = Bitmap.createBitmap(w, h,Bitmap.Config.ARGB_8888);
		Canvas tempCanvas = new Canvas(returnedBitmap);
		title.draw(tempCanvas);
		c.drawBitmap(returnedBitmap,x, y, new Paint());
	}
}