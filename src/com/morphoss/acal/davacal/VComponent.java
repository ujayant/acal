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

package com.morphoss.acal.davacal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ContentValues;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.providers.DavResources;

/**
 * <p>
 * This class represents a VComponent. VComponent objects are created from a DavResources row. Use
 * createComponentFromResource to create an instance of a VComponent child class.
 * </p>
 * <p>
 * This class uses Lazy, non persistent instantiation of its members to reduce memory footprint. If you plan
 * on performing multiple operations with the class it is advised that you setPerstenceOn first. This will
 * force this component and all children to retain their members, and then you can call setPersistenceOff
 * when finished to destroy them all. 
 * </p>
 * 
 * @author Morphoss Ltd
 * 
 */
public abstract class VComponent implements Parcelable {

	public static final String TAG = "aCal VComponent";
	
	public static final String			VCALENDAR			= "VCALENDAR";
	public static final String			VCARD				= "VCARD";
	public static final String			VEVENT				= "VEVENT";
	public static final String			VTODO				= "VTODO";
	public static final String			VJOURNAL			= "VJOURNAL";
	public static final String			VALARM				= "VALARM";
	public static final String			VTIMEZONE			= "VTIMEZONE";

	public final String					name;

	protected AcalCollection			collectionData;
	protected ComponentParts			content;	
	
	protected VComponent 				parent;

	// Patterns used for matching begin:component and end:component lines. Note that we explicitly
	// match the case choices since that is faster than using a case insensitive match
	private static final Pattern myBegin = Pattern.compile("[Bb][Ee][Gg][Ii][Nn]:([A-Za-z]+)\\r?",
				Pattern.MULTILINE  | Pattern.UNIX_LINES); 
	private static final Pattern myEnd = Pattern.compile("[Ee][Nn][Dd]:([A-Za-z]+)\\r?",
				Pattern.MULTILINE | Pattern.UNIX_LINES);

	
	// These members MUST remain private - if you must access them elsewhere create appropriate
	// getters. To maintain consistency they should not be changed by external or child classes. 
	private List<VComponent> children = null;
	protected boolean childrenSet = false;
	private Map<String, AcalProperty> properties;
	protected boolean propertiesSet = false;
	private int persistenceCount = 0;
	public final Integer resourceId;
	
	//Constructors and factory methods
	/**
	 * Constructor for child classes. Creates the VComponent and all its children from supplied ComponentParts
	 * and data.
	 * 
	 * @param splitter
	 * @param resourceRow
	 * @param collectionObject
	 */
	protected VComponent(ComponentParts splitter, int resId, AcalCollection collectionObject, VComponent parent) {
		this.resourceId = resId;
		this.collectionData = collectionObject;
		this.content = splitter;
		this.name = splitter.thisComponent;
		this.parent = parent;
	}

	protected VComponent(String typeName, VComponent parent) {
		this.name = typeName;
		this.parent = parent;
		this.collectionData = parent.collectionData;
		this.content = null;
		this.children = new ArrayList<VComponent>();
		this.properties = new HashMap<String,AcalProperty>();
		this.childrenSet = true;
		this.propertiesSet = true;
		this.resourceId = null;
	}

	protected VComponent(String typeName, AcalCollection collectionObject, VComponent parent) {
		this.collectionData = collectionObject;
		this.name = typeName;
		this.parent = parent;
		this.content = null;
		this.children = new ArrayList<VComponent>();
		this.properties = new HashMap<String,AcalProperty>();
		this.childrenSet = true;
		this.propertiesSet = true;
		this.resourceId = null;
	}

	
	public synchronized static VComponent createComponentFromResource(ContentValues resourceRow, AcalCollection collectionObject) throws VComponentCreationException {
		String blob = resourceRow.getAsString(DavResources.RESOURCE_DATA);
		int rid = resourceRow.getAsInteger(DavResources._ID);
		return createComponentFromBlob(blob,rid,collectionObject);
	}

	
	public synchronized static VComponent createComponentFromBlob(String blob, Integer resourceId, AcalCollection collectionObject) {
		
		// Remove all line spacing
		// Very probably we should do this when we write it into the local database.
		Matcher m = Constants.rfc5545UnWrapper.matcher(blob);
		blob = m.replaceAll("");

		ComponentParts splitter = new ComponentParts(blob);
		if ( splitter.thisComponent.equals(VCALENDAR) )
			return new VCalendar(splitter, resourceId, null,null, collectionObject, null);
		else if (splitter.thisComponent.equals(VCARD))
			return new VCard(splitter,resourceId, collectionObject,null);
		else if (splitter.thisComponent.equals(VEVENT))
			return new VEvent(splitter,resourceId, collectionObject,null);
		else if (splitter.thisComponent.equals(VTODO))
			return new VTodo(splitter,resourceId, collectionObject,null);
		else if (splitter.thisComponent.equals(VALARM))
			return new VAlarm(splitter,resourceId, collectionObject,null);
		else if (splitter.thisComponent.equals(VTIMEZONE))
			return new VTimezone(splitter,resourceId, collectionObject,null);
		else if (splitter.thisComponent.equals(VJOURNAL))
			return new VJournal(splitter,resourceId, collectionObject,null);
		else
			return new VGenericComponent(splitter,resourceId, collectionObject,null);
	}
	
