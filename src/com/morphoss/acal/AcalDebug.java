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

import android.util.Log;


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
}
