package com.morphoss.acal.weekview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.davacal.AcalEvent;
import com.morphoss.acal.davacal.SimpleAcalEvent;

public class WeekViewDays extends ImageView {
	
	public static final String TAG = "aCal - WeekViewDays";
	
	private WeekViewActivity context; 
	private AcalDateTime date;
	private float headerHeight = 0;
	private SimpleAcalEvent[][] headerTimeTable;
	private float headerHours;
	private float pixelsPerHour;
	private float size;
	private float lastMaxX=0;
	
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
	
	public float headerHeight() {
		return this.headerHeight;
	}
	
	//Calculates how big the header will be. saves some data as this will usually be followed by a draw call.
	public void calculateHeaderHeight() {

		float itemHeight = WeekViewActivity.FULLDAY_ITEM_HEIGHT;
		float totalWidth = this.getWidth();
	
		//some values important for calculating range etc.
		float numSeconds = (totalWidth/WeekViewActivity.DAY_WIDTH)*24F*3600F;
		float pixelsPerSecond = totalWidth/numSeconds;
		AcalDateTime startTime = date.clone();
		float numSecondsOffset = context.getScrollX()/pixelsPerSecond;
		startTime.addSeconds((int)(0-(numSecondsOffset)));	//represents 0 hour
		AcalDateTime endTime = startTime.clone();
		endTime.addSeconds((int)(numSeconds));		//represents end hour
		
		
		AcalDateRange range = new AcalDateRange(startTime,endTime);
		
		headerTimeTable = getTimeTable(getEventList(range));
		if (headerTimeTable.length <=0) {	this.headerHeight =0; return; }
		size = headerTimeTable.length;
		float depth = 0;
		for (int i = 0; i<size && headerTimeTable[i] != null && headerTimeTable[i].length>0 && headerTimeTable[i][0] != null;i++)  depth++;
		this.headerHeight = depth*itemHeight;
	}
	
	float getHeaderHeight() {
		if (headerHeight == 0) calculateHeaderHeight();
		return this.headerHeight; 
	}
	
