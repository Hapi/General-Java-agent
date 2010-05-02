package com.hapiware.agent;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;

import com.hapiware.agent.Agent;
import com.hapiware.agent.Agent.ConfigElements;


public class BaseConfigurationTest
	extends
		TestBase
{
	@Before
	public void setup() throws ParserConfigurationException
	{
		super.setup();
 	}

	@Test
	public void normalSituation() throws IOException
	{
		File file = createTemporaryConfigDocumentOnDisc(configDoc);
		ConfigElements configElements = Agent.readConfigurationFile(file.getCanonicalPath());
		assertEquals("com.hapiware.agent.AgentTest", configElements.getDelegateAgentName());
		URL[] classpathUrls = configElements.getClasspaths();
		assertEquals(new File(".").toURI().toURL(), classpathUrls[0]);
		assertEquals(new File(System.getProperty("user.home")).toURI().toURL(), classpathUrls[1]);
		assertEquals(new File("/").toURI().toURL(), classpathUrls[2]);
		file.delete();
	}
	
	@Test(expected=Agent.ConfigurationError.class)
	public void configurationFileDoesNotExist()
	{
		Agent.readConfigurationFile("this@F1le-name-does-not-@%½½½-surely-exist");
	}
	
	@Test(expected=Agent.ConfigurationError.class)
	public void configurationFileIsNotDefined()
	{
		Agent.readConfigurationFile(null);
	}
	
	@Test
	public void configurationTagIsMissing()
	{
		ConfigElements configElements =
			Agent.readDOMDocument(configDoc, this.getClass().toString());
		Object obj = Agent.unmarshall(this.getClass().getClassLoader(), configElements);
		assertEquals(null, obj);
	}

	@Test(expected=Agent.ConfigurationError.class)
	public void delegateIsMissing()
	{
		Element currentDelegate = (Element)agent.getFirstChild();
		Element newDelegate = configDoc.createElement("delegate");
		agent.replaceChild(newDelegate, currentDelegate);

		Agent.readDOMDocument(configDoc, this.getClass().toString());
	}

	@Test(expected=Agent.ConfigurationError.class)
	public void classPathEntryIsMissing()
	{
		Element currentEntry = (Element)classpath.getLastChild();
		Element newEntry = configDoc.createElement("entry");
		classpath.replaceChild(newEntry, currentEntry);

		Agent.readDOMDocument(configDoc, this.getClass().toString());
	}

	
	@Test(expected=Agent.ConfigurationError.class)
	public void classPathEntryDoesNotExist()
	{
		Element nonExistJar = configDoc.createElement("entry");
		nonExistJar.appendChild(
			configDoc.createTextNode("/this/directory_@-6-1-6/does/not/exist/my.jar")
		);
		classpath.appendChild(nonExistJar);

		Agent.readDOMDocument(configDoc, this.getClass().toString());
	}
}
