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
		agent.appendChild(configuration);
		
		// /agent/configuration/custom
		Element custom = configDoc.createElement("custom");
		configuration.appendChild(custom);
		
		Element item = configDoc.createElement("message");
		item.appendChild(configDoc.createTextNode("Hello Agent!"));
		custom.appendChild(item);

		item = configDoc.createElement("message");
		item.appendChild(configDoc.createTextNode("Same to you, too!"));
		custom.appendChild(item);
		
		item = configDoc.createElement("date");
		item.appendChild(configDoc.createTextNode("2010-09-19"));
		custom.appendChild(item);
 	}
	
	@Test
	public void normalSituation() throws IOException
	{
		ConfigElements configElements =
			Agent.readDOMDocument(configDoc, this.getClass().toString());

		TestConfiguration configuration =
			(TestConfiguration)Agent.unmarshall(this.getClass(), configElements);
		assertEquals("2010-09-19", configuration.getDate());
		assertEquals("Hello Agent!", configuration.getMessages().get(0));
		assertEquals("Same to you, too!", configuration.getMessages().get(1));
	}
	
	
	public static Object unmarshall(Element configElement) throws XPathExpressionException
	{
		XPath xpath = XPathFactory.newInstance().newXPath();
		
		NodeList messageEntries =
			(NodeList)xpath.evaluate(
				"./message",
				configElement,
				XPathConstants.NODESET
			);
		List<String> messages = new ArrayList<String>();
		for(int i = 0; i < messageEntries.getLength(); i++) {
			Node includeEntry = messageEntries.item(i).getFirstChild();
			if(includeEntry != null)
				messages.add(((Text)includeEntry).getData());
		}
		
		String date = (String)xpath.evaluate("./date", configElement, XPathConstants.STRING);
		
		return new TestConfiguration(messages, date);
	}
	
	
	public static class TestConfiguration
	{
		private final List<String> messages;
		private final String date;
		
		
		public TestConfiguration(List<String> messages, String date)
		{
			this.messages = Collections.unmodifiableList(messages);
			this.date = date;
		}


		public List<String> getMessages()
		{
			return messages;
		}


		public String getDate()
		{
			return date;
		}
	}
}
