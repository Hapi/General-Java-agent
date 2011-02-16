package com.hapiware.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;

import com.hapiware.agent.Agent.ConfigElements;



public class MapConfigurationTest
	extends
		TestBase
{
	private static final String FILENAME = BASEDIR + "agent-config-map.xml";
	
	
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
			item.setAttribute("key", "key-" + i);
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
		Map<String, String> map =
			(Map<String, String>)Agent.unmarshall(this.getClass(), configElements);
		assertEquals(NUMBER_OF_ITEMS, map.size());
		for(int i = 0; i < map.size(); i++) {
			assertTrue(map.containsKey("key-" + i));
			assertEquals(Integer.toString(i), map.get("key-" + i));
		}
	}
	
	@Test(expected=Agent.ConfigurationError.class)
	public void incorrectItems()
	{
		Element item = configDoc.createElement("item");
		item.appendChild(configDoc.createTextNode("616"));
		configuration.appendChild(item);

		ConfigElements configElements =
			Agent.readDOMDocument(configDoc, this.getClass().toString());
		Agent.unmarshall(this.getClass(), configElements);
	}
	
	@Test
	public void readFromFile()
	{
		ConfigElements configElements = Agent.readConfigurationFile(FILENAME);
		assertBasicConfiguration(configElements);
		@SuppressWarnings("unchecked")
		Map<String, String> map = (Map<String, String>)Agent.unmarshall(null, configElements);
		assertEquals("One", map.get("1"));
		assertEquals("Two", map.get("2"));
		assertEquals("Three", map.get("3"));
		assertNull(map.get("0"));
		assertNull(map.get("4"));
		assertNull(map.get("5"));
		assertNull(map.get("test"));
	}
}
