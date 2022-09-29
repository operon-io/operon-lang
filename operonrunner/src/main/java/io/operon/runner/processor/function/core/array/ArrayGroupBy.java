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

package io.operon.runner.processor.function.core.array;

import io.operon.runner.OperonContext;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.LambdaFunctionRef;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

// 
// [...] -> {[]}
// TODO: pos() is not supported?
//
public class ArrayGroupBy extends BaseArity1 implements Node, Arity1 {
     // no logger 
    
    public ArrayGroupBy(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        //
        // $by : FunctionRef | LambdaFunctionRef | expr
        //     : Should evaluate into String, which will
        //     : be used as the grouping-key.
        //
        this.setParams(params, "groupBy", "by"); // TODO: expr | func (xor-param name)?
    }

    public ObjectType evaluate() throws OperonGenericException {
        //:OFF:log.debug("groupBy :: evaluate()");
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ArrayType arrayToGroup = (ArrayType) currentValue.evaluate();
            ObjectType result = null;
            if (this.getParam1() != null) {
                result = ArrayGroupBy.doGroupBy(arrayToGroup, this.getParam1());
            }
            else {
                result = ArrayGroupBy.doDefaultGroupBy(arrayToGroup);
            }
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    //
    // Yields: {"1": value1, "2": value2, "n": value_n}
    //
    // @Deprecated
    public static ObjectType doMinimalGroupBy(ArrayType arrayToGroup) throws OperonGenericException {
        ObjectType result = new ObjectType(arrayToGroup.getStatement());
        // No need to check if arrayToGroup is empty since after for the empty result will be returned
        int i = 1;
        for (Node value : arrayToGroup.getValues()) {
            //System.out.println("Value: " + value);
            if (value instanceof OperonValue == false) {
                value = value.evaluate();
            }
            PairType pair = new PairType(arrayToGroup.getStatement());
            pair.setPair("\"" + i + "\"", (OperonValue) value);
            result.addPair(pair);
            i += 1;
        }
        return result;
    }

    // Assuming that the Array consists of only Objects:
    //
    // [{"bin": 10}, {"bai": 20} , {"baa": 30}, {"bin": 40}] => object({"mode": "object"}) --> {"bin": [10, 40], "bai": [20], "baa": [30]}
    //
    // If the Array consists of mixed-values, then other than Objects are keyed by running-number.
    //
    public static ObjectType doDefaultGroupBy(ArrayType arrayToGroup) throws OperonGenericException {
        Map<String, List<OperonValue>> groupMap = new HashMap<String, List<OperonValue>>();
        
        ObjectType result = new ObjectType(arrayToGroup.getStatement());
        
        if (arrayToGroup.getValues().size() == 0) {
            return result;
        }
        
        int i = 1;
        String key;
        boolean objValue = false;
        
        for (Node value : arrayToGroup.getValues()) {
            //System.out.println("Value: " + value);
            if (value instanceof OperonValue == false) {
                value = value.evaluate();
            }
        
            //
            // If Object, then take only the value (omit the omit)
            //
            if (value instanceof ObjectType) {
                objValue = true;
                List<PairType> valuePairs = ((ObjectType) value).getPairs();
                if (valuePairs.size() > 0) {
                    value = (OperonValue) valuePairs.get(0).getValue();
                }
                else {
                    value = new EmptyType(value.getStatement());
                }
                key = valuePairs.get(0).getKey().substring(1, valuePairs.get(0).getKey().length() - 1);
            }
            
            else {
                key = String.valueOf(i);
                i += 1;
            }
            
            if (groupMap.get(key) == null) {
                // Initialize key with an array.
                List<OperonValue> values = new ArrayList<OperonValue>();
                if (objValue == false) {
                    // This value is added to signal that we want to treat the value as singular,
                    // and not add it into array (i.e. "value" instead of ["value"]).
                    // This is checked in the next-phase (for-loop, which build the result).
                    values.add(new EndValueType(value.getStatement()));
                }
                values.add((OperonValue) value);
                groupMap.put(key, values);
            }
            else {
                groupMap.get(key).add((OperonValue) value);
            }
            objValue = false;
        }
        
        // Go through the groupMap and add to resultObj
		for (Map.Entry<String, List<OperonValue>> entry : groupMap.entrySet()) {
		    ArrayType groupArr = new ArrayType(arrayToGroup.getStatement());
		    for (OperonValue groupValue : entry.getValue()) {
		        groupArr.getValues().add(groupValue);
		    }
		    PairType groupPair = new PairType(arrayToGroup.getStatement());
		    String entryKey = entry.getKey();
		    if (groupArr.getValues().get(0) instanceof EndValueType) {
		        groupPair.setPair("\"" + entryKey + "\"", (OperonValue) groupArr.getValues().get(1));
		    } 
		    else {
		        groupPair.setPair("\"" + entryKey + "\"", groupArr);
		    }
		    result.addPair(groupPair);
		}
        return result;
    }

    public static ObjectType doGroupBy(ArrayType arrayToGroup, Node paramNode) throws OperonGenericException {
        ObjectType result = new ObjectType(arrayToGroup.getStatement());
        if (arrayToGroup.getValues().size() == 0) {
            return result;
        }
        Map<String, List<OperonValue>> groupMap = new HashMap<String, List<OperonValue>>();
        
        OperonValue valueToGroup = (OperonValue) arrayToGroup.getValues().get(0);
        paramNode.getStatement().setCurrentValue(valueToGroup);
        Node groupByExpr = paramNode.evaluate();
        
        if (groupByExpr instanceof FunctionRef) {
            for (int i = 0; i < arrayToGroup.getValues().size(); i ++) {
                //:OFF:log.debug("loop, i == " + i);
                valueToGroup = (OperonValue) arrayToGroup.getValues().get(i);
                FunctionRef groupFnRef = (FunctionRef) groupByExpr;
                groupFnRef.getParams().clear();
                groupFnRef.getParams().add(valueToGroup);
                groupFnRef.setCurrentValueForFunction(arrayToGroup);
                StringType groupKeyResult = (StringType) groupFnRef.invoke();
                String keyString = groupKeyResult.getJavaStringValue();
                if (groupMap.get(keyString) == null) {
                    List<OperonValue> values = new ArrayList<OperonValue>();
                    values.add(valueToGroup);
                    groupMap.put(keyString, values);
                }
                else {
                    groupMap.get(keyString).add(valueToGroup);
                }
            }
        }
        else if (groupByExpr instanceof LambdaFunctionRef) {
            for (int i = 0; i < arrayToGroup.getValues().size(); i ++) {
                //:OFF:log.debug("loop, i == " + i);
                valueToGroup = (OperonValue) arrayToGroup.getValues().get(i);
                LambdaFunctionRef groupFnRef = (LambdaFunctionRef) groupByExpr;
                
                Map<String, Node> lfrParams = groupFnRef.getParams();
                
                // Take the first param to find the param name
                String paramName = null;
                for (Map.Entry<String, Node> lfrParam : lfrParams.entrySet()) {
                    paramName = lfrParam.getKey();
                    break;
                }
                
                // Clear previous params that were set
                lfrParams.clear();
                
                // Set the new param
                lfrParams.put(paramName, valueToGroup);
                groupFnRef.setCurrentValueForFunction(arrayToGroup);
                StringType groupKeyResult = (StringType) groupFnRef.invoke();
                String keyString = groupKeyResult.getJavaStringValue();
                if (groupMap.get(keyString) == null) {
                    List<OperonValue> values = new ArrayList<OperonValue>();
                    values.add(valueToGroup);
                    groupMap.put(keyString, values);
                }
                else {
                    groupMap.get(keyString).add(valueToGroup);
                }
            }
        }
        else if (groupByExpr instanceof ArrayType) {
            //
            // Use the array to name the values with one-to-one mapping.
            // The values must be of StringType.
            //
            ArrayType keyNames = (ArrayType) groupByExpr;
            
            for (int i = 0; i < arrayToGroup.getValues().size(); i ++) {
    		    String keyName = ((StringType) keyNames.getValues().get(i).evaluate()).getJavaStringValue();
    		    
    		    PairType groupPair = new PairType(arrayToGroup.getStatement());
    		    groupPair.setPair("\"" + keyName + "\"", (OperonValue) arrayToGroup.getValues().get(i));
    		    result.addPair(groupPair);
            }
            return result;
        }
        else {
            //
            // Start loop from i = 1, assign here (before for-loop the first result)
            //
            StringType groupKeyResult = (StringType) groupByExpr;
            String keyString = groupKeyResult.getJavaStringValue();
            if (groupMap.get(keyString) == null) {
                List<OperonValue> values = new ArrayList<OperonValue>();
                values.add(valueToGroup);
                groupMap.put(keyString, values);
            }
            else {
                groupMap.get(keyString).add(valueToGroup);
            }
            
            // Start from i=1 since the first round was already done
            for (int i = 1; i < arrayToGroup.getValues().size(); i ++) {
                Node node = paramNode; // this.getParam1(); // we require unevaluated expr
                valueToGroup = (OperonValue) arrayToGroup.getValues().get(i);
                node.getStatement().setCurrentValue(valueToGroup);
                
                groupKeyResult = (StringType) node.evaluate();
                keyString = groupKeyResult.getJavaStringValue();
                if (groupMap.get(keyString) == null) {
                    List<OperonValue> values = new ArrayList<OperonValue>();
                    values.add(valueToGroup);
                    groupMap.put(keyString, values);
                }
                else {
                    groupMap.get(keyString).add(valueToGroup);
                }
            }
        }
        
        // Go through the groupMap and add to resultObj
		for (Map.Entry<String, List<OperonValue>> entry : groupMap.entrySet()) {
		    ArrayType groupArr = new ArrayType(arrayToGroup.getStatement());
		    for (OperonValue groupValue : entry.getValue()) {
		        groupArr.getValues().add(groupValue);
		    }
		    PairType groupPair = new PairType(arrayToGroup.getStatement());
		    groupPair.setPair("\"" + entry.getKey() + "\"", groupArr);
		    result.addPair(groupPair);
		}
        return result;
    }

}