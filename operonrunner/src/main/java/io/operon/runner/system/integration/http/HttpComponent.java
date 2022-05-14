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

package io.operon.runner.system.integration.http;

import java.util.Collections;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Stream;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.nio.charset.StandardCharsets;


import java.time.Duration;

import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.HttpCookie;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import static java.net.http.HttpClient.Version;
import static java.net.http.HttpClient.Redirect;

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpHeaders;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.Authenticator;

import java.io.UnsupportedEncodingException;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.Reader;
import java.io.InputStreamReader;
import java.lang.StringBuffer;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

import io.operon.runner.Main;
import io.operon.runner.statement.Statement;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.system.IntegrationComponent;
import io.operon.runner.system.integration.BaseComponent;
import io.operon.runner.processor.function.core.raw.RawToStringType;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.streamvaluewrapper.*;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.exception.OperonComponentException;

import org.apache.logging.log4j.LogManager;

//
// This component is a "producer", i.e. it only writes data.
//
public class HttpComponent extends BaseComponent implements IntegrationComponent {

     // no logger 
    private static final String COOKIES_HEADER = "Set-Cookie";
    private static java.net.CookieManager msCookieManager = new java.net.CookieManager();

    public HttpComponent() {
        //:OFF:log.debug("http :: constructor");
    }
    
    public OperonValue produce(OperonValue currentValue) throws OperonComponentException {
        //:OFF:log.debug("http :: produce");
        try {
            Info info = resolve(currentValue);
            OperonValue result = this.handleTask(currentValue, info);
            return result;
        } 
        
        catch (java.net.ConnectException e) {
            throw new OperonComponentException("Could not connect");
        } 
        
        catch (java.net.BindException e) {
            throw new OperonComponentException("Could not bind socket to local port");
        } 
        
        catch (java.net.NoRouteToHostException e) {
            throw new OperonComponentException("No route to host");
        } 
        
        catch (java.net.PortUnreachableException e) {
            throw new OperonComponentException("Target port unreachable");
        } 
        
        catch (OperonGenericException | IOException | InterruptedException e) {
            throw new OperonComponentException(e.getMessage());
        }
        
        // E.g. unsupporteduri:
        catch (Exception e) {
            throw new OperonComponentException(e.getMessage());
        }
    }

