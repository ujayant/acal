package com.morphoss.acal.xml;

import java.io.InputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class SaxDavXmlTreeBuilder  {

	public static SaxDavNode getXmlTree(InputStream xml) {
		  SAXParserFactory factory = SAXParserFactory.newInstance();
		  SaxDavNode root = new SaxDavNode();
		  try {
		        SAXParser saxParser = factory.newSAXParser();
		        saxParser.parse( xml, root.getHandler() );

		  } catch (Throwable err) {
		        err.printStackTrace ();
		  }
		  
		  return root;
	}
}
