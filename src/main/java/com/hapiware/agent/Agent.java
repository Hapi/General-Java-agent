package com.hapiware.agent;


import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;


/**
 * {@code Agent} is a generic solution to greatly simplify the agent programming (see
 * {@code java.lang.instrument} package description for more information about agents
 * in general).
 * <p>
 * 
 * The main idea is to have a totally separated environment for running agents. This means that
 * the agent uses its own namespace (i.e. class loader) for its classes. With {@code Agent}
 * a programmer can avoid .jar file version conflicts. That's why the {@code Agent} configuration
 * file has its own {@code classpath} element(s). Another advantage is that the XML configuration
 * file is always similar for different agents. And yet one more advantage is that the programmer
 * does not need to care about the manifest attributes mentioned in {@code java.lang.instrument}
 * package description. They are already handled for the programmer. 
 * 
 * 
 * <h3>Using {@code Agent}</h3>
 * 
 * {@code Agent} is specified by using Java {@code -javaagent} switch like this:
 * <blockquote>
 *     {@code -javaagent:agent-jarpath=path-to-xml-config-file}
 * </blockquote>
 *
 * For example:
 * <blockquote>
 * 		{@code
 * 			-javaagent:/users/me/agent/target/agent-1.0.0.jar=/users/me/agent/agent-config.xml
 * 		}
 * </blockquote>
 * 
 * 
 * 
 * <h3>Configuration file</h3>
 * 
 * The configuration file is an XML file and has the {@code <agent>} as its root element.
 * {@code <agent>} has the following childs:
 * <ul>
 * 		<li>{@code <variable>}, this is an <b>optional</b> element for simplifying the configuration</li>
 * 		<li>{@code <delegate>}, this is a <b>mandatory</b> element</li>
 * 		<li>
 * 			{@code <classpath-agent>}, this is a <b>mandatory</b> element and has at minimum of one (1)
 * 			{@code <entry>} child element.
 * 		</li>
 * 		<li>
 * 			{@code <classpath-main>}, this is an <b>optional</b> element and can have zero (0)
 * 			{@code <entry>} child elements.
 * 		</li>
 * 		<li>
 * 			{@code <configuration>}, which is an <b>optional</b> element and can have any kind of
 * 			child elements. {@code <configuration>} element can also have an optional
 * 			{@code unmarshaller} attribute to handle programmer's own configuration structures.
 * 			See <a href="#configuration-element">{@code /agent/configuration} element</a>
 * 		</li>
 * </ul>
 * 
 * So, in general a configuration XML file looks like this:
 * <xmp>
 * 	<?xml version="1.0" encoding="UTF-8" ?>
 *	<agent>
 *		<variable />
 *		<variable />
 *		...
 *		<delegate />
 *		<classpath-agent>
 *			<entry />
 *			<entry />
 *			...
 *		</classpath-agent>
 *		<classpath-main>
 *			<entry />
 *			<entry />
 *			...
 *		</classpath-main>
 *		
 *		<configuration>
 *			<!--
 *				This can be text, a predefined structure or
 *				programmer's own structure
 *			-->
 *		</configuration>
 *	</agent>
 * </xmp>
 * 
 * 
 * 
 * <h4><a name="variable-element">{@code /agent/variable} element</h4>
 * The {@code /agent/variable} element is <b>optional</b> and it is supposed to be used to simplify
 * the configuration file. Variables can be anywhere under {@code agent} element (i.e. they
 * need not to be in the beginning of the configuration file).
 * <p>
 * 
 * The {@code /agent/variable} element <u>must have (only) {@code name} attribute</u> which is used
 * as a reference in other parts of the configuration file. The {@code name} reference is replaced
 * by the value of the {@code /agent/variable} element. The variable is referenced with the following
 * pattern:
 * <blockquote>
 * 		${VARIABLE}
 * </blockquote>
 * 
 * where:
 * <ul>
 * 		<li>{@code VARIABLE} is the {@code name} attribute of the {@code /agent/variable} element</li>
 * </ul>
 * 
 * Here is a simple example where every {@code ${repo-path}} variable reference is replaced with
 * {@code /users/me/.m2/repository} string:
 * <xmp>
 * 	<?xml version="1.0" encoding="UTF-8" ?>
 *	<agent>
 *		<variable name="repo-path">/users/me/.m2/repository</variable>
 *		<delegate>com.hapiware.test.MyAgentDelegate</delegate>
 *		<classpath-agent>
 * 			<entry>/users/me/agent/target/my-delegate-1.0.0.jar</entry>
 * 			<entry>${repo-path}/asm/asm/3.1/asm-3.1.jar</entry>
 * 			<entry>${repo-path}/asm/asm-commons/3.1/asm-commons-3.1.jar</entry>
 * 			<entry>${repo-path}/asm/asm-util/3.1/asm-util-3.1.jar</entry>
 * 			<entry>${repo-path}/asm/asm-tree/3.1/asm-tree-3.1.jar</entry>
 *		</classpath-agent>
 *		<configuration>...</configuration>
 *	</agent>
 * </xmp>
 * 
 * 
 * Variables can be used more creatively if there is a need for that. This example produces exactly
 * the same result than the example above but the use of variables are more complex:
 * <xmp>
 * 	<?xml version="1.0" encoding="UTF-8" ?>
 *	<agent>
 *		<variable name="a">repo</variable>
 *		<variable name="b">path</variable>
 *		<variable name="c">ju</variable>
 *		<variable name="juuri">roo</variable>
 *		<variable name="${${c}uri}t">users</variable>
 *		<variable name="${a}-${b}">/${root}/me/.m2/repository</variable>
 *		<variable name="asm-package">asm</variable>
 *		<variable name="${asm-package}-version">3.1</variable>
 *		<delegate>com.hapiware.test.MyAgentDelegate</delegate>
 *		<classpath-agent>
 * 			<entry>/users/me/agent/target/my-delegate-1.0.0.jar</entry>
 * 			<entry>${repo-path}/${asm-package}/asm/${asm-version}/asm-${asm-version}.jar</entry>
 * 			<entry>${repo-path}/${asm-package}/asm-commons/${asm-version}/asm-commons-${asm-version}.jar</entry>
 * 			<entry>${repo-path}/${asm-package}/asm-util/${asm-version}/asm-util-${asm-version}.jar</entry>
 * 			<entry>${repo-path}/${asm-package}/asm-tree/${asm-version}/asm-tree-${asm-version}.jar</entry>
 *		</classpath-agent>
 *		<configuration>...</configuration>
 *	</agent>
 * </xmp>
 * 
 * 
 * 
 * <h4><a name="delegate-element">{@code /agent/delegate} element</h4>
 * The {@code /agent/delegate} element is <b>mandatory</b> and its value is the name of the delegate class
 * as a fully qualified name (e.g. {@code com.hapiware.asm.TimeMachineAgentDelegate}).
 * <p>
 * 
 * The agent delegate class must have the following method (with the exact signature):
 * <blockquote>
 * 		{@code public static void premain(Object config, Instrumentation instrumentation)}
 * </blockquote>
 * 
 * where:
 * <ul>
 * 		<li>
 * 			{@code Object config} is the configuration object based on the
 * 			<a href="#configuration-element">{@code /agent/configuration} element</a>.
 * 		</li>
 * 		<li>{@code Instrumentation instrumentation} has services to provide the instrumentation.</li>
 * </ul>
 * 
 * This {@code static void premain(Object, Instrumentation)} method <b>can do all the same
 * things</b> as defined for {@code static void premain(String, Instrumentation)} method in
 * the {@code java.lang.instrument} package description.  
 * 
 * 
 * 
 * <h4><a name="classpath-agent-element">{@code /agent/classpath-agent} element</h4>
 * The {@code /agent/classpath-agent} element is <b>mandatory</b> and is used to define the classpath
 * <b>for the agent <u>delegate</u> class</b>. This means that there is no need to put any of
 * the used libraries for the agent delegate class in to the classpath (and actually you shouldn't
 * do that because that's why the whole {@code /agent/classpath-agent} element exist).
 * <p>
 * The {@code /agent/classpath-agent} element must have at least one {@code <entry>} child element
 * but can have several. The only required classpath entry is the delegate agent (.jar file) itself.
 * However, usually there are other classpath entries for the libraries needed by the delegate
 * agent. Here is an example:
 * 
 * <xmp>
 * 	<?xml version="1.0" encoding="UTF-8" ?>
 *	<agent>
 *		<delegate>com.hapiware.agent.TimeMachineAgentDelegate</delegate>
 *		<classpath-agent>
 * 			<entry>/users/me/agent/target/timemachine-delegate-1.0.0.jar</entry>
 * 			<entry>/usr/local/asm-3.1/lib/all/all-asm-3.1.jar</entry>
 *		</classpath-agent>
 *		<configuration>...</configuration>
 *	</agent>
 * </xmp>
 * 
 * 
 * 
 * <h4><a name="classpath-main-element">{@code /agent/classpath-main} element</h4>
 * The {@code /agent/classpath-main} element is <b>optional</b> and is used to define the classpath
 * <b>for the class to be manipulated</b>. A good example of {@code /agent/classpath-main} element
 * is a case where the byte code of the original class is to be modified so that some additional
 * libraries are needed. For example, you added an external helper methdo call to the beginning of
 * a method to print out a stack trace. In this case the modified class needs a way to find the
 * used external library and {@code /agent/classpath-main} element is exactly for that purpouse.
 * <p>
 * The {@code /agent/classpath-main} element does not require any {@code <entry>} child element
 * and can have several. Here is an example:
 * 
 * <xmp>
 * 	<?xml version="1.0" encoding="UTF-8" ?>
 *	<agent>
 *		<delegate>com.hapiware.agent.InfoDelegate</delegate>
 *		<classpath-agent>
 * 			<entry>/users/me/agent/target/info-delegate-1.0.0.jar</entry>
 * 			<entry>/usr/local/asm-3.1/lib/all/all-asm-3.1.jar</entry>
 *		</classpath-agent>
 *		<classpath-main>
 * 			<entry>/users/me/some/other/library.jar</entry>
 *		</classpath-main>
 *		<configuration>...</configuration>
 *	</agent>
 * </xmp>
 *  
 * 
 * 
 * <h4><a name="configuration-element">{@code /agent/configuration/} element</a></h4>
 * The {@code /agent/configuration/} element is <b>optional</b> and has all the necessary
 * configuration information for the agent delegate class. The exact structure can depend on
 * the programmer but there are some predefined structures as well. The configuration object
 * is delivered to the agent delegate's {@code static void premain(Object, Instrumentation)}
 * method as the first (i.e. {@code Object}) argument.
 * <p>
 * 
 * All the possible options for configuration object creation are:
 * 	<ol>
 *		<li>{@code null}</li>
 * 		<li>{@code String}</li>
 * 		<li>{@code List<String>}</li>
 * 		<li>{@code Map<String, String>}</li>
 * 		<li>User defined configuration object</li>
 *	</ol>
 * 
 * <h5>{@code null}</h5>
 * If the {@code /agent/configuration/} element is not defined at all then {@code null} is
 * delivered to the agent delegate's {@code static void premain(Object, Instrumentation)} method
 * as a first argument. For example:
 * <xmp>
 * 	<?xml version="1.0" encoding="UTF-8" ?>
 *	<agent>
 *		<delegate>com.hapiware.test.MyAgentDelegate</delegate>
 *		<classpath-agent>
 * 			<entry>/users/me/agent/target/my-delegate-1.0.0.jar</entry>
 *		</classpath-agent>
 *	</agent>
 * </xmp>
 * which sends {@code null} to the {@code MyAgentDelegate.premain(Object, Instrumentation)} method
 * as a first argument.
 * 
 * <h5>{@code String}</h5>
 * If the {@code /agent/configuration/} element has only pure text (i.e. {@code String}), the
 * text string is delivered to the delegate's {@code static void premain(Object, Instrumentation)}
 * method as a first argument. For example:
 * <xmp>
 * 	<?xml version="1.0" encoding="UTF-8" ?>
 *	<agent>
 *		<delegate>com.hapiware.test.MyAgentDelegate</delegate>
 *		<classpath-agent>
 * 			<entry>/users/me/agent/target/my-delegate-1.0.0.jar</entry>
 *		</classpath-agent>
 *		<configuration>Show me!</configuration>
 *	</agent>
 * </xmp>
 * which sends "Show me!" to the {@code MyAgentDelegate.premain(Object, Instrumentation)} method
 * as a first argument.
 * 
 * <h5>{@code List<String>}</h5>
 * If the {@code /agent/configuration/} element has {@code <item>} child elements <u>without an 
 * attribute</u> then the {@code List<String>} is created which is in turn delivered to the agent
 * delegate's {@code static void premain(Object, Instrumentation)} method as a first argument. For
 * example:
 * <xmp>
 * 	<?xml version="1.0" encoding="UTF-8" ?>
 *	<agent>
 *		<delegate>com.hapiware.test.MyAgentDelegate</delegate>
 *		<classpath-agent>
 * 			<entry>/users/me/agent/target/my-delegate-1.0.0.jar</entry>
 *		</classpath-agent>
 *		<configuration>
 *			<item>One</item>
 *			<item>Two</item>
 *			<item>Three</item>
 *		</configuration>
 *	</agent>
 * </xmp>
 * which sends {@code List<String>} {"One", "Two", "Three"} to the
 * {@code MyAgentDelegate.premain(Object, Instrumentation)} method as a first argument.
 * 
 * <h5>{@code Map<String, String>}</h5>
 * If the {@code /agent/configuration/} element has {@code <item>} child elements <u>with a
 * {@code key} attribute</u> then the {@code Map<String, String>} is created which is in turn
 * delivered to the agent delegate's {@code static void premain(Object, Instrumentation)} method
 * as a first argument. For example:
 * <xmp>
 * 	<?xml version="1.0" encoding="UTF-8" ?>
 *	<agent>
 *		<delegate>com.hapiware.test.MyAgentDelegate</delegate>
 *		<classpath-agent>
 * 			<entry>/users/me/agent/target/my-delegate-1.0.0.jar</entry>
 *		</classpath-agent>
 *		<configuration>
 *			<item key="1">One</item>
 *			<item key="2">Two</item>
 *			<item key="3">Three</item>
 *		</configuration>
 *	</agent>
 * </xmp>
 * which sends {@code Map<String, String>} {{"1", "One"}, {"2", "Two"}, {"3", "Three"}} to the
 * {@code MyAgentDelegate.premain(Object, Instrumentation)} method as a first argument.
 * 
 * <h5>User defined configuration object</h5>
 * If the {@code /agent/configuration/} element has the {@code unmarshaller} attribute defined,
 * then {@code public static Object unmarshall(org.w3c.dom.Element configElement)} method of
 * the defined unmarshaller class is called with the {@code /agent/configuration} element as
 * an argument. The system then delivers the returned {@code Object} directly to the agent
 * delegate's {@code static void premain(Object, Instrumentation)} method as the first argument.
 * This approach makes it possible to create different configuration structures for the agent
 * delegate's {@code static void premain(Object, Instrumentation)} method very flexibly.
 * <p>
 * The {@code unmarshaller} attribute must be a fully qualified class name
 * (e.g. {@code com.hapiware.asm.TimeMachineAgentDelegate}) and assumes that the defined
 * class has {@code public static Object unmarshall(org.w3c.dom.Element configElement)} method
 * defined which returns the programmer's own confgiruation object. Here is an example:
 * <xmp>
 * 	<?xml version="1.0" encoding="UTF-8" ?>
 * 	<agent>
 * 		<delegate>com.hapiware.agent.TimeMachineAgentDelegate</delegate>
 * 		<classpath-agent>
 * 			<entry>/users/me/agent/target/timemachine-delegate-1.0.0.jar</entry>
 * 			<entry>/usr/local/asm-3.1/lib/all/all-asm-3.1.jar</entry>
 * 		</classpath-agent>
 * 		<configuration unmarshaller="com.hapiware.agent.TimeMachineAgentDelegate">
 * 			<include>^com/hapiware/.*f[oi]x/.+</include>
 * 			<include>^com/mysoft/.+</include>
 * 			<exclude>^com/hapiware/.+/CreateCalculationForm</exclude>
 * 			<time>2010-3-13@7:15:0</time>
 * 		</configuration>
 * 	</agent>
 * </xmp>
 * 
 * This assumes that {@code com.hapiware.asm.TimeMachineAgentDelegate} class has
 * {@code public static Object unmarshall(org.w3c.dom.Element configElement)} method defined
 * to handle {@code <include>}, {@code <exclude>} and {@code <time>} elements from the
 * {@code <configuration>} element. It is also assumed that the {@code Object} the
 * {@code unmarshall()} method returns can be properly handled (and type casted) in the
 * {@code static void premain(Object, Instrumentation)} method.
 * <p>
 * Notice that in this example the {@code unmarshall()} method was defined to be in the
 * {@code com.hapiware.asm.TimeMachineAgentDelegate} class but is not a requirement,
 * although it can make programmer's life a bit easier. 
 * 
 * 
 * @see java.lang.instrument
 * 
 * @author hapi
 *
 */
