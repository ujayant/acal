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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.morphoss.acal.Constants;

public class DomDavNode implements DavNode {

	private final static Pattern splitNsTag = Pattern.compile("^(.*):([^:]+)$"); 
	private String tagName;
	private HashMap<String,String> attributes;;
	private String text="";
	private String nameSpace;
	private ArrayList<DavNode> children;
	private Map<String,String> nameSpaces;
	private DavNode parent;

	public DomDavNode() {
		this.children = new ArrayList<DavNode>();
		this.tagName = "ROOT";
		this.nameSpace = null;
		this.attributes = new HashMap<String,String>();
		this.parent = null;
	}

	public DomDavNode(Node n, String ns, Map<String,String> nameSpaces, DomDavNode parent) {
		this.parent = parent;
		this.nameSpaces = nameSpaces;
		this.nameSpace = ns;
		this.tagName = n.getNodeName();
		this.children = new ArrayList<DavNode>();
		
		//Check for name space modifier in tagname
		Matcher m = splitNsTag.matcher(tagName);
		if ( m.matches() ) {
			tagName = m.group(2); 
			nameSpace = nameSpaces.get(m.group(1));
		}

		attributes = new HashMap<String,String>();
		NodeList nl = n.getChildNodes();
		for (int i = 0; i<nl.getLength(); i++) {
			Node item = nl.item(i);
			if (item.getNodeType() == Node.TEXT_NODE || item.getNodeType() == Node.CDATA_SECTION_NODE )
				text += item.getNodeValue();
		}
		NamedNodeMap attr = n.getAttributes();
		for (int i = 0; i<attr.getLength(); i++) {
			Node item = attr.item(i);
			if (item.getNodeType() == Node.ATTRIBUTE_NODE) {

				if (item.getNodeName().length() >= 6 && item.getNodeName().substring(0,6).equalsIgnoreCase("xmlns:")) {
					this.nameSpaces.put(item.getNodeName().substring(6),item.getNodeValue().toLowerCase().trim());
				} else {
					attributes.put(item.getNodeName().toLowerCase().trim(),item.getNodeValue().toLowerCase().trim());
				}
			}
		}
		if (attributes.containsKey("xmlns")) this.nameSpace = attributes.get("xmlns").toLowerCase().trim();
	}

	public DavNode getFirstChild() {
		return this.children.get(0);
	}

	public List<? extends DavNode> getChildren() {
		return Collections.unmodifiableList(this.children);
	}

	public String getText() {
		return this.text;
	}

	public String getNameSpace() {
		return this.nameSpace;
	}

	public boolean hasAttribute(String key) {
		return this.attributes.containsKey(key.toLowerCase());
	}
	public String getAttribute(String key) {
		return this.attributes.get(key.toLowerCase());
	}
	public void addChild(DavNode dn) {
		children.add(dn);
	}

	public List<DavNode> findNodesFromPath(String[] path, int curIndex) {
		if (!path[curIndex].equals(this.tagName)) return new ArrayList<DavNode>();
		ArrayList<DavNode> ret = new ArrayList<DavNode>();
		if (curIndex == path.length-1) {
			ret.add(this);
			return ret;
		}
		for (DavNode dn : this.children) {
			ret.addAll(dn.findNodesFromPath(path,curIndex+1));
		}
		return ret;
	}


	/**
	 * Returns the nodes which match the path below the current node, so if this node
	 * is the ROOT then getNodesFromPath("multistatus/response") will return all the
	 * response nodes.  With one of those response nodes getNodesFromPath("propstat/prop/getetag")
	 * would return all getetag nodes (within all props, within all propstats). 
	 * @param path
	 * @return A list of matching DavNodes, or null if path is null.
	 */
	public List<DavNode> getNodesFromPath(String path) {
		String[] tokens = path.split("/");
		if (tokens.length == 0) return null;

		ArrayList<DavNode> ret = new ArrayList<DavNode>();
		for (DavNode dn : this.children) {
			ret.addAll(dn.findNodesFromPath(tokens,0));
		}
		return ret;
	}

	/**
	 * When given an explicit path matching will return the text value of the first node
	 * matching the path.  Or null if no nodes match the path.
	 * @param path
	 * @return The segment name, or null if no such path existed.
	 */
	public String getFirstNodeText(String path) {
		List<DavNode> textNode = getNodesFromPath(path);
		if ( textNode.isEmpty() ) return null;

		return textNode.get(0).getText();
	}

	/**
	 * When given an explicit path matching will return the 'segment' (last part of path)
	 * for the href element at the end of the path.
	 * @param path
	 * @return The segment name, or null if no such path existed.
	 */
	public String segmentFromFirstHref(String path) {
		String name = getFirstNodeText(path);
		if ( name == null ) return null;

		Matcher m = Constants.matchSegmentName.matcher(name);
		if (m.find()) name = m.group(1);
		return name;
	}

	public void removeSubTree(DavNode node) {
		for (DavNode dn : children) {
			if (dn == node) {
				children.remove(node);
				return;
			}
			dn.removeSubTree(node);

		}

	}
	
	public DavNode getParent() {
		return this.parent;
	}
}
