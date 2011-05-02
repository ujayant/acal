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
	private int headerHeight = 0;
	private SimpleAcalEvent[][] headerTimeTable;
	
	//Constants
	private final int halfHourHeight = WeekViewActivity.HALF_HOUR_HEIGHT;
	private final int hourHeight = halfHourHeight*2;
	private final int secondsPerPixel = 3600/hourHeight;	

	//Drawing vars - easier to set these as class fields than to send them as parmaeters
	//All these vars are used in drawing and are recaculated each time draw() is called and co-ordinates have changed
	private int scrolly;			//y Scroll amount in pixels
	private int scrollx;			//x Scroll amnount in pixels
	private int width;			//The current screen width in pixels
	private int height;			//The current Screen height in pixels
	private int startSecondOfDay; //The first visible second of the day (0-86400)
	private long currentDate; 		//todays date in epoch UTC
	private long startSecondEpoch;	//the UTC epoch time fo the first visible second
	private int numSeconds;		//The number of visible seconds on the screen
	private int hourOffset;			//The offset in pixels from the start of the next hour 
	private int startHour;		//The first partially visible hour of the day
	private int endHour;			//The last partially visible hour of the day
	private int numHours;			//The number of visible hours
	
	//These to vars define the top left drawing position of the main area (excluded header)
	private int x = 0;			
	private int y = 0;
	
	private int size;
	private int lastMaxX=0;
	
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
	
	float getHeaderHeight() {
		return this.headerHeight; 
	}
	
	private void drawFulldayEvents(Canvas canvas, Paint p) {
		int itemHeight = WeekViewActivity.FULLDAY_ITEM_HEIGHT;
	
		//some values important for calculating range etc.
		float numSeconds = (width/WeekViewActivity.DAY_WIDTH)*24F*3600F;
		float pixelsPerSecond = width/numSeconds;
		AcalDateTime startTime = date.clone();
		float numSecondsOffset = context.getScrollX()/pixelsPerSecond;
		startTime.addSeconds((int)(0-(numSecondsOffset)));	//represents 0 hour
		AcalDateTime endTime = startTime.clone();
		endTime.addSeconds((int)(numSeconds));		//represents end hour
		AcalDateRange range = new AcalDateRange(startTime,endTime);
		
		headerTimeTable = getTimeTable(getEventList(range));
		if (headerTimeTable.length <=0) {	this.headerHeight =0; return; }
		size = headerTimeTable.length;
		
		int depth = 0;
		for (int i = 0; i<size && headerTimeTable[i] != null && headerTimeTable[i].length>0 && headerTimeTable[i][0] != null;i++)  depth++;
		this.headerHeight = depth*itemHeight;
		y = this.headerHeight;
		height = height-y;
		float dayWidth = WeekViewActivity.DAY_WIDTH;
		float scrollx = context.getScrollX();
		
		//draw borders around each day
		p.setStyle(Paint.Style.STROKE);
		p.setColor(context.getResources().getColor(R.color.WeekViewMultiDayBorder));
		for (float x = 0+scrollx-dayWidth; (x<width+(dayWidth*2)); x+=dayWidth)
		canvas.drawRect(x, 0, dayWidth+scrollx, headerHeight, p); 
		
		for (int i = 0; i<size;i++)  {
			boolean hasEvent = false;
			for(int j=0;j < headerTimeTable[i].length && headerTimeTable[i][j] != null;j++) {
				SimpleAcalEvent event = headerTimeTable[i][j];
					drawHorizontal(event, canvas, 0, (i*itemHeight), width, itemHeight, scrollx);
					hasEvent=true;
			}
			if (!hasEvent) break;
		}
		
		

		
	}
	
	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		
		//First check that we can draw anything at all
		if (this.getWidth() == 0) return;
		if (this.getHeight() == 0) return;
		if (this.isInEditMode()) return;	//can't draw in edit mode
		
		
		//Vars needed for drawing
		width = this.getWidth();
		height = this.getHeight();
		x = 0;
		y = 0;
		
		Paint p = new Paint();
		
		//need to draw header first. Drawing the header also adjusts the y value and height.
		this.drawFulldayEvents(canvas,p);
		
		
		
		//re-calculate all variables that may change from frame to frame		
		scrolly = context.getScrollY();
		scrollx = context.getScrollX();
		if (y!=0)
		startSecondOfDay = secondsPerPixel*(y+scrolly);
		else
		startSecondOfDay = scrolly;
		currentDate = date.getEpoch();
		startSecondEpoch = currentDate+startSecondOfDay;
		numSeconds = height*secondsPerPixel;  //number of seconds currently visible
		hourOffset = (3600-(startSecondOfDay%3600))/secondsPerPixel;	//offset in pixels for the next hour 
		startHour = startSecondOfDay/3600;
		endHour = startHour + (numSeconds/3600) + 1;
		numHours = numSeconds/3600;

		//main background
		p.setStyle(Paint.Style.FILL);
		p.setColor(Color.parseColor("#AAf0f0f0"));
		canvas.drawRect(x, y, x+width, y+height, p);
		
		drawGrid(canvas,p);
		
		/**float dayX = (int)(0-WeekViewActivity.DAY_WIDTH+context.getScrollX());
		float dayWidth = WeekViewActivity.DAY_WIDTH;
		p = new Paint();

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
		*/
	}
	
	private void drawBackground(Canvas canvas, float x, float y,float w, float h) {
		Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);
		p.setColor(Color.parseColor("#AAf0f0f0"));
		canvas.drawRect(x, y, w, h, p);
	}
	
	public void drawGrid(Canvas canvas, Paint p) {
		p.setStyle(Paint.Style.FILL);
		int dayX = (int)(0-WeekViewActivity.DAY_WIDTH+context.getScrollX());
		AcalDateTime currentDay = context.getCurrentDate().clone();
		currentDay.addDays(-1);
		
		//get the grid for each day
		Bitmap weekDayGrid = context.getImageCache().getWeekDayBox(startHour,numHours,hourOffset);
		Bitmap weekEndGrid = context.getImageCache().getWeekEndDayBox(startHour,numHours,hourOffset);
		while ( dayX <= width) {
			if (currentDay.getWeekDay() == AcalDateTime.SATURDAY || currentDay.getWeekDay() == AcalDateTime.SUNDAY)
				canvas.drawBitmap(weekEndGrid, dayX+x, y, p);
			else 
				canvas.drawBitmap(weekDayGrid, dayX+x, y, p);
			dayX += WeekViewActivity.DAY_WIDTH;
			currentDay.addDays(1);
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
	
	public void drawHorizontal(SimpleAcalEvent event, Canvas c, float x, float y, float width, float height, float scroll) {
		/**if ( height < 1f ) return;
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
					(int)eventWidth, (int)height, maxWidth, (int)height), (int)startX,y, new Paint());*/
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
