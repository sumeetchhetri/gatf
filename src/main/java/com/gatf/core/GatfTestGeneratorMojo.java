package com.gatf.core;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.jackson.map.ObjectMapper;
import org.reficio.ws.builder.SoapBuilder;
import org.reficio.ws.builder.SoapOperation;
import org.reficio.ws.builder.core.Wsdl;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.gatf.test.TestCase;
import com.gatf.view.ViewField;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.core.util.QuickWriter;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import com.thoughtworks.xstream.io.xml.XppDriver;

/**
 * @author Sumeet Chhetri<br/>
 *         The gatf maven plugin main class which creates testcases for all the rest-ful/soap-wsdl services mentioned. <br/>
 * 
 * <pre>
 * {@code
 * 	<plugin>
		<groupId>com.test</groupId>
		<artifactId>gatf-plugin</artifactId>
		<version>1.0</version>
		<configuration>
			<testPaths>
				<docTestPath>com.mtel.services.*</docTestPath>
			</testPaths>
			<soapWsdlKeyPairs>
				<soapWsdlKeyPair>AuthService,http://localhost:8081/soap/auth?wsdl</soapWsdlKeyPair>
				<soapWsdlKeyPair>ExampleService,http://localhost:8081/soap/example?wsdl</soapWsdlKeyPair>
				<soapWsdlKeyPair>MessageService,http://localhost:8081/soap/messages?wsdl</soapWsdlKeyPair>
				<soapWsdlKeyPair>UserService,http://localhost:8081/soap/user?wsdl</soapWsdlKeyPair>
			</soapWsdlKeyPairs>
			<urlPrefix>rest</urlPrefix>
			<inDataType>json</inDataType>
			<resourcepath>src/test/resources/generated</resourcepath>
			<enabled>true</enabled>
		</configuration>
		<executions>
			<execution>
				<goals>
					<goal>gatf</goal>
				</goals>
			</execution>
		</executions>
	</plugin>
 *   }
 * </pre>
 */
@Mojo(name = "gatf", aggregator = false, executionStrategy = "always", inheritByDefault = true, instantiationStrategy = InstantiationStrategy.PER_LOOKUP, defaultPhase = LifecyclePhase.GENERATE_TEST_RESOURCES, requiresDependencyResolution = ResolutionScope.TEST, requiresDirectInvocation = false, requiresOnline = false, requiresProject = true, threadSafe = true)
public class GatfTestGeneratorMojo extends AbstractMojo
{

    @Component
    protected MavenSession session;

    @Component
    private MavenProject project;

    @Parameter(alias = "testPaths")
    private String[] testPaths;

    /**
     * @return the testPaths
     */
    public String[] getTestPaths()
    {
        return testPaths;
    }

    /**
     * @param testPaths packageNames/classes to set
     */
    public void setTestPaths(String[] testPaths)
    {
        this.testPaths = testPaths;
    }

    @Parameter(alias = "soapWsdlKeyPairs")
    private String[] soapWsdlKeyPairs;
    
    public String[] getSoapWsdlKeyPairs() {
		return soapWsdlKeyPairs;
	}

	public void setSoapWsdlKeyPairs(String[] soapWsdlKeyPairs) {
		this.soapWsdlKeyPairs = soapWsdlKeyPairs;
	}

	@Parameter(alias = "urlPrefix")
    private String urlPrefix;

    public String getUrlPrefix()
    {
        return urlPrefix;
    }

    public void setUrlPrefix(String urlPrefix)
    {
        this.urlPrefix = urlPrefix;
    }

    @Parameter(alias = "resourcepath")
    private String resourcepath;

    /**
     * @return the resourcepath
     */
    public String getResourcepath()
    {
        return resourcepath;
    }

    /**
     * @param resourcepath the resourcepath to set
     */
    public void setResourcepath(String resourcepath)
    {
        this.resourcepath = resourcepath;
    }
    
    @Parameter(alias = "debug-enabled")
    private boolean debugEnabled;

    /**
     * @return the debugEnabled
     */
    public boolean isDebugEnabled()
    {
        return debugEnabled;
    }

    /**
     * @param debugEnabled the debugEnabled to set
     */
    public void setDebugEnabled(boolean debugEnabled)
    {
        this.debugEnabled = debugEnabled;
    }

