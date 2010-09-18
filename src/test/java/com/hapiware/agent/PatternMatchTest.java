package com.hapiware.agent;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;

import com.hapiware.agent.Agent.ConfigElements;


public class PatternMatchTest
	extends
		TestBase
{
	@Before
	public void setup() throws ParserConfigurationException
	{
		super.setup();
		
		// /agent/instrumented-class/include
		Element item = configDoc.createElement("include");
		item.appendChild(configDoc.createTextNode("^com/hapiware/.+"));
		instrumentedClass.appendChild(item);

		// /agent/instrumented-class/include
		item = configDoc.createElement("include");
		item.appendChild(configDoc.createTextNode("^com/mysoft/.+"));
		instrumentedClass.appendChild(item);

		// /agent/instrumented-class/exclude
		item = configDoc.createElement("exclude");
		item.appendChild(configDoc.createTextNode("^com/bea/.+"));
		instrumentedClass.appendChild(item);
	}
	
	@Test
	public void normalConfiguration() throws IOException
	{
		ConfigElements configElements =
			Agent.readDOMDocument(configDoc, this.getClass().toString());
		assertEquals(2, configElements.getIncludePatterns().length);
		assertEquals("^com/hapiware/.+", configElements.getIncludePatterns()[0].toString());
		assertEquals("^com/mysoft/.+", configElements.getIncludePatterns()[1].toString());
		
		assertEquals(1, configElements.getExcludePatterns().length);
		assertEquals("^com/bea/.+", configElements.getExcludePatterns()[0].toString());
	}

	@Test
	public void noExcludePatterns() throws IOException
	{
		final String tagName = "exclude";
		int len = instrumentedClass.getElementsByTagName(tagName).getLength();
		for(int i = 0; i < len; i++) {
			Element element = (Element)instrumentedClass.getElementsByTagName(tagName).item(0);
			element.getParentNode().removeChild(element);
		}

		Agent.readDOMDocument(configDoc, this.getClass().toString());
	}
}