public class Agent
{
	/**
	 * This method is called before the main method call right after the JVM initialisation. 
	 * <p>
	 * <b>Notice</b> that this method follows the <i>fail fast</i> idiom and thus
	 * throws a runtime exception if there is something wrong in the configuration file.
	 * 
	 * @param agentArgs
	 * 		Same string which was given to {@code -javaagent} as <i>options</i> (see the class
	 * 		description). Notice that <i>options</i> must be parsed by the agent itself.
	 * 
	 * @param instrumentation
	 * 		See {@code java.lang.instrument.Instrumentation}
	 * 
	 * @throws ConfigurationError
	 * 		If there is something wrong with the configuration file.
	 *
	 * @see java.lang.instrument
	 */
	public static void premain(String agentArgs, Instrumentation instrumentation)
	{
		ConfigElements configElements = readConfigurationFile(agentArgs);
		ClassLoader originalClassLoader = null;
		try {
			originalClassLoader = Thread.currentThread().getContextClassLoader();
			ClassLoader cl =
				new URLClassLoader(
					configElements.getAgentClasspaths(),
					originalClassLoader
				);
			Thread.currentThread().setContextClassLoader(cl);
			
			Object delegateConfiguration = unmarshall(cl, configElements);
			
			// Invokes the premain method of the delegate agent.
			Class<?> delegateAgentClass =
				(Class<?>)cl.loadClass(configElements.getDelegateAgentName());
			delegateAgentClass.getMethod(
				"premain",
				new Class[] {Object.class, Instrumentation.class}
			).invoke(null, delegateConfiguration, instrumentation);
		}
		catch(ClassNotFoundException e) {
			throw
				new ConfigurationError(
					"A delegate agent \""
						+ configElements.getDelegateAgentName() + "\" was not found.",
					e
				);
		}
		catch(NoSuchMethodException e) {
			throw
				new ConfigurationError(
					"static void premain(Object, Instrumentation) method was not defined in \""
						+ configElements.getDelegateAgentName() + "\".",
					e
				);
		}
		catch(IllegalArgumentException e) {
			throw
				new ConfigurationError(
					"Argument mismatch with static void premain(Object, Instrumentation) method "
						+ "in \"" + configElements.getDelegateAgentName() + "\".",
					e
				);
		}
		catch(InvocationTargetException e) {
			throw
				new ConfigurationError(
					"static void premain(Object, Instrumentation) method "
						+ "in \"" + configElements.getDelegateAgentName()
						+ "\" threw an exception.",
					e
				);
		}
		catch(IllegalAccessException e) {
			assert false : e;
		}
		finally {
			if(originalClassLoader != null) {
				URL[] mainClasspaths = configElements.getMainClasspaths();
				if(mainClasspaths.length > 0) {
					ClassLoader cl =
						new URLClassLoader(
							configElements.getMainClasspaths(),
							originalClassLoader
						);
					Thread.currentThread().setContextClassLoader(cl);
				}
				else
					Thread.currentThread().setContextClassLoader(originalClassLoader);
			}
		}
	}

	
	/**
	 * Reads the configuration file and creates the include and exclude regular expression
	 * pattern compilations for class matching.
	 * 
	 * @param configReader
	 * 		A configuration reader.
	 *  
	 * @return
	 * 		Configuration elements ({@link ConfigElements}) parsed from the configuration file.
	 * 
	 * @throws ConfigurationError
	 * 		If configuration file cannot be read or parsed properly.
	 */
	static ConfigElements readConfigurationFile(String configFileName)
	{
		if(configFileName == null)
			throw
				new ConfigurationError(
					"The agent configuration file is not defined."
				);
		
		File configFile = new File(configFileName);
		if(configFile.exists()) {
			DocumentBuilder builder;
			try {
				builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
				return readDOMDocument(builder.parse(configFile), configFile.getCanonicalPath());
			}
			catch(ParserConfigurationException e) {
				throw
					new ConfigurationError(
						"XML document builder cannot be created.",
						e
					);
			}
			catch(SAXException e) {
				throw
					new ConfigurationError(
						"Parsing the agent configuration file \""
							+ configFile + "\" didn't succeed.\n"
							+ "\t-> Make sure that the configuration file has been saved using "
							+ "the correct encoding (i.e the same what is claimed in "
							+ "XML declaration).",
						e
					);
			}
			catch(IOException e) {
				throw
					new ConfigurationError(
						"IO error with the agent configuration file \""
							+ configFile + "\".",
						e
					);
			}
		}
		else
			throw
				new ConfigurationError(
					"The agent configuration file \"" + configFile + "\" does not exist."
				);
	}
	
	
	/**
	 * This method does the actual work for {@link #readConfigurationFile(String)} method.
	 * This separation is mainly done for making unit testing easier. 
	 */
	static ConfigElements readDOMDocument(Document configDocument, String configFileName)
	{
		ConfigElements retVal = null;
		try {
			XPath xpath = XPathFactory.newInstance().newXPath();
			
			// All /agent/variables.
			NodeList allVariableEntries = 
				(NodeList)xpath.evaluate(
					"/agent/variable",
					configDocument,
					XPathConstants.NODESET
				);
			
			// /agent/variable[@name]
			NodeList variableEntriesWithName = 
				(NodeList)xpath.evaluate(
					"/agent/variable[@name]",
					configDocument,
					XPathConstants.NODESET
				);
			if(allVariableEntries.getLength() != variableEntriesWithName.getLength())
				throw
					new ConfigurationError("\"name\" is the only valid attribute for /agent/variable element.");
			
			Map<String, String> variables = new HashMap<String, String>();
			putVariablesWithNamesToMap(variableEntriesWithName, variables);
			
			// Replace all variables in attributes in the configuration file.
			NodeList allAttributes =
				(NodeList)xpath.evaluate(
					"/agent//@*",
					configDocument,
					XPathConstants.NODESET
				);
			Pattern variablePattern = Pattern.compile("(\\$\\{([^\\$\\{\\}]+?)\\})");
			boolean matched;
			do {
				matched = false;
				for(int i = 0; i < allAttributes.getLength(); i ++) {
					Node attributeEntry = allAttributes.item(i);
					String attributeValue = ((Attr)attributeEntry).getValue();
					Matcher m = variablePattern.matcher(attributeValue);
					while(m.find()) {
						matched = true;
						String substitute = variables.get(m.group(2));
						if(substitute == null) {
							String ex =
								"Attribute \"" + ((Attr)attributeEntry).getOwnerElement().getNodeName() 
								+ "[@" + attributeEntry.getNodeName() + "]\""
								+ " has an unrecognised variable " + m.group(1) + ".";
							throw new ConfigurationError(ex);
						}
						attributeValue = attributeValue.replace(m.group(1), substitute);
						((Attr)attributeEntry).setValue(attributeValue);
					}
				}
			
				// /agent/variable[@name] must be searched again and put to the map
				// in the case variables are used in /agent/variable elements as attributes.
				variableEntriesWithName = 
					(NodeList)xpath.evaluate(
						"/agent/variable[@name]",
						configDocument,
						XPathConstants.NODESET
					);
				putVariablesWithNamesToMap(variableEntriesWithName, variables);
			} while(matched);
			
			// Replace all variables in elements in the configuration file.
			NodeList allElements =
				(NodeList)xpath.evaluate(
					"/agent//*/text()",
					configDocument,
					XPathConstants.NODESET
				);
			do {
				matched = false;
				for(int i = 0; i < allElements.getLength(); i ++) {
					Node elementEntry = allElements.item(i);
					String elementValue = ((Text)elementEntry).getData();
					Matcher m = variablePattern.matcher(elementValue);
					while(m.find()) {
						matched = true;
						String substitute = variables.get(m.group(2));
						if(substitute == null) {
							String ex =
								"Element \"" + elementEntry.getParentNode().getNodeName() + "\""
								+ " has an unrecognised variable " + m.group(1) + ".";
							throw new ConfigurationError(ex);
						}
						elementValue = elementValue.replace(m.group(1), substitute);
						((Text)elementEntry).setData(elementValue);
					}
				}

				// /agent/variable[@name] must be searched again and put to the map
				// in the case variables are used in /agent/variable elements as values.
				variableEntriesWithName = 
					(NodeList)xpath.evaluate(
						"/agent/variable[@name]",
						configDocument,
						XPathConstants.NODESET
					);
				putVariablesWithNamesToMap(variableEntriesWithName, variables);
			} while(matched);

			// /agent/delegate
			String delegateAgent =
				(String)xpath.evaluate("/agent/delegate", configDocument, XPathConstants.STRING);
			if(delegateAgent.trim().length() == 0) {
				throw
					new ConfigurationError(
						"\"/agent/delegate\" node is missing from the agent configuration file \""
							+ configFileName + "\"."
					);
			}
			
			// /agent/classpath-agent
			NodeList agentClasspathEntries =
				(NodeList)xpath.evaluate(
					"/agent/classpath-agent/entry",
					configDocument,
					XPathConstants.NODESET
				);
			if(agentClasspathEntries.getLength() == 0)
				throw
					new ConfigurationError(
						"/agent/classpath-agent/entry is missing."
					);
			List<String> agentClasspaths = new ArrayList<String>();
			for(int i = 0; i < agentClasspathEntries.getLength(); i++) {
				Node classpathEntry = agentClasspathEntries.item(i).getFirstChild();
				if(classpathEntry != null)
					agentClasspaths.add(((Text)classpathEntry).getData());
				else
					throw
						new ConfigurationError(
							"/agent/classpath-agent/entry[" + i + "] does not have a value."
						);
			}
			
			// /agent/classpath-main
			NodeList mainClasspathEntries =
				(NodeList)xpath.evaluate(
					"/agent/classpath-main/entry",
					configDocument,
					XPathConstants.NODESET
				);
			List<String> mainClasspaths = new ArrayList<String>();
			for(int i = 0; i < mainClasspathEntries.getLength(); i++) {
				Node classpathEntry = mainClasspathEntries.item(i).getFirstChild();
				if(classpathEntry != null)
					mainClasspaths.add(((Text)classpathEntry).getData());
			}
			
			// /agent/configuration
			Node configuration = 
				(Node)xpath.evaluate(
					"/agent/configuration",
					configDocument,
					XPathConstants.NODE
				);
			String unmarshallerName = null;
			if(configuration != null) {
				NamedNodeMap configurationAttributes = configuration.getAttributes();
				Node unmarshaller = configurationAttributes.getNamedItem("unmarshaller");
				if(unmarshaller != null)
					unmarshallerName = unmarshaller.getNodeValue();
			}
			
			
			retVal = 
				new ConfigElements(
					agentClasspaths,
					mainClasspaths,
					delegateAgent,
					unmarshallerName,
					(Element)configuration
				);
			
		}
		catch(IOException e) {
			throw
				new ConfigurationError(
					"IO error with the agent configuration file \""
						+ configFileName + "\".",
					e
				);
		}
		catch(XPathExpressionException e) {
			throw
				new ConfigurationError(
					"A desired config node was not found from the agent configuration file \""
						+ configFileName + "\".",
					e
				);
		}
		
		return retVal;
	}

	
	private static void putVariablesWithNamesToMap(NodeList variableEntries, Map<String, String> map)
	{
		map.clear();
		for(int i = 0; i < variableEntries.getLength(); i++) {
			Node variableEntry = variableEntries.item(i);
			Node variableValue = variableEntry.getFirstChild();
			NamedNodeMap nameAttributes = variableEntry.getAttributes();
			Node nameAttribute = nameAttributes.getNamedItem("name");
			map.put(nameAttribute.getNodeValue(), ((Text)variableValue).getData());
		}
	}
	
	
	/**
	 * Creates an object according to the given configuration elements (i.e. /agent/configuration
	 * element).
	 * 
	 * @param classLoader
	 * 		A {@code ClassLoader} for unmarshall operation. 
	 * 
	 * @param configElements
	 * 		Configuration elements to be used as a basis for the configuration object creation.
	 * 
	 * @return
	 * 		A configuration object. There are five (5) possible options for configuration object
	 * 		creation:
	 * 		<ol>
	 * 			<li>{@code null}</li>
	 * 			<li>{@code String}</li>
	 * 			<li>{@code List<String>}</li>
	 * 			<li>{@code Map<String, String>}</li>
	 * 			<li>User defined configuration object</li>
	 * 		</ol>
	 * 
	 *		For more information, see the class description.
	 */
	static Object unmarshall(ClassLoader classLoader, ConfigElements configElements)
	{
		if(configElements.getUnmarshallerName() == null) {
			Element configElement = configElements.getConfigurationElement();
			if(configElement != null) {
				if(configElement.getElementsByTagName("item").getLength() > 0)
					return createCollectionConfiguration(configElements);
				else {
					Node firstNode = configElement.getFirstChild();
					if(firstNode == null || firstNode.getNodeValue().trim().length() == 0)
						throw
							new ConfigurationError(
								"/agent/configuration does not have a proper string "
									+ "(or any other elements)"
							);
					
					return firstNode.getNodeValue().trim();
				}
			}
			else
				return null;
		}
		else {
			try {
				// Invokes the unmarshaller.
				Class<?> unmarshallerClass =
					(Class<?>)classLoader.loadClass(configElements.getUnmarshallerName());
				return
					unmarshallerClass.getMethod(
						"unmarshall",
						new Class[] {Element.class}
					).invoke(null, configElements.getConfigurationElement());
			}
			catch(ClassNotFoundException e) {
				throw
					new ConfigurationError(
						"An unmarhaller class  \""
							+ configElements.getUnmarshallerName() + "\" was not found.",
						e
					);
			}
			catch(NoSuchMethodException e) {
				throw
					new ConfigurationError(
						"static Object unmarshall(Element) method was not defined in \""
							+ configElements.getUnmarshallerName() + "\".",
						e
					);
			}
			catch(IllegalArgumentException e) {
				throw
					new ConfigurationError(
						"Argument mismatch with static Object unmarshall(Element) method "
							+ "in \"" + configElements.getUnmarshallerName() + "\".",
						e
					);
			}
			catch(InvocationTargetException e) {
				throw
					new ConfigurationError(
						"static Object unmarshall(Element) method "
							+ "in \"" + configElements.getUnmarshallerName()
							+ "\" threw an exception.",
						e
					);
			}
			catch(IllegalAccessException e) {
				assert false: e;
				return null;
			}
		}
	}
	
	
	/**
	 * Creates either {@code List<String>} or {@code Map<String, String>} configuration object.
	 * 
	 * @param configElements
	 * 		Configuration elements to be used as a basis for the configuration object creation.
	 * 
	 * @return
	 * 		A configuration collection object which is either one of the following:
	 * 		<ul>
	 * 			<li>{@code List<String>}</li>
	 * 			<li>{@code Map<String, String>}</li>
	 * 		</ul>
	 */
	private static Object createCollectionConfiguration(ConfigElements configElements)
	{
		boolean dontUseMap = false;
		boolean dontUseList = false;
		Object retVal = null;

		XPath xpath = XPathFactory.newInstance().newXPath();
		try {
			NodeList configurationItems = (NodeList)xpath.evaluate(
				"./item",
				configElements.getConfigurationElement(),
				XPathConstants.NODESET
			);
			for(int i = 0; i < configurationItems.getLength(); i++) {
				Node item = configurationItems.item(i);
				if(item.getAttributes().getNamedItem("key") == null)
					dontUseMap = true;
				else
					dontUseList = true;
			}
			
			if(dontUseList && dontUseMap)
				throw
					new ConfigurationError(
						"/agent/configuration/item tags have improper attributes."
					);
			
			// Uses a list.
			if(!dontUseList) {
				List<String> list = new ArrayList<String>();
				for(int i = 0; i < configurationItems.getLength(); i++) {
					Node item = configurationItems.item(i).getFirstChild();
					if(item != null)
						list.add(((Text)item).getData());
					else
						list.add("");
				}
				retVal = list;
			}
			
			// Uses a map.
			if(!dontUseMap) {
				Map<String, String> map = new HashMap<String, String>();
				for(int i = 0; i < configurationItems.getLength(); i++) {
					Node item = configurationItems.item(i);
					Node keyNode = item.getAttributes().getNamedItem("key");
					String key = keyNode == null ? "" : keyNode.getNodeValue();
					if(item != null)
						map.put(key, ((Text)item.getFirstChild()).getData());
					else
						map.put(key, "");
				}
				retVal = map;
			}
		}
		catch(XPathExpressionException e) {
			assert false : e;
		}
		
		assert retVal != null;
		return retVal;
	}


