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

package com.morphoss.acal.service;

import java.util.ArrayList;
import java.util.Scanner;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class MyXMLHandler extends DefaultHandler {

	private boolean currentElement = false;
	//private String currentValue = null;
	private ArrayList<String> tags = new ArrayList<String>();
	
	public void startElement(String uri, String localName, String qname, Attributes attributes) throws SAXException {
		if (localName.equals("calendar-data")) {
			currentElement = true;
			tags.add("New Event:");
		}
		//this.currentValue = "TAG: "+localName+", QNAME: "+qname+"\n";
		//for (int i = 0; i< attributes.getLength(); i++) {
//			this.currentValue+= "\t"+attributes.getLocalName(i)+":"+attributes.getValue(i)+"\n";
	//	}
		//this.tags.add(currentValue);
		//currentValue = null;
	}
	public void endElement(String uri, String localName, String qName) throws SAXException {
		if (localName.equals("calendar-data")) currentElement = false;
	}
	public void characters(char[] ch, int start, int length) throws SAXException {
		if (currentElement) {
			
			String content = "";
			for (int i = start; i < (start+length); i++) {
			content += ch[i];
		}
		Scanner sc = new Scanner(content);
		//Pattern f = Pattern.compile("(.+?)([!=]=)(\\[(.*?),(.*?)\\]|([^\\]].+))");
	 	//Pattern intervalPattern = Pattern.compile("\\[(.*?),(.*?)\\]");
	 	
	 	while (sc.hasNext()) {
	 		String token = sc.nextLine();
	 		/**Matcher expressionMatcher = f.matcher(token);
	 		if (expressionMatcher.matches()) {
	 			if (expressionMatcher.groupCount() == 5) {
	 				// time-range 
	 			} else if (expressionMatcher.groupCount() == 3) {
	 				// simple matcher
	 			} else {
	 				tags.add("Bad Query syntax:" + token);
	 			}


	 			String z = expressionMatcher.group(0);
	 			tags.add("expression= "+z+"\n" +
	 					"\tfield: "+expressionMatcher.group(1)+"\n"+
	 					"\taction: "+expressionMatcher.group(2)+"\n"+
	 					"\tvalue: "+expressionMatcher.group(3)+"\n"+
	 					"v4: "+expressionMatcher.group(4)+"\n"+
	 					"v5: "+expressionMatcher.group(5));
	 		}
	 	}
		}*/


	 			String[] vals = token.split(":");
			if (vals.length > 1) {
				String left[] = vals[0].split(";");
				if (left.length > 0) vals[0] = left[0];
				if (vals[0].equalsIgnoreCase("DTSTART") || 
					vals[0].equalsIgnoreCase("DTEND") ||
					vals[0].equalsIgnoreCase("UID") ||
					vals[0].equalsIgnoreCase("SUMMARY"))
						tags.add("\t"+vals[0]+" = "+vals[1]);
				}
			}
		}
	 		}
	 	
	
	
	public ArrayList<String> getTags() {
		return this.tags;
	}
}
