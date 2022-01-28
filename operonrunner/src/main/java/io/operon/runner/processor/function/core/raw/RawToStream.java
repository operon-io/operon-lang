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

package io.operon.runner.processor.function.core.raw;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;
import java.io.ByteArrayInputStream;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.streamvaluewrapper.StreamValueWrapper;
import io.operon.runner.model.streamvaluewrapper.StreamValueByteArrayWrapper;

public class RawToStream extends BaseArity0 implements Node, Arity0 {
    
    public RawToStream(Statement statement) {
        super(statement);
        this.setFunctionName("toStream");
    }

    public StreamValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            RawValue raw = (RawValue) currentValue.evaluate();
            
            StreamValue result = new StreamValue(currentValue.getStatement());
            ByteArrayInputStream is = new ByteArrayInputStream(raw.getBytes());
            StreamValueWrapper svw = new StreamValueByteArrayWrapper(is);
            result.setValue(svw);
            
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "raw:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}