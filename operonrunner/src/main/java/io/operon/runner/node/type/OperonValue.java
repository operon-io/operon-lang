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

package io.operon.runner.node.type;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.LambdaFunctionRef;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.type.OperonValueConstraint;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// OperonValue is assignable.
// Those Nodes that extend OperonValue, become also
// assignable.
//
public class OperonValue extends AbstractNode implements Node {
     // no logger 
    
    private Node value; // boxed value
    
    // This means that the value has been calculated to atomic -json-value.
    //private boolean unboxed = false;
    
    //
    // This means that if evaluate is called, then the current-value (this) is returned instantly.
    // This is required when accessing value through LetStatement, where the value has already been calculated,
    // e.g. ObjectType, which contains expressions would otherwise re-evaluate the expressions.
    //
    //private boolean preventReEvaluation = false;
    
    private OperonValueConstraint constraint;
    
    public OperonValue(Statement stmnt) {
        super(stmnt);
    }

    // Sets the boxed-value
    public void setValue(Node value) {
        //assert (value != null): "OperonValue :: setValue :: trying to set null-value.";
        this.value = value;
        this.setUnboxed(false);
        this.setPreventReEvaluation(false);
    }

    // Evaluates the boxed value
    public OperonValue evaluate() throws OperonGenericException {
        //:OFF:log.debug("OperonValue :: evaluate, bindings size :: " + value.getBindings().size());

        OperonValue result = value.evaluate();
        //:OFF:log.debug("  OperonValue :: " + result.getClass().getName());
        // update the currentValue from the statement
        //assert(this.getStatement() != null): "OperonValue.evaluate() :: getStatement was null";
        // # Removed this for #objWithCvSubstringTest
        //this.getStatement().setCurrentValue(result);
        //assert(result != null): "OperonValue.evaluate() :: result was null";
        return result;
    }
    
    public OperonValue unbox() throws OperonGenericException {
        OperonValue result = this;
        while (result.getUnboxed() == false
            && (result instanceof LambdaFunctionRef == false)
            && (result instanceof FunctionRef == false)) {
            result = result.evaluate();
        }
        
        return result;
    }
    
    public static OperonValue fromBoolean(Statement stmt, boolean value) {
        if (value) {
            return new TrueType(stmt);
        }
        else {
            return new FalseType(stmt);
        }
    }
    
    // Returns the boxed-value
    public Node getValue() {
        return this.value;
    }

    public OperonValue lock() {
        this.setPreventReEvaluation(true);
        this.setUnboxed(true);
        //this.getValue().lock();
        return this;
    }

    //
    // Creates a deep-copy of the Jsonvalue, with array-copying flag.
    //
    public OperonValue copy(boolean deepCopyArrays) throws OperonGenericException {
        ////:OFF:log.debug("OperonValue :: copy()");
        if (deepCopyArrays) {
            OperonValue result = JsonUtil.copyOperonValueWithArray(this);
            return result;
        }
        else {
            OperonValue result = JsonUtil.copyOperonValue(this);
            return result;
        }
        
    }

    //
    // Creates a deep-copy of the Jsonvalue, excluding the Arrays.
    //
    public OperonValue copy() throws OperonGenericException {
        ////:OFF:log.debug("OperonValue :: copy()");
        OperonValue result = JsonUtil.copyOperonValue(this);
        return result;
    }
    
    public void setOperonValueConstraint(OperonValueConstraint c) {
        this.constraint = c;
    }
    
    public OperonValueConstraint getOperonValueConstraint() {
        return this.constraint;
    }

    @Override
    public String toString() {
        //assert (this.getValue() != null): "OperonValue :: toString :: null value.";
        String result = this.getValue().toString();
        return result;
    }
    
    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        if (ofmt == null) {ofmt = new OutputFormatter();}
        String result = this.getValue().toFormattedString(ofmt);
        return result;
    }

    @Override
    public String toYamlString(YamlFormatter yf) {
        if (yf == null) {yf = new YamlFormatter();}
        String result = this.getValue().toYamlString(yf);
        return result;
    }

    @Override
    public String toTomlString(OutputFormatter ofmt) {
        if (ofmt == null) {ofmt = new OutputFormatter();}
        String result = this.getValue().toTomlString(ofmt);
        return result;
    }

}