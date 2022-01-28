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

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import io.operon.runner.OperonContext;
import io.operon.runner.Context;
import io.operon.runner.node.Node;
import io.operon.runner.node.Operator;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.type.*;
import io.operon.runner.ExceptionHandler;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class LetStatement extends BaseStatement implements Statement {
    private static Logger log = LogManager.getLogger(LetStatement.class);
    
    private Node configs;
    
    public static enum ResetType {
        NEVER, AFTER_QUERY, ALWAYS, AFTER_SCOPE;
    }
    
    public static enum EvaluateType {
        LAZY, EAGER;
    }

    private ResetType resetType = ResetType.AFTER_SCOPE;
    private EvaluateType evaluateType = EvaluateType.LAZY;
    private OperonValue evaluatedValue;
    private OperonValueConstraint constraint;
    private boolean configsResolved = false;
    
    // NOTE: this is used only to set the bind-value (operator-overloading),
    // the actual valueRefStr is in the Map-structures of other statements,
    // which is then used to refer into this Let-stmt.
    private String valueRefStr;
    
    // This controls the individual valueRef.
    private List<Operator> bindValuesList;
    
    private Map<String, List<Operator>> bindValues;
    
    public LetStatement(Context ctx) {
        super(ctx);
        this.bindValuesList = new ArrayList<Operator>();
        this.bindValues = new HashMap<String, List<Operator>>();
    }
    
    public OperonValue evaluate() throws OperonGenericException {
        log.debug("Let-statement :: evaluate()");
        //System.out.println("Let-statement :: evaluate()");
        
        //
        // TODO: check if this applies when ResetType="ALWAYS"!
        //
        if (this.getResetType() == ResetType.ALWAYS) {
            this.reset();
        }
        if (this.getEvaluatedValue() != null) {
            //System.out.println("Let-statement :: evaluate() :: return evaluated value");
            return this.getEvaluatedValue();
        }

        //System.out.println("In Let-evaluate(), CV still null");
        if (this.getCurrentValue() == null) {
            //System.out.println("Let-statement :: evaluate() :: resolve new currentValue");
            
            //
            // Will have to resolve from parents, or finally set as empty.
            //
            Statement parent = this.getPreviousStatement();
            OperonValue parentCurrentValue = null;
            if (parent != null) {
                parentCurrentValue = parent.getCurrentValue();
            }
            while (parent != null && parentCurrentValue == null) {
                parent = parent.getPreviousStatement();
                if (parent != null) {
                    parentCurrentValue = parent.getCurrentValue();
                }
            }
            if (parentCurrentValue == null) {
                //System.out.println("Let-stmt: set current-value as empty, because there was no parent-stmt.");
                this.setCurrentValue(new EmptyType(this));
            }
            else {
                this.setCurrentValue(parentCurrentValue);
            }
        }
        //System.out.println("In Let-evaluate(), CV: " + this.getCurrentValue());
        if (this.configsResolved == false) {
            OperonValue currentValueCopy = this.getCurrentValue();
            this.resolveConfigs();
            this.setCurrentValue(currentValueCopy);
        }

        this.getNode().getStatement().setCurrentValue(this.getCurrentValue());
        OperonValue result = null;
        try {
            result = this.getNode().evaluate();
        } catch (OperonGenericException e) {
            if (this.getExceptionHandler() != null) {
                //
                // Apply ExceptionHandler:
                //
                
                //System.out.println("LetStatement :: apply ExceptionHandler: " + e.getMessage());
                ExceptionHandler eh = this.getExceptionHandler();
                ErrorValue errorValue = ExceptionHandler.createErrorValue(eh.getExceptionHandlerExpr().getStatement(), e);
                eh.getExceptionHandlerExpr().getStatement().getRuntimeValues().put("$error", errorValue);
                result = eh.evaluate(e);
            }
            else {
                log.debug("LetStatement :: exceptionHandler missing, throw to upper-level");
                //
                // Throw the exception to upper-level, since no ExceptionHandler was found:
                //
                throw e;
            }
        }

        this.setEvaluatedValue(result);
        
        if (this.getOperonValueConstraint() != null) {
            //
            // Apply constraint-check (for the expr-output):
            //
            OperonValueConstraint jvc = this.getOperonValueConstraint();
            OperonValueConstraint.evaluateConstraintAgainstOperonValue(this.getEvaluatedValue(), jvc);
        }
        
        log.debug("LET STATEMENT RETURN :: " + this.getEvaluatedValue());
        return this.getEvaluatedValue();
    }
    
    public void resolveConfigs() throws OperonGenericException {
        log.debug("Let, resolveConfigs");
        ObjectType conf = this.getConfigs();
        if (conf == null) {
            return;
        }
        for (PairType pair : conf.getPairs()) {
            String key = pair.getKey();
            switch (key.toLowerCase()) {
                case "\"update\"":
                    String resetTypeStr = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    resetTypeStr.substring(1, resetTypeStr.length() - 1);
                    this.setResetType(LetStatement.ResetType.valueOf(resetTypeStr.toUpperCase()));
                    break;
                // 
                // Lazy | Eager
                // Enumerate these when checking the statement's Let -statetement (i.e. resolveConfigs eagerly)
                // 
                case "\"evaluate\"":
                    String evaluateTypeStr = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    evaluateTypeStr.substring(1, evaluateTypeStr.length() - 1);
                    try {
                        this.setEvaluateType(LetStatement.EvaluateType.valueOf(evaluateTypeStr.toUpperCase()));
                    } catch (Exception e) {
                        ErrorUtil.createErrorValueAndThrow(this, "STATEMENT", "VALUE", "Cannot resolve evaluate-option type: " + evaluateTypeStr);
                    }
                    break;
                case "\"bind\"":
                    ArrayType bindArray = (ArrayType) pair.getEvaluatedValue();
                    List<Node> bindArrayNodes = bindArray.getValues();
                    List<Operator> operators = new ArrayList<Operator>();
                    
                    String operatorAsString = null;
                    FunctionRef functionRef = null;
                    boolean isCascade = false;
                    
                    for (Node node : bindArrayNodes) {
                        ObjectType bindObj = ((ObjectType) (OperonValue) node.evaluate());
                        List<PairType> bindObjPairs = bindObj.getPairs();
                        for (PairType bindObjPair : bindObjPairs) {
                            String bindObjPairKey = bindObjPair.getKey();
                            switch (bindObjPairKey) {
                                case "\"operator\"":
                                    operatorAsString = ((StringType) bindObjPair.getEvaluatedValue()).getJavaStringValue();
                                    break;
                                case "\"functionRef\"":
                                    functionRef = (FunctionRef) bindObjPair.getEvaluatedValue();
                                    break;
                                case "\"cascade\"":
                                    OperonValue cascadeNode = bindObjPair.getEvaluatedValue();
                                    if (cascadeNode.evaluate() instanceof TrueType) {
                                        isCascade = true;
                                    }
                                    break;
                            }
                        }
                        // TODO: throw: error if operatorAsString or functionRef missing (null)
                        Operator op = new Operator(this); // TODO: might not to inherit Node, therefore giving statement not required.
                        op.setOperator(operatorAsString);
                        op.setFunctionRef(functionRef);
                        op.setCascade(isCascade);
                        operators.add(op);
                    }

                    bindValuesList.addAll(operators);
                    //System.out.println("PUT from LetStatement: " + this.getValueRefStr());

                    this.getBindValues().put(this.getValueRefStr(), bindValuesList);
                    break;
                default:
                    break;
            }
        }
        this.configsResolved = true;
        // resolving configs changes the currentValue, therefore we set it back to null,
        // assuming that resolveConfigs is done prior to evalute() -call.
        this.setCurrentValue(null);
    }
    
    public ResetType getResetType() {
        return this.resetType;
    }
    
    public void setResetType(ResetType rt) {
        this.resetType = rt;
    }
    
    public EvaluateType getEvaluateType() {
        return this.evaluateType;
    }
    
    public void setEvaluateType(EvaluateType et) {
        this.evaluateType = et;
    }
    
    public void reset() {
        log.debug("LetStatement :: reset() called");
        //System.out.println("LetStatement :: reset() called");
        if (this.getResetType() != ResetType.NEVER) {
            log.debug("LetStatement :: resetting");
            log.debug("LetStatement :: rt-values :: " + this.getRuntimeValues());
            this.getRuntimeValues().clear();
            this.setEvaluatedValue(null);
            //System.out.println("LetStatement :: reset done...");
        }
    }
    
    public void setEvaluatedValue(OperonValue ev) {
        //System.out.println("LetStatement :: setEvaluatedValue");
        if (ev != null) {
            ev.setPreventReEvaluation(true);
        }
        this.evaluatedValue = ev;
    }
    
    public OperonValue getEvaluatedValue() {
        return this.evaluatedValue;
    }
    
    public void setOperonValueConstraint(OperonValueConstraint c) {
        this.constraint = c;
    }

    public OperonValueConstraint getOperonValueConstraint() {
        return this.constraint;
    }
    
    public void setConfigs(Node conf) {
        this.configs = conf;
    }
    
    public void setValueRefStr(String vrStr) {
        this.valueRefStr = vrStr;
    }
    
    public String getValueRefStr() {
        return this.valueRefStr;
    }
    
    public List<Operator> getBindValuesList() {
        return this.bindValuesList;
    }
    
    public Map<String, List<Operator>> getBindValues() {
        return this.bindValues;
    }
    
    public ObjectType getConfigs() throws OperonGenericException {
        if (this.configs == null) {
            return new ObjectType(this);
        }
        this.configs = (ObjectType) this.configs.evaluate();
        return (ObjectType) this.configs;
    }

}