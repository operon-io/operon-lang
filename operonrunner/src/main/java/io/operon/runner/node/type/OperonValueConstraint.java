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
import io.operon.runner.processor.function.core.MappableTo;
import io.operon.runner.node.UnaryNode;
import io.operon.runner.node.BinaryNode;
import io.operon.runner.node.MultiNode;
import io.operon.runner.node.ValueRef;
import io.operon.runner.node.ObjAccess;
import java.util.List;
import java.util.ArrayList;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// OperonValueConstraint (PairType, Let -statement, FunctionCalls)
//
public class OperonValueConstraint extends AbstractNode implements Node {
     // no logger 
    private Node constraintExpr;
    private String constraintAsString;
    private OperonValue valueToEvaluateAgainst;
    private OperonValue evaluatedValue; // Evaluates to TrueType or FalseType

    public OperonValueConstraint(Statement stmnt) {
        super(stmnt);
    }

    public OperonValue evaluate() throws OperonGenericException {
        //:OFF:log.debug("OperonValueConstraint :: evaluate :: against value :: " + this.getValueToEvaluateAgainst().getClass().getName());
        this.evaluatedValue = (OperonValue) this.getValueToEvaluateAgainst().evaluate();
        //:OFF:log.debug("    evaluatedValue :: " + this.evaluatedValue);
        
        // Set the currentValue (valueToEvaluateAgainst) and evaluate the constraintExpr
        this.getStatement().setCurrentValue(this.getEvaluatedValue());
    
        OperonValue result = null;
        try {
            result = this.getValueConstraint().evaluate(); // evaluates constraintExpr
        } catch (Exception e) {
            FalseType falseResult = new FalseType(this.getStatement());
            //:OFF:log.debug("OperonValueConstraint :: EXCEPTION while evaluating the constraintExpr");
            return falseResult;
        }
        
        if (result instanceof TrueType) {
            //:OFF:log.debug("OperonValueConstraint :: constraint evaluation returned TrueType for value :: " + this.getEvaluatedValue());
            return result;
        }
        
        else if (result instanceof FalseType) {
            //:OFF:log.debug("OperonValueConstraint :: constraint evaluation returned FalseType for value :: " + this.getEvaluatedValue());
            return result;
        }
        
        else {
            //:OFF:log.debug("OperonValueConstraint :: constraint evaluation returned instanceof :: " + result.getClass().getName());
            List<Node> params = new ArrayList<Node>();
            params.add(result);
            this.getStatement().setCurrentValue(this.evaluatedValue);
            MappableTo mapTo = new MappableTo(this.getStatement(), params);
            this.setValueConstraint(mapTo);
            return this.getValueConstraint().evaluate();
        }
    
    }

    public void setValueConstraint(Node constraintExpr) {
        this.constraintExpr = constraintExpr;
    }
    
    public Node getValueConstraint() {
        return this.constraintExpr;
    }
    
    public void setValueToEvaluateAgainst(OperonValue value) throws OperonGenericException {
        //:OFF:log.debug("OperonValueConstraint :: setValueToEvaluateAgainst :: " + value.getClass().getName());
        //this.valueToEvaluateAgainst = (OperonValue) value.evaluate(); // might not work if DeferredMultiNode
        this.valueToEvaluateAgainst = value;
    }

    public OperonValue getValueToEvaluateAgainst() {
        return this.valueToEvaluateAgainst;
    }

    public OperonValue getEvaluatedValue() {
        return this.evaluatedValue;
    }

    public void setConstraintAsString(String jvc) {
        this.constraintAsString = jvc;
    }
    
    public String getConstraintAsString() {
        if (this.constraintAsString == null) {
            return "";
        }
        else {
            return this.constraintAsString;
        }
    }
    
    public static void evaluateConstraintAgainstOperonValue(OperonValue value, OperonValueConstraint c) throws OperonGenericException{
        //:OFF:log.debug("Base-statement :: evaluateConstraint()");

        // Put the same constraint also into OperonValue:
        value.setOperonValueConstraint(c);
        //:OFF:log.debug("Base-statement :: evaluateConstraint() :: added constraint to evaluated value.");
        
        //System.out.println("Constraint value: " + value);
        c.setValueToEvaluateAgainst(value);
        OperonValue constraintResult = c.evaluate();
        if (constraintResult instanceof FalseType) {
            //System.out.println("Throwing exception, constraint evaluated into false");
            String source = "";
            if (c.getSourceCodeLineNumber() != -1) {
                source = ". Line #" + c.getSourceCodeLineNumber();
            }
            ErrorUtil.createErrorValueAndThrow(value.getStatement(), "CONSTRAINT", "VIOLATION", "Value " + value + " violates constraint " +  c.getConstraintAsString() + source);
        }
        else {
            //System.out.println("Constraint evaluated into true");
        }
    }
    
    @Override
    public String toString() {
        return this.getConstraintAsString();
    }

    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        return this.getConstraintAsString();
    }

    @Override
    public String toYamlString(YamlFormatter yf) {
        return this.getConstraintAsString();
    }

    @Override
    public String toTomlString(OutputFormatter ofmt) {
        return this.getConstraintAsString();
    }

}