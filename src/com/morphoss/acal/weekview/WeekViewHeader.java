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
		// TODO Auto-generated constructor stub
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
		int x = 0; int y = 0;
		int dayWidth = WeekViewActivity.DAY_WIDTH;
		int dayHeight = this.getHeight();
		int totalWidth = this.getWidth();
		while(x<totalWidth) {
			drawBox(x,y,dayWidth,dayHeight,canvas,startDate);
			startDate.addDays(1);
			x+=dayWidth;
		}
		
		
	}

	public void setDate(AcalDateTime date) {
		this.date = date;
	}
	
	private void drawBox(int x, int y, int w, int h, Canvas c, AcalDateTime day) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = (View) inflater.inflate(R.layout.week_view_assets, null);
		TextView title = ((TextView) v.findViewById(R.id.WV_header_day_box));
		title.setVisibility(View.VISIBLE);
		String formatString = "EEE\nMMM d";
		if (day.get(AcalDateTime.DAY_OF_WEEK) == this.getFirstDay(context)) {
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
	
	private int getFirstDay(Context context) {
		//check preferred first day
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		int ret = 0;
		try {
			ret = Integer.parseInt(prefs.getString(context.getString(R.string.firstDayOfWeek), "0"));
			if ( ret < AcalDateTime.MONDAY || ret > AcalDateTime.SUNDAY ) throw new Exception();
		}
		catch( Exception e ) {
			ret = AcalDateTime.MONDAY; 
		}
		return ret;
	}
}
