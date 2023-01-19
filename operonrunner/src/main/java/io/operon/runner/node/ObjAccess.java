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
 
import io.operon.runner.statement.Statement; 
import io.operon.runner.Context;
import io.operon.runner.BaseContext;
import io.operon.runner.model.OperonConfigs;
import io.operon.runner.model.path.*;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil; 
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.processor.function.SupportsAttributes;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 
 
public class ObjAccess extends AbstractNode implements Node, SupportsAttributes { 
     // no logger  
    private String objAccessKey;

    public ObjAccess(Statement stmnt) { 
        super(stmnt); 
    }
 
    public void setObjAccessKey(String key) { 
        this.objAccessKey = key; 
    } 
 
    public OperonValue evaluate() throws OperonGenericException { 
        //:OFF:log.debug("ENTER ObjAccess.evaluate(). Stmt: " + this.getStatement().getId());
        //System.out.println("ENTER ObjAccess.evaluate(). Stmt: " + this.getStatement().getId());
        
        // get currentValue from the statement 
        OperonValue currentValue = this.getStatement().getCurrentValue();
        //OperonValue evaluatedValue = currentValue; 
        if (currentValue instanceof ObjectType == false && currentValue instanceof ArrayType == false) { 
            currentValue = currentValue.evaluate();
        }
 
        if (currentValue instanceof ObjectType) { 
            //:OFF:log.debug("EXIT ObjAccess.evaluate() obj");
            OperonValue res = evaluateObj((ObjectType) currentValue);
            return res; 
        } 
         
        else if (currentValue instanceof ArrayType) { 
            //:OFF:log.debug("EXIT ObjAccess.evaluate() array");
            return evaluateArray( (ArrayType) currentValue ); 
        } 
        //:OFF:log.debug("ObjAccess: cannot access object. Wrong type: " + currentValue); 
        return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "OBJACCESS", "TYPE", "Cannot access object. Wrong type. Key: " + this.getObjAccessKey() + ", line #" + this.getSourceCodeLineNumber());
    } 
     
    private OperonValue evaluateObj(ObjectType obj) throws OperonGenericException { 
        OperonValue result = null;
        
        //:OFF:log.debug("Accessing: " + this.getObjAccessKey());
        //System.out.println("Accessing: " + this.getObjAccessKey());
        
        
        if (obj.getIndexedPairs() != null) {
            PairType p = obj.getIndexedPairs().get("\"" + this.getObjAccessKey() + "\"");
            if (p != null) {
                result = p.getEvaluatedValue();
                // No position available
                //parentKey = p.getKey();
                // TODO: check if parentKey assign can be removed!
            }
        }
        
        
        if (result == null) {
            // Access by object's key. Loop through the pairs and check which pair matches.
            String parentKey = null;
            int resultPairPosition = 0;
            int pairPosition = 1;
            for (PairType pair : obj.getPairs()) { 
                //:OFF:log.debug("KEY :: " + pair.getKey()); 
                if (pair.getKey().equals("\"" + this.getObjAccessKey() + "\"")) { 
                    result = pair.getEvaluatedValue(); 
                    resultPairPosition = pairPosition;
                    parentKey = pair.getKey();
                    break; 
                }
                pairPosition += 1;
            } 
        }
        else {
            //System.out.println("Cache hit");
        }
        
        // If not found, return Empty 
        if (result == null) { 
            result = new EmptyType(this.getStatement()); 
        }
        else {

        }

        // Inject the object itself as selfreference and find root for rootreference
        if (result instanceof LambdaFunctionRef) { 
            LambdaFunctionRef lfr = (LambdaFunctionRef) result; 
            //System.out.println("ObjAccess :: lfr :: put _");
            lfr.getStatement().getRuntimeValues().put("_", obj); 

            if (lfr.isInvokeOnAccess()) {
                result = lfr.invoke();
            }
        } 

        else if (result instanceof FunctionRef) {
            FunctionRef fr = (FunctionRef) result; 

            //System.out.println("Put _ :: " + obj + ", STMT="+fr.getStatement().getId());
            fr.getStatement().getRuntimeValues().put("_", obj); 
        }
        
        // update the currentValue from the statement 
        this.getStatement().setCurrentValue(result); 
        this.setEvaluatedValue(result); 
        return result;
    }
     
    private ArrayType evaluateArray(ArrayType array) throws OperonGenericException {         
        //:OFF:log.debug("Accessing array of objects: " + this.getObjAccessKey()); 
         
        ArrayType resultArray = new ArrayType(this.getStatement()); 
         
        List<Node> arrayValues = array.getValues(); 
        Path currentPath = this.getStatement().getCurrentPath();
        
        for (int i = 0; i < arrayValues.size(); i ++) { 
            Node arrayNode = arrayValues.get(i); 
            ////:OFF:log.debug("    >> Looping: " + i); 
            if (arrayNode.evaluate() instanceof ObjectType) {
                Node obj = evaluateObj((ObjectType) arrayNode.evaluate());
                if (obj instanceof ObjectType && ((ObjectType) obj).getPairs().size() == 0) {
                    // Do not add empty object
                    continue;
                }
                else {
                    resultArray.addValue(obj);
                }
            } 
        } 
        
        // update the currentValue from the statement 
        this.getStatement().setCurrentValue(resultArray); 
         
        this.setEvaluatedValue(resultArray); 
        return resultArray; 
    }
     
    public String getObjAccessKey() { 
        return this.objAccessKey; 
    } 
     
    public String toString() { 
        return this.getEvaluatedValue().toString(); 
    } 
}