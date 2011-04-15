package com.morphoss.acal.weekview;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.widget.TextView;

import com.morphoss.acal.R;

public class WeekViewImageCache {
	
	private float dayWidth;
	private float halfHeight;
	private Context c;
	
	private Bitmap sidebar;
	private int sidebarWidth = -1;
	
	private Bitmap daybox;
	
	private HashMap<Long,Bitmap> eventMap = new HashMap<Long, Bitmap>();
	private Queue<Long> eventQueue = new LinkedList<Long>();
	
	public WeekViewImageCache(Context c, float dayWidth, float halfHeight) {
		this.c=c;
		this.dayWidth=dayWidth;
		this.halfHeight=halfHeight;
	}
	public void cacheDayBoxes() {
		if (daybox != null) return;
		
		//First do regular box
		Bitmap returnedBitmap = Bitmap.createBitmap((int)dayWidth, (int)(halfHeight*2),Bitmap.Config.ARGB_4444);
		Canvas canvas = new Canvas(returnedBitmap);
		float hourHeight = halfHeight*2;
		Paint p = new Paint();
		p.setStyle(Paint.Style.STROKE);
		p.setColor(c.getResources().getColor(R.color.WeekViewDayGridBorder));
		canvas.drawRect(0,0,dayWidth,hourHeight,p);
		
		p.setStyle(Paint.Style.STROKE);
		p.setColor(c.getResources().getColor(R.color.WeekViewDayGridBorder));
		//draw dotted center line
		DashPathEffect dashes = new DashPathEffect(WeekViewActivity.DASHED_LINE_PARAMS,0);
		p.setPathEffect(dashes);
		canvas.drawLine(0,halfHeight, dayWidth, halfHeight, p);
		this.daybox = returnedBitmap;
	}
	
	public Bitmap getWeekDayBox(int startHour, int endHour, float offset) {
		cacheDayBoxes();
		int numHours = endHour - startHour;
		Bitmap returnedBitmap = Bitmap.createBitmap((int)dayWidth, (int)((halfHeight*2)*numHours),Bitmap.Config.ARGB_4444);
		Canvas canvas = new Canvas(returnedBitmap);
		Paint p = new Paint();
		
		// add yellow for work day
		float sizeofHour = halfHeight*2;
		float wdStart = WeekViewActivity.START_HOUR;
		if (WeekViewActivity.START_MINUTE != 0)wdStart+= (WeekViewActivity.START_MINUTE/60F);
		float wdEnd = WeekViewActivity.END_HOUR;
		if (WeekViewActivity.END_MINUTE != 0)wdEnd+= (WeekViewActivity.END_MINUTE/60F);
		float startHr = startHour-(offset/sizeofHour);
		float endHr = endHour-(offset/sizeofHour);
		if (wdStart < endHr && wdEnd > startHr) {
			p.setStyle(Paint.Style.FILL);
			p.setColor(c.getResources().getColor(R.color.WeekViewDayGridWorkTimeBG));
			float y = Math.max(wdStart-startHr,0)*sizeofHour;
			float height = Math.min(wdEnd-startHr,endHr);
			height = height*sizeofHour;
			height = Math.max(height,0);
			canvas.drawRect(0, y, dayWidth,y+height , p);
		}
		
		for (int curHour = startHour-1; curHour<=endHour; curHour++) {
			float curY = (halfHeight*2)*(curHour-startHour);
			canvas.drawBitmap(daybox, 0, curY+offset, p);
		}
		
		return returnedBitmap;
	}
	public Bitmap getWeekEndDayBox(int startHour, int endHour, float offset) {
		cacheDayBoxes();
		int numHours = endHour - startHour;
		Bitmap returnedBitmap = Bitmap.createBitmap((int)dayWidth, (int)((halfHeight*2)*numHours),Bitmap.Config.ARGB_4444);
		Canvas canvas = new Canvas(returnedBitmap);
		Paint p = new Paint();
		for (int curHour = startHour-1; curHour<endHour; curHour++) {
			float curY = (halfHeight*2)*(curHour-startHour);
			canvas.drawBitmap(daybox, 0, curY+offset, p);
		}
		return returnedBitmap;
	}
	
