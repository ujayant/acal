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
import android.widget.ImageView;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.davacal.AcalEvent;

public class WeekViewDays extends ImageView {
	
	public static final String TAG = "aCal - WeekViewDays";
	
	private WeekViewActivity context; 
	private AcalDateTime date;
	private float headerHeight = 0;
	
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
	
	public float getHeaderHeight() {
		return this.headerHeight;
	}
	
	private float drawHeader(Canvas canvas, float x, float y, float w, float h) {
		Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);
		p.setColor(Color.parseColor("#00ffffff"));
		canvas.drawRect(x, y, w, 80, p);
		p.setStyle(Paint.Style.STROKE);
		p.setColor(Color.parseColor("#00000000"));
		canvas.drawRect(x, y, w, 80, p);
		this.headerHeight = 80;
		return headerHeight;
	}
	
	private void drawBackground(Canvas canvas, float x, float y,float w, float h) {
		Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);
		p.setColor(Color.parseColor("#AAf0f0f0"));
		canvas.drawRect(x, y, w, h, p);
	}
	
	public void drawGrid(Canvas canvas, float x, float y, float w, float h, float startHour, float numHours, float offset) {
		Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);
		int dayX = (int)(0-WeekViewActivity.DAY_WIDTH+context.getScrollX());
		AcalDateTime currentDay = context.getCurrentDate().clone();
		currentDay.addDays(-1);
		
		//get the grid for each day
		Bitmap weekDayGrid = context.getImageCache().getWeekDayBox((int)startHour,(int)(startHour+numHours),offset);
		Bitmap weekEndGrid = context.getImageCache().getWeekEndDayBox((int)startHour,(int)(startHour+numHours),offset);
		while (dayX<= w) {
			if (currentDay.getWeekDay() == AcalDateTime.SATURDAY || currentDay.getWeekDay() == AcalDateTime.SUNDAY)
				canvas.drawBitmap(weekEndGrid, dayX+x, y, p);
			else 
				canvas.drawBitmap(weekDayGrid, dayX+x, y, p);
			dayX+=WeekViewActivity.DAY_WIDTH;
			currentDay.addDays(1);
		}
	}

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		if (this.getWidth() == 0) return;
		if (this.getHeight() == 0) return;
		float width = this.getWidth();
		float height = this.getHeight();
		float x = 0;
		float y = 0;
		
		//main background
		drawBackground(canvas,0,y,width, height);
		
		//header
		y = drawHeader(canvas,x,y,width,height);	//returns the height of the header
		height = height-y;
		
		if (this.isInEditMode()) return;	//cant draw the rest in edit mode
		
		//Define some useful vars		
		float scrolly = context.getScrollY();
		float scrollx = context.getScrollX();

		float halfHourHeight = WeekViewActivity.HALF_HOUR_HEIGHT;
		float hourHeight = halfHourHeight*2;  
		float pixelsPerMinute = (hourHeight*24)/(24*60);
		
		//what is the visible range?
		int startHour = (int) (scrolly/hourHeight);
		int numHours = (int) (height/hourHeight)+2;
		
		//how much we need to adjust the y co-ordinate due to scrolling
		float offset = ((startHour*hourHeight)-scrolly);
		
		drawGrid(canvas,x,y,width,height, startHour, numHours, offset);
		
		float dayX = (int)(0-WeekViewActivity.DAY_WIDTH+context.getScrollX());
		float dayWidth = WeekViewActivity.DAY_WIDTH;
		Paint p = new Paint();

		AcalDateTime currentDay = this.date.clone();
		currentDay.addDays(-1);
		//draw evnets
		while (dayX<= width) {
		
			SimpleEventObject[][] timeTable = getTimeTable(getEventList(currentDay,pixelsPerMinute));
			//draw visible events
			if (timeTable.length <=0) {
				currentDay.addDays(1);
				dayX+=dayWidth;
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
						event.draw(canvas, dayX+(i*singleWidth), y, singleWidth, height,  scrolly);
						//event.draw(canvas, y+scrolly, height, dayX+(i*singleWidth), singleWidth,p);
						hasEvent=true;
				}
				if (!hasEvent) break;
			}
			currentDay.addDays(1);
			dayX+=dayWidth;
		}
		//border
		p.reset();
		p.setStyle(Paint.Style.STROKE);
		p.setColor(0xff333333);
		canvas.drawRect(x, y, x+width, y+height, p);
		//draw uber-cool shading effect
		int hhh = WeekViewActivity.HALF_HOUR_HEIGHT/2;
		 
		int base = 0x333333;
		int current = 0xc0;
		int decrement = current/(hhh-3);
		
		int color = (current << 24)+base; 
		p.setColor(color);
		for (int i = 0; i < 3; i++) {
			canvas.drawLine(x, y+i, x+width, y+i, p);
		}
		
		
		for (int i=3; i<hhh;i++) {
			current-=decrement;
			color = (current << 24)+base; 
			p.setColor(color);
			canvas.drawLine(x, y+i, x+width, y+i, p);
		}
		
	}

	
	public class SimpleEventObject implements Comparable<SimpleEventObject> {
		private int startMinute; 	//the minute in the day that this event starts
		private int endMinute;		//the minute in the day that this event ends
		private AcalEvent event;
		
		public SimpleEventObject(AcalEvent event, float pixelsPerMinute) {
			startMinute = event.dtstart.getDaySecond()/60;
			endMinute = event.getEnd().getDaySecond()/60;
			this.event = event;
		}
		
	/**	public void draw(Canvas c, float offset, float screenHeight, float xOffset, float width, Paint p) {
			float totalDayHeight = WeekViewActivity.HALF_HOUR_HEIGHT*48;
			float pixelsPerMinute = totalDayHeight/(24*60);
			float yMinute = offset/pixelsPerMinute;
			float endMinute = yMinute + screenHeight/pixelsPerMinute;
			if ((this.startMinute <= endMinute) && this.endMinute >= yMinute ){
				float offy = (startMinute-yMinute)*pixelsPerMinute;
				c.drawBitmap(context.getImageCache().getEventBitmap(event, (int)width, (int)height), xOffset,offy, p);
			}
		}*/
		
		public void draw(Canvas canvas, float x, float y, float width, float screenHeight, float verticalOffset) {
			float totalDayHeight = WeekViewActivity.HALF_HOUR_HEIGHT*48F;
			float pixelsPerMinute = totalDayHeight/(24*60);
			float yMinute = verticalOffset/pixelsPerMinute;
			float endMinute = yMinute + (y+screenHeight)/pixelsPerMinute;
			
			if ((this.startMinute <= endMinute) && this.endMinute >= yMinute ){
				//are we larger than screen height? if so draw to screen height
				Paint p = new Paint();
				p.setStyle(Paint.Style.FILL);
				p.setColor(0xff555555);
				//calulate our y point
				float yStart = y+((startMinute-yMinute)*pixelsPerMinute);
				//calulate our end point
				float yEnd = yStart+((this.endMinute-this.startMinute)*pixelsPerMinute);
				//dont draw above y
				if (yStart < y) yStart = y;
				float height = Math.min(yEnd-y, screenHeight);
				if (height <= 0 || width <= 0) return;
				
				//canvas.drawRect(x, yStart, x+width,Math.min(yEnd, y+screenHeight) , p);
				canvas.drawBitmap(context.getImageCache().getEventBitmap(event, (int)width, (int)height), x,(int)(yStart), new Paint());
				//float offy = (startMinute-yMinute)*pixelsPerMinute;
				
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
}
