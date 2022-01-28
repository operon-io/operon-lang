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

package io.operon.runner.model.streamvaluewrapper;

import java.io.InputStream;
import java.io.IOException;
import java.io.Closeable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.CharBuffer;

import io.operon.runner.OperonContext;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class StreamValueInputStreamWrapper implements StreamValueWrapper {
    private static Logger log = LogManager.getLogger(StreamValueInputStreamWrapper.class);
    
    private boolean supportsJson = false;
    
    private InputStream is;
    private BufferedReader reader = null;

    public StreamValueInputStreamWrapper() {}
    
    public StreamValueInputStreamWrapper(InputStream is) {
        this.is = is;
    }

    public void setInputStream(InputStream is) {
        this.is = is;
    }

    public InputStream getInputStream() {
        return this.is;
    }

    public boolean supportsJson() {
        return this.supportsJson;
    }
    
    public void setSupportsJson(boolean sj) {
        this.supportsJson = sj;
    }

    public void close() throws IOException {
        this.is.close();
    }
    
    //
    // Reads one OperonValue from inputstream:
    //
    public OperonValue readJson() throws OperonGenericException {
        try {
            if (this.reader == null) {
                this.reader = new BufferedReader(new InputStreamReader(this.getInputStream()));
            }
            
            String line = null;
            OperonValue result = null;
    
            int i = 0;
            while (i < 10) {
                if (reader.ready()) {
                    line = reader.readLine();
                    if (line != null) {
                        return JsonUtil.lwOperonValueFromString(line);
                    }
                    else {
                        log.debug("streamvaluewrapper :: read empty line");
                        i += 1;
                        OperonContext jsonContext = new OperonContext();
                        Statement stmt = new DefaultStatement(jsonContext);
                        this.getInputStream().close();
                        return new EndValueType(stmt);
                    }
                }
                else {
                    if (i % 5 == 0) {
                        log.debug("streamvaluewrapper :: waiting for stream to be ready.");
                    }
                    Thread.sleep(50);
                    i += 1;
                }
            }
            
            this.getInputStream().close();
            OperonContext jsonContext = new OperonContext();
            Statement stmt = new DefaultStatement(jsonContext);
            return new EndValueType(stmt);
        } catch (IOException ioe) {
            throw new OperonGenericException(ioe.getMessage());
        } catch (InterruptedException ie) {
            throw new OperonGenericException(ie.getMessage());
        }
    }

}