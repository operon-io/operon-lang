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

package io.operon.runner.statement;

import io.operon.runner.OperonContext;
import io.operon.runner.Context;
import io.operon.runner.node.Node;
import io.operon.runner.model.InputSource;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.FalseType;
import io.operon.runner.node.type.OperonValueConstraint;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class FromStatement extends BaseStatement implements Statement {
     // no logger 
    
    private InputSource inputSource;

    public FromStatement(Context ctx) {
        super(ctx);
    }
    
    public OperonValue evaluate() throws OperonGenericException {
        //return this.getNode().evaluate();
        return null;
    }
    
    public void setInputSource(InputSource is) {
        this.inputSource = is;
    }
    
    public InputSource getInputSource() {
        return this.inputSource;
    }
}