	public Bitmap getSideBar(int width) {
		if (width == sidebarWidth) return sidebar;
		float y = 0;
		boolean half = false;
		boolean byHalves = (halfHeight > WeekViewActivity.PIXELS_PER_TEXT_ROW);
		float rowHeight = halfHeight * (byHalves?1f:2f);
		if ( !byHalves ) y -= ((5f * halfHeight) / 3f);
		int hour = 0;
		Bitmap master = Bitmap.createBitmap((int)width, (int)halfHeight*50,Bitmap.Config.ARGB_4444);
		Canvas masterCanvas = new Canvas(master);
		String am = c.getString(R.string.oneCharMorning);
		String pm = c.getString(R.string.oneCharAfternoon);
		while ( hour<=24 ) {
			LayoutInflater inflater = (LayoutInflater) c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View v = (View) inflater.inflate(R.layout.week_view_assets, null);
			TextView box;
			if ( !half ) box= ((TextView) v.findViewById(R.id.WV_side_box));
			else box= ((TextView) v.findViewById(R.id.WV_side_box_half));
			box.setVisibility(View.VISIBLE);
			String text = "";
			
			if (WeekViewActivity.TIME_24_HOUR) {
				if (half) text=":30";
				else text = hour+"";
			} else {
				if (half) text=":30";
				else if (hour == 0)text="12 "+am;
				else {
					int hd = hour;
					if (hour >= 13) hd-=12; 
					text=(int)hd+" "+(hour<12?am:pm);
				}
			}

			y += rowHeight;
			box.setText(text);
			box.setTextSize(TypedValue.COMPLEX_UNIT_SP, WeekViewActivity.TEXT_SIZE_SIDE);
			box.measure(MeasureSpec.makeMeasureSpec((int) width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) rowHeight, MeasureSpec.EXACTLY));
			box.layout(0,0, (int)width, (int)rowHeight);
			Bitmap returnedBitmap = Bitmap.createBitmap((int)width, (int)rowHeight,Bitmap.Config.ARGB_4444);
			Canvas tempCanvas = new Canvas(returnedBitmap);
			box.draw(tempCanvas);
			masterCanvas.drawBitmap(returnedBitmap, 0, y, new Paint());

			if ( byHalves ) {
				half = !half;
				if ( !half ) hour++;
			}
			else
				hour++;
		}
		sidebar = master;
		sidebarWidth=width;
		return sidebar;
	}
	
	public Bitmap getEventBitmap(long resourceId, String summary, int colour,
							int width, int height, int maxWidth, int maxHeight) {
		long hash = getEventHash(resourceId,maxWidth,maxHeight);
		if (eventMap.containsKey(hash)) {
			eventQueue.remove(hash);
			eventQueue.offer(hash);	//re prioritise
			Bitmap base = eventMap.get(hash);
			if (base.getHeight() < height) height = base.getHeight();
			return Bitmap.createBitmap(base, 0, 0, width, height, null, false);
		}
		if (eventMap.size() > 100) eventQueue.poll(); //make space
		//now construct the Bitmap
		if ( height > maxHeight ) maxHeight = height;
		if ( width > maxWidth ) maxWidth = width;
		LayoutInflater inflater = (LayoutInflater) c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = (View) inflater.inflate(R.layout.week_view_assets, null);
		TextView title = ((TextView) v.findViewById(R.id.WV_event_box));
		title.setTextSize(TypedValue.COMPLEX_UNIT_SP, WeekViewActivity.TEXT_SIZE_EVENT);
		title.setBackgroundColor((colour&0x00ffffff)|0xA0000000); //add some transparancy
		title.setVisibility(View.VISIBLE);
		title.setText(summary);
		title.measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY));
		title.layout(0, 0, maxWidth, maxHeight);
		Bitmap returnedBitmap = Bitmap.createBitmap(maxWidth, maxHeight, Bitmap.Config.ARGB_8888);
		Canvas tempCanvas = new Canvas(returnedBitmap);
		title.draw(tempCanvas);
		//draw a border
		Paint p = new Paint();
		p.setStyle(Paint.Style.STROKE);
		p.setColor(colour|0xff000000);
		for (int i = 0; i<WeekViewActivity.EVENT_BORDER; i++) {
			tempCanvas.drawRect(i, i, maxWidth-i, maxHeight-i, p);
		}
		eventMap.put(hash, returnedBitmap);
		eventQueue.offer(hash);
		return Bitmap.createBitmap(returnedBitmap, 0, 0, width, height, null, false);
	}
	public long getEventHash(long resourceId, int width, int height) {
		return (long)width + ((long)dayWidth * (long)resourceId);
	}
}