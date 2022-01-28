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

package io.operon.runner.node;

import java.util.Map;
import java.util.HashMap;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

import io.operon.runner.BaseContext;
import io.operon.runner.Context;
import io.operon.runner.OperonContext;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.statement.Statement;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.node.Operator;
import io.operon.runner.OperonRunner;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public abstract class AbstractNode implements Node, java.io.Serializable {
    private static Logger log = LogManager.getLogger(AbstractNode.class);
    
    private Statement statement;
    
    private boolean isEmpty = false; // compiler sets this if value is EmptyType
    
    private OperonValue evaluatedValue;
    private boolean doBindings = true;
    private String expr; // this is the expr in plain String, set by the compiler.
    
    // This means that the value has been calculated to atomic -json-value.
    protected boolean unboxed = false;
    
    //
    // This means that if evaluate is called, then the current-value (this) is returned instantly.
    // This is required when accessing value through LetStatement, where the value has already been calculated,
    // e.g. ObjectType, which contains expressions would otherwise re-evaluate the expressions.
    //
    protected boolean preventReEvaluation = false;
    
    // String: the operator-name, e.g. "="
    private Map<String, Operator> bindings;
    
    public AbstractNode(Statement statement) {
        this.statement = statement;
        this.bindings = new HashMap<String, Operator>();
    }
    
    public OperonValue evaluate() throws OperonGenericException {
        return null;
    }

    public Statement getStatement() {
        return this.statement;
    }
    
    //
    // used for prototype-functions:
    //
    public void setStatement(Statement s) {
        this.statement = s;
    }

    public void setEvaluatedValue(OperonValue value) {
        this.evaluatedValue = value;
    }
    
    public OperonValue getEvaluatedValue() {
        return this.evaluatedValue;
    }
    
    public boolean isEmptyValue() {
        return this.isEmpty;
    }

    public void setIsEmptyValue(boolean ie) {
        this.isEmpty = ie;
    }
    
    public void setUnboxed(boolean ub) {
        this.unboxed = ub;
    }

    public boolean getUnboxed() {
        return this.unboxed;
    }

    public Node lock() {
        this.setPreventReEvaluation(true);
        this.setUnboxed(true);
        return this;
    }

    public void setPreventReEvaluation(boolean pe) {
        this.preventReEvaluation = pe;
    }

    public boolean getPreventReEvaluation() {
        return this.preventReEvaluation;
    }
    
    public Map<String, Operator> getBindings() {
        if (this.getDoBindings() == true) {
            return this.bindings;
        }
        
        else {
            return new HashMap<String, Operator>();
        }
    }
    
    public void setDoBindings(boolean doBind) {
        this.doBindings = doBind;
    }
    
    public boolean getDoBindings() {
        return this.doBindings;
    }
    
    public void setExpr(String expr) {
        this.expr = expr;
    }
    
    public String getExpr() {
        return this.expr;
    }
    
    //
    // AFTER this the result node will NOT allow to traverse the previous-statements (scopes)
    // - The currentValue MUST be a JSON-value (not e.g. a FunctionRef or LambdaFunctionRef)
    //
    // Param linkScope : tells to link the previousStatement to the new statement
    // 
    public static Node deepCopyNode(Statement currentStatement, Node node, boolean linkScope) throws OperonGenericException {
        //System.out.println("DeepCopy called. stmt=" + currentStatement);
        String expr = node.getExpr();
        if (expr == null) {
            // If not an expr, then OperonValue, which can be serialized and then parsed:
            //System.out.println("  >> expr null");
            String valStr = node.toString();
            return JsonUtil.lwOperonValueFromString(valStr);
            //throw new RuntimeException("AbstractNode.deepCopyNode :: FATAL :: expr was null for Node: " + node.getClass().getName());
        }
        //System.out.println("  >> DeepCopy expr :: " + expr);
        //System.out.println("  >> LinkScope :: " + linkScope);
        
        try {
            Context ctx = new OperonContext();
            DefaultStatement newStatement = new DefaultStatement(ctx);
            //System.out.println("  >> newStatement 1 :: " + newStatement);
            OperonValue currentValueCopy = null;
            if (currentStatement.getCurrentValue() != null) {
                String cvStr = currentStatement.getCurrentValue().toString();
                currentValueCopy = JsonUtil.lwOperonValueFromString(cvStr);
            }
            else {
                currentValueCopy = new EmptyType(newStatement);
            }
            Node newNode = OperonRunner.compileExpr(newStatement, expr);
            newNode.getStatement().setCurrentValue(currentValueCopy);
            if (linkScope) {
                //System.out.println("  >> Linking scope " + newNode.getStatement() + " to: " + currentStatement);
                newNode.getStatement().setPreviousStatement(currentStatement);
            }
            return newNode;
        } catch (Exception e) {
            System.err.println("deepCopyNode: ERROR: " + e.getMessage());
            OperonGenericException oge = new OperonGenericException(e.getMessage());
            throw oge;
        }
    }

    @Override
    public String toFormattedString(OutputFormatter pp) {
        return this.toString();
    }
    
    @Override
    public String toYamlString(YamlFormatter yf) {
        return this.toString();
    }
}
