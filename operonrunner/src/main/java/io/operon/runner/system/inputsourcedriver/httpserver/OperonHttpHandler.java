/*
 *   Copyright 2022-2023, operon.io
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

package io.operon.runner.system.inputsourcedriver.httpserver;

import java.util.Map;
import java.util.List;
import java.util.LinkedList;
import java.util.Date;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.io.UnsupportedEncodingException;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import io.operon.runner.Main;
import io.operon.runner.OperonContext;
import io.operon.runner.OperonContextManager;
import static io.operon.runner.OperonContextManager.ContextStrategy;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.util.JsonUtil;

//
// Handle single path
//
public class OperonHttpHandler implements HttpHandler {    

    private HttpServerSystem.Info info;
    private OperonContextManager ocm;
    private String path;
    private HttpServerSystem.PathInfo pi;

    public OperonHttpHandler(OperonContextManager ocm, String path, 
                HttpServerSystem.Info info, HttpServerSystem.PathInfo pi) {
        this.info = info;
        this.ocm = ocm;
        this.path = path;
        this.pi = pi;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        HttpServerSystem.RequestMethod requestMethod = null;
        String httpExchangeRequestMethod = httpExchange.getRequestMethod().toUpperCase();
        
        debug(info, "Method: " + httpExchangeRequestMethod);
        
        switch (httpExchangeRequestMethod) { 
            case "GET":
                requestMethod = HttpServerSystem.RequestMethod.GET;
                break;
            case "POST":
                requestMethod = HttpServerSystem.RequestMethod.POST;
                break;
            default:
                requestMethod = HttpServerSystem.RequestMethod.GET;
                break;
        }
        
        try {
            //
            // Resolve sessionId from the cookie, if available:
            //
            debug(info, "HttpHandler: resolve sessionId");
            String sessionHeader = this.getRequestHeader(httpExchange, info.si.sessionHeader);
            String sessionId = null;
            if (sessionHeader != null) {
                String [] sessionHeaderParts = sessionHeader.split("=");
                if (sessionHeaderParts.length > 0) {
                    sessionId = sessionHeaderParts[1];
                }
            }
            debug(info, "HttpHandler: resolve operon-context");
            OperonContext ctx = ocm.resolveContext(sessionId);
            DefaultStatement stmt = new DefaultStatement(ctx);
            
            if (pi.methods.contains(HttpServerSystem.RequestMethod.ALL) == false 
                  && pi.methods.contains(requestMethod) == false) {
                debug(info, "HttpHandler: method not supported");
                ErrorValue errorResponse = new ErrorValue(stmt);
                errorResponse.setCode("HTTPSERVER_METHOD_NOT_SUPPORTED");
                errorResponse.setMessage("Method " + requestMethod + " not supported for the path " + this.path);
                handleResponse(errorResponse, httpExchange);
            }
            
            else {
                debug(info, "HttpHandler: start request-mapping");
                Map<String, List<String>> requestParamValues = null;
                URI requestURI = httpExchange.getRequestURI();
                debug(info, "HttpHandler: read query-params");
                requestParamValues = this.getQueryParams(requestURI.toString());
                ObjectType request = new ObjectType(stmt);
                
                //
                // $.id
                // --> mapped from path.id
                //
                debug(info, "HttpHandler: map id");
                PairType pairId = new PairType(stmt);
                StringType sNode = new StringType(stmt);
                sNode.setValue("\"" + pi.id + "\"");
                pairId.setPair("\"id\"", sNode);
                request.addPair(pairId);
                
                //
                // $.method
                // --> mapped from requestMethod (GET, POST, PUT, DELETE, etc.)
                //
                debug(info, "HttpHandler: map method");
                PairType pairMethod = new PairType(stmt);
                StringType methodNode = new StringType(stmt);
                methodNode.setValue("\"" + requestMethod.toString() + "\"");
                pairMethod.setPair("\"method\"", methodNode);
                request.addPair(pairMethod);
                
                //
                // $.ip (user ip-address)
                //
                debug(info, "HttpHandler: map ip-address");
                PairType pairIp = new PairType(stmt);
                StringType sIpNode = new StringType(stmt);
                sIpNode.setValue("\"" + httpExchange.getRemoteAddress().toString() + "\"");
                pairIp.setPair("\"ip\"", sIpNode);
                request.addPair(pairIp);
        
                // 
                // $.params {} (Request query-params)
                //
                debug(info, "HttpHandler: map params");
                PairType paramsObjPair = new PairType(stmt);
                ObjectType paramsObj = new ObjectType(stmt);
                
                // add all the params into own object-pairs:
                for (Map.Entry<String, List<String>> entry : requestParamValues.entrySet()) {
                    PairType paramsObjPairPair = new PairType(stmt);
                    StringType paramsObjPairPairValue = new StringType(stmt);
                    String paramKey = entry.getKey();
                    if (paramKey.startsWith(":")) {
                        paramKey = paramKey.substring(1, paramKey.length());
                    }
                    paramsObjPairPairValue.setValue("\"" + entry.getValue().get(0) + "\"");
                    paramsObjPairPair.setPair("\"" + paramKey + "\"", paramsObjPairPairValue);
                    paramsObj.addPair(paramsObjPairPair);
                }
                paramsObjPair.setPair("\"params\"", paramsObj);
                request.addPair(paramsObjPair);
                
                // 
                // $.requestHeaders {}
                //
                debug(info, "HttpHandler: map request headers");
                PairType requestHeadersObjPair = new PairType(stmt);
                ObjectType requestHeadersObj = new ObjectType(stmt);
                
                String contentType = "";
                
                // add all the requestHeaders into own object-pairs:
                for (Map.Entry<String, List<String>> entry : httpExchange.getRequestHeaders().entrySet()) {
                    PairType requestHeadersObjPairPair = new PairType(stmt);
                    StringType requestHeadersObjPairPairValue = new StringType(stmt);
                    String requestHeadersKey = entry.getKey();
                    if (requestHeadersKey.startsWith(":")) {
                        requestHeadersKey = requestHeadersKey.substring(1, requestHeadersKey.length());
                    }
                    String entryValue = entry.getValue().get(0);
                    if (requestHeadersKey.toLowerCase().equals("content-type")) {
                        contentType = entryValue;
                    }
                    requestHeadersObjPairPairValue.setValue("\"" + entryValue + "\"");
                    requestHeadersObjPairPair.setPair("\"" + requestHeadersKey + "\"", requestHeadersObjPairPairValue);
                    requestHeadersObj.addPair(requestHeadersObjPairPair);
                }
                requestHeadersObjPair.setPair("\"requestHeaders\"", requestHeadersObj);
                request.addPair(requestHeadersObjPair);
                
                //
                // $.body
                // --> mapped from Request, only if POST, PUT or PATCH
                // NOTE: requires that the content-type is application/json
                //
                debug(info, "HttpHandler: map body");
                if (requestMethod == HttpServerSystem.RequestMethod.POST || requestMethod == HttpServerSystem.RequestMethod.PUT || requestMethod == HttpServerSystem.RequestMethod.PATCH) {
                    PairType pair = new PairType(stmt);
                    OperonValue bodyNode = new OperonValue(stmt);
    
                    // pull request body                
                    InputStream reqBodyIS = httpExchange.getRequestBody();
                    
                    final int bufferSize = 1024;
                    final char[] buffer = new char[bufferSize];
                    final StringBuilder out = new StringBuilder();
                    Reader in = new InputStreamReader(reqBodyIS, StandardCharsets.UTF_8);
                    int charsRead;
                    while((charsRead = in.read(buffer, 0, buffer.length)) > 0) {
                        out.append(buffer, 0, charsRead);
                    }
                    
                    debug(info, "HttpHandler: map body: read inputstream");
                    String materializedRequestBody = out.toString();
                    if (materializedRequestBody.isEmpty()) {
                        bodyNode = new EmptyType(stmt);
                    }
                    
                    else {
                        if (contentType.isEmpty() == false && contentType.toLowerCase().contains("application/json")) {
                            debug(info, "HttpHandler: map body: read as application/json");
                            debug(info, "HttpHandler: map body from str: " + materializedRequestBody);
                            bodyNode.setValue(JsonUtil.operonValueFromString(materializedRequestBody));
                            debug(info, "HttpHandler: map body: set as JSON-value");
                        }
                        else {
                            // read as bytes.
                            debug(info, "HttpHandler: map body from bytes");
                            RawValue raw = new RawValue(stmt);
                            raw.setValue(materializedRequestBody.getBytes());
                            bodyNode = raw;
                        }
                    }
                    
                    pair.setPair("\"body\"", bodyNode);
                    request.addPair(pair);
                }
                
                Date now = new Date();
                
                if (info.si.logRequests) {
                    System.out.println(now + " :: REQUEST: " + request);
                }
                
                // Set the initial value into OperonContext:
                debug(info, "HttpHandler: bind initial value for operon-context");
    
                // Evaluate the query against the initial value:
                debug(info, "HttpHandler: evaluate query");
                
                //
                //OperonValue response = ctx.evaluateSelectStatement();
                //
                
                //OperonValue response = OperonContextManager.evaluateSelectStatementInNewIsolate(ocm, sessionId, null);
                
                OperonValue response = null;
                
                // This works with native-image, the problem is with the sessionAffinate (correlationId)
                if (false && Main.isNative) {
                    System.out.println("evaluateSelectStatementInNewIsolate");
                    System.out.println("  correlationId=" + sessionId);
                    response = OperonContextManager.evaluateSelectStatementInNewIsolate(ocm, sessionId, null);
                }
                else {
                    ctx.setInitialValue(request);
                    debug(info, "HttpHandler: evaluate select-statement");
                    response = ctx.evaluateSelectStatement();                    
                }
                
                if (info.si.logResponses) {
                    System.out.println(now + " :: RESPONSE: " + response);
                }
                
                debug(info, "HttpHandler: handle response");
                handleResponse(response, httpExchange);
            }
        } catch (OperonGenericException oge) {
            throw new IOException(oge.getMessage());
        }
    }

    private String getRequestHeader(HttpExchange httpExchange, String header) {
        for (Map.Entry<String, List<String>> entry : httpExchange.getRequestHeaders().entrySet()) {
            String requestHeadersKey = entry.getKey();
            if (requestHeadersKey.equals(header)) {
                String entryValue = entry.getValue().get(0);
                return entryValue;
            }
        }
        return null;
    }

    private boolean isNullOrEmpty(String value) {
        if (value == null) {return true;}
        if (value.isEmpty()) {return true;}
        return false;
    }

    public Map<String, List<String>> getQueryParams(String url) throws UnsupportedEncodingException {
      try {
          int queryPartStartIndex = url.lastIndexOf("?");
          if (queryPartStartIndex > 0) {
              url = url.substring(queryPartStartIndex + 1, url.length());
          }
          final Map<String, List<String>> query_pairs = new LinkedHashMap<String, List<String>>();
          final String[] pairs = url.split("&");
          for (String pair : pairs) {
            final int idx = pair.indexOf("=");
            final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
            if (!query_pairs.containsKey(key)) {
              query_pairs.put(key, new LinkedList<String>());
            }
            final String value = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") : null;
            query_pairs.get(key).add(value);
          }
          return query_pairs;
      } catch (Exception e) {
          System.err.println("ERROR SIGNAL: queryParams");
          return null;
      }
    }

    private void handleResponse(OperonValue response, HttpExchange httpExchange) throws OperonGenericException, IOException {
        OutputStream outputStream = httpExchange.getResponseBody();
        
        int statusCode = 200;
        OperonValue responseValue = new EmptyType(response.getStatement());
        //System.out.println("handleResponse");
        //System.out.println("  response=" + response);
        
        if (response instanceof ObjectType == false) {
            //System.out.println("response was " + response.getClass().getName());
            response = response.evaluate();
            //System.out.println("response is now " + response.getClass().getName());
        }
        else {
            //System.out.println("response is ObjectType");
        }
        
        if (response instanceof ObjectType) {
            //
            // Loop through the object keys and set the response info (e.g. statusCode)
            //
            debug(info, "HttpHandler: handle ObjectType-response");
            for (PairType pair : ((ObjectType) response).getPairs()) {
                if (pair.getKey().equals("\"statusCode\"")) {
                    statusCode = (int)((NumberType) pair.getValue().evaluate()).getDoubleValue();
                }
                else if (pair.getKey().equals("\"headers\"")) {
                    Headers hdrs = httpExchange.getResponseHeaders();
                    ObjectType headersObj = (ObjectType) pair.getValue().evaluate();
                    for (PairType headerPair : headersObj.getPairs()) {
                        String headerKey = headerPair.getKey().substring(1, headerPair.getKey().length() - 1); // remove double quotes
                        String headerValue = "";
                        OperonValue hdrValueEvaluated = headerPair.getValue().evaluate();
                        if (hdrValueEvaluated instanceof StringType) {
                            headerValue = ((StringType) hdrValueEvaluated).getJavaStringValue();
                        }
                        else {
                            headerValue = hdrValueEvaluated.toString();
                        }
                        hdrs.set(headerKey, headerValue);
                    }
                }
                else if (pair.getKey().equals("\"body\"")) {
                    //System.out.println("handleResponse :: body");
                    responseValue = pair.getValue();
                }
            }
        }
        
        else if (response instanceof ErrorValue) {
            debug(info, "HttpHandler: handle ErrorValue -response");
            responseValue = response;
        }
        
        else {
            debug(info, "HttpHandler: response not mappable. Ensure that response is Object.");
        }

        String responseStr = responseValue.toString();
        
        httpExchange.sendResponseHeaders(statusCode, responseStr.length());
        outputStream.write(responseStr.getBytes());
        outputStream.flush();
        outputStream.close();
    }

    private void debug(HttpServerSystem.Info info, String msg) {
        if (info.si.debug) {
            System.out.println(msg);
        }
    }

}