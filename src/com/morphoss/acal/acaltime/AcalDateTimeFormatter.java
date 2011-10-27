package com.morphoss.acal.acaltime;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.morphoss.acal.StaticHelpers;

public class AcalDateTimeFormatter {

	public static DateFormat longDate = DateFormat.getDateInstance(DateFormat.LONG);
	public static DateFormat timeAmPm = new SimpleDateFormat("hh:mmaa");
	public static DateFormat time24Hr = new SimpleDateFormat("HH:mm");
	public static DateFormat longDateTime = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT);
	
	public static String fmtFull( AcalDateTime dateTime, boolean prefer24hourFormat) {
		if ( dateTime == null ) return "- - - - - -";

		Date javaDate = dateTime.toJavaDate();
		StringBuilder b = new StringBuilder(StaticHelpers.capitaliseWords(longDate.format(javaDate)));
		b.append(' ');
		b.append((prefer24hourFormat?time24Hr:timeAmPm).format(javaDate));
		if ( !dateTime.isFloating() ) {
			if ( TimeZone.getDefault().getID().equalsIgnoreCase( dateTime.getTimeZoneName() ) ) {
				b.append(' ');
				b.append(dateTime.getTimeZoneName());
			}
		}
		return b.toString();
	}

}
