/*
 * Copyright (C) 2011 Morphoss Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.morphoss.acal;

import java.util.regex.Pattern;

import android.os.Environment;

/**
 * Constants class for keeping Global constant values.
 * 
 * @author Morphoss Ltd
 *
 */
public class Constants {

	public static final String PUBLIC_DATA_DIR = Environment.getExternalStorageDirectory()+"/acal/";
	public static final String COPY_DB_TARGET = PUBLIC_DATA_DIR+"acal.db"; //File path and name of copy
	public static final long MAXIMUM_SERVICE_WORKER_DELAY_MS = 1000*60*60*24;	//maximum time between worker thread runs in ms
	public static final long SERVICE_WORKER_GRACE_PERIOD = 1000*60*60*1;		//Amount of time we will allow worker to be 'late' before assuming its hung

	/** Generally useful patterns */
	public static final Pattern lineSplitter = Pattern.compile("\r?\n"); 
	public static final Pattern rfc5545UnWrapper = Pattern.compile("\r?\n ");
	public static final Pattern tzOlsonExtractor = Pattern.compile(".*((?:Antarctica|America|Africa|Atlantic|Asia|Australia|Indian|Europe|Pacific|US)/(?:(?:[^/\"]+)/)?[^/\"]+)\"?");
	public final static Pattern splitOnCommas = Pattern.compile(",");
	
	/** How much stuff to spit out into the logs */
	public static final boolean LOG_VERBOSE = false;		//Very verbose play by play execution information
	public static final boolean LOG_DEBUG = false;			//Information relevant to debugging tasks.
	public static final boolean DEBUG_SETTINGS = false;		// Does the debugging menu appear in Settings

	/** And sometimes we want to really deeply debug specific bits */
	public static final boolean	debugRepeatRule					= false;
	public static final boolean	debugCalendar					= false;
	public static final boolean	debugSyncCollectionContents		= false;
	public static final boolean	debugCalendarDataService		= false;
	public static final boolean	debugMonthView					= false;
	public static final boolean	debugVComponent					= false;
	public static final boolean	debugDateTime					= false;
	public static final boolean	debugDavCommunication			= false;
	public static final boolean	debugAlarms						= false;
	
	public static final long DEFAULT_MAX_AGE_WIFI = 1000*60*30;		// The default to use when initialising a new collection
	public static final long DEFAULT_MAX_AGE_3G = 1000*60*60*2;		// The default to use when initialising a new collection
	
	public static final String	NS_DAV							= "DAV:";
	public static final String	NS_CALDAV						= "urn:ietf:params:xml:ns:caldav";
	public static final String	NS_CARDDAV						= "urn:ietf:params:xml:ns:carddav";
	public static final String	NS_ACAL							= "urn:com:morphoss:acal";
	public static final String	NS_CALENDARSERVER				= "http://calendarserver.org/ns/";
	public static final String	NS_ACALCONFIG					= "urn:com:morphoss:acalconfig";

	public static final String	CRLF	= "\r\n";
	
	public static final String URLEncoding = "utf-8";
}
