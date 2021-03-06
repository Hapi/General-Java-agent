package com.hapiware.agent;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.hapiware.agent.Agent.ConfigElements;

public abstract class TestBase
{
	protected static final String BASEDIR = "src/test/resources/";
	
	
	protected Document configDoc;
	protected Element agent;
	protected Element classpath;
	protected Element instrumentedClass; 
	protected Element configuration;
	
	
	protected void setup()
		throws
			ParserConfigurationException
	{
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		configDoc = builder.newDocument();

		// /agent
		agent = configDoc.createElement("agent");
		configDoc.appendChild(agent);
		
		// /agent/delegate
		Element delegate = configDoc.createElement("delegate");
		delegate.appendChild(configDoc.createTextNode("com.hapiware.agent.AgentTest"));
		agent.appendChild(delegate);
		
		// /agent/classpath
		classpath = configDoc.createElement("classpath");
		agent.appendChild(classpath);
		
		// /agent/classpath/entry
		Element entry1 = configDoc.createElement("entry");
		entry1.appendChild(configDoc.createTextNode("."));
		classpath.appendChild(entry1);
		Element entry2 = configDoc.createElement("entry");
		entry2.appendChild(configDoc.createTextNode(System.getProperty("user.home")));
		classpath.appendChild(entry2);
		Element entry3 = configDoc.createElement("entry");
		entry3.appendChild(configDoc.createTextNode("/"));
		classpath.appendChild(entry3);
		
		// /agent/filter
		instrumentedClass = configDoc.createElement("filter");
		agent.appendChild(instrumentedClass);
	}
	
	protected File createTemporaryConfigDocumentOnDisc(Document configurationDocument)
	{
		File file = null;
		try {
			file = File.createTempFile("agent-unit-test", ".xml");
			file.deleteOnExit();
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
	        //transformer.setOutputProperty(OutputKeys.METHOD, "xml");
			DOMSource source = new DOMSource(configDoc);
			StreamResult result =  new StreamResult(file);
			//StreamResult result =  new StreamResult(System.out);
			transformer.transform(source, result);
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		catch(TransformerException e) {
			e.printStackTrace();
		}
		return file;
	}
	
	protected static void assertBasicConfiguration(ConfigElements configElements)
	{
		assertEquals("com.hapiware.asm.AgentDelegate", configElements.getDelegateAgentName());
		assertEquals("^com/hapiware/.*f[oi]x/.+", configElements.getIncludePatterns()[0].toString());
		assertEquals("^com/mysoft/.+", configElements.getIncludePatterns()[1].toString());
		assertEquals(
			"^com/hapiware/.+/CreateCalculationForm",
			configElements.getExcludePatterns()[0].toString()
		);
	}
}
