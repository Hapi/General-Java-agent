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

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
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
 * 		<li>{@code <delegate>}, this is a <b>mandatory</b> element to define the agent delegate</li>
 * 		<li>
 * 			{@code <classpath>}, this is a <b>mandatory</b> element and has at minimum of one (1)
 * 			{@code <entry>} child element.
 * 		</li>
 * 		<li>
 * 			{@code <filter>}, this is an <b>optional</b> element and can have zero (0)
 * 			{@code <include>} and/or {@code <exclude>} child elements. Filters are regular
 * 			expressions patterns to include or exclude classes to be instrumented.
 * 		</li>
 * 		<li>
 * 			{@code <configuration>}, which is an <b>optional</b> element is used to configure
 * 			agent delegate.
 * 			See <a href="#agent-configuration-element">{@code /agent/configuration} element</a>
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
 *		<classpath>
 *			<entry />
 *			<entry />
 *			...
 *		</classpath>
 *		<filter>
 *			<include />
 *			<include />
 *			<exclude />
 *			<exclude />
 *			...	
 *		</filter>	
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
 * <h4><a name="agent-variable-element">{@code /agent/variable} element</a></h4>
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
 *		<classpath>
 * 			<entry>/users/me/agent/target/my-delegate-1.0.0.jar</entry>
 * 			<entry>${repo-path}/asm/asm/3.1/asm-3.1.jar</entry>
 * 			<entry>${repo-path}/asm/asm-commons/3.1/asm-commons-3.1.jar</entry>
 * 			<entry>${repo-path}/asm/asm-util/3.1/asm-util-3.1.jar</entry>
 * 			<entry>${repo-path}/asm/asm-tree/3.1/asm-tree-3.1.jar</entry>
 *		</classpath>
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
 *		<classpath>
 * 			<entry>/users/me/agent/target/my-delegate-1.0.0.jar</entry>
 * 			<entry>${repo-path}/${asm-package}/asm/${asm-version}/asm-${asm-version}.jar</entry>
 * 			<entry>${repo-path}/${asm-package}/asm-commons/${asm-version}/asm-commons-${asm-version}.jar</entry>
 * 			<entry>${repo-path}/${asm-package}/asm-util/${asm-version}/asm-util-${asm-version}.jar</entry>
 * 			<entry>${repo-path}/${asm-package}/asm-tree/${asm-version}/asm-tree-${asm-version}.jar</entry>
 *		</classpath>
 *		<configuration>...</configuration>
 *	</agent>
 * </xmp>
 * 
 * 
 * 
 * <h4><a name="agent-delegate-element">{@code /agent/delegate} element</a></h4>
 * The {@code /agent/delegate} element is <b>mandatory</b> and its value is the name of the delegate class
 * as a fully qualified name (e.g. {@code com.hapiware.asm.TimeMachineAgentDelegate}).
 * <p>
 * 
 * The agent delegate class must have the following method (with the exact signature):
 * <blockquote>
 * 	<pre>
 * 		public static void premain(
 * 			java.util.regex.Pattern[] includePatterns,
 * 			java.util.regex.Pattern[] excludePatterns,
 * 			Object config,
 * 			Instrumentation instrumentation
 * 		)
 * 	</pre>
 * </blockquote>
 * 
 * where:
 * <ul>
 * 		<li>
 * 			{@code java.util.regex.Pattern[] includePatterns} has a list of regular expression
 * 			patterns to be used	to include classes for instrumentation.
 * 			See <a href="#agent-filter-element">{@code /agent/filter}</a>
 * 		</li>
 * 		<li>
 * 			{@code java.util.regex.Pattern[] excludePatterns} has a list of regular expression
 * 			patterns to be used	to set classes not to be instrumented.
 * 			See <a href="#agent-filter-element">{@code /agent/filter}</a>
 * 		</li>
 * 		<li>
 * 			{@code Object config} is the configuration object based on the
 * 			<a href="#agent-configuration-element">{@code /agent/configuration} element</a>.
 * 		</li>
 * 		<li>{@code Instrumentation instrumentation} has services to provide the instrumentation.</li>
 * </ul>
 * 
 * This {@code static void premain(java.util.regex.Pattern[], java.util.regex.Pattern[], Object, Instrumentation)}
 * method <b>can do all the same things</b> as defined for {@code static void premain(String, Instrumentation)}
 * method in the {@code java.lang.instrument} package description.  
 * 
 * 
 * 
 * <h4><a name="agent-classpath-element">{@code /agent/classpath} element</a></h4>
 * The {@code /agent/classpath} element is <b>mandatory</b> and is used to define the classpath
 * <b>for the agent <u>delegate</u> class</b>. This means that there is no need to put any of
 * the used libraries for the agent delegate class in to your environment classpath.
 * <p>
 * The {@code /agent/classpath} element must have at least one {@code <entry>} child element
 * but can have several. The only required classpath entry is the delegate agent (.jar file) itself.
 * However, usually there are other classpath entries for the libraries needed by the delegate
 * agent. Here is an example:
 * 
 * <xmp>
 * 	<?xml version="1.0" encoding="UTF-8" ?>
 *	<agent>
 *		<delegate>com.hapiware.agent.TimeMachineAgentDelegate</delegate>
 *		<classpath>
 * 			<entry>/users/me/agent/target/timemachine-delegate-1.0.0.jar</entry>
 * 			<entry>/usr/local/asm-3.1/lib/all/all-asm-3.1.jar</entry>
 *		</classpath>
 *		<configuration>...</configuration>
 *	</agent>
 * </xmp>
 * 
 * 
 * 
 * <h4><a name="agent-filter-element">{@code /agent/filter} element</a></h4>
 * The {@code /agent/filter} is <b>optional</b> and is used to filter classes to be
 * instrumented.
 * <p>
 * The {@code /agent/filter} element can have several {@code include} and/or {@code exclude}
 * elements but can have also none of them. Here is an example:
 * <xmp>
 * 	<?xml version="1.0" encoding="UTF-8" ?>
 * 	<agent>
 *		<delegate>com.hapiware.test.MyAgentDelegate</delegate>
 * 		<classpath>
 * 			<entry>/users/me/agent/target/my-delegate-1.0.0.jar</entry>
 * 		</classpath>
 * 		<filter>
 * 			<include>^com/hapiware/.*f[oi]x/.+</include>
 * 			<include>^com/mysoft/.+</include>
 * 			<exclude>^com/hapiware/.+/CreateCalculationForm</exclude>
 * 		</filter>
 * 		<configuration>...</configuration>
 * 	</agent>
 * </xmp>
 * 
 * <h5>{@code <include>} element</h5>
 * {@code <include>} element can be used for matching the possible candidates for instrumentation.
 * If none is defined then one pattern containing <b>{@code ".+"}</b> is assumed as a default value.
 * {@code <include>} element is a normal Java regular expression.
 * <p>
 * <b>Notice</b> that the class names are presented in the internal form of fully qualified class
 * names as defined in The Java Virtual Machine Specification (e.g. "java/util/List"). So, when
 * you create {@code <include>} and {@code <exclude>} elements, remember that package names are
 * separated with slash (/) instead of period (.).
 *
 * <h5>{@code <exclude>} element</h5>
 * {@code <exclude>} can be used to ensure that the instrumentation is not done for some classes.
 * {@code <exclude>} element is a normal Java regular expression.
 * <p>
 * <b>Notice</b> that the class names are presented in the internal form of fully qualified class
 * names as defined in The Java Virtual Machine Specification (e.g. "java/util/List"). So, when
 * you create {@code <include>} and {@code <exclude>} elements, remember that package names are
 * separated with slash (/) instead of period (.).
 * 
 * 
 * <h4><a name="agent-configuration-element">{@code /agent/configuration/} element</a></h4>
 * The {@code /agent/configuration/} element is <b>optional</b> and has all the necessary
 * configuration information for the agent delegate class. The exact structure can depend on
 * the programmer but there are some predefined structures as well. The configuration object
 * is delivered to the agent delegate's
 * {@code static void premain(java.util.regex.Pattern[], java.util.regex.Pattern[], Object, Instrumentation)}
 * method as an {@link Object} argument.
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
 * delivered to the agent delegate's
 * {@code static void premain(java.util.regex.Pattern[], java.util.regex.Pattern[], Object, Instrumentation)}
 * method as an {@link Object} argument. For example:
 * <xmp>
 * 	<?xml version="1.0" encoding="UTF-8" ?>
 *	<agent>
 *		<delegate>com.hapiware.test.MyAgentDelegate</delegate>
 *		<classpath>
 * 			<entry>/users/me/agent/target/my-delegate-1.0.0.jar</entry>
 *		</classpath>
 *	</agent>
 * </xmp>
 * which sends {@code null} to the
 * {@code MyAgentDelegate.premain(java.util.regex.Pattern[], java.util.regex.Pattern[], Object, Instrumentation)}
 * method as an {@link Object} argument.
 * 
 * <h5>{@code String}</h5>
 * If the {@code /agent/configuration/} element has only pure text (i.e. {@code String}), the
 * text string is delivered to the delegate's
 * {@code static void premain(java.util.regex.Pattern[], java.util.regex.Pattern[], Object, Instrumentation)}
 * method as an {@link Object} argument. For example:
 * <xmp>
 * 	<?xml version="1.0" encoding="UTF-8" ?>
 *	<agent>
 *		<delegate>com.hapiware.test.MyAgentDelegate</delegate>
 *		<classpath>
 * 			<entry>/users/me/agent/target/my-delegate-1.0.0.jar</entry>
 *		</classpath>
 *		<configuration>Show me!</configuration>
 *	</agent>
 * </xmp>
 * which sends "Show me!" to the
 * {@code MyAgentDelegate.premain(java.util.regex.Pattern[], java.util.regex.Pattern[], Object, Instrumentation)}
 * method as an {@link Object} argument.
 * 
 * <h5>{@code List<String>}</h5>
 * If the {@code /agent/configuration/} element has {@code <item>} child elements <u>without an 
 * attribute</u> then the {@code List<String>} is created which is in turn delivered to the agent
 * delegate's
 * {@code static void premain(java.util.regex.Pattern[], java.util.regex.Pattern[], Object, Instrumentation)}
 * method as an {@link Object} argument. For example:
 * <xmp>
 * 	<?xml version="1.0" encoding="UTF-8" ?>
 *	<agent>
 *		<delegate>com.hapiware.test.MyAgentDelegate</delegate>
 *		<classpath>
 * 			<entry>/users/me/agent/target/my-delegate-1.0.0.jar</entry>
 *		</classpath>
 *		<configuration>
 *			<item>One</item>
 *			<item>Two</item>
 *			<item>Three</item>
 *		</configuration>
 *	</agent>
 * </xmp>
 * which sends {@code List<String>} {"One", "Two", "Three"} to the
 * {@code MyAgentDelegate.premain(java.util.regex.Pattern[], java.util.regex.Pattern[], Object, Instrumentation)}
 * method as an {@link Object} argument.
 * 
 * <h5>{@code Map<String, String>}</h5>
 * If the {@code /agent/configuration/} element has {@code <item>} child elements <u>with a
 * {@code key} attribute</u> then the {@code Map<String, String>} is created which is in turn
 * delivered to the agent delegate's
 * {@code static void premain(java.util.regex.Pattern[], java.util.regex.Pattern[], Object, Instrumentation)}
 * method as an {@link Object} argument. For example:
 * <xmp>
 * 	<?xml version="1.0" encoding="UTF-8" ?>
 *	<agent>
 *		<delegate>com.hapiware.test.MyAgentDelegate</delegate>
 *		<classpath>
 * 			<entry>/users/me/agent/target/my-delegate-1.0.0.jar</entry>
 *		</classpath>
 *		<configuration>
 *			<item key="1">One</item>
 *			<item key="2">Two</item>
 *			<item key="3">Three</item>
 *		</configuration>
 *	</agent>
 * </xmp>
 * which sends {@code Map<String, String>} {{"1", "One"}, {"2", "Two"}, {"3", "Three"}} to the
 * {@code MyAgentDelegate.premain(java.util.regex.Pattern[], java.util.regex.Pattern[], Object, Instrumentation)}
 * method as an {@link Object} argument.
 * 
 * <h5>User defined configuration object</h5>
 * If the {@code /agent/configuration/} element has the {@code custom} child element defined,
 * then {@code public static Object unmarshall(org.w3c.dom.Element configElement)} method must
 * be defined to the agent delegate class in addition to {@code premain()} method. The
 * {@code unmarshall()} method is called with the {@code /agent/configuration/custom} element
 * as an argument. The system then delivers the returned {@code Object} directly to the agent
 * delegate's
 * {@code static void premain(java.util.regex.Pattern[], java.util.regex.Pattern[], Object, Instrumentation)}
 * method as an {@link Object} argument. This approach makes it possible to create different configuration
 * structures for the agent delegate's
 * {@code static void premain(java.util.regex.Pattern[], java.util.regex.Pattern[], Object, Instrumentation)}
 * method very flexibly.
 * <p>
 * {@code public static Object unmarshall(org.w3c.dom.Element configElement)} is assumed to return
 * a programmer's own configuration object. Here is an example:
 * <xmp>
 * 	<?xml version="1.0" encoding="UTF-8" ?>
 * 	<agent>
 * 		<delegate>com.hapiware.agent.FancyAgentDelegate</delegate>
 * 		<classpath>
 * 			<entry>/users/me/agent/target/fancy-delegate-1.0.0.jar</entry>
 * 			<entry>/usr/local/asm-3.1/lib/all/all-asm-3.1.jar</entry>
 * 		</classpath>
 * 		<filter>
 * 			<include>^com/hapiware/.*f[oi]x/.+</include>
 * 			<include>^com/mysoft/.+</include>
 * 			<exclude>^com/hapiware/.+/CreateCalculationForm</exclude>
 * 		</filter>
 * 		<configuration>
 * 			<custom>
 * 				<message>Hello World!</message>
 * 				<date>2010-3-13</date>
 * 			</custom>
 * 		</configuration>
 * 	</agent>
 * </xmp>
 * 
 * This assumes that {@code com.hapiware.asm.FancyAgentDelegate} class has
 * {@code public static Object unmarshall(org.w3c.dom.Element configElement)} method defined
 * to handle {@code <message>} and {@code <date>} elements from the {@code <configuration/custom>}
 * element. It is also assumed that the {@code Object} the {@code unmarshall()} method returns
 * can be properly handled (and type casted) in the
 * {@code static void premain(java.util.regex.Pattern[], java.util.regex.Pattern[], Object, Instrumentation)}
 * method.
 * 
 * 
 * @see java.lang.instrument
 * 
 * @author <a href="http://www.hapiware.com" target="_blank">hapi</a>
 *
 */
