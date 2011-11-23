/*
 * Copyright (C) 2011 Morphoss Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


public final class AcalDebug {

	public static void heapDebug(String TAG,String msg) {
		Runtime r = Runtime.getRuntime();
		r.gc();
		try {
			Thread.sleep(100);
		}
		catch ( InterruptedException e ) { }
		long used = r.totalMemory() - r.freeMemory();
		double percentUsed = ((double) used) / ((double) r.maxMemory()) * 100.0;
		used /= 1024;
		int logLevel = ( percentUsed > 80 ? Log.ERROR : (percentUsed > 50 ? Log.WARN : Log.INFO) );
		Log.println(logLevel, TAG, String.format("%-40.40s: Heap used: %dk (%.2f%%) of max: %dk", msg, used, percentUsed, r.maxMemory()/1024));
	}

	
	static Set<?> lastSeen = new HashSet<Object>();
	static Set<?> seen = new HashSet<Object>();
	public static String dump( Object o, int maxDepth ) {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			return gson.toJson(o);
		}
		catch( Exception e ) {
			return "Unable to convert object to JSON";
		}
	}

	public static String oldDump( Object o, int maxDepth ) {
		if ( maxDepth < 0 ) return "";
		StringBuffer buffer = new StringBuffer();
		Class<?> oClass = o.getClass();
		if ( oClass.isArray() ) {
			buffer.append("[\n");
			for (int i = 0; i < Array.getLength(o); i++) {
				if ( i < 0 ) buffer.append(",");
				Object value = Array.get(o, i);
				try {
					buffer.append(value.getClass().isArray() ? dump(value, maxDepth - 1) : value);
				}
				catch ( Exception e ) {
					buffer.append("Exception: " + e.getMessage());
				}
				buffer.append("\n");
			}
			buffer.append("]\n");
		}
		else {
			buffer.append("{\n");
			while ( oClass != null ) {
				Field[] fields = oClass.getDeclaredFields();
				for (int i = 0; i < fields.length; i++) {
					if ( buffer.length() < 1 ) buffer.append(",");
					fields[i].setAccessible(true);
					buffer.append(fields[i].getName());
					buffer.append("=");
					try {
						Object value = fields[i].get(o);
						if ( value != null ) {
							buffer.append(value.getClass().isArray() ? dump(value, maxDepth - 1) : value);
						}
					}
					catch ( Exception e ) {
						buffer.append("Exception: " + e.getMessage());
					}
					buffer.append("\n");
				}
				oClass = oClass.getSuperclass();
			}
			buffer.append("}\n");
		}
		return buffer.toString();
	}
}
