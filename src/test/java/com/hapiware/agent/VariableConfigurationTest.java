package com.hapiware.agent;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.hapiware.agent.Agent.ConfigElements;


public class VariableConfigurationTest
	extends
		TestBase
{
	protected Element[] variables = null;
	
	@Before
	public void setup() throws ParserConfigurationException
	{
		super.setup();
	}

	@Test
	public void normalSituation() throws IOException
	{
		final String[][] variableValues = {
			{ "package", "hapiware" },
			{ "root", "One"},
			{ "cat", "miu-mau" }
		};
		setUpVariables(variableValues);

		// /agent/configuration
		configuration = configDoc.createElement("configuration");
		agent.appendChild(configuration);
		
		// /agent/configuration/item
		Element item = configDoc.createElement("item");
		item.appendChild(configDoc.createTextNode("^com/${package}/.+"));
		configuration.appendChild(item);
		item = configDoc.createElement("item");
		item.appendChild(configDoc.createTextNode("test-${package}-${root}: ${package} says ${cat}."));
		configuration.appendChild(item);
		
		ConfigElements configElements =
			Agent.readDOMDocument(configDoc, this.getClass().toString());

		@SuppressWarnings("unchecked")
		List<String> list =
			(List<String>)Agent.unmarshall(this.getClass().getClassLoader(), configElements);
		assertEquals("^com/hapiware/.+", list.get(0));
		assertEquals("test-hapiware-One: hapiware says miu-mau.", list.get(1));
	}
	
	@Test
	public void combinedVariables()
	{
		final String[][] variableValues = {
			{ "a", "ac" },
			{ "b", "ag" },
			{ "p${a}k${b}e", "hapiware" },
			{ "says", "miu" },
			{ "cat", "${says}-mau" }
		};
		setUpVariables(variableValues);
		
		// /agent/configuration
		configuration = configDoc.createElement("configuration");
		agent.appendChild(configuration);
		
		// /agent/configuration/item
		Element item = configDoc.createElement("item");
		item.appendChild(configDoc.createTextNode("test-${package}: says ${cat}."));
		configuration.appendChild(item);
		
		ConfigElements configElements =
			Agent.readDOMDocument(configDoc, this.getClass().toString());

		@SuppressWarnings("unchecked")
		List<String> list =
			(List<String>)Agent.unmarshall(this.getClass().getClassLoader(), configElements);
		assertEquals("test-hapiware: says miu-mau.", list.get(0));
	}
	
	@Test
	public void nestedVariables()
	{
		final String[][] variableValues = {
			{ "a", "seg" },
			{ "segment", "kag" },
			{ "pac${${a}ment}e", "hapiware" },
			{ "b", "ju" },
			{ "juuri", "roo" },
			{ "root", "/users/me" }
		};
		setUpVariables(variableValues);
		
		// /agent/configuration
		configuration = configDoc.createElement("configuration");
		agent.appendChild(configuration);
		
		// /agent/configuration/item
		Element item = configDoc.createElement("item");
		item.appendChild(configDoc.createTextNode("test-${package}: [${${${b}uri}t}]"));
		configuration.appendChild(item);
		
		ConfigElements configElements =
			Agent.readDOMDocument(configDoc, this.getClass().toString());

		@SuppressWarnings("unchecked")
		List<String> list =
			(List<String>)Agent.unmarshall(this.getClass().getClassLoader(), configElements);
		assertEquals("test-hapiware: [/users/me]", list.get(0));
	}
	
	@Test(expected=Agent.ConfigurationError.class)
	public void unrecognisedVariableInElement()
	{
		Element entry = configDoc.createElement("entry");
		entry.appendChild(configDoc.createTextNode("user.${miuku}"));
		classpath.appendChild(entry);
		
		Agent.readDOMDocument(configDoc, this.getClass().toString());
	}

	@Test(expected=Agent.ConfigurationError.class)
	public void unrecognisedVariableInAttribute()
	{
		// /agent/configuration
		configuration = configDoc.createElement("configuration");
		agent.appendChild(configuration);
		
		Element item = configDoc.createElement("item");
		item.setAttribute("key", "miu-${say}-mou");
		item.appendChild(configDoc.createTextNode("Just for testing."));
		configuration.appendChild(item);
		
		Agent.readDOMDocument(configDoc, this.getClass().toString());
	}
	
	@Test(expected=Agent.ConfigurationError.class)
	public void variableHasWrongAttributes()
	{
		Element variable = configDoc.createElement("variable");
		variable.setAttribute("name", "Hello");
		variable.appendChild(configDoc.createTextNode("World"));
		agent.appendChild(variable);
		
		// The wrong attribute.
		variable = configDoc.createElement("variable");
		variable.setAttribute("type", "file");
		variable.appendChild(configDoc.createTextNode("/users/me"));
		agent.appendChild(variable);
		
		Agent.readDOMDocument(configDoc, this.getClass().toString());
	}
	
	
	private void setUpVariables(String[][] variableTable)
	{
		try {
			XPath xpath = XPathFactory.newInstance().newXPath();
			
			// Removes all variables.
			NodeList allVariableEntries = 
				(NodeList)xpath.evaluate(
					"/agent/variable",
					configDoc,
					XPathConstants.NODESET
				);
			for(int i = 0; i < allVariableEntries.getLength(); i++)
				configDoc.removeChild(allVariableEntries.item(i));
		}
		catch(XPathExpressionException e) {
			System.err.print("This should never happen, but here we are... Exception was: ");
			e.printStackTrace(System.err);
		}

		// Sets up all the variables.
		variables = null;
		variables = new Element[variableTable.length];
		int i = 0;
		for(String[] variable : variableTable) {
			variables[i] = configDoc.createElement("variable");
			variables[i].setAttribute("name", variable[0]);
			variables[i].appendChild(configDoc.createTextNode(variable[1]));
			agent.appendChild(variables[i]);
			i++;
		}
	}
}