public class Agent
{
	private final static String PREMAIN_SIGNATURE =
		"static void premain(java.util.regex.Pattern[], java.util.regex.Pattern[], Object, Instrumentation)";
	
	
	/**
	 * This method is called before the main method call right after the JVM initialisation. 
	 * <p>
	 * <b>Notice</b> that this method follows the <i>fail fast</i> idiom and thus
	 * throws a runtime exception if there is something wrong in the configuration file.
	 * 
	 * @param agentArgs
	 * 		Same string which was given to {@code -javaagent} as <i>options</i> (see the class
	 * 		description).
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
					configElements.getClasspaths(),
					originalClassLoader
				);
			Thread.currentThread().setContextClassLoader(cl);
			
			
			Class<?> delegateAgentClass =
				(Class<?>)cl.loadClass(configElements.getDelegateAgentName());
			Object delegateConfiguration = unmarshall(delegateAgentClass, configElements);
			
			// Invokes the premain method of the delegate agent.
			delegateAgentClass.getMethod(
				"premain",
				new Class[] {Pattern[].class, Pattern[].class, Object.class, Instrumentation.class}
			).invoke(
				null,
				configElements.getIncludePatterns(),
				configElements.getExcludePatterns(),
				delegateConfiguration,
				instrumentation
			);
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
					PREMAIN_SIGNATURE + " method was not defined in \""
						+ configElements.getDelegateAgentName() + "\".",
					e
				);
		}
		catch(IllegalArgumentException e) {
			throw
				new ConfigurationError(
					"Argument mismatch with " + PREMAIN_SIGNATURE + " "
						+ "in \"" + configElements.getDelegateAgentName() + "\".",
					e
				);
		}
		catch(InvocationTargetException e) {
			throw
				new ConfigurationError(
					PREMAIN_SIGNATURE + " in \"" + configElements.getDelegateAgentName()
						+ "\" threw an exception.",
					e
				);
		}
		catch(IllegalAccessException e) {
			assert false : e;
		}
		finally {
			if(originalClassLoader != null)
				Thread.currentThread().setContextClassLoader(originalClassLoader);
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
			try {
				DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
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
							+ "\t->Make sure that the configuration file has been saved using "
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
			// Validate configuration document.
			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
			SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Source schemaFile =	new StreamSource(classLoader.getResourceAsStream("agent.xsd"));
			Schema schema = factory.newSchema(schemaFile);
			Validator validator = schema.newValidator();
			validator.validate(new DOMSource(configDocument));
			
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
			
			// /agent/classpath
			NodeList classpathEntries =
				(NodeList)xpath.evaluate(
					"/agent/classpath/entry",
					configDocument,
					XPathConstants.NODESET
				);
			List<String> classpaths = new ArrayList<String>();
			for(int i = 0; i < classpathEntries.getLength(); i++) {
				Node classpathEntry = classpathEntries.item(i).getFirstChild();
				classpaths.add(((Text)classpathEntry).getData());
			}
			
			// /agent/filter/include
			NodeList includeEntries = 
				(NodeList)xpath.evaluate(
					"/agent/filter/include",
					configDocument,
					XPathConstants.NODESET
				);
			List<Pattern> includePatterns = new ArrayList<Pattern>();
			for(int i = 0; i < includeEntries.getLength(); i++) {
				Node includeEntry = includeEntries.item(i).getFirstChild();
				if(includeEntry != null)
					includePatterns.add(Pattern.compile(((Text)includeEntry).getData()));
			}
			if(includePatterns.size() == 0)
				includePatterns.add(Pattern.compile(".+"));
			
			// /agent/filter/exclude
			NodeList excludeEntries = 
				(NodeList)xpath.evaluate(
					"/agent/filter/exclude",
					configDocument,
					XPathConstants.NODESET
				);
			List<Pattern> excludePatterns = new ArrayList<Pattern>();
			for(int i = 0; i < excludeEntries.getLength(); i++) {
				Node excludeEntry = excludeEntries.item(i).getFirstChild();
				if(excludeEntry != null)
					excludePatterns.add(Pattern.compile(((Text)excludeEntry).getData()));
			}
			
			// /agent/configuration
			Node configuration = 
				(Node)xpath.evaluate(
					"/agent/configuration",
					configDocument,
					XPathConstants.NODE
				);
			
			retVal = 
				new ConfigElements(
					classpaths,
					includePatterns,
					excludePatterns,
					delegateAgent,
					(Element)configuration
				);
			
		}
		catch(SAXException e) {
			throw
				new ConfigurationError(
					"Validting the agent configuration file \""
						+ configFileName + "\" didn't succeed.\n"
						+ "\t->" + e.getMessage(),
					e
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
	static Object unmarshall(Class<?> delegateAgentClass, ConfigElements configElements)
	{
		Element configElement = configElements.getConfigurationElement();
		if(configElement != null) {
			if(configElement.getElementsByTagName("item").getLength() > 0)
				return createCollectionConfiguration(configElements);
			else {
				Node firstNode = configElement.getFirstChild();
				Node targetNode = firstNode;
				boolean customElementFound = false;
				boolean textNodeFound = false;
				while(targetNode != null) {
					if(targetNode.getNodeName().equals("#text") && targetNode.getNodeValue().trim().length() > 0)
						textNodeFound = true;
					if(targetNode.getNodeName().equals("custom")) {
						customElementFound = true;
						break;
					}
					targetNode = targetNode.getNextSibling();
				}
				if(textNodeFound && customElementFound)
					throw
						new ConfigurationError(
							"/agent/configuration/custom element cannot co-exist with a text element "
							+ "(i.e. a pure string under /agent/configuration)."
						);
				if(!customElementFound)
					targetNode = firstNode;
				if(customElementFound) {
					try {
						// Invokes the unmarshaller.
						return
							delegateAgentClass.getMethod(
								"unmarshall",
								new Class[] {Element.class}
							).invoke(null, targetNode);
					}
					catch(NoSuchMethodException e) {
						throw
							new ConfigurationError(
								"static Object unmarshall(Element) method was not defined in \""
									+ delegateAgentClass.getName() + "\".",
								e
							);
					}
					catch(IllegalArgumentException e) {
						throw
							new ConfigurationError(
								"Argument mismatch with static Object unmarshall(Element) method "
									+ "in \"" + delegateAgentClass.getName() + "\".",
								e
							);
					}
					catch(InvocationTargetException e) {
						throw
							new ConfigurationError(
								"static Object unmarshall(Element) method "
									+ "in \"" + delegateAgentClass.getName()
									+ "\" threw an exception.",
								e
							);
					}
					catch(IllegalAccessException e) {
						assert false: e;
						return null;
					}
				}
				else {
					if(targetNode == null || targetNode.getNodeValue().trim().length() == 0)
						throw
							new ConfigurationError(
								"/agent/configuration does not have a proper string (i.e. text element) "
									+ "(or any other elements)"
							);
					
					return targetNode.getNodeValue().trim();
				}
			}
		}
		else
			return null;
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
		private final List<Pattern> includePatterns;
		private final List<Pattern> excludePatterns;
		private final List<URL> classpaths;
		private final Element configurationElement;
		
		public ConfigElements(
			List<String> classpaths,
			List<Pattern> includePatterns,
			List<Pattern> excludePatterns,
			String delegateAgentName,
			Element configElement
		)
			throws
				MalformedURLException
		{
			List<URL> classpathsAsURLs = new ArrayList<URL>();
			for(String classpath : classpaths) {
				File file = new File(classpath);
				if(!file.exists())
					throw
						new ConfigurationError(
							"Class path entry \"" + file + "\" does not exist."
						);
				classpathsAsURLs.add(file.toURI().toURL());
			}
			this.classpaths = Collections.unmodifiableList(classpathsAsURLs);
			
			this.includePatterns = Collections.unmodifiableList(includePatterns);
			this.excludePatterns = Collections.unmodifiableList(excludePatterns);

			this.delegateAgentName = delegateAgentName;
			this.configurationElement = configElement;
		}

		public Element getConfigurationElement()
		{
			return configurationElement;
		}

		public String getDelegateAgentName()
		{
			return delegateAgentName;
		}

		public URL[] getClasspaths()
		{
			return classpaths.toArray(new URL[0]);
		}

		public Pattern[] getIncludePatterns()
		{
			return includePatterns.toArray(new Pattern[0]);
		}
		
		public Pattern[] getExcludePatterns()
		{
			return excludePatterns.toArray(new Pattern[0]);
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
