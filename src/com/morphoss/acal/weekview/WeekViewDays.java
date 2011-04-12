package com.morphoss.acal.weekview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.widget.ImageView;
import android.widget.TextView;

import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.davacal.AcalEvent;
import com.morphoss.acal.weekview.WeekViewMultiDay.SimpleEventObject;

public class WeekViewDays extends ImageView {
	
	public static final String TAG = "aCal - WeekViewDays";
	
	private WeekViewActivity context; 
	private AcalDateTime date;
	private WeekViewSideBar sidebar; 
	private WeekViewMultiDay multi;
	private WeekViewHeader header;	
	private float scrolly = 0;
	private float scrollx = 0; 
	
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
	
	public void move(float dx, float dy) {
		this.scrolly+=dy;
		if (scrolly < 0) scrolly = 0;
		if (scrolly > (WeekViewActivity.HALF_HOUR_HEIGHT*48-this.getHeight())) scrolly = WeekViewActivity.HALF_HOUR_HEIGHT*48-this.getHeight();
		this.scrollx-=dx;
		if (this.scrollx >= WeekViewActivity.DAY_WIDTH) {
			this.date.addDays(-1);
			this.scrollx-=WeekViewActivity.DAY_WIDTH;
		} else if (this.scrollx <= 0-WeekViewActivity.DAY_WIDTH) {
			this.date.addDays(1);
			this.scrollx+=WeekViewActivity.DAY_WIDTH;
		}
		this.multi.moveX(dx);
		this.header.moveX(dx);
		this.invalidate();
		this.multi.invalidate();
		this.header.invalidate();
	}

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		if (this.getWidth() == 0) return;
		if (this.getHeight() == 0) return;
		Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);
		p.setColor(Color.parseColor("#AAf0f0f0"));
		canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), p);
		
		//first draw the grid
		float dayWidth = WeekViewActivity.DAY_WIDTH;
		float halfHourHeight = WeekViewActivity.HALF_HOUR_HEIGHT;
		float hourHeight = halfHourHeight*2;  
		float width = this.getWidth();
		float height = this.getHeight();
		
		//what is the visible range?
		int startHour = (int) (scrolly/hourHeight);
		int numHours = (int) (height/hourHeight)+2;
		float offset = (startHour*hourHeight)-scrolly;
		
		//make sure friends get drawn correctly
		int location[] = new int[2];
		this.getLocationOnScreen(location);
		sidebar.setVerticalOffset((int)((location[1]-scrolly)-hourHeight));
		
		//need some values for drawing
		float pixelsPerMinute = (hourHeight*24)/(24*60);
		AcalDateTime currentDay = this.date.clone();
		
		
		
		//get the grid for each day
		Bitmap daygrid = context.getImageCache().getDayBox(startHour,startHour+numHours);
		
		//draw each day grid
		for (int x = -(int)(dayWidth-scrollx); x<= width; x+=dayWidth) {
			canvas.drawBitmap(daygrid, x, offset, p);
			
			SimpleEventObject[][] timeTable = getTimeTable(getEventList(currentDay,pixelsPerMinute));
			//draw visible events
			if (timeTable.length <=0) {
				currentDay.addDays(1);
				continue;
			}
			p.reset();
			p.setStyle(Paint.Style.FILL);
			int size = timeTable.length;
			float depth = 0;
			for (int i = 0; i<size && timeTable[i] != null && timeTable[i].length>0 && timeTable[i][0] != null;i++)  depth++;
			float singleWidth = (float)dayWidth/depth;
			for (int i = 0; i<size;i++)  {
				boolean hasEvent = false;
				for(int j=0;j < timeTable[i].length && timeTable[i][j] != null;j++) {
					SimpleEventObject event = timeTable[i][j];
						event.draw(canvas, scrolly, height, x+(i*singleWidth), singleWidth,p);
						hasEvent=true;
				}
				if (!hasEvent) break;
			}
			currentDay.addDays(1);
		}
	}

	
	public class SimpleEventObject implements Comparable<SimpleEventObject> {
		private int startMinute; 	//the minute in the day that this event starts
		private int endMinute;		//the minute in the day that this event ends
		private float height;
		private AcalEvent event;
		
		public SimpleEventObject(AcalEvent event, float pixelsPerMinute) {
			startMinute = event.dtstart.getDaySecond()/60;
			endMinute = event.getEnd().getDaySecond()/60;
			height = (endMinute-startMinute)*pixelsPerMinute;
			height = Math.max(WeekViewActivity.MINIMUM_DAY_EVENT_HEIGHT, height);	//minimum height
			this.event = event;
		}
		
		public void draw(Canvas c, float offset, float screenHeight, float xOffset, float width, Paint p) {
			float totalDayHeight = WeekViewActivity.HALF_HOUR_HEIGHT*48;
			float pixelsPerMinute = totalDayHeight/(24*60);
			float yMinute = offset/pixelsPerMinute;
			float endMinute = yMinute + screenHeight/pixelsPerMinute;
			if ((this.startMinute <= endMinute) && this.endMinute >= yMinute ){
				float offy = (startMinute-yMinute)*pixelsPerMinute;
				c.drawBitmap(context.getImageCache().getEventBitmap(event, (int)width, (int)height), xOffset,offy, p);
			}
		}
		
		@Override
		public int compareTo(SimpleEventObject seo) {
			return this.startMinute-seo.startMinute;
		}
	}
	private ArrayList<SimpleEventObject> getEventList(AcalDateTime day, float pixelsPerMinute) {
		ArrayList<AcalEvent> eventList = context.getEventsForDay(day);
		ArrayList<SimpleEventObject> events = new ArrayList<SimpleEventObject>();
		for (AcalEvent e : eventList) {
			//only add events that cover at less than one full calendar day
			if (e.getDuration().getDurationMillis()/1000 >= 86400) continue;	//more than 24 hours, cant go in
			AcalDateTime start = e.getStart().clone();
			int startSec = start.getDaySecond();
			if (startSec != 0) {
				start.setHour(0); start.setMinute(0);start.setSecond(0); start.addDays(1);	//move forward to 00:00:00	
			}
			//start is now at the first 'midnight' of the event. Duration to end MUST be < 24hours for us to want this event
			if(start.getDurationTo(e.getEnd()).getDurationMillis()/1000 >=86400) continue;
			events.add(new SimpleEventObject(e,pixelsPerMinute));
		}
		return events;
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
					else if (row[j].endMinute <= events.get(x).startMinute) {j++; continue; }
					else break;
				}
				i++;
			}
		}
		return timetable;
	}

	
	public void addScrollFriends(WeekViewSideBar sidebar, WeekViewMultiDay multi, WeekViewHeader header) {
		this.sidebar = sidebar;
		this.multi = multi;
		this.header = header;
	}

	public void setDate(AcalDateTime date) {
		this.date = date;
	}
}
