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
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import io.operon.runner.statement.Statement;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.statement.FunctionStatement;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// Synopsis:
//     '=>' pattern_configs? expr function_arguments
// 
// Examples:
// => [foo(), Lambda(): "foo";]()
// => $b() # NOTE: Could later be also: "=> b()" (the standard function-call -syntax).
//
public class FunctionRefInvoke extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(FunctionRefInvoke.class);
    
    // Expression which refers to FunctionRef (FR) or LambdaFunctionRef (LFR).
    // May also be an Array, which contains FR or LFR, in which case
    // they are all invoked.
    private Node refExpr;
    private FunctionArguments functionArguments;
    
    // E.g. parallel -option
    private Node configs;
    
    public FunctionRefInvoke(Statement stmnt) {
        super(stmnt);
    }

    public OperonValue evaluate() throws OperonGenericException {
        log.debug("ENTER FunctionRefInvoke.evaluate()");
        OperonValue currentValue = this.getStatement().getCurrentValue();
        
        OperonValue ref = (OperonValue) this.getRefExpr().evaluate(); // evaluate is required for: LambdaFunctionRefTests.lambdaFunctionRef5Test:
        
        //
        // TODO: ref is not always LFR/FR, it could also be an Array of LFR/FR.
        //

        final OperonValue currentValueForInvoke = currentValue;
        
        //log.debug("!!! CURRENT VALUE COPY :: " + currentValueCopy);
        //System.out.println("FunctionRefInvoke :: CV Copy=" + currentValueCopy + /*", prototype=" + this.getStatement().isPrototype() +*/ ", stmt=" + this.getStatement().getClass().getName());
        //System.out.println("FunctionRefInvoke evaluate()");
        //System.out.println("  Configs: " + this.getConfigs());
        
        log.debug("  >> FunctionRefInvoke :: evaluate the valueRef (stmt=" + this.getStatement().getId() + ")");

        // DEBUG: test if ref is lfr before evaluating it.
        log.debug("  >> FunctionRefInvoke :: ref = " + this.getRefExpr().getClass().getName());

        Info info = this.resolveConfigs(this.getStatement());

        //System.out.println("FunctionRefInvoke evaluate() >> 3");

        if (info.parallel == false) {
            //System.out.println("evaluate() parallel false");
            return this.doInvoke(info, currentValueForInvoke, ref);
        }
        
        else {
            //System.out.println("evaluate() parallel true");
            ArrayType refs = null;
            
            if (ref instanceof ArrayType) {
                refs = (ArrayType) ref;
            }
            else {
                // Not a list, process single node (ref):
                return this.doInvoke(info, currentValue, ref);
            }
            
            List<Node> parallelResults = refs.getValues().parallelStream().map(
                node -> {
                    try {
                        // No need to deepcopy the node, because they are different list-items.
                        // Only the currentStatement could be messed?
                        //
                        //System.out.println("Parallel :: deepCopy node");
                        //System.out.println("  COPY node, expr=" + node.getExpr());
                        boolean linkScope = false;
                        Node nodeCopy = AbstractNode.deepCopyNode(this.getStatement(), node, linkScope);
                        return this.doInvoke(info, currentValueForInvoke, nodeCopy);
                    } catch (OperonGenericException e) {
                        //
                        // TODO: check stopOnException flag from info
                        //
                        System.err.println("FunctionRefInvoke: Error: " + e.getMessage());
                        EmptyType empty = new EmptyType(this.getStatement());
                        return empty;
                    } catch (Exception e) {
                        //
                        // TODO: check stopOnException flag from info
                        //
                        System.err.println("FunctionRefInvoke: Error: " + e.getMessage());
                        EmptyType empty = new EmptyType(this.getStatement());
                        return empty;
                    }
                }
            ).collect(Collectors.toList());
            
            ArrayType result = new ArrayType(this.getStatement());
            result.getValues().addAll(parallelResults);

            this.getStatement().setCurrentValue(result);
            return result;
        }

    }

    public OperonValue doInvoke(Info info, OperonValue currentValue, Node ref) throws OperonGenericException {
        // NOTE: this sets the currentValue as FunctionRef (or LambdaFunctionRef),
        //       so the actual currentValue must be set back in the next step.
        //       Tested by: functionRefInvoke8Test
        Statement stmt = currentValue.getStatement();
        
        stmt.setCurrentValue(currentValue);
        log.debug("  >> FunctionRefInvoke :: done evaluating the valueRef");
        //System.out.println("  invoke CV copy=" + currentValueCopy);

        //System.out.println("doInvoke 0. ref: " + ref.getClass().getName());
        
        ref.getStatement().setCurrentValue(currentValue);
        ref = ref.evaluate();
        
        //System.out.println("doInvoke 1");
        if (ref instanceof FunctionRef) {
            //System.out.println("doInvoke FunctionRef");
            OperonValue result = FunctionRef.setParamsAndInvokeFunctionRef(ref, currentValue, this.getFunctionArguments());
            this.setEvaluatedValue((OperonValue) result);
            return (OperonValue) result;
        }
        
        else if (ref instanceof LambdaFunctionRef) {
            //System.out.println("  LFR: invoke CV copy=" + currentValueCopy);
            OperonValue result = LambdaFunctionRef.setParamsAndInvokeLambdaFunctionRef((LambdaFunctionRef) ref, currentValue, this.getFunctionArguments());
            this.setEvaluatedValue((OperonValue) result);
            return result;
        }
        
        else if (ref instanceof ArrayType) {
            //System.out.println("doInvoke handleArray");
            //System.out.println("CV=" + this.getStatement().getCurrentValue());
            OperonValue resultArray = this.handleArray(ref, currentValue, info);
            //System.out.println(">> resultArray: " + resultArray);
            this.setEvaluatedValue(resultArray);
            return resultArray;
        }
        
        else {
            //System.out.println("doInvoke expr");
            // ref was already evaluated, it wasn't lfr or fr, so we decide it was expr, and
            // we'll assign the value for this node:
            this.setEvaluatedValue((OperonValue) ref);
            return (OperonValue) ref;
        }
    }

    public void setRefExpr(Node refExpr) {
        this.refExpr = refExpr;
    }

    public Node getRefExpr() {
        return this.refExpr;
    }

    public void setFunctionArguments(FunctionArguments fArgs) {
        this.functionArguments = fArgs;
    }
    
    public FunctionArguments getFunctionArguments() {
        return this.functionArguments;
    }

    public String toString() {
        return this.getEvaluatedValue().toString();
    }
    
    private OperonValue handleArray(Node ref, OperonValue currentValue, Info info) throws OperonGenericException {
        log.debug("FunctionRefInvoke :: handleArray");
        ArrayType arr = (ArrayType) ref;
        // TODO: the result might depend on invoke-configuration, i.e.
        //  => [f(), g()] () {invoke configs here, e.g. "passResultValueForNextFunctionAsArgument": true}
        //  which would reduce the result into single value.
        // For now, the default behavior is to evaluate the functionRef in-place, and put the result-value
        // into resultArray's corresponding position...
        ArrayType resultArray = new ArrayType(this.getStatement());
        //System.out.println("Start loop, size: " + arr.getValues().size());
        for (int i = 0; i < arr.getValues().size(); i ++) {
            Node value = arr.getValues().get(i);
            this.getStatement().setCurrentValue(currentValue);
            
            // TODO: should this be... recursive?
            //  Should we refactor the functionRef and lambdaFunctionRef -handling into own methods?
            //  What about: => [ f(), [f(), g()] ] () ?
            // How steep the recursion should go? What about objects that are inside array, but have arrays with funcrefs?
            // Simples possible (default) behavior would be to just check for functionRefs and lambdaFunctionRefs, and
            // only invoke them, and ignore others.
            // I might want to change this, after thinking this through. It certainly is trivial to add this kind of support,
            // if wanted... NOTE: this could be configurable property of invocation as well! {"recursive": true}
            //log.debug(">>>>>>>>>>>>>>> VALUE ::: " + value.getClass().getName());
            
            value = value.evaluate();
            //System.out.println("evaluated value :: " + value);
            if (value instanceof FunctionRef) {
                //System.out.println("value was FunctionRef. CV Copy=" + currentValueCopy);
                OperonValue valueResult = FunctionRef.setParamsAndInvokeFunctionRef(value, currentValue, this.getFunctionArguments());
                //System.out.println("RESULT=" + valueResult);
                resultArray.addValue((OperonValue) valueResult);
            }
            else if (value instanceof LambdaFunctionRef) {
                OperonValue valueResult = LambdaFunctionRef.setParamsAndInvokeLambdaFunctionRef((LambdaFunctionRef) value, currentValue, this.getFunctionArguments());
                resultArray.addValue((OperonValue) valueResult);
            }
            else if (value instanceof ArrayType) {
                log.debug("ArrayType found, recursive: " + info.recursive);
                //System.out.println("ArrayType found, recursive: " + info.recursive);
                
                // Recursive-option is problematic when done like this: "=> arguments json_obj?",
                // because normally the json_obj would become the new currentValue,
                // so, the better way would be to create pattern:
                // Invoke ({"recursive": true}) [a(), b(), [c(), d()]] End
                
                if (info.recursive) {
                    log.debug("Recursive array handling");
                    OperonValue resultSubArray = this.handleArray(value, currentValue, info);
                    resultArray.addValue(resultSubArray);
                }
                else {
                    resultArray.addValue((OperonValue) value);
                }
            }
            else {
                //System.out.println(">> else, add value: " + value);
                resultArray.addValue((OperonValue) value);
            }
        }
        return resultArray;
    }
    
    public Info resolveConfigs(Statement stmt) throws OperonGenericException {
        Info info = new Info();

        if (this.configs == null) {
            return info;
        }

        OperonValue currentValue = stmt.getCurrentValue();

        for (PairType pair : this.getConfigs().getPairs()) {
            String key = pair.getKey();
            switch (key.toLowerCase()) {
                case "\"parallel\"":
                    OperonValue parallelValue = pair.getEvaluatedValue();
                    if (parallelValue instanceof FalseType) {
                        info.parallel = false;
                    }
                    else {
                        info.parallel = true;
                    }
                    break;
                case "\"recursive\"":
                    OperonValue recursiveValue = pair.getEvaluatedValue();
                    if (recursiveValue instanceof FalseType) {
                        info.recursive = false;
                    }
                    else {
                        info.recursive = true;
                    }
                    break;
                default:
                    break;
            }
        }
        
        stmt.setCurrentValue(currentValue);
        return info;
    }
    
    private class Info {
        public boolean parallel = false;
        public boolean recursive = false;
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