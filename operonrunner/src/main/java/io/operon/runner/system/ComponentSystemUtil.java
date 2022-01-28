/*
 *   Copyright 2022, operon.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.operon.runner.system;

import java.io.IOException;
import java.io.File;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.PairType;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class ComponentSystemUtil {
    private static Logger log = LogManager.getLogger(ComponentSystemUtil.class);

    public static final String DEFAULT_COMPONENTS_DEFINITION_FILE = "components.json";

    public static ObjectType loadInputSourceDriverComponentDefinition(String isdName, String componentsFilePath) throws OperonGenericException {
        //System.out.println(">>>> LOADING ISD <<<<");
        ArrayType componentsDefinitionArr = loadComponentDefinitionFile(componentsFilePath);
        return loadComponentDefinition(componentsDefinitionArr, "isd", isdName);
    }

    public static ObjectType loadIntegrationComponentDefinition(String integrationComponentName, String componentsFilePath) throws OperonGenericException {
        ArrayType componentsDefinitionArr = loadComponentDefinitionFile(componentsFilePath);
        return loadComponentDefinition(componentsDefinitionArr, "integration", integrationComponentName);
    }
    
    //
    // The structure to read is:
    //  [
    //    {
    //      "type" | "name" | "path"
    //    }
    //  ]
    //  We must choose the correct component -object based on "type" and "name" -fields.
    //
    public static ObjectType loadComponentDefinition(ArrayType componentsDefinitionArr,
                            String componentType, String componentName) throws OperonGenericException {
        
        ObjectType result = null;
        boolean foundComponent = false;
        
        for (Node arrValue : componentsDefinitionArr.getValues()) {
            ObjectType obj = (ObjectType) arrValue.evaluate();
            
            boolean typeMatch = false;
            boolean nameMatch = false;
            
            for (PairType pair : obj.getPairs()) {
                String key = pair.getKey();
                String pairValue = null;
                if (key.equals("\"type\"")) {
                    pairValue = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    if (pairValue.equals(componentType)) {
                        typeMatch = true;
                    }
                    else {
                        typeMatch = false;
                    }
                }
                if (key.equals("\"name\"")) {
                    pairValue = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    if (pairValue.equals(componentName)) {
                        nameMatch = true;
                    }
                    else {
                        nameMatch = false;
                    }
                }
                if (typeMatch && nameMatch) {
                    result = obj;
                    log.debug("Found component-object: " + obj);
                    //System.out.println("Found component-object: " + obj);
                    return result;
                }
            }
        }
        ErrorUtil.createErrorValueAndThrow(null, "COMPONENT", "LOAD", "Component definition not found :: " + componentType + " :: " + componentName);
        return null; // previous statement throws, we never get here
    }
    
    public static ArrayType loadComponentDefinitionFile(String fileName) throws OperonGenericException {
        try {
            if (fileName == null) {
                fileName = DEFAULT_COMPONENTS_DEFINITION_FILE;
            }
            File f = new File(fileName);
            String componentDefinitionJsonStr = null;
            if (f.exists()) {
                // Read from module's own directory:
                componentDefinitionJsonStr =  new String(Files.readAllBytes(Paths.get(fileName)));
            }
            else {
                // Read from Working directory:
                componentDefinitionJsonStr =  new String(Files.readAllBytes(Paths.get(fileName)));
            }
            
            if (componentDefinitionJsonStr.startsWith("[") == false) {
                System.err.println("Invalid components definition file :: " + fileName);
                ErrorUtil.createErrorValueAndThrow(null, "COMPONENT", "LOAD", "Invalid components definition file :: " + fileName);
            }
            OperonValue componentDefinition = JsonUtil.operonValueFromString(componentDefinitionJsonStr);
            return (ArrayType) componentDefinition.evaluate();
        } catch (IOException ioe) {
            ErrorUtil.createErrorValueAndThrow(null, "COMPONENT", "LOAD", "Could not load components definition file :: " + fileName);
            return null; // previous statement throws, we never get here
        }
    }

    public static Class loadComponent(String JAR_URL, String JAR_FILE_PATH) throws OperonGenericException {
        //System.out.println("loadComponent :: working directory: " + System.getProperty("user.dir"));
        //System.out.println("    JAR_URL :: " + JAR_URL);
        //System.out.println("    JAR_FILE_PATH :: " + JAR_FILE_PATH);
        log.debug("loadComponent :: working directory: " + System.getProperty("user.dir"));
        log.debug("    JAR_URL :: " + JAR_URL);
        log.debug("    JAR_FILE_PATH :: " + JAR_FILE_PATH);
        URLClassLoader urlClassLoader;
        try {
            // Create a URL that refers to a jar file in the file system
            URL FileSysUrl = new URL(JAR_URL);
 
            // Create a jar URL connection object
            JarURLConnection jarURLConnection = (JarURLConnection) FileSysUrl.openConnection();
             
            // Get the jar file
            JarFile jarFile = jarURLConnection.getJarFile();
             
            // Get jar file name
            //System.out.println("Jar Name: " + jarFile.getName());
            log.debug("Jar Name: " + jarFile.getName());
            
            // When no entry is specified on the URL, the entry name is null
            //System.out.println("\nJar Entry: " + jarURLConnection.getJarEntry());
            log.debug("\nJar Entry: " + jarURLConnection.getJarEntry());
             
            // Get the manifest of the jar
            Manifest manifest = jarFile.getManifest();
 
            // Print the manifest attributes
            //System.out.println("\nManifest file attributes: ");
            log.debug("\nManifest file attributes: ");
            for (Entry entry : manifest.getMainAttributes().entrySet()) {
                //System.out.println(entry.getKey() +": "+ entry.getValue());
                log.debug(entry.getKey() +": "+ entry.getValue());
            }
            //System.out.println("\nExternal JAR Execution output: ");
 
            // Get the jar URL which contains target class
            URL[] classLoaderUrls = new URL[]{new URL(JAR_FILE_PATH)};
            
            // Create a classloader and load the entry point class
            urlClassLoader = new URLClassLoader(classLoaderUrls);

            // Get the main class name (the entry point class)
            String mainClassName = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);  
 
            // Load the target class
            Class beanClass = urlClassLoader.loadClass( mainClassName );
            return beanClass;
        } catch (MalformedURLException e) {
            System.err.println("ERROR SIGNAL: loadComponent, malformedURL: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("ERROR SIGNAL: loadComponent, class not found: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("ERROR SIGNAL: loadComponent, io-error: " + e.getMessage());
        }
        String errorString = "Failed to load class :: " + JAR_URL + " :: " + JAR_FILE_PATH;
        //OperonValue error = JsonUtil.operonValueFromString("\"" + errorString.replaceAll("\"", "\\\\\"") + "\"");
        ErrorUtil.createErrorValueAndThrow(null, "COMPONENT", "LOAD", errorString);
        return null; // previous statement throws, we never get here
    }

}