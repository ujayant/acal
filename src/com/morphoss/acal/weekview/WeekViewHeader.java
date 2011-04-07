package com.morphoss.acal.weekview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;

public class WeekViewHeader extends ImageView {
	
	private Context context; 
	private AcalDateTime date;
	
	/** Default Constructor */
	public WeekViewHeader(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
	}
	
	/** Default Constructor */
	public WeekViewHeader(Context context, AttributeSet attrs) {
		super(context,attrs);
		this.context = context;
	}
	
	
	/** Default Constructor */
	public WeekViewHeader(Context context) {
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
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = (View) inflater.inflate(R.layout.week_view_assets, null);
		TextView title = ((TextView) v.findViewById(R.id.WV_header_day_box));
		title.setText("Mon\nApr 20");
		title.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
		title.layout(0, 0, w, h);
		Bitmap returnedBitmap = Bitmap.createBitmap(w, h,Bitmap.Config.ARGB_8888);
		Canvas tempCanvas = new Canvas(returnedBitmap);
		title.draw(tempCanvas);
		c.drawBitmap(returnedBitmap,x, y, new Paint());
	}
}
