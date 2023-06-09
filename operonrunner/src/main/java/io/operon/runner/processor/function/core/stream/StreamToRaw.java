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

package io.operon.runner.processor.function.core.raw;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.RawValue;
import io.operon.runner.node.type.StreamValue;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.streamvaluewrapper.*;

public class StreamToRaw extends BaseArity0 implements Node, Arity0 {
    
    public StreamToRaw(Statement statement) {
        super(statement);
        this.setFunctionName("toRaw");
        this.setNs(Namespaces.STREAM);
    }

    public RawValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            StreamValue sv = (StreamValue) currentValue.evaluate();
            
            StreamValueWrapper svw = sv.getStreamValueWrapper();
            
            RawValue result = new RawValue(currentValue.getStatement());
            
            if (svw instanceof StreamValueByteArrayWrapper) {
                byte[] targetArray = new byte[((StreamValueByteArrayWrapper) svw).getByteArrayInputStream().available()];
                ((StreamValueByteArrayWrapper) svw).getByteArrayInputStream().read(targetArray);
                result.setValue(targetArray);
            }
            
            else if (svw instanceof StreamValuePipedInputStreamWrapper) {
                byte[] targetArray = new byte[((StreamValuePipedInputStreamWrapper) svw).getPipedInputStream().available()];
                ((StreamValuePipedInputStreamWrapper) svw).getPipedInputStream().read(targetArray);
                result.setValue(targetArray);
            }
            
            else {
                throw new Exception("could not recognize stream-type");
            }
            
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "stream", e.getMessage());
            return null;
        }
    }

}