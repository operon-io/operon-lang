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

package io.operon.runner.processor;

import io.operon.runner.node.Node;
import io.operon.runner.node.Operator;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.statement.Statement;
import io.operon.runner.node.type.*;
import io.operon.runner.util.ErrorUtil;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

/**
 * 
 * 
 * 
 */
public abstract class BaseBinaryNodeProcessor implements BinaryNodeProcessor, java.io.Serializable {
     // no logger 
    
    protected OperonValue lhsResult;
    protected OperonValue rhsResult;
    protected int sourceCodeLineNumber = -1;
    
    //short processed = 0;

    public void preprocess(Statement statement, Node lhs, Node rhs) throws OperonGenericException {
        //:OFF:log.debug("BaseBinaryNodeProcessor :: preprocess");

        OperonValue initialValue = statement.getCurrentValue();
        OperonValue initialValueRhsCopy = null;

        //if (processed > 2) {
        //    return;
        //}
        //processed += 1;

        // cv @ might be null when executing only select statement (from unit-tests)
        if (initialValue != null) {
            //:OFF:log.debug("  BaseBinaryNodeProcessor :: has initialValue");
            //
            // This hax stretches the limits of the current architecture...
            //
            try {
                initialValueRhsCopy = initialValue.copy();
            }
            catch (Exception e) {
                return;
            }
        }
        
        lhsResult = lhs.evaluate();
        //:OFF:log.debug("  BaseBinaryNodeProcessor :: lhsresult bindings size :: " + lhsResult.getBindings().size());
        
        // Fork the initial value for rhs:
        statement.setCurrentValue(initialValueRhsCopy);
        rhsResult = rhs.evaluate();
        if (lhsResult instanceof OperonValue) {
            //:OFF:log.debug("  BaseBinaryNodeProcessor :: lhsresult is OperonValue --> unboxing");
            //:OFF:log.debug("  BaseBinaryNodeProcessor :: lhsresult bindings size before unboxing :: " + lhsResult.getBindings().size());
            Map<String, Operator> bindings = lhsResult.getBindings(); // HACK: for passing the bindings after evaluating (unboxing) OperonValue
            
            // Related to OperatorTests#35, might remove if not needed!
            boolean lhsDoBindings = lhsResult.getDoBindings();
            // TODO: should we copy bindings for rhs aswell?!
            
            //
            // NOTES:
            //   - The ArrayType or ObjectType are NOT unboxed, because that would evaluate ALL
            //   the items in the structure. When done e.g. in Loop -pattern, this would
            //   slow down Operon. Example: 'Select: [] Loop ($i: 1000): @ + 1;'
            //   - This check is done only for LHS.
            //   - Only BinaryOp where this has effect is Plus.
            //
            if (lhsResult.getUnboxed() == false 
                && (lhsResult instanceof ArrayType == false) 
                && (lhsResult instanceof ObjectType == false)) {
                lhsResult = lhsResult.unbox();
            }
            lhsResult.getBindings().putAll(bindings);
            
            // Test#35
            lhsResult.setDoBindings(lhsDoBindings);
            //:OFF:log.debug("  BaseBinaryNodeProcessor :: lhsresult bindings size after unboxing :: " + lhsResult.getBindings().size());
        }
        
        if (rhsResult instanceof OperonValue) {
            if (rhsResult.getUnboxed() == false) {
                rhsResult = rhsResult.unbox();
            }
        }
    }
    
    protected void preprocessRhs(Statement statement, Node rhs) throws OperonGenericException {
        OperonValue initialValue = statement.getCurrentValue();
        rhsResult = rhs.evaluate();
        if (rhsResult instanceof OperonValue) {
            rhsResult = rhsResult.evaluate(); // unbox
        }
        statement.setCurrentValue(initialValue); // restore current-value for statement
    }
    
    public OperonValue process(Statement statement, Node lhs, Node rhs) throws OperonGenericException {
        //:OFF:log.debug("BaseBinaryNodeProcessor :: process :: Returning null");
        return null;
    }

    public boolean customBindingCheck(Node lhs, Node rhs, String binaryOperator) {
        // rhsResult might be null for logical And, because it is only evaluated if LHS is true
        if (rhsResult != null) {
            //:OFF:log.debug("customBindingCheck :: rhs -> doBindings() :: " + rhs.getDoBindings());
            //:OFF:log.debug("customBindingCheck :: rhsResult -> doBindings() :: " + rhsResult.getDoBindings());
            //:OFF:log.debug("customBindingCheck :: rhsResult.getBindings().get(binaryOperator) != null :: " + rhsResult.getBindings().get(binaryOperator) != null);
            //:OFF:log.debug("customBindingCheck :: rhsResult.getBindings().size() :: " + rhsResult.getBindings().size());
        }
        if ( (lhsResult.getBindings().size() > 0 && lhs.getDoBindings() == true && lhsResult.getBindings().get(binaryOperator) != null) ||
             (rhsResult != null && rhsResult.getBindings().size() > 0 && rhs.getDoBindings() == true && rhsResult.getBindings().get(binaryOperator) != null)) {
            //:OFF:log.debug("customBindingCheck :: yes, do bindings");
            return true;
        }
        
        else {
            //:OFF:log.debug("customBindingCheck :: don't do bindings");
            return false;
        }
    }

    public OperonValue doCustomBinding(Statement statement, Node lhs, Node rhs, String binaryOperator) throws OperonGenericException {
        // If binding is on rhs, then use that one instead!
        // If both sides have bindings, then what to do? We should probably default to using lhs.
        Operator op = null;
        
        // rhsResult could be null on logical And (rhs not evaluated yet)
        if (rhsResult == null) {
            preprocessRhs(statement, rhs);
        }
        
        if (lhsResult.getBindings().size() > 0 && lhs.getDoBindings()) {
            op = lhsResult.getBindings().get(binaryOperator);
        }
        
        else {
            op = rhsResult.getBindings().get(binaryOperator);
        }
        
        if (op != null) {
            //System.out.println("START customBinding");
            FunctionRef fRef = op.getFunctionRef();
            lhs.setDoBindings(false); // TODO: may not be needed
            rhs.setDoBindings(false); // TODO: may not be needed
            lhsResult.setDoBindings(false);
            rhsResult.setDoBindings(false);
            fRef.setArgument(lhsResult);
            fRef.setArgument(rhsResult);
            //:OFF:log.debug("doCustomBinding :: invoke binary function");
            OperonValue result = (OperonValue) fRef.invoke();
            //:OFF:log.debug("doCustomBinding :: done invoking binary function");
            lhsResult.setDoBindings(true);
            rhsResult.setDoBindings(true);
            lhs.setDoBindings(true); // TODO: may not be needed
            rhs.setDoBindings(true); // TODO: may not be needed
            
            // Cascade lhs by default, use rhs is lhs bindings are missing.
            // TODO: think if we want to use binding-side preference on the Operator! Operator(+, myOp, cascade, left)
            if (op.getCascade()) {
                //:OFF:log.debug("=== CASCADING BINDINGS ===");
                Map<String, Operator> lhsBindings = lhsResult.getBindings();
                boolean lhsDoBindings = lhsResult.getDoBindings();
                
                if (lhsDoBindings && lhsBindings.size() > 0) {
                    result.getBindings().putAll(lhsBindings);
                    result.setDoBindings(lhsDoBindings);
                }
                
                else {
                    result.getBindings().putAll(rhsResult.getBindings());
                    result.setDoBindings(rhsResult.getDoBindings());
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