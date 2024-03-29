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

package io.operon.runner.node;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.PairType;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.TrueType;
import io.operon.runner.node.type.FalseType;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class Operator extends AbstractNode implements Node {
     // no logger 

    // This should be set dynamically (during runtime).
    private OperonValue valueToApplyAgainst;

    // When accessing the valueRef, there should be recorded information of the Operator Bindings.
    // This could be asked e.g. from BindManager -singleton, which could held Map<String, Operator>,
    // where the key (String) is the value-to be reffed.
    // When using the operator (e.g. Binary=), the Equals -operator should first check from the LHS
    // if it has any Bindings, and use that instead of the standard Equals-operation.
    
    private String operator;
    
    //
    // The function which implements the custom binding, unary or binary.
    //
    private FunctionRef functionRef;
    
    //
    // Controls if the resulting value should also have the same operator-binding.
    //
    private boolean cascade = false;
    
    public Operator(Statement stmnt) {
        super(stmnt);
    }

    // Not sure if this can be evaluated, because this is not part of the wirings.
    // I think that this must register to OperonContext's Bindings or BindManager,
    // which will cascade the Bindings dynamically, when requested.
    public OperonValue evaluate() throws OperonGenericException {
        //:OFF:log.debug("ENTER Operator.evaluate()");
        return null;
    }

    public void setOperator(String op) {
        this.operator = op;
    }
    
    public String getOperator() {
        return this.operator;
    }
    
    public void setFunctionRef(FunctionRef funcRef) {
        this.functionRef = funcRef;
    }
    
    public void setCascade(boolean c) {
        this.cascade = c;
    }
    
    public boolean getCascade() {
        return this.cascade;
    }
    
    public FunctionRef getFunctionRef() {
        return this.functionRef;
    }
}