	/************************************
	 * 			Public Methods			*
	 ************************************/
	 
	
	public synchronized int getResourceId() {
		try {
			// When we are adding events this could be null
			return this.resourceId;
		}
		catch( NullPointerException e ) {};
		return -1;
	}
	
	public synchronized int getCollectionId() {
		return this.collectionData.collectionId;
	}

	public synchronized void setCollection(AcalCollection newCollection) {
		if ( newCollection != null )
			this.collectionData = newCollection;
	}

	public synchronized int size() {
		if ( content != null ) return content.partInfo.size();
		populateChildren();
		int answer = children.size();
		destroyChildren();
		return answer;
	}

	
	public synchronized List<VComponent> getChildren() {
		this.populateChildren();
		if ( persistenceCount == 0 ) {
			List<VComponent> ret = Collections.unmodifiableList(this.children);
			if (this.persistenceCount == 0) this.destroyChildren();
			return ret;
		}
		persistenceCount++;
		return children;
	}

	
	public synchronized VComponent getTopParent() {
		VComponent cur = this;
		while (cur.parent != null) cur=cur.parent;
		return cur;
	}
	
	/**
	 * Explodes all data in this node and child nodes. Uses lots of memory, so be sure to call
	 * Implode when done.
	 */
	
	public synchronized void setPersistentOn() throws YouMustSurroundThisMethodInTryCatchOrIllEatYouException {
		this.persistenceCount++;
	}
	
	public synchronized void setPersistentOff() {
		this.persistenceCount--;
		if (persistenceCount == 0) {
			this.destroyChildren();
			this.destroyProperties();
		} else if (this.persistenceCount < 0) throw new IllegalStateException("Persistence Count below 0 - NOT ALLOWED!");
	}
	
	public synchronized void setEditable() {
		if ( persistenceCount < 1000 ) {
			this.persistenceCount = 100000;
			populateRecursively();
		}
	}

	/**
	 * Returns the name of this component type, such as "VCALENDAR", "VCARD", etc.
	 * @return
	 */
	public synchronized String getName() {
		return name;
	}

	public synchronized String getOriginalBlob() {
		if ( content == null ) content = new ComponentParts(buildContent());
		return content.componentString;
	}

	/**
	 * This can be useful if you want the value of a unique property.  Otherwise, not so much.
	 * @param name
	 * @return
	 */
	public synchronized AcalProperty getProperty(String name) {
		if (name == null) return null;
		this.populateProperties();
		if ( properties == null ) return null;
		AcalProperty ret = properties.get(name.toUpperCase());
		if (this.persistenceCount == 0) destroyProperties();
		return ret;
	}

	/**
	 * This can be useful if you know you don't have two properties of the same name.
	 * @return
	 */
	public synchronized Map<String,AcalProperty> getProperties() {
		this.populateProperties();
		Map<String,AcalProperty>ret = Collections.unmodifiableMap(properties);
		if (this.persistenceCount == 0) this.destroyProperties();
		return ret;
	}

	/**
	 * If you have multiple properties with the same name, you'll need this one...
	 * @return an array of AcalProperty which are all of the properties of the component
	 */
	public synchronized AcalProperty[] getAllProperties() {
		AcalProperty[] ret = new AcalProperty[content.propertyLines.length];
		for( int i=0; i < content.propertyLines.length; i++ ) {
			ret[i] = AcalProperty.fromString(content.propertyLines[i]);
		}
		return ret;
	}
	
