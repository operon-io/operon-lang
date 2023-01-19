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

package io.operon.runner.system.integration.out;

import io.operon.runner.OperonContext;
import io.operon.runner.util.RandomUtil;

import java.util.Date;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.system.IntegrationComponent;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.Context;
import io.operon.runner.Main;
import io.operon.runner.processor.function.core.string.StringToRaw;

import java.lang.reflect.Method;

import io.operon.runner.system.ComponentSystemUtil;
import io.operon.runner.system.integration.BaseComponent;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.exception.OperonComponentException;

import org.apache.logging.log4j.LogManager;


public class OutComponent extends BaseComponent implements IntegrationComponent {
     // no logger 

    public OutComponent() {}
    
    public OperonValue produce(OperonValue currentValue) throws OperonComponentException {
        try {
            //System.out.println("OUT: CV = " + currentValue);
            Info info = this.resolve(currentValue);
            Context ctx = currentValue.getStatement().getOperonContext();
            StringBuilder sb = new StringBuilder();
            if (info.prefixId) {
                if (this.getComponentId() != null) {
                    sb.append("out:" + this.getComponentId() + " :: ");
                }
                else {
                    sb.append("out :: ");
                }
            }
            
            if (info.outputAsRaw) {
                currentValue = (OperonValue) currentValue.evaluate();
                byte[] uninterpretedBytes = null;
                if (currentValue instanceof RawValue) {
                    RawValue raw = (RawValue) currentValue;
                    uninterpretedBytes = raw.getBytes();
                }
                else if (currentValue instanceof OperonValue) {
                    uninterpretedBytes = StringToRaw.stringToBytes(((StringType) currentValue).getJavaStringValue(), true);
                }
                else {
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "OUT", "ERROR", "Cannot convert value to raw-value.");
                }
                
                if (info.rawEncoding != null) {
                    String text = new String(uninterpretedBytes, info.rawEncoding);
                    if (ctx.getContextLogger() == null) {
                        System.out.println(text);
                    }
                    else {
                        ctx.getContextLogger().println(text);
                    }
                }
                else {
                    if (ctx.getContextLogger() == null) {
                        System.out.println(uninterpretedBytes);
                    }
                    else {
                        ctx.getContextLogger().println(uninterpretedBytes);
                    }
                }
            }
            
            else {
                if (info.timestamp) {
                    sb.append((new Date()).getTime());
                    if (info.message != null || info.printValue == true) {
                        sb.append(" :: ");
                    }
                }
                
                if (info.message == null) {
                    if (info.printValue) {
                        if (info.prettyPrint) {
                            sb.append(OperonContext.serializeAsPrettyJson(currentValue));
                        }
                        else {
                            sb.append(currentValue.toString());
                        }
                    }
                }
                
                else {
                    sb.append(info.message);
                    if (info.printValue) {
                        sb.append(" :: ");
                        if (info.prettyPrint) {
                            sb.append(OperonContext.serializeAsPrettyJson(currentValue));
                        }
                        else {
                            sb.append(currentValue.toString());
                        }
                    }
                }
                
                if (ctx.getContextLogger() == null) {
                    System.out.println(sb.toString());
                }
                
                else {
                    ctx.getContextLogger().println(sb.toString());
                }
            }
            return currentValue;
        } catch (OperonGenericException e) {
            throw new OperonComponentException(e.getErrorJson());
        }
    }
    
    public Info resolve(OperonValue currentValue) throws OperonGenericException {
        OperonValue currentValueCopy = currentValue;
        
        ObjectType jsonConfiguration = this.getJsonConfiguration();
        jsonConfiguration.getStatement().setCurrentValue(currentValueCopy);
        List<PairType> jsonPairs = jsonConfiguration.getPairs();

        Info info = new Info();
        
        for (PairType pair : jsonPairs) {
            String key = pair.getKey();
            OperonValue currentValueCopy2 = currentValue;
            pair.getStatement().setCurrentValue(currentValueCopy2);
            switch (key.toLowerCase()) {
                case "\"message\"":
                    //System.out.println(">>>> 1");
                    OperonValue msgNode = (OperonValue) pair.getEvaluatedValue();
                    //System.out.println("msgNode class=" + msgNode.getClass().getName());
                    String sMessage = ((StringType) msgNode).getJavaStringValue();
                    info.message = sMessage;
                    break;
                case "\"rawencoding\"":
                    String sRawEncoding = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.rawEncoding = Charset.forName(sRawEncoding);
                    break;
                case "\"printvalue\"":
                    Node printValueNode = pair.getEvaluatedValue();
                    if (printValueNode instanceof TrueType) {
                        info.printValue = true;
                    }
                    else {
                        info.printValue = false;
                    }
                    break;
                case "\"prettyprint\"":
                    Node prettyPrint_Node = pair.getEvaluatedValue();
                    if (prettyPrint_Node instanceof TrueType) {
                        info.prettyPrint = true;
                    }
                    else {
                        info.prettyPrint = false;
                    }
                    break;
                case "\"raw\"":
                    Node outputAsRawNode = pair.getEvaluatedValue();
                    if (outputAsRawNode instanceof TrueType) {
                        info.outputAsRaw = true;
                    }
                    else {
                        info.outputAsRaw = false;
                    }
                    break;
                case "\"timestamp\"":
                    Node timestampNode = pair.getEvaluatedValue();
                    if (timestampNode instanceof TrueType) {
                        info.timestamp = true;
                    }
                    else {
                        info.timestamp = false;
                    }
                    break;
                case "\"prefixid\"":
                    Node prefixIdNode = pair.getEvaluatedValue();
                    if (prefixIdNode instanceof TrueType) {
                        info.prefixId = true;
                    }
                    else {
                        info.prefixId = false;
                    }
                    break;
                case "\"valueonly\"":
                    Node valueOnlyNode = pair.getEvaluatedValue();
                    if (valueOnlyNode instanceof TrueType) {
                        info.valueOnly = true;
                        
                        info.prefixId = false;
                        info.message = null;
                        info.timestamp = false;
                    }
                    else {
                        info.valueOnly = false;
                    }
                    break;
                default:
                    //:OFF:log.debug("out -producer: no mapping for configuration key: " + key);
                    System.err.println("out -producer: no mapping for configuration key: " + key);
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "OUT", "ERROR", "out -producer: no mapping for configuration key: " + key);
            }
        }
        
        currentValue.getStatement().setCurrentValue(currentValueCopy);
        return info;
    }

    private class Info {
        private String message = null;
        private boolean printValue = true;
        private boolean prettyPrint = false;
        private boolean timestamp = true;
        private boolean prefixId = true;
        private boolean valueOnly = false; // when set true, then printValue: true, timestamp: false, prefixId: false, message: null
        
        // If this option is true, then other options are ignored.
        // Tries to cast the current-value as RawValue.
        private boolean outputAsRaw = false; // set with "raw"
        private Charset rawEncoding = Main.defaultCharset; // null = none, print as raw. UTF-8
    }

}