package com.hapiware.agent;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import com.hapiware.agent.Agent;
import com.hapiware.agent.Agent.ConfigElements;



public class CustomisedConfigurationTest
	extends
		TestBase
{
	private Element configuration;
	
	
	@Before
	public void setup() throws ParserConfigurationException
	{
		super.setup();
		
		// /agent/configuration
		configuration = configDoc.createElement("configuration");
		configuration.setAttribute("unmarshaller", this.getClass().getName());
		agent.appendChild(configuration);
		
		// /agent/configuration/item
		Element item = configDoc.createElement("include");
		item.appendChild(configDoc.createTextNode("^com/hapiware/.+"));
		configuration.appendChild(item);

		item = configDoc.createElement("include");
		item.appendChild(configDoc.createTextNode("^com/mysoft/.+"));
		configuration.appendChild(item);

		item = configDoc.createElement("exclude");
		item.appendChild(configDoc.createTextNode("^com/bea/.+"));
		configuration.appendChild(item);

		item = configDoc.createElement("time");
		item.appendChild(configDoc.createTextNode("+30-20-10@1:2:3"));
		configuration.appendChild(item);
 	}
	
	@Test
	public void normalSituation() throws IOException
	{
		ConfigElements configElements =
			Agent.readDOMDocument(configDoc, this.getClass().toString());

		TestConfiguration configuration =
			(TestConfiguration)Agent.unmarshall(this.getClass().getClassLoader(), configElements);
		assertEquals("+30-20-10@1:2:3", configuration.getTime());
		assertEquals("^com/hapiware/.+", configuration.getIncludes().get(0));
		assertEquals("^com/mysoft/.+", configuration.getIncludes().get(1));
		assertEquals("^com/bea/.+", configuration.getExcludes().get(0));
	}
	
	
	public static Object unmarshall(Element configElement) throws XPathExpressionException
	{
		XPath xpath = XPathFactory.newInstance().newXPath();
		
		NodeList includeEntries =
			(NodeList)xpath.evaluate(
				"./include",
				configElement,
				XPathConstants.NODESET
			);
		List<String> includes = new ArrayList<String>();
		for(int i = 0; i < includeEntries.getLength(); i++) {
			Node includeEntry = includeEntries.item(i).getFirstChild();
			if(includeEntry != null)
				includes.add(((Text)includeEntry).getData());
		}
		
		NodeList excludeEntries =
			(NodeList)xpath.evaluate(
				"./exclude",
				configElement,
				XPathConstants.NODESET
			);
		List<String> excludes = new ArrayList<String>();
		for(int i = 0; i < excludeEntries.getLength(); i++) {
			Node excludeEntry = excludeEntries.item(i).getFirstChild();
			if(excludeEntry != null)
				excludes.add(((Text)excludeEntry).getData());
		}
		
		String time = (String)xpath.evaluate("./time", configElement, XPathConstants.STRING);
		
		return new TestConfiguration(excludes, includes, time);
	}
	
	
	public static class TestConfiguration
	{
		private final List<String> excludes;
		private final List<String> includes;
		private final String time;
		
		
		public TestConfiguration(List<String> excludes, List<String> includes, String time)
		{
			this.excludes = Collections.unmodifiableList(excludes);
			this.includes = Collections.unmodifiableList(includes);
			this.time = time;
		}


		public List<String> getIncludes()
		{
			return includes;
		}


		public List<String> getExcludes()
		{
			return excludes;
		}


		public String getTime()
		{
			return time;
		}
	}
}
