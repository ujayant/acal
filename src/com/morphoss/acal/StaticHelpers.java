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


public final class StaticHelpers {

	
	public static String[] mergeArrays(String[] first, String[] second) {
		String[] result = new String[first.length+second.length];
		for (int i = 0; i < first.length; i++) result[i] = first[i];
		for (int i = 0; i < second.length; i++) result[first.length+i] = second[i];
		return result;
	}


	/**
	 * A helper to reliably turn a string into an int, returning 0 on any failure condition. 
	 * @param intThing
	 * @return
	 */
	public static int safeToInt( String intThing ) {
		if ( intThing == null ) return 0;
		try {
			int ret = Integer.parseInt(intThing);
			return ret;
		}
		catch ( Exception e ){
			return 0;
		}
	}

	public static String randomColorString() {
		String ret = "#";
		int colours[] = new int[3]; 
		int startpos = (int) (Math.random() * 3);
		colours[startpos] = (int) (Math.random() * 5.0) + 3;
		if ( ++startpos > 2 ) startpos -= 2;
		colours[startpos] = (int) (Math.random() * 9.0) + 3;
		if ( ++startpos > 2 ) startpos -= 2;
		colours[startpos] = (int) (Math.random() * 7.0) + 3;
		
		if ( Math.random() < 0.5 ) {
			int tmp = colours[1];
			colours[1] = colours[2];
			colours[2] = tmp;
		}

		if ( Math.random() < 0.5 ) {
			int tmp = colours[0];
			colours[0] = colours[2];
			colours[2] = tmp;
		}

		if ( Math.random() < 0.5 ) {
			int tmp = colours[0];
			colours[0] = colours[1];
			colours[1] = tmp;
		}

		for (int i = 0; i<3; i++) {
			ret += Integer.toHexString(colours[i]) + Integer.toHexString(colours[i]);
		}
		return ret;
	}
	
}
