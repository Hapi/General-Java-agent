package com.hapiware.agent;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;

import com.hapiware.agent.Agent;
import com.hapiware.agent.Agent.ConfigElements;



public class ListConfigurationTest
	extends
		TestBase
{
	private static final int NUMBER_OF_ITEMS = 5;
	
	private Element configuration;
	
	
	@Before
	public void setup() throws ParserConfigurationException
	{
		super.setup();
		
		// /agent/configuration
		configuration = configDoc.createElement("configuration");
		agent.appendChild(configuration);
		
		// /agent/configuration/item
		for(int i = 0; i < NUMBER_OF_ITEMS; i++) {
			Element item = configDoc.createElement("item");
			item.appendChild(configDoc.createTextNode(Integer.toString(i)));
			configuration.appendChild(item);
		}
 	}
	
	@Test
	public void normalSituation() throws IOException
	{
		ConfigElements configElements =
			Agent.readDOMDocument(configDoc, this.getClass().toString());

		@SuppressWarnings("unchecked")
		List<String> list =
			(List<String>)Agent.unmarshall(this.getClass().getClassLoader(), configElements);
		assertEquals(NUMBER_OF_ITEMS, list.size());
		int i = 0;
		for(String value : list)
			assertEquals(Integer.toString(i++), value);
	}
	
	@Test(expected=Agent.ConfigurationError.class)
	public void incorrectItems()
	{
		Element item = configDoc.createElement("item");
		item.setAttribute("key", "additional");
		item.appendChild(configDoc.createTextNode("616"));
		configuration.appendChild(item);

		ConfigElements configElements =
			Agent.readDOMDocument(configDoc, this.getClass().toString());
		Agent.unmarshall(this.getClass().getClassLoader(), configElements);
	}
}