    private OperonValue handleTask(OperonValue currentValue, Info info) throws OperonGenericException, IOException, InterruptedException {
        debug(info, "handleTask");
        ObjectType result = new ObjectType(currentValue.getStatement());

        //
        // Build client:
        //
        
        HttpClient.Builder builder = HttpClient.newBuilder();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        builder.executor(executor);
        
        if (info.httpVersion != null) {
            if (info.httpVersion == 1.1) {
                builder.version(Version.HTTP_1_1);
            }
        }
        
        if (info.followRedirects != null) {
            builder.followRedirects(info.followRedirects);
        }

        if (info.connectTimeout != null) {
            builder.connectTimeout(Duration.ofMillis(info.connectTimeout));
        }
        
        if (info.proxy != null) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(info.proxy.host, info.proxy.port)));
        }
        
        //builder.authenticator(Authenticator.getDefault());
        
        HttpClient client = builder.build();
        
        //
        // Build request:
        //
        
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        
        if (info.params == null) {
            try {
                URI uri = URI.create(info.url);
                debug(info, "URL: " + uri.toURL().toString());
                requestBuilder.uri(uri);
            } catch (IllegalArgumentException iae) {
                URI uri = URI.create("https://" + info.url);
                debug(info, "URL: " + uri.toURL().toString());
                requestBuilder.uri(uri);
            }
        }
        else {
            String queryParamsUriEncodedStr = HttpComponent.getParamsString(info.params);
            debug(info, URI.create(info.url + queryParamsUriEncodedStr).toString());
            try {
                requestBuilder.uri(URI.create(info.url + queryParamsUriEncodedStr));
            } catch (IllegalArgumentException iae) {
                requestBuilder.uri(URI.create("https://" + info.url + queryParamsUriEncodedStr));
            }
        }

        //
        // Set automatic headers:
        //
        requestBuilder.header("Accept", "*/*");
        requestBuilder.header("User-agent", "operon.io/components/oc/http/" + Main.VERSION);
        requestBuilder.header("Accept-Encoding", "gzip");
        
        if (info.componentConfiguration != null && info.sendComponentConfigurationAsHeader) {
            // serialize as string. The component must parse it first.
            requestBuilder.header("componentconfiguration", info.componentConfiguration.toString().replaceAll("\"", "\\\\\""));
        }

        //
        // Set user-defined headers:
        //
        if (info.headers != null) {
            String headerKey = "";
            for (PairType hdrPair : info.headers.getPairs()) {
                //hdrPair.lock();
                //System.out.println("hdr :: " + hdrPair.getPreventReEvaluation());
                //StringType hdrValue = ((StringType) hdrPair.getValue().evaluate());
                StringType hdrValue = ((StringType) hdrPair.getEvaluatedValue());
                headerKey = hdrPair.getKey().substring(1, hdrPair.getKey().length() - 1);
                requestBuilder.header(headerKey, hdrValue.getJavaStringValue());
                debug(info, "Adding header: " + headerKey + ":" + hdrValue.getJavaStringValue());
            }
        }

        if (info.method == RequestMethod.POST || info.method == RequestMethod.PUT || info.method == RequestMethod.PATCH) {
            //
            // Determine body's data-type:
            //
            byte[] bytes = null;
            
            if (info.writeAs == WriteAsType.RAW) {
                RawValue cv = (RawValue) currentValue;
                bytes = cv.getBytes();
                
                // automatically set the content-type:
                if (info.contentType == null) {
                    requestBuilder.header("Content-Type", "octet-stream");
                }
                
                // manually set the content-type:
                else {
                    requestBuilder.header("Content-Type", info.contentType);
                }
            }
            
            else if (info.writeAs == WriteAsType.JSON) {
                bytes = currentValue.toString().getBytes(StandardCharsets.UTF_8);
                // automatically set the content-type:
                if (info.contentType == null) {
                    requestBuilder.header("Content-Type", "application/json");
                }
                
                // manually set the content-type:
                else {
                    requestBuilder.header("Content-Type", info.contentType);
                }
            }
            
            requestBuilder.header("charset", "utf-8");
            
            //
            // This is probably set automatically:
            //
            //requestBuilder.header("Content-Length", Integer.toString(bytes.length));

            //
            // set the body-data
            //
            requestBuilder.method(info.method.toString(), BodyPublishers.ofByteArray(bytes));
            
        }
        
        debug(info, "Build request");
        HttpRequest request = requestBuilder.build();
        debug(info, "Build request done");
        //
        // Send the request:
        //
        HttpResponse<?> response = null;
        
        OperonValue responseValue = null;
        Integer statusCode = null;
        
        ObjectType headersObj = new ObjectType(currentValue.getStatement());
        PairType headersPair = new PairType(currentValue.getStatement());
        headersPair.setPair("\"headers\"", headersObj);
        result.addPair(headersPair);
        
        // 
        // Response-headers
        // 
        ObjectType allHeadersObj = new ObjectType(currentValue.getStatement());
        HttpHeaders headers = null;
        
        //Map<String, List<String>> headersMap = headers.map();
        
        if (info.readAs == ReadAsType.JSON) {
            debug(info, "Send request");
            response = client.send(request, BodyHandlers.ofInputStream());
            debug(info, "Sent request");
            //response = client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
            
        	//
        	// NOTE! There's some problem with httpclient: when content-encoding is not gzip,
        	//       then the InputStream is closed too early. Presumably some background-thread
        	//       does this. Causes problems at least in single-threaded environment.
        	//
        	statusCode = response.statusCode();
        	debug(info, "StatusCode = " + statusCode);
        	
        	if (statusCode >= 400 && info.createErrorOnFailureStatusCode) {
        	    debug(info, "Status was >= 400, creating error");
        	    responseValue = this.createErrorSinceFailureStatus(currentValue.getStatement(), statusCode, "");
        	}
            
            else {
                headers = response.headers();
                AtomicBoolean gzipped = this.extractHeaders(info, headers, allHeadersObj);
            	
        	    debug(info, "Get response body");
                String responseBodyStr = null;
                
                InputStream is = (InputStream) response.body();
                //InputStream is = new ByteArrayInputStream(bodyStr.getBytes());
                
                if (gzipped.get()) {
                    debug(info, "gzip - compression detected: deflating response.");
                    
                    //
                    // This works
                    //
                    InputStream bodyStream = new GZIPInputStream(is);
                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = bodyStream.read(buffer)) > 0) {
                        outStream.write(buffer, 0, length);
                    }
                
                    responseBodyStr = new String(outStream.toByteArray(), "UTF-8");
                	debug(info, "Read the GZIPInputStream");
                }
                
                else {
                    // For some reason this does not always work (for dbx-test2)
                    // 
                    //System.out.println(">> no gzip");
                    // Materialize input-stream:
                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                    
                    byte[] buffer = new byte[4096];
                    int length;
                    //
                    // Inputstream could be "closed", therefore try to catch the IO-exception.
                    //
                    // length is the number of bytes actually read.
                    try {
                        while ((length = is.read(buffer)) > 0) {
                            outStream.write(buffer, 0, length);
                            //System.out.println(">> Wrote " + length + " bytes.");
                        }
                    } catch (IOException e) {
                        // The stream could have been closed by background-thread.
                        debug(info, "http: exception while reading response :: stream was closed.");
                    }
                    
                    responseBodyStr = new String(outStream.toByteArray(), "UTF-8");
                }
            
        	    debug(info, "Response as str: " + responseBodyStr);
        	    responseValue = JsonUtil.operonValueFromString(responseBodyStr);
        	    debug(info, "Converted response to OperonValue");
            }
        }
        
        else if (info.readAs == ReadAsType.RAW) {
            response = client.send(request, BodyHandlers.ofByteArray());
            statusCode = response.statusCode();
        	if (statusCode >= 400 && info.createErrorOnFailureStatusCode) {
        	    responseValue = this.createErrorSinceFailureStatus(currentValue.getStatement(), statusCode, "");
        	}
        	else {
                headers = response.headers();
                AtomicBoolean gzipped = this.extractHeaders(info, headers, allHeadersObj);
        	    
                if (gzipped.get()) {
                    debug(info, "gzip - compression detected: deflating response.");
                    byte[] bodyBytes = (byte[]) response.body();
                    InputStream is = new ByteArrayInputStream(bodyBytes);
                    InputStream bodyStream = new GZIPInputStream(is);
                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = bodyStream.read(buffer)) > 0) {
                        outStream.write(buffer, 0, length);
                    }
    
                    RawValue raw = new RawValue(currentValue.getStatement());
                    raw.setValue(outStream.toByteArray());
                    responseValue = raw;
                }
                
        	    else {
                    RawValue raw = new RawValue(currentValue.getStatement());
                    raw.setValue((byte[]) response.body());
                    responseValue = raw;
        	    }
        	}
        }
        
        else if (info.readAs == ReadAsType.STREAM) {
            response = client.send(request, BodyHandlers.ofInputStream());
            statusCode = response.statusCode();
        	if (statusCode >= 400 && info.createErrorOnFailureStatusCode) {
        	    responseValue = this.createErrorSinceFailureStatus(currentValue.getStatement(), statusCode, "");
        	}
        	
            else {
                headers = response.headers();
                AtomicBoolean gzipped = this.extractHeaders(info, headers, allHeadersObj);
            	
        	    debug(info, "Get response body");
                String responseBodyStr = null;
                
                InputStream is = (InputStream) response.body();
                
                if (gzipped.get()) {
                    debug(info, "gzip - compression detected: deflating response.");

                    InputStream bodyStream = new GZIPInputStream(is);

                    StreamValueWrapper svw = new StreamValueInputStreamWrapper(bodyStream);
                    StreamValue sv = new StreamValue(currentValue.getStatement());
                    sv.setValue(svw);
                    responseValue = sv;
                }
            	else {
                    StreamValueWrapper svw = new StreamValueInputStreamWrapper((InputStream) response.body());
                    StreamValue sv = new StreamValue(currentValue.getStatement());
                    sv.setValue(svw);
                    responseValue = sv;
            	}
            }        	
        }
        
        else if (info.readAs == ReadAsType.EMPTY) {
            response = client.send(request, BodyHandlers.discarding());
            statusCode = response.statusCode();
        	if (statusCode >= 400 && info.createErrorOnFailureStatusCode) {
        	    responseValue = this.createErrorSinceFailureStatus(currentValue.getStatement(), statusCode, "");
        	}
        	else {
                responseValue = new EmptyType(currentValue.getStatement());
        	}
        }
        
        PairType allHeadersPair = new PairType(currentValue.getStatement());
        allHeadersPair.setPair("\"responseHeaders\"", allHeadersObj);
        headersObj.addPair(allHeadersPair);
        
        PairType bodyPair = new PairType(currentValue.getStatement());
        bodyPair.setPair("\"body\"", responseValue);
        result.addPair(bodyPair);
      
        NumberType statusNum = new NumberType(currentValue.getStatement());
        statusNum.setDoubleValue((double) statusCode);
        statusNum.setPrecision((byte) 0);
        PairType statusPair = new PairType(currentValue.getStatement());
        statusPair.setPair("\"statusCode\"", statusNum);
        headersObj.addPair(statusPair);
        
        
        //for (Map.Entry<String, List<String>> entry : headersMap.entrySet()) {
        //    
        //    System.out.println("Key = " + entry.getKey() + 
        ///                     ", Value = " + entry.getValue());
        //}
        
        executor.shutdownNow();
        client = null;
        return result;
    }

    //
    // Returns true if gzipped, otherwise false.
    //
    private AtomicBoolean extractHeaders(Info info, HttpHeaders headers, ObjectType allHeadersObj) {
        Statement stmt = allHeadersObj.getStatement();
        // 
        // Extract headers:
        // 
        AtomicBoolean gzipped = new AtomicBoolean(false);
        headers.map().entrySet().stream()
        	  .filter(entry -> entry.getKey() != null)
        	  .forEach(entry -> {
        	      try {
            	      PairType hdrPair = new PairType(stmt);
            	      ArrayType hdrArray = new ArrayType(stmt);
            	      
            	      List<String> headerValues = entry.getValue();
            	      
            	      for (String hdr : headerValues) {
                          StringType hdrArrayStr = new StringType(stmt);
            	          hdr = RawToStringType.sanitizeForStringType(hdr);
            	          hdrArrayStr.setFromJavaString(hdr);
            	          hdrArray.getValues().add(hdrArrayStr);
            	          debug(info, "Response header: " + entry.getKey() + ": " + hdr);
            	          if (entry.getKey().toLowerCase().equals("content-encoding") && hdr.contains("gzip")) {
            	              gzipped.set(true);
            	          }
            	      }
            	      hdrPair.setPair("\"" + entry.getKey() + "\"", hdrArray);
            	      allHeadersObj.addPair(hdrPair);
        	      } catch (OperonGenericException oge) {
        	          System.err.println("ERROR: " + oge.getMessage());
        	      }
        	});
        	return gzipped;
    }

    private ErrorValue createErrorSinceFailureStatus(Statement stmt, Integer statusCode, String errorMessage) {
        String errorType = "HTTP";
        String errorCode = "STATUS_CODE_" + statusCode;
        ErrorValue responseValue = ErrorUtil.createErrorValue(stmt, errorType, errorCode, errorMessage);
        return responseValue;
    }

    private void debug(Info info, String msg) {
        if (info.debug) {
            System.out.println(msg);
        }
    }

    private static String getParamsString(ObjectType params) throws OperonGenericException, UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        result.append("?");
        for (PairType pair : params.getPairs()) {
          result.append(URLEncoder.encode(pair.getKey().substring(1, pair.getKey().length() - 1), "UTF-8"));
          result.append("=");
          result.append(URLEncoder.encode(((StringType) pair.getValue().evaluate()).getJavaStringValue(), "UTF-8"));
          result.append("&");
        }
    
        String resultString = result.toString();
        
        return resultString.length() > 0
          ? resultString.substring(0, resultString.length() - 1)
          : resultString;
	}

    public Info resolve(OperonValue currentValue) throws OperonGenericException {
        OperonValue currentValueCopy = currentValue;
        
        ObjectType jsonConfiguration = this.getJsonConfiguration();
        jsonConfiguration.getStatement().setCurrentValue(currentValueCopy);
        // NOTE: jsonConfiguration has been evaluated in the this.getJsonConfiguration() -call.
        List<PairType> jsonPairs = jsonConfiguration.getPairs();
        Info info = new Info();
        
        for (PairType pair : jsonPairs) {
            String key = pair.getKey();
            OperonValue currentValueCopy2 = currentValue;
            pair.getStatement().setCurrentValue(currentValueCopy2);
            switch (key.toLowerCase()) {
                case "\"url\"":
                    String url = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.url = url;
                    break;
                case "\"method\"":
                    String method = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    RequestMethod rm = RequestMethod.valueOf(method.toUpperCase());
                    info.method = rm;
                    break;
                case "\"writeas\"":
                    String sWa = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.writeAs = WriteAsType.valueOf(sWa.toUpperCase());
                    break;
                case "\"readas\"":
                    String readAs = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.readAs = ReadAsType.valueOf(readAs.toUpperCase());
                    break;
                case "\"contenttype\"":
                    String contentType = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.contentType = contentType;
                    break;
                case "\"httpversion\"":
                    int httpVersion = (int) ((NumberType) pair.getEvaluatedValue()).getDoubleValue();
                    info.httpVersion = httpVersion;
                    break;
                case "\"connecttimeout\"":
                    long connectTimeout = (long) ((NumberType) pair.getEvaluatedValue()).getDoubleValue();
                    info.connectTimeout = connectTimeout;
                    break;
                case "\"followredirects\"":
                    String followRedirects = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.followRedirects = Redirect.valueOf(followRedirects.toUpperCase());
                    break;
                case "\"proxy\"":
                    ObjectType proxyObj = ((ObjectType) pair.getEvaluatedValue());
                    ProxyInfo pi = new ProxyInfo();
                    //
                    // TODO: copy the currentValue?
                    //
                    for (PairType proxyPair : proxyObj.getPairs()) {
                        String proxyPairKey = proxyPair.getKey();
                        switch (proxyPairKey.toLowerCase()) {
                            case "\"host\"":
                                String host = ((StringType) proxyPair.getEvaluatedValue()).getJavaStringValue();
                                pi.host = host;
                                break;
                            case "\"port\"":
                                int port = (int) ((NumberType) proxyPair.getEvaluatedValue()).getDoubleValue();
                                pi.port = port;
                                break;
                        }
                    }
                    info.proxy = pi;
                    break;
                

                case "\"headers\"":
                    //ObjectType hdrsObj = (ObjectType) pair.getValue().evaluate();
                    ObjectType hdrsObj = (ObjectType) pair.getEvaluatedValue();
                    info.headers = hdrsObj;
                    break;
                case "\"params\"":
                    ObjectType paramsObj = (ObjectType) pair.getEvaluatedValue();
                    info.params = paramsObj;
                    break;
                case "\"componentconfiguration\"":
                    ObjectType componentConfigurationObj = (ObjectType) pair.getEvaluatedValue();
                    info.componentConfiguration = componentConfigurationObj;
                    break;
                case "\"sendcomponentconfigurationasheader\"":
                    Node sendComponentConfigurationAsHeaderValue = pair.getEvaluatedValue();
                    if (sendComponentConfigurationAsHeaderValue instanceof FalseType) {
                        info.sendComponentConfigurationAsHeader = false;
                    }
                    else {
                        info.sendComponentConfigurationAsHeader = true;
                    }
                    break;
                case "\"createerroronfailurestatuscode\"":
                    Node createErrorOnFailureStatusCode_Node = pair.getEvaluatedValue();
                    if (createErrorOnFailureStatusCode_Node instanceof TrueType) {
                        info.createErrorOnFailureStatusCode = true;
                    }
                    else {
                        info.createErrorOnFailureStatusCode = false;
                    }
                    break;
                case "\"debug\"":
                    Node debug_Node = pair.getEvaluatedValue();
                    if (debug_Node instanceof TrueType) {
                        info.debug = true;
                    }
                    else {
                        info.debug = false;
                    }
                    break;
                default:
                    //:OFF:log.debug("file -producer: no mapping for configuration key: " + key);
                    System.err.println("file -producer: no mapping for configuration key: " + key);
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "HTTP", "ERROR", "http -producer: no mapping for configuration key: " + key);
            }
        }
        
        currentValue.getStatement().setCurrentValue(currentValueCopy);
        return info;
    }
    
    private class Info {
        private String url = null;
        private RequestMethod method = RequestMethod.GET;
        
        private WriteAsType writeAs = WriteAsType.JSON; // Tells how the request should be set.
        
        private ReadAsType readAs = ReadAsType.JSON; // Tells how the response should be interpreted.
        
        private String contentType = null; // Automatically set. Can be overridden here.
        
        private Integer httpVersion = null; // 2 (default) or 1.1
        private Redirect followRedirects = null; // ALWAYS, NORMAL, NEVER (default)
        private Long connectTimeout = null; // milliseconds
        private ProxyInfo proxy = null;
        
        private ObjectType headers = null;
        private ObjectType params = null;
        private ObjectType componentConfiguration = null;
        private boolean sendComponentConfigurationAsHeader = true; // if FALSE, then try to add into the body as own object, which assumes that body is an object
        
        // common configuration option
        private boolean debug = false;
        private boolean createErrorOnFailureStatusCode = false;
    }

    private class ProxyInfo {
        private String host = "";
        private int port = 80; // default
    }

    private enum WriteAsType {
        JSON, RAW;
    }
    
    private enum ReadAsType {
        JSON, RAW, STREAM, EMPTY;
    }
    
    enum RequestMethod {
        GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS, TRACE, CONNECT;
    }

}