	public synchronized  boolean containsProperty(AcalProperty property) {
		this.populateProperties();
		Set<String> propKeys = properties.keySet();
		for (String key  : propKeys) {
			if (properties.get(key).equals(property)) {
				if (this.persistenceCount == 0) this.destroyProperties();
				return true;
			}
		}
		if (this.persistenceCount == 0) this.destroyProperties();
		return false;
	}

	
	public synchronized boolean containsPropertyKey(String name) {
		if (name == null) return false;
		if ( !propertiesSet ) {
			for( int i=0; i < content.propertyLines.length; i++ ) {
				if (content.propertyLines[i].length() >= name.length()
							&& content.propertyLines[i].substring(0, name.length()).equalsIgnoreCase(name))
					return true;
			}
			return false;
		}
		return this.properties.containsKey(name.toUpperCase());
	}

	
	public synchronized int getCollectionColour() {
		try {
			// When we are adding events we might not have a collection yet
			return this.collectionData.getColour();
		}
		catch( NullPointerException e ) {};
		return 0; // black, or so we believe :-)
	}

	
	public boolean getAlarmEnabled() {
		try {
			// When we are adding events we might not have a collection yet
			return this.collectionData.alarmsEnabled;
		}
		catch( NullPointerException e ) {};
		return true;
	}
	
	
	/****************************************
	 * 			Protected Methods			*
	 ****************************************/
	protected synchronized boolean isPersistenceOn() {
		return this.persistenceCount > 0;
	}
	
	private synchronized String buildContent() {
		StringBuilder contentString = new StringBuilder("BEGIN:");
		try {
			this.setPersistentOn();
			this.populateProperties();
			this.populateChildren();
			contentString.append(name);
			contentString.append(Constants.CRLF);
			for( String p : properties.keySet() ) {
				contentString.append(properties.get(p).toRfcString());
				contentString.append(Constants.CRLF);
			}
			for( VComponent child : children ) {
				contentString.append(child.buildContent());
			}
			contentString.append("END:");
			contentString.append(name);
			contentString.append(Constants.CRLF);
			this.setPersistentOff();
		} catch (Exception e) {
			Log.e(TAG,"ContentBuilder threw error when creating blob: "+e);
			Log.e(TAG, Log.getStackTraceString(e));
			return "";
		}
			if (!this.isPersistenceOn()) {
				this.destroyChildren();
				this.destroyProperties();
		}
		return contentString.toString(); 
	}

	
	/**
	 * Will always return a string, in response to a request for the value of a property. That
	 * will be the empty string if the property does not exist, or the like. 
	 * @param propertyName
	 * @return A string which is the value of the property, or empty, if the property is not set.
	 */
	public String safePropertyValue( String propertyName ) {
		String propertyValue = null;
		try {
			propertyValue = this.getProperty(propertyName).getValue();
		}
		catch (Exception e) {
		}
		if (propertyValue == null) propertyValue = "";
		return propertyValue;
	}

	
	/**
	 * Populates the array of children
	 */
	protected synchronized void populateChildren() {
		if (this.childrenSet) return;
		this.children = new ArrayList<VComponent>(this.content.partInfo.size());
		for (PartInfo childInfo : this.content.partInfo) {
			ComponentParts childSplitter = new ComponentParts( childInfo.getComponent(content.componentString));
			if ( childInfo.type.equals(VEVENT))
				this.children.add(new VEvent(childSplitter, this.resourceId, collectionData,this));
			else if (childInfo.type.equals(VALARM))
				this.children.add(new VAlarm(childSplitter, this.resourceId, collectionData,this));
			else if (childInfo.type.equals(VTODO))
				this.children.add(new VTodo(childSplitter,this.resourceId,collectionData,this));
			else
				this.children.add(new VGenericComponent(childSplitter,this.resourceId,collectionData,this));
		}
		this.childrenSet = true;
	}

	protected synchronized void destroyChildren() {
		if ( this.persistenceCount > 0 ) return;
		if ( content == null ) content = new ComponentParts(buildContent());
		this.children = null;
		this.childrenSet = false;
	}

	protected synchronized void populateProperties() {
		if (propertiesSet) return;
		properties = new HashMap<String,AcalProperty>(content.propertyLines.length);
		if (properties == null) {
			Log.e(TAG, "Somehow an object that was just instatiated is null????");
		}
		for( int i=0; i < content.propertyLines.length; i++ ) {
			AcalProperty p = AcalProperty.fromString(content.propertyLines[i]);
			try {
				properties.put(p.getName(), p);
			}
			catch ( Exception e ) {
				Log.i(TAG,Log.getStackTraceString(e));
			}
		}
		this.propertiesSet = true;
		return;
	}
	
	
	
	protected synchronized void destroyProperties() {
		if ( content == null ) content = new ComponentParts(buildContent());
		this.propertiesSet = false;
		properties = null;
	}


	protected synchronized void populateRecursively() {
		populateProperties();
		populateChildren();
		for( VComponent child : children ) {
			child.populateRecursively();
		}
	}

	
	/**
	 * <p>
	 * This class holds the type of a component (in uppercase) along with start & end positions within
	 * a mysterious string.  This really isn't going to work without the string, of course, which is
	 * held in the ComponentParts class.
	 * </p>
	 * 
	 * @author Morphoss Ltd
	 *
	 */
	static class PartInfo {
		public final String type;
		public final int begin;
		public final int end;
		
