package com.hapiware.agent;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;

import com.hapiware.agent.Agent.ConfigElements;



public class StringConfigurationTest
	extends
		TestBase
{
	private static final String FILENAME = BASEDIR + "agent-config-string.xml";
	
	
	private Element configuration;
	
	
	@Before
	public void setup() throws ParserConfigurationException
	{
		super.setup();
		
		// /agent/configuration
		configuration = configDoc.createElement("configuration");
		agent.appendChild(configuration);
 	}
	
	@Test
	public void normalSituation() throws IOException
	{
		configuration.appendChild(configDoc.createTextNode("This is text."));
		
		ConfigElements configElements =
			Agent.readDOMDocument(configDoc, this.getClass().toString());
		String value = (String)Agent.unmarshall(this.getClass(), configElements);
		assertEquals("This is text.", value);
	}
	
	@Test(expected=Agent.ConfigurationError.class)
	public void configurationDataIsMissing() throws IOException
	{
		ConfigElements configElements =
			Agent.readDOMDocument(configDoc, this.getClass().toString());
		Agent.unmarshall(this.getClass(), configElements);
	}
	
	@Test(expected=Agent.ConfigurationError.class)
	public void configurationDataIsEmptyString() throws IOException
	{
		configuration.appendChild(configDoc.createTextNode(""));

		ConfigElements configElements =
			Agent.readDOMDocument(configDoc, this.getClass().toString());
		Agent.unmarshall(this.getClass(), configElements);
	}
	
	@Test
	public void readFromFile()
	{
		ConfigElements configElements = Agent.readConfigurationFile(FILENAME);
		assertBasicConfiguration(configElements);
		String str = (String)Agent.unmarshall(null, configElements);
		assertEquals("Hello World!", str);
	}
}
