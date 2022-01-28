/** OPERON-LICENSE **/
package io.operon.runner.system.inputsourcedriver.httpserver;

import java.util.Collections;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Stream;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.stream.Collectors;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.operon.runner.OperonContext;
import io.operon.runner.OperonContextManager;
import static io.operon.runner.OperonContextManager.ContextStrategy;
import io.operon.runner.OperonRunner;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.EmptyType;
import io.operon.runner.node.type.RawValue;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.TrueType;
import io.operon.runner.node.type.FalseType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.PairType;
import io.operon.runner.system.InputSourceDriver;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.system.BaseSystem;
import io.operon.runner.statement.FromStatement;

import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class HttpServerSystem implements InputSourceDriver {

    private static Logger log = LogManager.getLogger(HttpServerSystem.class);
    private ObjectType jsonConfiguration; // json-configuration for the component
    private boolean isRunning;
    private long pollCounter = 0L;
    private OperonContextManager ocm;
    private ThreadPoolExecutor threadPoolExecutor;
    private HttpServer server;

    public boolean isRunning() {
        return this.isRunning;
    }

    public OperonContextManager getOperonContextManager() {
        return this.ocm;
    }
    
    public void setOperonContextManager(OperonContextManager o) {
        this.ocm = o;
    }

    public HttpServerSystem() {}
    
    public void start(OperonContextManager o) {
        try {
            Info info = this.resolve();
            
            if (info.pathDefinitionsFolder != null) {
                //System.out.println("Reading paths from files.");
                // - read the files from the folder.
                // - parse each file from JSON
                String key = "paths";
                
                Path folder = Paths.get(info.pathDefinitionsFolder);

                try {
                    List<File> files = Files.list(folder)
                                .map(Path::toFile)
                                .collect(Collectors.toList());

                    for (File file: files) {
                        Path path = file.toPath();
                        String fileContent = new String(Files.readAllBytes(path));
                        ArrayType pathDefinition = (ArrayType) JsonUtil.operonValueFromString(fileContent).evaluate();
                        // add into PathInfo -list:
                        this.readPathsArray(info, pathDefinition.getValues(), info.paths, key);
                    }
                } catch (IOException e) {
                    System.err.println("httpserver :: ERROR :: while configurating pathDefinitionsFolder: " + e.getMessage());
                    System.err.println("    current working directory is :: " + System.getProperty("user.dir"));
                }
            }
            
            this.isRunning = true;
            debug(info, "HttpServer: create Operon-context");
            if (this.getOperonContextManager() == null && o != null) {
                ocm = o;
                if (info.si.contextManagement != null) {
                    ocm.setContextStrategy(info.si.contextManagement);
                }
            }
            else if (o == null) {
                OperonContext ctx = new OperonContext();
                ocm = new OperonContextManager(ctx, info.si.contextManagement);
            }
            ocm.setRemoveUnusedStates(info.si.removeUnusedStates, info.si.removeUnusedStatesAfterMillis, info.si.removeUnusedStatesInThread);
            debug(info, "HttpServer: create thread-pool");
            threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(info.si.threadPoolSize);
            debug(info, "HttpServer: create http-server, host=" + info.si.host + ", port=" + info.si.port);
            
            server = HttpServer.create(new InetSocketAddress(info.si.host, info.si.port), info.si.backlogSize);
            
            debug(info, "HttpServer: map paths");
            for (PathInfo pi : info.paths) {
                debug(info, "  map path to server-context: /" + pi.path);
                OperonHttpHandler handler = new OperonHttpHandler(ocm, "" + pi.path, info, pi);
                server.createContext("/" + pi.path, handler);
            }
            
            server.setExecutor(threadPoolExecutor);
            System.out.println("Operon: starting HTTP-server on " + info.si.host + ", listening port " + info.si.port);
            server.start();
        } catch (Exception ex) {
            System.err.println("HttpServer: could not start server. Server already running?");
            OperonGenericException oge = new OperonGenericException(ex.getMessage());
            oge.setErrorMessage(ex.getMessage());
            ocm.getOperonContext().setException(oge);
        }
    }

/*
    private static boolean hasMoreThanOneUrlParam(String path) {
        int firstColon = path.indexOf(":");
        int lastColon = path.lastIndexOf(":");
        if (firstColon < 0) {
            return false;
        }
        if (firstColon == lastColon) {
            return false;
        }
        return true;
    }
    //
    // TODO: should only be applied for those mappings which have just one urlParam
    //
    private static String removePathUrlParams(String path) {
        // Remove "/test/:name" --> "/test"
        // "firstname/:fname/lastname/:lname" --> "/firstname/foo/lastname/bar"
        // is this possible? --> createContext dynamically...
        int lastColon = path.lastIndexOf(":");
        if (lastColon > 0) {
            return path.substring(0, lastColon);
        }
        return path;
    }
*/
    private static String readFile(String filePath) {
        StringBuilder contentBuilder = new StringBuilder();
        try (Stream<String> stream = Files.lines(Paths.get(filePath), StandardCharsets.UTF_8)) {
            stream.forEach(s -> contentBuilder.append(s).append("\n"));
        }
        catch (IOException e) {
            System.err.println("ERROR SIGNAL");
        }
        return contentBuilder.toString();
    }
    
    public void requestNext() {}
    
    public void stop() {
        server.stop(0);
        threadPoolExecutor.shutdown();
        try {
            threadPoolExecutor.awaitTermination(2, TimeUnit.MINUTES);
        } catch (Exception e) {
            System.err.println("httpserver :: error while shutting down the http-server");
        }
        this.isRunning = false;
        log.info("Stopped");
    }
    
    private void debug(Info info, String msg) {
        if (info.si.debug) {
            System.out.println(msg);
        }
    }
    
    public void setJsonConfiguration(ObjectType jsonConfig) { this.jsonConfiguration = jsonConfig; }
    public ObjectType getJsonConfiguration() { return this.jsonConfiguration; }
    public Long getPollCounter() { return this.pollCounter; }
 
    private Info resolve() throws Exception {
        List<PairType> jsonPairs = this.getJsonConfiguration().getPairs();
        
        Info ci = new Info();
        
        ServerInfo si = new ServerInfo();
        ci.si = si;
        ci.paths = new ArrayList<PathInfo>();
        List<PathInfo> paths = new ArrayList<PathInfo>();
        
        for (PairType pair : jsonPairs) {
            String key = pair.getKey();
            
            switch (key.toLowerCase()) {
                case "\"debug\"":
                    Node debug_Node = pair.getValue().evaluate();
                    if (debug_Node instanceof TrueType) {
                        si.debug = true;
                    }
                    else {
                        si.debug = false;
                    }
                    break;
                case "\"host\"":
                    String host = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    si.host = host;
                    break;
                case "\"port\"":
                    int port = (int)( ((NumberType) pair.getValue().evaluate()).getDoubleValue() );
                    si.port = port;
                    break;
                case "\"pathdefinitionsfolder\"":
                    String pathDefinitionsFolder = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    ci.pathDefinitionsFolder = pathDefinitionsFolder;
                    break;
                case "\"threadpoolsize\"":
                    int threadPoolSize = (int)( ((NumberType) pair.getValue().evaluate()).getDoubleValue() );
                    si.threadPoolSize = threadPoolSize;
                    break;
                case "\"sessionheader\"":
                    String sessionHeader = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    si.sessionHeader = sessionHeader;
                    break;
                case "\"contextmanagement\"":
                    String iContextManagementStr = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    if (OperonContextManager.ContextStrategy.valueOf(iContextManagementStr.toUpperCase()) 
                          == OperonContextManager.ContextStrategy.ALWAYS_CREATE_NEW) {
                        si.contextManagement = OperonContextManager.ContextStrategy.ALWAYS_CREATE_NEW;
                    }
                    else if (OperonContextManager.ContextStrategy.valueOf(iContextManagementStr.toUpperCase()) 
                          == OperonContextManager.ContextStrategy.REUSE_BY_CORRELATION_ID) {
                        si.contextManagement = OperonContextManager.ContextStrategy.REUSE_BY_CORRELATION_ID;
                    }
                    else if (OperonContextManager.ContextStrategy.valueOf(iContextManagementStr.toUpperCase()) 
                          == OperonContextManager.ContextStrategy.SINGLETON) {
                        si.contextManagement = OperonContextManager.ContextStrategy.SINGLETON;
                    }
                    break;
                case "\"basepath\"":
                    String basePath = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    si.basePath = basePath;
                    break;
                case "\"usesession\"":
                    Node useSession_Node = pair.getValue().evaluate();
                    if (useSession_Node instanceof FalseType) {
                        si.useSession = false;
                    }
                    else {
                        si.useSession = true;
                    }
                    break;
                case "\"logrequests\"":
                    Node logRequests_Node = pair.getValue().evaluate();
                    if (logRequests_Node instanceof FalseType) {
                        si.logRequests = false;
                    }
                    else {
                        si.logRequests = true;
                    }
                    break;
                case "\"logresponses\"":
                    Node logResponses_Node = pair.getValue().evaluate();
                    if (logResponses_Node instanceof FalseType) {
                        si.logResponses = false;
                    }
                    else {
                        si.logResponses = true;
                    }
                    break;
                case "\"backlogsize\"":
                    Node backlogSizeNode = pair.getValue().evaluate();
                    int backlogSizeInt = (int) ((NumberType) pair.getValue().evaluate()).getDoubleValue();
                    if (backlogSizeInt < -1) {
                        ErrorUtil.createErrorValueAndThrow(pair.getStatement(), "SYSTEM", "CONFIGURATION", "backlogSize must be >= 0");
                    }
                    si.backlogSize = backlogSizeInt;
                    break;
                case "\"removeunusedstates\"":
                    Node removeUnusedStatesValue = pair.getValue().evaluate();
                    if (removeUnusedStatesValue instanceof FalseType) {
                        si.removeUnusedStates = false;
                    }
                    else {
                        si.removeUnusedStates = true;
                    }
                    break;
                case "\"removeunusedstatesinthread\"":
                    Node removeUnusedStatesInThreadValue = pair.getValue().evaluate();
                    if (removeUnusedStatesInThreadValue instanceof FalseType) {
                        si.removeUnusedStatesInThread = false;
                    }
                    else {
                        si.removeUnusedStatesInThread = true;
                    }
                    break;
                case "\"removeunusedstatesaftermillis\"":
                    Node removeUnusedStatesAfterMillisNode = pair.getValue().evaluate();
                    long removeUnusedStatesAfterMillisLong = (long) ((NumberType) pair.getValue().evaluate()).getDoubleValue();
                    if (removeUnusedStatesAfterMillisLong < 0L) {
                        ErrorUtil.createErrorValueAndThrow(pair.getStatement(), "SYSTEM", "CONFIGURATION", "removeUnusedStatesAfterMillis must be >= 0");
                    }
                    si.removeUnusedStatesAfterMillis = removeUnusedStatesAfterMillisLong;
                    break;
                case "\"paths\"":
                    List<Node> pathNodes = ((ArrayType) pair.getValue().evaluate()).getValues();
                    this.readPathsArray(ci, pathNodes, paths, key);
                    break;
                default:
                    System.err.println("HttpServer -isd: no mapping for configuration key: " + key);
            }
        }
        return ci;
    }
    
    private void readPathsArray(Info ci, List<Node> pathNodes, List<PathInfo> paths, String key) throws OperonGenericException {
        for (Node pathNode : pathNodes) {
            ObjectType pathObj = (ObjectType) pathNode.evaluate();
            
            PathInfo pi = new PathInfo();
            
            for (PairType pathPair : pathObj.getPairs()) {
                String pathKey = pathPair.getKey();
                switch (pathKey.toLowerCase()) {
                    case "\"id\"":
                        String infoId = ((StringType) pathPair.getValue().evaluate()).getJavaStringValue();
                        pi.id = infoId;
                        break;
                    case "\"path\"":
                        String infoPath = ((StringType) pathPair.getValue().evaluate()).getJavaStringValue();
                        pi.path = infoPath;
                        break;
                    case "\"methods\"":
                        ArrayType infoMethodArray = (ArrayType) pathPair.getValue().evaluate();
                        List<RequestMethod> infoMethods = new ArrayList<RequestMethod>();
                        for (Node n : infoMethodArray.getValues()) {
                            StringType methodName = (StringType) n.evaluate();
                            String methodNameStr = methodName.getJavaStringValue();
                            try {
                                RequestMethod rm = RequestMethod.valueOf(methodNameStr.toUpperCase());
                                infoMethods.add(rm);
                            } catch (Exception e) {
                                ErrorUtil.createErrorValueAndThrow(pathPair.getStatement(), "SYSTEM", "CONFIGURATION", "Invalid HTTP-method: " + methodNameStr);
                            }
                        }
                        pi.methods = infoMethods;
                        break;
                    default:
                        System.err.println("httpserver -isd: no mapping for configuration key: " + key + "." + pathKey);
                }
            }
            ci.paths.add(pi);
        }
    }
    
    //
    // these are package-scoped for OperonHttpHandler (no private-modifier):
    //
    class Info {
        ServerInfo si;
        List<PathInfo> paths;
        
        // It is possible to give the paths in a separate .json -files.
        // Each file contains an array of path-objects.
        // If paths are given in "paths" as json-array, then those are not overridden,
        // but these paths are added as new ones.
        String pathDefinitionsFolder = null;
    }

    class ServerInfo {
        boolean debug = false;
        String host = "localhost";
        int port = 8080;
        int threadPoolSize = 10;
        OperonContextManager.ContextStrategy contextManagement = OperonContextManager.ContextStrategy.ALWAYS_CREATE_NEW;
        String sessionHeader = "X-OPERON-SESSION-ID";
        String basePath;

        boolean useSession = true;
        boolean logRequests = true;
        boolean logResponses = true;
        
        
        // Maximum number of incoming TCP connections which the system will queue internally.
        // Connections are queued while they are waiting to be accepted by the HttpServer. When the limit is reached, 
        // further connections may be rejected (or possibly ignored) by the underlying TCP implementation. Setting the 
        // right backlog value is a compromise between efficient resource usage in the TCP layer (not setting it too high) 
        // and allowing adequate throughput of incoming requests (not setting it too low).
        //
        // If this value is less than or equal to zero, then a system default value is used.
        int backlogSize = 0;
        //
        // When using REUSE_BY_CORRELATION_ID, then the State -objects may accumulate into
        // memory. We can remove those that were not accessed within given period.
        //
        private boolean removeUnusedStates = false;
        //
        // When removing old State-objects, we can use a background Thread to do this
        // job. In some systems it may be better to do the removal without threads.
        //
        private boolean removeUnusedStatesInThread = false;
        //
        // This is the period of inaccess. States that have not been accessed within
        // this period will get removed if "removeUnusedStates" is set to true.
        //
        private long removeUnusedStatesAfterMillis = 60000L;
    }

    class PathInfo {
        String id;
        String path;
        List<RequestMethod> methods = new ArrayList<RequestMethod>();
    }
    
    enum RequestMethod {
        ALL("all"), GET("get"), POST("post"), PUT("put"), PATCH("patch"), 
        DELETE("delete"), HEAD("head"), OPTIONS("options"), 
        TRACE("trace"), CONNECT("connect");
        
        private String method;
        
        RequestMethod(String m) {
            this.method = m;
        }
        
        public String getRequestMethod() {
            return this.method;
        }
        
        @Override
        public String toString() {
            return this.method;
        }
    }
}