	/**
	 * {@code ConfigElements} is data object for collecting all the necessary items from
	 * the agent configuration file. 
	 * <p>
	 * {@code ConfigElements} is <b>immutable</b>.
	 * 
	 * @author hapi
	 *
	 */
	static class ConfigElements
	{
		private final String delegateAgentName;
		private final String unmarshallerName;
		private final List<URL> agentClasspaths;
		private final List<URL> mainClasspaths;
		private final Element configurationElement;
		
		public ConfigElements(
			List<String> agentClasspaths,
			List<String> mainClasspaths,
			String delegateAgentName,
			String unmarshallerName,
			Element configElement
		)
			throws
				MalformedURLException
		{
			List<URL> agentClasspathsAsURLs = new ArrayList<URL>();
			for(String agentClasspath : agentClasspaths) {
				File file = new File(agentClasspath);
				if(!file.exists())
					throw
						new ConfigurationError(
							"Agent class path entry \"" + file + "\" does not exist."
						);
				agentClasspathsAsURLs.add(file.toURI().toURL());
			}
			this.agentClasspaths = Collections.unmodifiableList(agentClasspathsAsURLs);
			
			List<URL> mainClasspathsAsURLs = new ArrayList<URL>();
			for(String mainClasspath : mainClasspaths) {
				File file = new File(mainClasspath);
				if(!file.exists())
					throw
						new ConfigurationError(
							"Main class path entry \"" + file + "\" does not exist."
						);
				mainClasspathsAsURLs.add(file.toURI().toURL());
			}
			this.mainClasspaths = Collections.unmodifiableList(mainClasspathsAsURLs);

			this.delegateAgentName = delegateAgentName;
			this.unmarshallerName = unmarshallerName;
			this.configurationElement = configElement;
		}

		public String getUnmarshallerName()
		{
			return unmarshallerName;
		}

		public Element getConfigurationElement()
		{
			return configurationElement;
		}

		public String getDelegateAgentName()
		{
			return delegateAgentName;
		}

		public URL[] getAgentClasspaths()
		{
			URL[] asUrls = new URL[agentClasspaths.size()];
			return agentClasspaths.toArray(asUrls);
		}

		public URL[] getMainClasspaths()
		{
			URL[] asUrls = new URL[mainClasspaths.size()];
			return mainClasspaths.toArray(asUrls);
		}
	}
	
	
	/**
	 * A runtime error to indicate that there is something wrong with the configuration of
	 * the agent. 
	 * 
	 * @author hapi
	 *
	 */
	static class ConfigurationError extends Error
	{
		private static final long serialVersionUID = 1L;
		

		public ConfigurationError()
		{
			super();
		}

		public ConfigurationError(String message, Throwable cause)
		{
			super(message, cause);
		}

		public ConfigurationError(String message)
		{
			super(message);
		}

		public ConfigurationError(Throwable cause)
		{
			super(cause);
		}
	}
}
