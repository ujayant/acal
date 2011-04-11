package com.morphoss.acal.weekview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

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
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.davacal.AcalEvent;

public class WeekViewMultiDay extends ImageView {
	
	private WeekViewActivity context; 
	private AcalDateTime date;
	private float scrollx = 0;
	
	/** Default Constructor */
	public WeekViewMultiDay(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
		this.date = this.context.getCurrentDate().clone();
	}
	
	/** Default Constructor */
	public WeekViewMultiDay(Context context, AttributeSet attrs) {
		super(context,attrs);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
		this.date = this.context.getCurrentDate().clone();
	}
	
	
	/** Default Constructor */
	public WeekViewMultiDay(Context context) {
		super(context);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
		this.date = this.context.getCurrentDate().clone();
	}
	
	public void moveX(float dx) {
		this.scrollx+=dx;
		if (this.scrollx >= WeekViewActivity.DAY_WIDTH) {
			this.date.addDays(1);
			this.scrollx-=WeekViewActivity.DAY_WIDTH;
		} else if (this.scrollx <= 0-WeekViewActivity.DAY_WIDTH) {
			this.date.addDays(-1);
			this.scrollx+=WeekViewActivity.DAY_WIDTH;
		}
		
	}
	
	private ArrayList<SimpleEventObject> getEventList(long startEpoch, AcalDateRange range, float pixelsPerHour) {
		ArrayList<AcalEvent> eventList = context.getEventsForDays(range);
		ArrayList<SimpleEventObject> events = new ArrayList<SimpleEventObject>();
		for (AcalEvent e : eventList) {
			//only add events that cover at least one full calendar day
			if (e.getDuration().getDurationMillis()/1000 < 86400) continue;	//less than 24 hours, cant go in
			AcalDateTime start = e.getStart().clone();
			int startSec = start.getDaySecond();
			if (startSec != 0) {
				start.setHour(0); start.setMinute(0);start.setSecond(0); start.addDays(1);	//move forward to 00:00:00	
			}
			//start is now at the first 'midnight' of the event. Duration to end MUST be > 24hours for us to want this event
			if(start.getDurationTo(e.getEnd()).getDurationMillis()/1000 <86400) continue;
			events.add(new SimpleEventObject(e,startEpoch,pixelsPerHour));
		}
		return events;
	}

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);
		p.setColor(Color.parseColor("#F0F0F0"));
		canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), p);
	
		if (this.isInEditMode()) {
			return;
		}
		int x = 0; int y = 0;
		float dayWidth = WeekViewActivity.DAY_WIDTH;
		float dayHeight = this.getHeight();
		float totalWidth = this.getWidth();
	
		//need to calulate the number of hours that are visible
		float hours = (totalWidth/dayWidth)*24;
		float pixelsPerHour =(totalWidth/hours);
		AcalDateTime end = date.clone();
		
		end.addSeconds((int)(hours*3600));
		AcalDateRange range = new AcalDateRange(date,end);
		
		SimpleEventObject[][] timeTable = getTimeTable(getEventList(date.getEpoch(),range,pixelsPerHour));
		if (timeTable.length <=0) return;
		int size = timeTable.length;
		float depth = 0;
		for (int i = 0; i<size && timeTable[i] != null && timeTable[i].length>0 && timeTable[i][0] != null;i++)  depth++;
		float singleHeight = (float)dayHeight/depth;
		for (int i = 0; i<size;i++)  {
			boolean hasEvent = false;
			for(int j=0;j < timeTable[i].length && timeTable[i][j] != null;j++) {
				SimpleEventObject event = timeTable[i][j];
					event.draw((int)(0-dayWidth-scrollx),i*singleHeight,singleHeight,canvas,context);
					hasEvent=true;
			}
			if (!hasEvent) break;
		}
		
		//draw borders around each day
		p.setStyle(Paint.Style.STROKE);
		p.setColor(Color.parseColor("#333333"));
		for (x =0; (x<totalWidth+dayWidth); x+=dayWidth)
		canvas.drawRect(x-scrollx, 0, dayWidth-scrollx, canvas.getHeight()-1, p); 
	}

	public void setDate(AcalDateTime date) {
		this.date = date;
	}
	
	
	public class SimpleEventObject implements Comparable<SimpleEventObject> {
		private int startX;
		private int width;
		private int end;
		private String title;
		private int colour;
		
		public SimpleEventObject(AcalEvent event, long startEpoch, float pixelsPerHour) {
			this.title = event.summary;
			this.colour = event.colour;
			event.getStart().applyLocalTimeZone();
			event.getEnd().applyLocalTimeZone();
			startX = Math.max((int)((((event.getStart().getEpoch()-startEpoch)/(60L*60L))*pixelsPerHour)),0);
			width =(int) (((event.getEnd().getEpoch()-event.getStart().getEpoch())/(60L*60L))*pixelsPerHour);
			end = startX+width-1;
		}
		
		public void draw(float x, float y, float height, Canvas c, Context con) {
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View v = (View) inflater.inflate(R.layout.week_view_assets, null);
			TextView title = ((TextView) v.findViewById(R.id.WV_header_multi_day_box));
			title.setVisibility(View.VISIBLE);
			title.setText(this.title);
			title.setBackgroundColor(colour);
			title.measure(MeasureSpec.makeMeasureSpec((int) width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) height, MeasureSpec.EXACTLY));
			title.layout(0,0, (int)width, (int)height);
			Bitmap returnedBitmap = Bitmap.createBitmap((int)width, (int)height,Bitmap.Config.ARGB_8888);
			Canvas tempCanvas = new Canvas(returnedBitmap);
			title.draw(tempCanvas);
			c.drawBitmap(returnedBitmap,startX+x, y, new Paint());
		}

		@Override
		public int compareTo(SimpleEventObject another) {
			return this.startX-another.startX;
		}
	}
	
	private static SimpleEventObject[][] getTimeTable(List<SimpleEventObject> events) {
		Collections.sort(events);
		SimpleEventObject[][] timetable = new SimpleEventObject[events.size()][events.size()]; //maximum possible
		for (int x = 0; x<events.size(); x++) {
			int i = 0;
			boolean go = true;
			while(go) {
				SimpleEventObject[] row = timetable[i];
				int j=0;
				while(true) {
					if (row[j] == null) { row[j] = events.get(x); go=false; break; }
					else if (row[j].end <= events.get(x).startX) {j++; continue; }
					else break;
				}
				i++;
			}
		}
		return timetable;
	}

}
