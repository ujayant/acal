package com.morphoss.acal.xml;

import java.util.List;

public interface DavNode {

		public DavNode getFirstChild();

		public List<? extends DavNode> getChildren();

		public String getText();

		public String getNameSpace();

		public boolean hasAttribute(String key);
		
		public String getAttribute(String key);
		
		public void addChild(DavNode dn);
		
		/**
		 * Returns the nodes which match the path below the current node, so if this node
		 * is the ROOT then getNodesFromPath("multistatus/response") will return all the
		 * response nodes.  With one of those response nodes getNodesFromPath("propstat/prop/getetag")
		 * would return all getetag nodes (within all props, within all propstats). 
		 * @param path
		 * @return A list of matching DavNodes, or null if path is null.
		 */
		public List<DavNode> getNodesFromPath(String path); 
		
		/**
		 * When given an explicit path matching will return the text value of the first node
		 * matching the path.  Or null if no nodes match the path.
		 * @param path
		 * @return The segment name, or null if no such path existed.
		 */
		public String getFirstNodeText(String path);
		
		/**
		 * When given an explicit path matching will return the 'segment' (last part of path)
		 * for the href element at the end of the path.
		 * @param path
		 * @return The segment name, or null if no such path existed.
		 */
		public String segmentFromFirstHref(String path);
		
		public void removeSubTree(DavNode node);
		
		public DavNode getParent();

		public List<DavNode> findNodesFromPath(String[] path, int curIndex);
}