		public PartInfo( final String typeName, final int startPos, final int endPos ) {
			type = typeName.toUpperCase();
			begin = startPos;
			end = endPos;
		}
		
		public String getComponent( final String blob ) {
			if ( begin < 0 || end > blob.length() || end < begin ) {
				throw new IllegalArgumentException("(0 <= begin <= end <= string length) must be true!");
			}
			return blob.substring(begin,end);
		}
	}

	/**
	 * <p>
	 * Splits the component up into an array of properties (Strings) and an array of componentinfo, which is the
	 * name of the sub-component and the string offsets into the original componentString.
	 * </p>
	 * 
	 * @author Morphoss Ltd
	 *
	 */
	protected static class ComponentParts {
		public final String[] propertyLines;
		public final List<PartInfo> partInfo;
		public final String componentString;
		public final String thisComponent;
		
		ComponentParts( final String blob ) {
			this.componentString = blob;
			Matcher begin = myBegin.matcher(blob);
			Matcher end = myEnd.matcher(blob);
			partInfo = new ArrayList<PartInfo>();
			int pos = 0;
			int endPos = 0;
			int thisFound = 0;
			int loopLimiter = 0;
			String props = "";
			String componentName = "";
			if( begin.find(pos) ) {
				thisComponent = begin.group(1);
				pos = begin.end()+1;
				endPos = pos;
				if( !begin.find(pos) && end.find(pos) ) {
					props += blob.substring(pos, end.start() - 1);
				}
			}
			else {
				thisComponent = "";
			}
			while( begin.find(pos) && loopLimiter++ < 10000 ) {
				thisFound = begin.start();
				if ( thisFound > pos ) {
					props += blob.substring(pos, thisFound - 1);
					pos = thisFound;
				}
				componentName = begin.group(1);
				endPos = pos;
				while( end.find(endPos) && loopLimiter++ < 10000 ) {
					if ( end.group(1).equals(componentName) ) {
						PartInfo info = new PartInfo(componentName, pos, end.end()); 
						partInfo.add( info );
						pos = end.end()+1;
						break;
					}
					endPos = end.end()+1;
				}
			}
			propertyLines = Constants.lineSplitter.split(props);
		}
	}
	
	public String getCurrentBlob() {
		// We have to assume the caller already did the necessary setPersistent() & populate things.
		return buildContent();
	}

	public boolean addChild(VComponent child) {
		if (!childrenSet) throw new IllegalStateException("Children must be set with persistence on to add child to vcomp");
		return this.children.add(child);
	}
	
	public synchronized boolean removeChild(VComponent child) {
		if (!childrenSet) throw new IllegalStateException("Children must be set with persistence on to rem child to vcomp");
		return this.children.remove(child);
	}
	
	public AcalProperty addProperty(AcalProperty property) {
		if (!propertiesSet) throw new IllegalStateException("Properties must be set with persistence on to add prop to vcomp");
		return this.properties.put(property.getName(), property);
	}
	
	public AcalProperty removeProperty(String name) {
		if (!propertiesSet) throw new IllegalStateException("Properties must be set with persistence on to rem prop to vcomp");
		return this.properties.remove(name);
	}

	public AcalProperty setUniqueProperty(AcalProperty property) {
		if (!propertiesSet) throw new IllegalStateException("Properties must be set with persistence on to add prop to vcomp");
		this.properties.remove(property.getName());
		return this.properties.put(property.getName(), property);
	}
	
	public void removeProperties( String[] names ) {
		if (!propertiesSet) throw new IllegalStateException("Properties must be set with persistence on to rem prop to vcomp");
		for ( String n : names ) {
			properties.remove(n);
		}
	}


	public static VComponent fromDatabase(Context context, int resourceId) throws VComponentCreationException {
		ContentValues res = DavResources.getRow(resourceId, context.getContentResolver());
		AcalCollection collection = AcalCollection.fromDatabase(context, res.getAsInteger(DavResources.COLLECTION_ID));
		return createComponentFromResource(res, collection);
	}

	public VComponent(Parcel in) {
		this.resourceId = in.readInt();
		this.name = in.readString();
		String original = in.readString();
		ComponentParts origParts = null;
		if ( original != null ) origParts = new ComponentParts(original);
		content = new ComponentParts(in.readString());
		setEditable();
		content = origParts;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(resourceId);
		dest.writeString(name);
		dest.writeString(content == null ? null : content.componentString);
		dest.writeString(getCurrentBlob());
	}

	@Override
	public int describeContents() {
		// @todo Auto-generated method stub
		return 0;
	}

}