    @Parameter(alias = "requestDataType")
    private String requestDataType;
    
    public String getInDataType() {
		return requestDataType;
	}

	public void setInDataType(String requestDataType) {
		this.requestDataType = requestDataType;
	}

	@Parameter(alias = "enabled")
	private boolean enabled;
	
	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/**
     * @param classes Generates all testcases for all the rest-full services found in the classes discovered
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void generateRestTestCases(List<Class> classes)
    {
        try
        {
            Map<String,Integer> classTypes = new HashMap<String,Integer>();
            for (Class claz : classes)
            {
                classTypes.put(claz.getSimpleName(), 0);

                Annotation theClassPath = claz.getAnnotation(Path.class);
                if (theClassPath == null)
                    theClassPath = claz.getAnnotation(RequestMapping.class);

                // Will create only documentation if this is not a rest ful web service
                if (theClassPath != null)
                {
                	List<TestCase> tcases = new ArrayList<TestCase>();
                    Method[] methods = claz.getMethods();
                    for (Method method : methods)
                    {
                        Annotation theMethodPath = method.getAnnotation(Path.class);
                        if (theMethodPath == null)
                            theMethodPath = method.getAnnotation(RequestMapping.class);
                        if (theMethodPath != null)
                        {
                        	TestCase tcase = new TestCase();
                            String completeServicePath = null;
                            String httpMethod = "";
                            String consumes = "text/plain";

                            if (theClassPath instanceof Path && theMethodPath instanceof Path)
                            {
                                Path cpath = (Path) theClassPath;
                                Path mpath = (Path) theMethodPath;
                                completeServicePath = cpath.value() + mpath.value();
                            }
                            else if (theClassPath instanceof RequestMapping
                                    && theMethodPath instanceof RequestMapping)
                            {
                                RequestMapping cpath = (RequestMapping) theClassPath;
                                RequestMapping mpath = (RequestMapping) theMethodPath;
                                completeServicePath = cpath.value()[0] + mpath.value()[0];
                                httpMethod = mpath.method()[0].name();
                                consumes = mpath.consumes()[0];
                            }
                            else
                            {
                                throw new Exception("Invalid Annotation found on the Service class - "
                                        + claz.getSimpleName());
                            }

                            if (completeServicePath != null && completeServicePath.charAt(0) == '/')
                            {
                                completeServicePath = completeServicePath.substring(1);
                            }

                            String url = (getUrlPrefix() != null ? getUrlPrefix() : "");
                            if (!url.trim().isEmpty() && url.trim().charAt(url.trim().length() - 1) != '/')
                                url = url.trim() + "/";
                            url += completeServicePath;

                            String completeServiceSubmitPath = url;
                            Class<?>[] argTypes = method.getParameterTypes();
                            Annotation[][] argAnot = method.getParameterAnnotations();

                            ViewField contentvf = null;
                            Map<String, String> params = new HashMap<String, String>();
                            Map<String, String> hparams = new HashMap<String, String>();
                            for (int i = 0; i < argTypes.length; i++)
                            {
                            	Annotation[] annotations = getRestArgAnnotation(argAnot[i]);
                                if (annotations[0]!=null)
                                {
                                    String formpnm = null;

                                    if (annotations[0] instanceof FormParam)
                                    {
                                    	formpnm = ((FormParam) annotations[0]).value();
                                    	if(annotations[1]!=null)
                                    		params.put(formpnm, ((DefaultValue)annotations[1]).value());
                                    	else
                                    		params.put(formpnm, String.valueOf(getPrimitiveValue(argTypes[i])));
                                    	continue;
                                    }
                                    
                                    if (annotations[0] instanceof RequestParam)
                                    {
                                        if (completeServiceSubmitPath.indexOf("?") == -1)
                                            completeServiceSubmitPath += "?";
                                        if(((RequestParam) annotations[0]).defaultValue()!=null)
                                        	completeServiceSubmitPath += ((RequestParam) annotations[0]).value() + "={"
                                                    + ((RequestParam) annotations[0]).defaultValue() + "}&";
                                        else
                                        	completeServiceSubmitPath += ((RequestParam) annotations[0]).value() + "={"
                                        			+ ((RequestParam) annotations[0]).value() + "}&";
                                        continue;
                                    }

                                    if (annotations[0] instanceof QueryParam)
                                    {
                                        if (completeServiceSubmitPath.indexOf("?") == -1)
                                            completeServiceSubmitPath += "?";
                                        if(annotations[1]!=null)
                                        	completeServiceSubmitPath += ((QueryParam) annotations[0]).value() + "={"
                                                    + ((DefaultValue)annotations[1]).value() + "}&";
                                        else
                                        	completeServiceSubmitPath += ((QueryParam) annotations[0]).value() + "={"
                                                    + ((QueryParam) annotations[0]).value() + "}&";
                                        continue;
                                    }

                                    if (annotations[0] instanceof HeaderParam)
                                    {
                                        formpnm = ((HeaderParam) annotations[0]).value();
                                    	if(annotations[1]!=null)
                                    		hparams.put(formpnm, ((DefaultValue)annotations[1]).value());
                                    	else
                                    		hparams.put(formpnm, String.valueOf(getPrimitiveValue(argTypes[i])));
                                        continue;
                                    }
                                }
                                else
                                {
                                	ViewField vf = getViewField(argTypes[i]);
                                    if(vf!=null) {
                                    	contentvf = vf;
                                    	break;
                                    }
                                }
                            }

                            classTypes.put(claz.getSimpleName(), classTypes.get(claz.getSimpleName()) + 1);

                            Annotation hm = method.getAnnotation(POST.class);
                            if (hm != null)
                            {
                                httpMethod = "POST";
                            }
                            else
                            {
                                hm = method.getAnnotation(GET.class);
                                if (hm != null)
                                {
                                    httpMethod = "GET";
                                }
                                else
                                {
                                    hm = method.getAnnotation(PUT.class);
                                    if (hm != null)
                                    {
                                        httpMethod = "PUT";
                                    }
                                    else
                                    {
                                        hm = method.getAnnotation(DELETE.class);
                                        if (hm != null)
                                        {
                                            httpMethod = "DELETE";
                                        }
                                    }
                                }
                            }

                            Annotation annot = method.getAnnotation(Consumes.class);
                            if (annot != null)
                            {
                                consumes = ((Consumes) annot).value()[0];
                            }
                            
                            String produces = null;
                            annot = method.getAnnotation(Produces.class);
                            if (annot != null)
                            {
                            	produces = ((Produces) annot).value()[0];
                            }

                            String content = "";
                            if (!params.isEmpty() || consumes.equals(MediaType.APPLICATION_FORM_URLENCODED))
                            {
                            	for (Map.Entry<String, String> entry: params.entrySet()) {
                            		content += entry.getKey() + "=" + entry.getValue() + "&";
								}
                            }
                            else if(("JSON".equalsIgnoreCase(getInDataType()) || consumes.equals(MediaType.APPLICATION_JSON))
                            		&& contentvf!=null && contentvf.getValue()!=null)
                            {
                            	content = new ObjectMapper().writeValueAsString(contentvf.getValue());
                            }
                            else if (("XML".equalsIgnoreCase(getInDataType()) || consumes.equals(MediaType.APPLICATION_XML))
                            		&& contentvf!=null && contentvf.getValue()!=null)
                            {
                            	JAXBContext context = JAXBContext.newInstance(contentvf.getClaz());
                                Marshaller m = context.createMarshaller();
                                m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
                                StringWriter writer = new StringWriter();
                                m.marshal(contentvf.getValue(), writer);
                                content = writer.toString();
                            }
                            
                            tcase.setUrl(completeServiceSubmitPath);
                            tcase.setMethod(httpMethod);
                            tcase.setContent(content);
                            tcase.setName(claz.getName()+"."+method.getName());
                            tcase.setDescription(tcase.getName());
                            tcase.setDetailedLog(false);
                            tcase.setSkipTest(false);
                            tcase.setSecure(false);
                            tcase.setSoapBase(false);
                            tcase.setExpectedResCode(200);
                            tcase.setExpectedResContentType(produces);
                            tcase.setHeaders(hparams);
                            
                            tcases.add(tcase);
                        }
                    }
                    if(!tcases.isEmpty()) {
                    	XStream xstream = new XStream(
                			new XppDriver() {
                				public HierarchicalStreamWriter createWriter(Writer out) {
                					return new PrettyPrintWriter(out) {
                						boolean cdata = false;
                						public void startNode(String name, Class clazz){
                							super.startNode(name, clazz);
                							cdata = (name.equals("content") || name.equals("expectedResContent"));
                						}
                						protected void writeText(QuickWriter writer, String text) {
                							if(cdata) {
                								writer.write("<![CDATA[");
                								writer.write(text);
                								writer.write("]]>");
                							} else {
                								writer.write(text);
                							}
                						}
                					};
                				}
                			}
                		);
                		xstream.processAnnotations(new Class[]{TestCase.class});
                		xstream.alias("TestCases", List.class);
                		
                		String file = getResourcepath() + "/" + claz.getName().replaceAll("\\.", "_") + "_testcases_rest.xml";
                		xstream.toXML(tcases, new FileOutputStream(file));
                    }
                }
            }
        }
        catch (Exception e)
        {
            getLog().error(e);
        }
    }


    private Annotation[] getRestArgAnnotation(Annotation[] annotations) {
    	
    	Annotation[] annot = new Annotation[2];
    	if(annotations!=null && annotations.length>0) 
    	{
	    	for (Annotation annotation : annotations) 
	    	{
				if(annotation instanceof FormParam || annotation instanceof QueryParam
		                || annotation instanceof RequestParam || annotation instanceof HeaderParam
		                || annotation instanceof PathParam)
					annot[0] = annotation;
				if(annotation instanceof DefaultValue)
					annot[1] = annotation;
			}
    	}
		return annot;
	}

	public static String getFileExtension(String fileName)
    {
        if (fileName == null || fileName.indexOf(".") == -1)
            return null;
        return fileName.substring(fileName.lastIndexOf("."));
    }

    /**
     * @param sourceFile
     * @param destFile
     * @throws IOException Copy a file to some desired location
     */
    @SuppressWarnings("resource")
    public static void copyFile(File sourceFile, File destFile) throws IOException
    {
        if (!destFile.exists())
        {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;
        try
        {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();

            // previous code: destination.transferFrom(source, 0, source.size());
            // to avoid infinite loops, should be:
            long count = 0;
            long size = source.size();
            while ((count += destination.transferFrom(source, count, size - count)) < size)
                ;
        }
        finally
        {
            if (source != null)
            {
                source.close();
            }
            if (destination != null)
            {
                destination.close();
            }
        }
    }

    /**
     * Scans all classes accessible from the context class loader which belong to the given package and subpackages.
     * 
     * @param packageName The base package
     * @return The classes
     * @throws ClassNotFoundException
     * @throws IOException
     */
    @SuppressWarnings({ "rawtypes" })
    private static List<Class> getClasses(String packageName) throws ClassNotFoundException, IOException
    {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assert classLoader != null;
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<File>();
        while (resources.hasMoreElements())
        {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }
        ArrayList<Class> classes = new ArrayList<Class>();
        for (File directory : dirs)
        {
            classes.addAll(findClasses(directory, packageName));
        }
        return classes;
    }

    /**
     * Recursive method used to find all classes in a given directory and subdirs.
     * 
     * @param directory The base directory
     * @param packageName The package name for classes found inside the base directory
     * @return The classes
     * @throws ClassNotFoundException
     */
    @SuppressWarnings({ "rawtypes" })
    private static List<Class> findClasses(File directory, String packageName) throws ClassNotFoundException
    {
        List<Class> classes = new ArrayList<Class>();
        if (!directory.exists())
        {
            return classes;
        }
        File[] files = directory.listFiles();
        for (File file : files)
        {
            if (file.isDirectory())
            {
                assert !file.getName().contains(".");
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            }
            else if (file.getName().endsWith(".class"))
            {
                Class claz = Thread.currentThread().getContextClassLoader()
                        .loadClass(packageName + '.' + file.getName().substring(0, file.getName().length() - 6));
                if (claz != null)
                {
                    classes.add(claz);
                }
                else
                {
                    classes.add(Class.forName(packageName + '.'
                            + file.getName().substring(0, file.getName().length() - 6)));
                }
            }
        }
        return classes;
    }

    /**
     * @param claz
     * @param parameters
     * @param naming
     * @param formpnm
     * @param isheaderParam
     * @param heirarchy
     * @throws Exception Add new ViewFeild objects that represnt the form elements on the test page of the give rest
     *             full service
     */
    private ViewField getViewField(@SuppressWarnings("rawtypes") Class claz) throws Exception
    {
    	ViewField viewField = null;
        if ((claz.equals(Integer.class) || claz.equals(String.class) || claz.equals(Short.class)
                || claz.equals(Long.class) || claz.equals(Double.class) || claz.equals(Float.class)
                || claz.equals(Boolean.class) || claz.equals(int.class) || claz.equals(short.class)
                || claz.equals(long.class) || claz.equals(double.class) || claz.equals(float.class)
                || claz.equals(boolean.class) || claz.equals(Number.class) || claz.equals(Date.class)))
        {
            viewField = new ViewField();
            viewField.setClaz(claz);
            viewField.setValue(getPrimitiveValue(claz));
        }
        else if (claz.equals(Map.class) || claz.equals(HashMap.class)
                || claz.equals(LinkedHashMap.class))
        {
        	viewField = new ViewField();
            viewField.setClaz(claz);
            viewField.setValue(getMapValue(claz, claz.getTypeParameters()));
        }
        else if (claz.equals(List.class) || claz.equals(ArrayList.class)
                || claz.equals(LinkedList.class) || claz.equals(Set.class) 
                || claz.equals(HashSet.class) || claz.equals(LinkedHashSet.class))
        {
        	viewField = new ViewField();
            viewField.setClaz(claz);
            viewField.setValue(getListSetValue(claz, claz.getTypeParameters()));
        }
        else
        {
            viewField = new ViewField();
            viewField.setClaz(claz);
            viewField.setValue(getObject(claz));
        }
        return viewField;
    }
    
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object getObject(Class claz) throws Exception {

    	if(claz.isEnum())return claz.getEnumConstants()[0];
    	
    	if (claz.equals(Map.class) || claz.equals(HashMap.class)
                || claz.equals(LinkedHashMap.class))
        {
    		return getMapValue(claz, claz.getTypeParameters());
        }
        else if (claz.equals(List.class) || claz.equals(ArrayList.class)
                || claz.equals(LinkedList.class) || claz.equals(Set.class) 
                || claz.equals(HashSet.class) || claz.equals(LinkedHashSet.class)
                || claz.equals(Collection.class))
        {
        	return getListSetValue(claz, claz.getTypeParameters());
        }
    	
    	Constructor cons = null;
    	try {
    		cons = claz.getConstructor(new Class[]{});
		} catch (Exception e) {
			getLog().error("No public no-args constructor found for class " + claz.getName());
			return null;
		}
    	
    	Object object = cons.newInstance(new Object[]{});
        PropertyDescriptor[] propertyDescriptors = Introspector.getBeanInfo(claz, Object.class).getPropertyDescriptors();
        for (PropertyDescriptor field : propertyDescriptors)
        {
            if (field.getPropertyType().equals(Integer.class) || field.getPropertyType().equals(String.class)
                    || field.getPropertyType().equals(Short.class) || field.getPropertyType().equals(Long.class)
                    || field.getPropertyType().equals(Double.class) || field.getPropertyType().equals(Float.class)
                    || field.getPropertyType().equals(Boolean.class) || field.getPropertyType().equals(int.class)
                    || field.getPropertyType().equals(short.class) || field.getPropertyType().equals(long.class)
                    || field.getPropertyType().equals(double.class) || field.getPropertyType().equals(float.class)
                    || field.getPropertyType().equals(boolean.class) || field.getPropertyType().equals(Number.class)
                    || field.getPropertyType().equals(Date.class))
            {
                field.getWriteMethod().invoke(object, getPrimitiveValue(field.getPropertyType()));
            }
            else if (field.getPropertyType().equals(Map.class) || field.getPropertyType().equals(HashMap.class)
                    || field.getPropertyType().equals(LinkedHashMap.class))
            {
            	ParameterizedType type = (ParameterizedType) field.getReadMethod().getGenericReturnType();
            	field.getWriteMethod().invoke(object, getMapValue(field.getPropertyType(), type.getActualTypeArguments()));
            }
            else if (field.getPropertyType().equals(List.class) || field.getPropertyType().equals(ArrayList.class)
                    || field.getPropertyType().equals(LinkedList.class) || field.getPropertyType().equals(Set.class) 
                    || field.getPropertyType().equals(HashSet.class) || field.getPropertyType().equals(LinkedHashSet.class)
                    || field.getPropertyType().equals(Collection.class))
            {
            	ParameterizedType type = (ParameterizedType) field.getReadMethod().getGenericReturnType();
            	field.getWriteMethod().invoke(object, getListSetValue(field.getPropertyType(), type.getActualTypeArguments()));
            }
            else if (!claz.equals(field.getPropertyType()))
            {
            	Object fieldval = getObject(field.getPropertyType());
            	field.getWriteMethod().invoke(object, fieldval);
            }
            else if (claz.equals(field.getPropertyType()))
            {
                getLog().info("Ignoring recursive fields...");
            }
        }
    
        return object;
    }
    
    private Object getObject(Type claz) throws Exception {
    	ParameterizedType type = (ParameterizedType)claz;
    	if (type.getRawType().equals(Map.class) || type.getRawType().equals(HashMap.class)
                || type.getRawType().equals(LinkedHashMap.class))
        {
    		return getMapValue(type.getRawType(), type.getActualTypeArguments());
        }
        else if (type.getRawType().equals(List.class) || type.getRawType().equals(ArrayList.class)
                || type.getRawType().equals(LinkedList.class) || type.getRawType().equals(Set.class) 
                || type.getRawType().equals(HashSet.class) || type.getRawType().equals(LinkedHashSet.class)
                || type.getRawType().equals(Collection.class))
        {
        	return getListSetValue(type.getRawType(), type.getActualTypeArguments());
        }
    	return null;
    }
    
    
    private Object getPrimitiveValue(Type claz) {
    	 if (isPrimitive(claz))
    	 {
    		 if(claz.equals(boolean.class) || claz.equals(Boolean.class)) {
    			 Random rand = new Random();
    			 return rand.nextBoolean();
    		 } else if(claz.equals(Date.class)) {
    			 return new Date();
    		 } else if(claz.equals(Double.class) || claz.equals(double.class)) {
    			 Random rand = new Random(12345678L);
    			 return rand.nextDouble();
    		 } else if(claz.equals(Float.class) || claz.equals(float.class)) {
    			 Random rand = new Random(12345678L);
    			 return rand.nextFloat();
    		 } else if(claz.equals(String.class)) {
    			 return RandomStringUtils.randomAlphabetic(10);
    		 } else {
    			 Random rand = new Random();
    			 return rand.nextInt((short)123);
    		 }
    	 }
    	 return null;
    }
    
    private boolean isPrimitive(Type claz) {
    	return (claz.equals(Integer.class) || claz.equals(String.class) || claz.equals(Short.class)
                || claz.equals(Long.class) || claz.equals(Double.class) || claz.equals(Float.class)
                || claz.equals(Boolean.class) || claz.equals(int.class) || claz.equals(short.class)
                || claz.equals(long.class) || claz.equals(double.class) || claz.equals(float.class)
                || claz.equals(boolean.class) || claz.equals(Number.class) || claz.equals(Date.class));
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
	private Object getMapValue(Type type, Type[] types) throws Exception {
    	
    	Class claz = null;
    	if(type.equals(Map.class) || type.equals(HashMap.class)
                || type.equals(LinkedHashMap.class))
    		claz = HashMap.class;
    	
    	Constructor cons = claz.getConstructor(new Class[]{});
    	Object object = cons.newInstance(new Object[]{});
    	
    	for (int i = 0; i < 3; i++)
    	{
	    	Object k = null;
	    	if(isPrimitive(types[0])) {
	    		k = getPrimitiveValue(types[0]);
	    	} else {
	    		if(types[0] instanceof Class)
	    			k = getObject((Class)types[0]);
	    		else
	    		{
	    			ParameterizedType typk = (ParameterizedType)types[0];
	    			k = getObject(typk);
	    		}
	    	}
	    	Object v = null;
	    	if(isPrimitive(types[1])) {
	    		v = getPrimitiveValue(types[1]);
	    	} else {
	    		if(types[1] instanceof Class)
	    			v = getObject((Class)types[1]);
	    		else
	    			v = getObject(types[1]);
	    	}
    		((Map)object).put(k, v);
    	}
    	return object;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
	private Object getListSetValue(Type type, Type[] types) throws Exception {
    	
    	Class claz = null;
    	if(type.equals(List.class) || type.equals(Collection.class)
    			|| type.equals(ArrayList.class) || type.equals(LinkedList.class))
    		claz = ArrayList.class;
    	else if(type.equals(Set.class) || type.equals(HashSet.class) || type.equals(LinkedHashSet.class))
    		claz = HashSet.class;
    	
    	Constructor cons = claz.getConstructor(new Class[]{});
    	Object object = cons.newInstance(new Object[]{});
    	
        if (types.length == 1)
        {
        	for (int i = 0; i < 3; i++) {
	        	Object v = null;
	        	if(isPrimitive(types[0])) {
	        		v = getPrimitiveValue(types[0]);
	        	} else {
		    		if(types[0] instanceof Class)
		    			v = getObject((Class)types[0]);
		    		else
		    			v = getObject(types[0]);
		    	}
	        	((Collection)object).add(v);
			}
        }
    	return object;
    }
    

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private ClassLoader getClassLoader()
    {
        try
        {
            List classpathElements = project.getCompileClasspathElements();
            classpathElements.add(project.getBuild().getOutputDirectory());
            classpathElements.add(project.getBuild().getTestOutputDirectory());
            URL[] urls = new URL[classpathElements.size()];
            for (int i = 0; i < classpathElements.size(); i++)
            {
                urls[i] = new File((String) classpathElements.get(i)).toURI().toURL();
            }
            return new URLClassLoader(urls, getClass().getClassLoader());
        }
        catch (Exception e)
        {
            getLog().error("Couldn't get the classloader.");
        }
        return getClass().getClassLoader();
    }

    /**
     * @param zipFile
     * @param directoryToExtractTo Provides file unzip functionality
     */
    public void unzipZipFile(InputStream zipFile, String directoryToExtractTo)
    {
        ZipInputStream in = new ZipInputStream(zipFile);
        try
        {
            File directory = new File(directoryToExtractTo);
            if (!directory.exists())
            {
                directory.mkdirs();
                getLog().info("Creating directory for Extraction...");
            }
            ZipEntry entry = in.getNextEntry();
            while (entry != null)
            {
                try
                {
                    File file = new File(directory, entry.getName());
                    if (entry.isDirectory())
                    {
                        file.mkdirs();
                    }
                    else
                    {
                        FileOutputStream out = new FileOutputStream(file);
                        byte[] buffer = new byte[2048];
                        int len;
                        while ((len = in.read(buffer)) > 0)
                        {
                            out.write(buffer, 0, len);
                        }
                        out.close();
                    }
                    in.closeEntry();
                    entry = in.getNextEntry();
                }
                catch (Exception e)
                {
                    getLog().error(e);
                }
            }
        }
        catch (IOException ioe)
        {
            getLog().error(ioe);
            return;
        }
    }

    public static void main(String[] args) throws Exception
    {
    	if(args.length>0) {
    		
    		InputStream io = new FileInputStream(args[0]);
    		XStream xstream = new XStream(new XppDriver());
    		xstream.processAnnotations(new Class[]{GatfConfiguration.class});
    		xstream.alias("testPaths", String[].class);
    		xstream.alias("testPath", String.class);
    		xstream.alias("soapWsdlKeyPairs", String[].class);
    		xstream.alias("soapWsdlKeyPair", String.class);
    		
    		GatfConfiguration config = (GatfConfiguration)xstream.fromXML(io);
    		
    		GatfTestGeneratorMojo testGenerator = new GatfTestGeneratorMojo();
    		testGenerator.setDebugEnabled(false);
    		testGenerator.setEnabled(config.isEnabled());
    		testGenerator.setInDataType(config.getRequestDataType());
    		testGenerator.setTestPaths(config.getTestPaths());
    		testGenerator.setSoapWsdlKeyPairs(config.getSoapWsdlKeyPairs());
    		testGenerator.setUrlPrefix(config.getUrlPrefix());
    		testGenerator.setResourcepath(config.getResourcepath());
    		testGenerator.execute();
    	}
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @SuppressWarnings("rawtypes")
    public void execute()
    {
    	if(!isEnabled())
    	{
    		getLog().info("Skipping gatf-plugin execution....");
    		return;
    	}
    	
        Thread currentThread = Thread.currentThread();
        ClassLoader oldClassLoader = currentThread.getContextClassLoader();
        try
        {
            currentThread.setContextClassLoader(getClassLoader());
            getLog().info("Inside execute");
            if (getTestPaths() == null)
            {
                getLog().info("Nothing to generate..");
                return;
            }
            List<Class> allClasses = new ArrayList<Class>();
            if (getTestPaths() != null)
            {
                for (String item : getTestPaths())
                {
                    if (item.endsWith(".*"))
                    {
                        List<Class> classes = getClasses(item.substring(0, item.indexOf(".*")));
                        if (classes != null && classes.size() > 0)
                        {
                            allClasses.addAll(classes);
                            getLog().info("Adding package " + item);
                        }
                        else
                        {
                            getLog().error("Error:package not found - " + item);
                        }
                    }
                    else
                    {
                        try
                        {
                            allClasses.add(Thread.currentThread().getContextClassLoader().loadClass(item));
                            getLog().info("Adding class " + item);
                        }
                        catch (Exception e)
                        {
                            getLog().error("Error:class not found - " + item);
                        }
                    }
                }
            }
            generateRestTestCases(allClasses);
            generateSoapTestCases();
            
            getLog().info("Done execute");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            currentThread.setContextClassLoader(oldClassLoader);
        }
    }

	private void generateSoapTestCases() {
		try {
			StringBuilder build = new StringBuilder();
			for (String wsdlLoc : getSoapWsdlKeyPairs()) {
				if(!wsdlLoc.trim().isEmpty())
				{
					String[] wsdlLocParts = wsdlLoc.split(",");
					getLog().info("Started Parsing WSDL location - " + wsdlLocParts[1]);
					
					Wsdl wsdl = Wsdl.parse(wsdlLocParts[1]);
					for (QName bindingName : wsdl.getBindings()) {
						SoapBuilder builder = wsdl.getBuilder(bindingName);
						List<TestCase> tcases = new ArrayList<TestCase>();
						for (SoapOperation operation : builder.getOperations()) {
							String request = builder.buildInputMessage(operation);
							
							TestCase tcase = new TestCase();
    						tcase.setUrl(builder.getServiceUrls().get(0));
                            tcase.setMethod(HttpMethod.POST);
                            tcase.setContent(request);
                            tcase.setName(wsdlLocParts[0]+"."+operation.getOperationName());
                            tcase.setDescription(tcase.getName());
                            tcase.setDetailedLog(false);
                            tcase.setSkipTest(false);
                            tcase.setSecure(false);
                            tcase.setSoapBase(false);
                            tcase.setExpectedResCode(200);
                            tcase.setExpectedResContentType(MediaType.APPLICATION_XML);
                            tcase.getHeaders().put("SOAPAction", operation.getSoapAction());
                            
                            tcases.add(tcase);
                            if(!tcases.isEmpty()) 
                            {
                            	XStream xstream = new XStream(
                        			new XppDriver() {
                        				public HierarchicalStreamWriter createWriter(Writer out) {
                        					return new PrettyPrintWriter(out) {
                        						boolean cdata = false;
                        						public void startNode(String name, @SuppressWarnings("rawtypes") Class clazz){
                        							super.startNode(name, clazz);
                        							cdata = (name.equals("content") || name.equals("expectedResContent"));
                        						}
                        						protected void writeText(QuickWriter writer, String text) {
                        							if(cdata) {
                        								writer.write("<![CDATA[");
                        								writer.write(text);
                        								writer.write("]]>");
                        							} else {
                        								writer.write(text);
                        							}
                        						}
                        					};
                        				}
                        			}
                        		);
                        		xstream.processAnnotations(new Class[]{TestCase.class});
                        		xstream.alias("TestCases", List.class);
                        		
                        		String file = getResourcepath() + "/" + wsdlLocParts[0] + "_testcases_soap.xml";
                        		xstream.toXML(tcases, new FileOutputStream(file));
                            }
							getLog().info("Adding message for SOAP operation - " + operation.getOperationName());
						}
					}
					getLog().info("Done Parsing WSDL location - " + wsdlLocParts[1]);
					build.append(wsdlLoc.trim()+"\n");
				}
			}
			
			String file = getResourcepath() + "/wsdl-locations.csv";
			Writer writer = new BufferedWriter(new FileWriter(file));
			writer.write(build.toString());
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
