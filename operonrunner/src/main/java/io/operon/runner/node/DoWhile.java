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
import java.util.stream.Collectors;
import java.io.IOException;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.model.exception.BreakLoopException;
import io.operon.runner.model.exception.ContinueLoopException;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.ModuleContext;
import io.operon.runner.processor.BinaryNodeProcessor; 
import io.operon.runner.statement.Statement; 
import io.operon.runner.statement.LetStatement; 
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 
 
/** 
 *  'Do' (':')? Let* expr While (expr) END
 *  Example: 0 Do @ + 1 While (@ &lt; 10); #&gt; 9
 *  
 */ 
public class DoWhile extends AbstractNode implements Node {
     // no logger  

    private Node doExpr; // Do expr
    private Node predicateExpr; // While (predicateExpr);
    private Node configs;

    public DoWhile(Statement stmnt) {
        super(stmnt);
    }

    public OperonValue evaluate() throws OperonGenericException {
        assert (this.getDoExpr() != null) : "DoWhile.evaluate() : doExpr was null";
        OperonValue currentValue = null;
        currentValue = this.getStatement().getPreviousStatement().getCurrentValue();
        
        Info info = this.resolveConfigs();

        OperonValue result = currentValue;
        Node dExpr = this.getDoExpr();
        
        do {
            dExpr.getStatement().setCurrentValue(result);
            this.eagerEvaluateLetStatements();
            try {
                result = dExpr.evaluate();
            } catch (BreakLoopException ble) {
                break;
            } catch (ContinueLoopException cle) {
                this.synchronizeState();
                continue;
            }
            this.synchronizeState();
        } while (this.evaluatePredicate(result));
        
        this.getStatement().getPreviousStatement().setCurrentValue(result);
        //System.out.println(">> return result");
        
        return result;
    }

    private boolean evaluatePredicate(OperonValue result) throws OperonGenericException {
        this.getPredicateExpr().getStatement().setCurrentValue(result);
        OperonValue predicateResult = this.getPredicateExpr().evaluate();
        if (predicateResult instanceof TrueType) {
            return true;
        }
        else {
            return false;
        }
    }

    private void eagerEvaluateLetStatements() throws OperonGenericException {
        //
        // Eager-evaluate Let-statements:
        //
        for (java.util.Map.Entry<String, LetStatement> entry : this.getStatement().getLetStatements().entrySet()) {
		    LetStatement letStatement = (LetStatement) entry.getValue();
		    letStatement.resolveConfigs();
		    if (letStatement.getEvaluateType() == LetStatement.EvaluateType.EAGER) {
		        letStatement.evaluate();
		    }
	    }
    }

    private void synchronizeState() {
        for (LetStatement lstmnt : this.getStatement().getLetStatements().values()) {
            if (lstmnt.getResetType() == LetStatement.ResetType.AFTER_SCOPE) {
                lstmnt.reset();
            }
        }
    }

    public void setPredicateExpr(Node pExpr) {
        this.predicateExpr = pExpr;
    }
    
    public Node getPredicateExpr() {
        return this.predicateExpr;
    }

    public void setDoExpr(Node dExpr) {
        this.doExpr = dExpr;
    }
    
    public Node getDoExpr() {
        return this.doExpr;
    }

    public void setConfigs(Node conf) {
        this.configs = conf;
    }
    
    public ObjectType getConfigs() throws OperonGenericException {
        if (this.configs == null) {
            return new ObjectType(this.getStatement());
        }
        this.configs = (ObjectType) this.configs.evaluate();
        return (ObjectType) this.configs;
    }

    public Info resolveConfigs() throws OperonGenericException {
        Info info = new Info();
        
        if (this.configs == null) {
            return info;
        }
        
        for (PairType pair : this.getConfigs().getPairs()) {
            String key = pair.getKey();
            switch (key.toLowerCase()) {
                // Check the evaluatedValue for pairs
                default:
                    break;
            }
        }
        return info;
    }

    private class Info {

    }

}