	private void drawFulldayEvents(Canvas canvas) {
		Paint p = new Paint();
		float dayWidth = WeekViewActivity.DAY_WIDTH;
		float itemHeight = WeekViewActivity.FULLDAY_ITEM_HEIGHT;
		float totalWidth = this.getWidth();
		float scrollx = context.getScrollX();
		
		//draw borders around each day
		p.setStyle(Paint.Style.STROKE);
		p.setColor(context.getResources().getColor(R.color.WeekViewMultiDayBorder));
		for (float x = 0+scrollx-dayWidth; (x<totalWidth+(dayWidth*2)); x+=dayWidth)
		canvas.drawRect(x, 0, dayWidth+scrollx, headerHeight, p); 
		
		for (int i = 0; i<size;i++)  {
			boolean hasEvent = false;
			for(int j=0;j < headerTimeTable[i].length && headerTimeTable[i][j] != null;j++) {
				SimpleAcalEvent event = headerTimeTable[i][j];
					drawHorizontal(event, canvas, 0, (i*itemHeight), totalWidth, itemHeight, scrollx);
					hasEvent=true;
			}
			if (!hasEvent) break;
		}
		
		
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
		while ( dayX <= w) {
			if (currentDay.getWeekDay() == AcalDateTime.SATURDAY || currentDay.getWeekDay() == AcalDateTime.SUNDAY)
				canvas.drawBitmap(weekEndGrid, dayX+x, y, p);
			else 
				canvas.drawBitmap(weekDayGrid, dayX+x, y, p);
			dayX += WeekViewActivity.DAY_WIDTH;
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
		
		//need to calculate height
		calculateHeaderHeight();
		y = this.headerHeight;
		height = height-y;
		
		if (this.isInEditMode()) return;	//cant draw the rest in edit mode
		
		//Define some useful vars		
		float scrolly = context.getScrollY();
		

		float halfHourHeight = WeekViewActivity.HALF_HOUR_HEIGHT;
		float hourHeight = halfHourHeight*2;  
		
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
		//draw events
		while (dayX<= width) {
		
			SimpleAcalEvent[][] timeTable = getImprovedTimeTable(getEventList(currentDay));
			//draw visible events
			if (timeTable.length  <=0) {
				currentDay.addDays(1);
				dayX+=dayWidth;
				continue;
			}
			p.reset();
			p.setStyle(Paint.Style.FILL);
			int size = timeTable.length;
			Set<SimpleAcalEvent> drawn = new HashSet<SimpleAcalEvent>(); //events can show up several times in the timetable, keep a record of whats already drawn
			for (int i = 0; i<size;i++)  {
				int curX = 0;
				for(int j=0;j < timeTable[i].length;j++) {
					if (timeTable[i][j] != null) {
						if (drawn.contains(timeTable[i][j])) {
							curX+=timeTable[i][j].getLastWidth();
							continue;
						}
						drawn.add(timeTable[i][j]);
						
						//calculate width
						int depth  = 0;
						for (int k = j; k<=lastMaxX && (timeTable[i][k] == null || timeTable[i][k] == timeTable[i][j]); k++) depth++;
						float singleWidth = (dayWidth/(lastMaxX+1))*(depth);	
						SimpleAcalEvent event = timeTable[i][j];
						drawVertical(event, canvas, dayX+curX, y, singleWidth, height,  scrolly);
						event.setLastWidth((int)singleWidth);
						curX+=singleWidth;
					}
				}
			}
			currentDay.addDays(1);
			dayX+=dayWidth;
		}
		//now draw the header (if there is one)
		if (headerHeight > 0) {
			drawFulldayEvents(canvas);
		}
		
		
		//border
		p.reset();
		p.setStyle(Paint.Style.STROKE);
		p.setColor(0xff333333);
		canvas.drawRect(x, y, x+width, y+height, p);
		//draw uber-cool shading effect
		if (headerHeight != 0 ) {
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
	}


	public void drawVertical(SimpleAcalEvent event, Canvas canvas, float x, float y, float width, float screenHeight, float verticalOffset) {
		if ( width < 1f ) return;
		float totalDayHeight = WeekViewActivity.HALF_HOUR_HEIGHT*48F;
		float pixelsPerSecond = totalDayHeight/(24*60*60);
		float ySecond = verticalOffset/pixelsPerSecond;
		float endSecond = ySecond+(screenHeight/pixelsPerSecond);
		float eventStart = (event.start%86400);
		float eventEnd = (event.end%86400);
		
		
			
		if ((eventStart <= endSecond) && eventEnd >= ySecond ){
			// So some part of us is on screen
			Paint p = new Paint();
			p.setStyle(Paint.Style.FILL);
			p.setColor(0xff555555);
			//calulate our y point
			float yStart = y+((eventStart-ySecond)*pixelsPerSecond);
			//calulate our end point
			float yEnd = yStart+((eventEnd-eventStart)*pixelsPerSecond);
			//dont draw above y
			if (yStart < y) yStart = y;
				//are we larger than screen height? if so draw to screen height
			float height = Math.min(yEnd-yStart, screenHeight);
			if ( height < 1f ) return;
				//are we smaller than min event height? if so draw to that
			height = Math.max(height, WeekViewActivity.MINIMUM_DAY_EVENT_HEIGHT);
			
			int maxHeight = 1 + (int) (pixelsPerSecond * (float) (eventEnd - eventStart));
				//canvas.drawRect(x, yStart, x+width,Math.min(yEnd, y+screenHeight) , p);
			canvas.drawBitmap(context.getImageCache().getEventBitmap(event.resourceId,event.summary,event.colour,
						(int)width, (int)height,(int)width, maxHeight), x,(int)(yStart), new Paint());
			//float offy = (startMinute-yMinute)*pixelsPerMinute;
				
		}
	}

	public void drawHorizontal(SimpleAcalEvent event, Canvas c, float x, float y, float width, float height, float scroll) {
		if ( height < 1f ) return;
		//first we need to calulate the number of visible hours, and identify what epoch hour starts at x=0
		float numSeconds = (width/WeekViewActivity.DAY_WIDTH)*24F*3600F;
		float pixelsPerSecond = width/numSeconds;
		AcalDateTime startTime = date.clone();
		float numSecondsOffset = scroll/pixelsPerSecond;
		startTime.addSeconds((int)(0-(numSecondsOffset)));	//represents 0 hour
		AcalDateTime endTime = startTime.clone();
		endTime.addSeconds((int)(numSeconds));		//represents end hour
		
		float startSecond = (event.start-startTime.getEpoch());
		float endSecond = (event.end-startTime.getEpoch());
		int maxWidth = 1 + (int) (numSeconds * pixelsPerSecond); 
		if (startSecond < 0) startSecond = 0;
		if (endSecond>numSeconds) endSecond = numSeconds;
		if (endSecond-startSecond <=0) return; //we are not visible
		float eventWidth = (endSecond-startSecond)*pixelsPerSecond;
		float startX = x+ (startSecond*pixelsPerSecond);
		if ( eventWidth < 1f ) return;
		c.drawBitmap(context.getImageCache().getEventBitmap(event.resourceId,event.summary,event.colour,
					(int)eventWidth, (int)height, maxWidth, (int)height), (int)startX,y, new Paint());
	}
	
	//for horizontal
	private ArrayList<SimpleAcalEvent> getEventList(AcalDateTime day) {
		ArrayList<AcalEvent> eventList = context.getEventsForDay(day);
		ArrayList<SimpleAcalEvent> events = new ArrayList<SimpleAcalEvent>();
		for (AcalEvent e : eventList) {
			//only add events that cover less than one full calendar day
			if (e.getDuration().getDurationMillis()/1000 >= 86400) continue;	//more than 24 hours, cant go in
			AcalDateTime start = e.getStart().clone();
			int startSec = start.getDaySecond();
			if (startSec != 0) {
				start.setHour(0); start.setMinute(0);start.setSecond(0); start.addDays(1);	//move forward to 00:00:00	
			}
			//start is now at the first 'midnight' of the event. Duration to end MUST be < 24hours for us to want this event
			if(start.getDurationTo(e.getEnd()).getDurationMillis()/1000 >=86400) continue;
			SimpleAcalEvent ret =SimpleAcalEvent.getSimpleEvent(e); 
			events.add(ret);
		}
		return events;
	}
	
	
	//used for vertical
	private static SimpleAcalEvent[][] getTimeTable(List<SimpleAcalEvent> events) {
		Collections.sort(events);
		SimpleAcalEvent[][] timetable = new SimpleAcalEvent[events.size()][events.size()]; //maximum possible
		for (int x = 0; x<events.size(); x++) {
			int i = 0;
			boolean go = true;
			while(go) {
				SimpleAcalEvent[] row = timetable[i];
				int j=0;
				while(true) {
					if (row[j] == null) { row[j] = events.get(x); go=false; break; }
					else if (!(row[j].end >=(events.get(x).start))) {j++; continue; }
					else break;
				}
				i++;
			}
		}
		return timetable;
	}
	
	private SimpleAcalEvent[][] getImprovedTimeTable(List<SimpleAcalEvent> events) {
		Collections.sort(events);
		SimpleAcalEvent[][] timetable = new SimpleAcalEvent[events.size()][events.size()]; //maximum possible
		int maxX = 0;
		for (SimpleAcalEvent seo : events){
			int x = 0; int y = 0;
			while (timetable[y][x] != null) {
				if (seo.overlaps(timetable[y][x])) {
					
					//if our end time is before [y][x]'s, we need to extend[y][x]'s range
					if (seo.end < (timetable[y][x].end)) timetable[y+1][x] = timetable[y][x];
					x++;
				} else {
					y++;
				}
			}
			timetable[y][x] = seo;
			if (x > maxX) maxX = x;
		}
		lastMaxX = maxX;
		return timetable;
	}
	
	private ArrayList<SimpleAcalEvent> getEventList(AcalDateRange range) {
		ArrayList<AcalEvent> eventList = context.getEventsForDays(range);
		ArrayList<SimpleAcalEvent> events = new ArrayList<SimpleAcalEvent>();
		for (AcalEvent e : eventList) {
			e.dtstart.applyLocalTimeZone();
			//only add events that cover at least one full calendar day
			if (e.getDuration().getDurationMillis()/1000 < 86400) continue;	//less than 24 hours, cant go in
			AcalDateTime start = e.getStart().clone();
			int startSec = start.getDaySecond();
			if (startSec != 0) {
				start.setHour(0); start.setMinute(0);start.setSecond(0); start.addDays(1);	//move forward to 00:00:00	
			}
			//start is now at the first 'midnight' of the event. Duration to end MUST be > 24hours for us to want this event
			if(start.getDurationTo(e.getEnd()).getDurationMillis()/1000 <86400) continue;
			events.add(SimpleAcalEvent.getSimpleEvent(e));
		}
		return events;
	}

}
