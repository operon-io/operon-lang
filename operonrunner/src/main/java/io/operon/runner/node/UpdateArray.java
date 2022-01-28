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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.operon.runner.node.Node;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.core.GenericUpdate;
import io.operon.runner.node.type.*;
import io.operon.runner.model.UpdatePair;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

//
// Used in Update_array -expr
//
public class UpdateArray extends AbstractNode implements Node, java.io.Serializable {
    private static Logger log = LogManager.getLogger(UpdateArray.class);

    private Node configs;
    private Node updatePathsExpr;
    private Node updateValuesExpr;
    
    private boolean configSilentErrors = true;
    private boolean isUpdateExprSingleValue = true;
    
    public UpdateArray(Statement stmnt) {
        super(stmnt);
    }

    public void setIsUpdateExprSingleValue(boolean b) {
        this.isUpdateExprSingleValue = b;
    }
    
    public boolean getIsUpdateExprSingleValue() {
        return this.isUpdateExprSingleValue;
    }

    public OperonValue evaluate() throws OperonGenericException {
        log.debug("ENTER UpdateArray.evaluate()");
        //System.out.println("ENTER UpdateArray.evaluate()");
        OperonValue currentValue = this.getStatement().getCurrentValue();
        Node updatePathsExpr = this.getUpdatePathsExpr();

        ArrayType updatePathsArray = (ArrayType) updatePathsExpr.evaluate();
        if (updatePathsArray.getValues().isEmpty()) {
            return currentValue;
        }
        OperonValue result = currentValue;

        // if [[Path]] then .get(0) returns an Array
        OperonValue p1Val = updatePathsArray.getValues().get(0).evaluate();
        
        if (p1Val instanceof Path) {
            ((Path) p1Val).setObjLink(currentValue);
        }

        //this.getUpdateValuesExpr().getStatement().setCurrentValue(p1Val);
        //OperonValue updateValueEvaluated = this.getUpdateValuesExpr().evaluate();
        OperonValue updateValueEvaluated = null;
        //
        // current-value Update [expr]: expr End
        // (i.e. update multiple Paths with single value): current-value Update [Path, Path, Path]: update-expr;
        // @ = currently processed path, which has linked obj.
        //   [...]
        //    \|/
        //     A
        //System.out.println("Is single :: " + this.getIsUpdateExprSingleValue());
        if (this.getIsUpdateExprSingleValue() == true) {
            //System.out.println("UpdateArray :: Array -- single");
            for (int i = 0; i < updatePathsArray.getValues().size(); i ++) {
                Path p = (Path) updatePathsArray.getValues().get(i).evaluate();
                p.setObjLink(result); // link the result as a new root-object for Path
                this.getUpdateValuesExpr().getStatement().setCurrentValue(p);
                try {
                    updateValueEvaluated = this.getUpdateValuesExpr().evaluate(); // in wrongly cascading updates this could throw
                    List<Node> params = new ArrayList<Node>();
                    params.add(p);
                    params.add(updateValueEvaluated);
                    //System.out.println("prepare GenericUpdate");
                    //System.out.println("$value=" + updateValueEvaluated);
                    //System.out.println("$target=" + p);
                    GenericUpdate genUp = new GenericUpdate(this.getStatement(), params);
                    genUp.getStatement().setCurrentValue(result);
                    OperonValue updated = genUp.evaluate(); // in wrongly cascading updates this could also throw
                    result = updated;
                } catch (OperonGenericException oge) {
                    //
                    // !!! WARNING !!!
                    //
                    // It is possible that there are wrongly cascading updates,
                    // that is the first update makes the second update impossible.
                    // We do not want to raise an exception by default in these cases.
                    //
                    if (this.configSilentErrors) {
                        // Do nothing
                        //System.out.println("UpdateArray --> error --> prevent re-throw");
                    }
                    else {
                        throw oge;
                    }
                }
            }
        }
        // 
        // Update [expr]: [expr]
        //  (i.e. update multiple Paths with corresponding multiple values -> one-to-one mapping.)
        //   [. . .]
        //    | | |
        //   [A B C]
        //
        else {
            //System.out.println("UpdateArray :: Array -- array");
            this.getUpdateValuesExpr().getStatement().setCurrentValue(p1Val);
            updateValueEvaluated = this.getUpdateValuesExpr().evaluate();
            List<Node> updateValues = ((ArrayType) updateValueEvaluated).getValues();
            
            //System.out.println("UpdateArray :: updateValues=" + updateValues);
            
            // This is used to check if the first value from LHS is a sub-array (case: [[Paths], [Paths]])
            OperonValue firstPathValue = updatePathsArray.getValues().get(0).evaluate();
            
            //
            // Update [Paths]: [expr]
            //
            if (firstPathValue instanceof ArrayType == false) {
                for (int i = 0; i < updatePathsArray.getValues().size(); i ++) {
                    //System.out.println("for i=" + i);
                    Path p = (Path) updatePathsArray.getValues().get(i).evaluate();
                    p.setObjLink(result);
                    
                    // NOTE: evaluate for expr which is [expr] also evaluates
                    //       the array items, therefore we cannot get a singular
                    //       unevaluated item, unless it is a Lambda.
                    //       As an example: [Path]: [@ + 10, @ + 20] --> will set the @ same for each,
                    //       which will yield the wrong desired result.
                    // Therefore we use Lambdas to encapsulate the @.
                    
                    OperonValue uv = (OperonValue) updateValues.get(i);
                    try {
                        if (uv instanceof LambdaFunctionRef) {
                            //System.out.println("uv LFR");
                            LambdaFunctionRef updateFnRef = (LambdaFunctionRef) uv;
                            updateFnRef.getParams().clear();
                            // NOTE: we cannot guarantee the order of keys that Map.keySet() returns,
                            //       therefore we must assume that the keys are named in certain manner.
                            //updateFnRef.getParams().put("$a", valueToTest);
                            updateFnRef.setCurrentValueForFunction(p);
                            //System.out.println("uv LFR invoking");
                            uv = updateFnRef.invoke();
                            //System.out.println("uv LFR invoked");
                        }
                        
                        else if (uv instanceof FunctionRef) {
                            //System.out.println("uv FR");
                            FunctionRef updateFnRef = (FunctionRef) uv;
                            
                            updateFnRef.getParams().clear();
                            //updateFnRef.getParams().add(valueToTest);
                            updateFnRef.setCurrentValueForFunction(p);
                            uv = updateFnRef.invoke();
                        }
                        
                        List<Node> params = new ArrayList<Node>();
                        params.add(p);
                        params.add(uv);
                        GenericUpdate genUp = new GenericUpdate(this.getStatement(), params);
                        genUp.getStatement().setCurrentValue(result);
                        
                        //System.out.println("call generic-update");
                        //System.out.println("   - p:" + p);
                        //System.out.println("   - uv: " + uv);
                        OperonValue updated = genUp.evaluate();
                        //System.out.println("generic-update call done");
                        result = updated;
                    } catch (OperonGenericException oge) {
                        //
                        // !!! WARNING !!!
                        //
                        // It is possible that there are wrongly cascading updates,
                        // that is the first update makes the second update impossible.
                        // We do not want to raise an exception by default in these cases.
                        if (this.configSilentErrors) {
                            // Do nothing
                            //System.out.println("UpdateArray --> error --> prevent re-throw");
                        }
                        else {
                            throw oge;
                        }
                    }
                }
            }
            else {
                //
                // [[Paths]]: [expr]
                //
                // Update [[expr]]: [expr]
                //  (i.e. update multiple Paths defined in multiple subarrays, with multiple corresponding values.)
                //   [ [...], [...], [...]] (Paths)
                //      \|/    \|/    \|/
                //   [   A   ,  B   ,  C  ] (Update-values)
                //
                //System.out.println("UpdateArray :: Array[Array] -- array");
                for (int i = 0; i < updatePathsArray.getValues().size(); i ++) {
                    ArrayType subarrayPaths = (ArrayType) updatePathsArray.getValues().get(i).evaluate();
                    List<Node> subarrayPathsList = subarrayPaths.getValues();

                    for (int j = 0; j < subarrayPathsList.size(); j ++) {
                        //System.out.println("subarrayPaths :: " + j);
                        OperonValue uv = (OperonValue) updateValues.get(i);
                        try {
                            Path p = (Path) subarrayPathsList.get(j).evaluate();
                            //System.out.println("  p[j]=" + p);
                            p.setObjLink(result);
    
                            if (uv instanceof LambdaFunctionRef) {
                                //System.out.println("uv LFR");
                                LambdaFunctionRef updateFnRef = (LambdaFunctionRef) uv;
                                updateFnRef.getParams().clear();
                                //updateFnRef.getParams().put("$path", p);
                                //System.out.println("put cv=" + p);
                                updateFnRef.setCurrentValueForFunction(p);
                                uv = updateFnRef.invoke();
                                //System.out.println("uv=" + uv);
                            }
    
                            else if (uv instanceof FunctionRef) {
                                FunctionRef updateFnRef = (FunctionRef) uv;
                                
                                updateFnRef.getParams().clear();
                                //updateFnRef.getParams().add(valueToTest);
                                updateFnRef.setCurrentValueForFunction(p);
                                uv = updateFnRef.invoke();
                            }
    
                            List<Node> params = new ArrayList<Node>();
                            params.add(p);
                            params.add(uv);
                            GenericUpdate genUp = new GenericUpdate(this.getStatement(), params);
                            genUp.getStatement().setCurrentValue(result);

                            OperonValue updated = genUp.evaluate();
                            result = updated;
                        } catch (OperonGenericException oge) {
                            //
                            // !!! WARNING !!!
                            //
                            // It is possible that there are wrongly cascading updates,
                            // that is the first update makes the second update impossible.
                            // We do not want to raise an exception by default in these cases.
                            if (this.configSilentErrors) {
                                // Do nothing
                                //System.out.println("UpdateArray --> error --> prevent re-throw");
                            }
                            else {
                                throw oge;
                            }
                        }
                    }
                }
            }
        }

        if (result == null) {
            return currentValue;
        }
        else {
            return result;
        }
    }

    public void setUpdatePathsExpr(Node up) {
        this.updatePathsExpr = up;
    }
    
    public Node getUpdatePathsExpr() {
        return this.updatePathsExpr;
    }

    public void setUpdateValuesExpr(Node uv) {
        this.updateValuesExpr = uv;
    }
    
    public Node getUpdateValuesExpr() {
        return this.updateValuesExpr;
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

}