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

package io.operon.runner.processor;

import io.operon.runner.node.Node;
import io.operon.runner.node.Operator;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.statement.Statement;
import io.operon.runner.node.type.TrueType;
import io.operon.runner.node.type.FalseType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.util.ErrorUtil;

import java.util.Map;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 * 
 * 
 * 
 */
public abstract class BaseUnaryNodeProcessor implements UnaryNodeProcessor, java.io.Serializable {
     // no logger 
    
    protected OperonValue nodeResult;
    protected int sourceCodeLineNumber = -1;
    
    public void preprocess(Statement statement, Node node) throws OperonGenericException {
        //:OFF:log.debug("BaseUnaryNodeProcessor :: preprocess");
        OperonValue initialValue = statement.getCurrentValue();
        nodeResult = node.evaluate();
        //:OFF:log.debug("  BaseUnaryNodeProcessor :: nodeResult bindings size :: " + nodeResult.getBindings().size());
        // Fork the initial value for rhs:
        statement.setCurrentValue(initialValue);
        
        if (nodeResult instanceof OperonValue) {
            //:OFF:log.debug("  BaseUnaryNodeProcessor :: noderesult is OperonValue --> unboxing");
            //:OFF:log.debug("  BaseUnaryNodeProcessor :: noderesult bindings size before unboxing :: " + nodeResult.getBindings().size());
            Map<String, Operator> bindings = nodeResult.getBindings(); // HACK: for passing the bindings after evaluating (unboxing) OperonValue
            
            // Related to OperatorTests#35, might remove if not needed!
            boolean nodeDoBindings = nodeResult.getDoBindings();
            // TODO: should we copy bindings for rhs aswell?!
            if (nodeResult.getUnboxed() == false) {
                nodeResult = nodeResult.unbox();
            }
            nodeResult.getBindings().putAll(bindings);
            
            // Test#35
            nodeResult.setDoBindings(nodeDoBindings);
            //:OFF:log.debug("  BaseUnaryNodeProcessor :: nodeResult bindings size after unboxing :: " + nodeResult.getBindings().size());
        }
    }
    
    public OperonValue process(Statement statement, Node lhs, Node rhs) throws OperonGenericException {
        //:OFF:log.debug("BaseUnaryNodeProcessor :: process :: Returning null");
        return null;
    }

    public boolean customBindingCheck(Node node, String unaryOperator) {
        //:OFF:log.debug("customBindingCheck :: node -> doBindings() :: " + node.getDoBindings());
        //:OFF:log.debug("customBindingCheck :: node.getBindings().get(unaryOperator) != null :: " + node.getBindings().get(unaryOperator) != null);
        //:OFF:log.debug("customBindingCheck :: node.getBindings().size() :: " + node.getBindings().size());
        if ( (node.getBindings().size() > 0 && node.getDoBindings() == true && node.getBindings().get(unaryOperator) != null) ) {
            //:OFF:log.debug("customBindingCheck :: yes, do bindings");
            return true;
        }
        
        else {
            //:OFF:log.debug("customBindingCheck :: don't do bindings");
            return false;
        }
    }

    public OperonValue doCustomBinding(Statement statement, Node node, String unaryOperator) throws OperonGenericException {
        // If binding is on rhs, then use that one instead!
        // If both sides have bindings, then what to do? We should probably default to using lhs.
        Operator op = null;
        if (node.getBindings().size() > 0 && node.getDoBindings()) {
            op = (Operator) node.getBindings().get(unaryOperator);
        }
        
        if (op != null) {
            FunctionRef fRef = op.getFunctionRef();
            nodeResult.setDoBindings(false); // TODO: may not be needed
            fRef.setArgument(nodeResult);
            //:OFF:log.debug("doCustomBinding :: INVOKE UNARY FUNCTION !!!!!!!");
            OperonValue result = (OperonValue) fRef.invoke();
            //:OFF:log.debug("doCustomBinding :: DONE INVOKING UNARY FUNCTION !!!!!!!");
            nodeResult.setDoBindings(true);
            
            // Cascade lhs by default, use rhs is lhs bindings are missing.
            // TODO: think if we want to use binding-side preference on the Operator! Operator(+, myOp, cascade, left)
            if (op.getCascade()) {
                //:OFF:log.debug("=== CASCADING BINDINGS ===");
                Map<String, Operator> nodeResultBindings = nodeResult.getBindings();
                boolean nodeResultDoBindings = nodeResult.getDoBindings();
                
                if (nodeResultDoBindings && nodeResultBindings.size() > 0) {
                    result.getBindings().putAll(nodeResultBindings);
                    result.setDoBindings(nodeResultDoBindings);
                }

            }
            
            return result;
        }
        return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "MISSING", "Could not find Operator.");
    }

    public int getSourceCodeLineNumber() {
        return this.sourceCodeLineNumber;
    }

    public void setSourceCodeLineNumber(int ln) {
        this.sourceCodeLineNumber = ln;
    }

}