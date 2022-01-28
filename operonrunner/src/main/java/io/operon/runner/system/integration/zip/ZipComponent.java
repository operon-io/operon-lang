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

package io.operon.runner.system.integration.zip;

import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.LinkOption;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.system.IntegrationComponent;
import io.operon.runner.system.integration.BaseComponent;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.GzipUtil;
import io.operon.runner.util.StringUtil;
import io.operon.runner.util.ErrorUtil;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.exception.OperonComponentException;

import org.apache.logging.log4j.LogManager;

//
// This component is a "producer".
//
public class ZipComponent extends BaseComponent implements IntegrationComponent, java.io.Serializable {
    private static Logger log = LogManager.getLogger(ZipComponent.class);

    private ObjectType jsonConfiguration; // optional: json-configuration for the component
    private boolean isRunning;
    private Long pollCounter = 0L;
    private ObjectType initialValue;
    
    public ZipComponent() {}

    public OperonValue produce(OperonValue currentValue) throws OperonComponentException {
        log.debug("zip :: produce");
        try {
            Info info = resolve(currentValue);
            OperonValue result = this.handleTask(currentValue, info);
            return result;
        } catch (OperonGenericException | IOException e) {
            ErrorValue error = ErrorUtil.createErrorValue(currentValue.getStatement(), "COMPONENT", "ZIP", e.getMessage());
            throw new OperonComponentException(error);
        }
    }

    private OperonValue handleTask(OperonValue currentValue, Info info) throws OperonGenericException, IOException {
        OperonValue result = new OperonValue(currentValue.getStatement());
        
        boolean compressMode = true;
        
        if (info.mode == Mode.AUTO) {
            currentValue = (OperonValue) currentValue.evaluate();
            if (currentValue instanceof RawValue) {
                byte[] bytes = ((RawValue) currentValue).getBytes();
                if (GzipUtil.isCompressed(bytes)) {
                    compressMode = false;
                    byte[] deflated = GzipUtil.decompress(bytes);
                    RawValue rawResult = new RawValue(currentValue.getStatement());
                    rawResult.setValue(deflated);
                    result = rawResult;
                }
            }
        }
        
        else if (info.mode == Mode.DEFLATE) {
            compressMode = false;
            currentValue = (OperonValue) currentValue.evaluate();
            byte[] bytes = ((RawValue) currentValue).getBytes();
            byte[] deflated = GzipUtil.decompress(bytes);
            RawValue rawResult = new RawValue(currentValue.getStatement());
            rawResult.setValue(deflated);
            result = rawResult;
        }
        
        else if (info.mode == Mode.COMPRESS) {
            // compressMode = true; // this is set by default
        }
        
        if (compressMode) {
            if (info.zipType == ZipType.GZIP) {
                if (currentValue instanceof RawValue) {
                    RawValue raw = (RawValue) currentValue;
                    byte[] compressed = GzipUtil.compress(raw.getBytes());
                    RawValue rawResult = new RawValue(currentValue.getStatement());
                    rawResult.setValue(compressed);
                    result = rawResult;
                }
                else {
                    byte[] compressed = GzipUtil.compress(currentValue.toString().getBytes(StandardCharsets.UTF_8));
                    RawValue rawResult = new RawValue(currentValue.getStatement());
                    rawResult.setValue(compressed);
                    result = rawResult;
                }
            }
        }
        
        currentValue.getStatement().setCurrentValue(result);
        return result;
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
            //System.out.println("KEY=" + key);
            switch (key.toLowerCase()) {
                case "\"ziptype\"":
                    String zt = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    try {
                        info.zipType = ZipType.valueOf(zt.toUpperCase());
                    } catch(Exception e) {
                        System.err.println("ERROR SIGNAL: zipType-property");
                    }
                    break;
                case "\"mode\"":
                    String m = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    try {
                        info.mode = Mode.valueOf(m.toUpperCase());
                    } catch(Exception e) {
                        System.err.println("ERROR SIGNAL: mode-property");
                    }
                    break;
                default:
                    log.debug("zip -producer: no mapping for configuration key: " + key);
                    System.err.println("zip -producer: no mapping for configuration key: " + key);
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "ZIP", "ERROR", "zip -producer: no mapping for configuration key: " + key);
            }
        }
        
        currentValue.getStatement().setCurrentValue(currentValueCopy);
        return info;
    }
    
    private class Info {
        private ZipType zipType = ZipType.GZIP;
        private Mode mode = Mode.AUTO;
        private long timeZipIterations = 3L; // How many iterations for time-zip, more iterations might allow better compression, but is not guaranteed.
    }

    private enum ZipType {
        GZIP("gzip"); // standard zipping
        private String zipType = "gzip";
        ZipType(String type) {
            this.zipType = type;
        }
        public String getZipType() { return this.zipType; }
    }

    private enum Mode {
        AUTO("auto"), COMPRESS("compress"), DEFLATE("deflate");
        private String mode = "auto";
        Mode(String m) {
            this.mode = m;
        }
        public String getMode() { return this.mode; }
    }

}
