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

package com.morphoss.acal.xml;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Node;

public class CalendarTimeZoneNode extends DavNode {

	public static final String TAG = "CalendarTimeZoneNode";
	private String olsonName = "UTC";

	/**
	 * Accepts an XML timezone node and attempts to rip the Olson Name from it.
	 * @param n
	 * @param ns
	 * @param nameSpaces
	 * @param parent
	 */
	public CalendarTimeZoneNode (Node n, String ns, Map<String,String> nameSpaces, DavNode parent) {
		super(n,ns,nameSpaces,parent);
		String text = this.getText();
		Pattern pattern = Pattern.compile("TZID.*:.*((Antarctica|America|Africa|Atlantic|Asia|Australia|Indian|Europe|Pacific|US)/(([^/]+)/)?[^/]+)$");
		Matcher m = pattern.matcher(text);
		if ( m.find()) olsonName = m.group(1);
	}

	public String getTZName() {
		return this.olsonName;
	}
}
