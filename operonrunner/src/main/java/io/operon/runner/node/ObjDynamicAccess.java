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
import io.operon.runner.node.Node; 
import io.operon.runner.node.ValueRef; 
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil; 
import io.operon.runner.util.ErrorUtil; 
 
import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 
 
public class ObjDynamicAccess extends AbstractNode implements Node { 
     // no logger  

    private Node keyExpr;
    private Node configs;
    private Info resolvedConfigs;
     
    public ObjDynamicAccess(Statement stmnt) { 
        super(stmnt); 
    } 
 
    public OperonValue evaluate() throws OperonGenericException { 
        //:OFF:log.debug("ENTER ObjDynamicAccess.evaluate(). Stmt: " + this.getStatement().getId()); 
        
        // get currentValue from the statement 
        OperonValue value = this.getStatement().getCurrentValue(); 
        OperonValue evaluatedValue = (OperonValue) value.evaluate();
        
        Info info = this.resolveConfigs(this.getStatement());
        
        if (evaluatedValue instanceof ObjectType) { 
            //System.out.println("ObjDynamicAccess :: obj");
            //:OFF:log.debug("EXIT ObjDynamicAccess.evaluate() obj"); 
            return evaluateObj( (ObjectType) evaluatedValue, info); 
        } 

        else if (evaluatedValue instanceof ArrayType) {
            //System.out.println("ObjDynamicAccess :: array");
            //:OFF:log.debug("EXIT ObjDynamicAccess.evaluate() array");
            return evaluateArray( (ArrayType) evaluatedValue, info);
        }
        //:OFF:log.debug("ObjDynamicAccess: cannot access object. Wrong type: " + evaluatedValue); 
        return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "OBJECT_DYNAMIC_ACCESS", "TYPE", "Cannot access object. Wrong type. Line #" + this.getSourceCodeLineNumber());
    } 
     
    // 
    // lhs is object, i.e. accessing object 
    // 
    private OperonValue evaluateObj(ObjectType obj, Info info) throws OperonGenericException { 
        OperonValue result = null;
        
        if (info.scanObject == false) {
            //System.out.println("ObjDynamicAccess :: scan = false");
            
            //
            // Scan through the struct, and evaluate the key for each PairType
            //
            for (int i = 0; i < obj.getPairs().size(); i ++) {
                //System.out.println("ObjDynamicAccess :: " + i);
                PairType pair = obj.getPairs().get(i);
                //:OFF:log.debug("    Obj key :: " + pair.getKey());
                
                //
                // Set the Pair's key as the currentValue for keyExpr:
                //
                Node dynamicKeyExpr = this.getKeyExpr();
                StringType pairKeyStr = new StringType(pair.getStatement());
                pairKeyStr.setValue(pair.getKey());
                dynamicKeyExpr.getStatement().setCurrentValue(pairKeyStr);
                OperonValue dynamicKeyExprResult = (OperonValue) dynamicKeyExpr.evaluate();
                
                result = this.handleResult(dynamicKeyExprResult, pair, i);
                if (result != null) {
                    //System.out.println("ObjDynamicAccess :: not null");
                    this.attachAttributes((OperonValue) result, obj);
                    break;
                }
                else {
                    if (info.valueOnEmpty != null) {
                        result = info.valueOnEmpty.evaluate();
                    }
                    else if (info.onEmptyType != null) {
                        if (info.onEmptyType == OnEmptyType.THROW) {
                            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "OBJECT_DYNAMIC_ACCESS", "TYPE", "Field not found. Line #" + this.getSourceCodeLineNumber());
                        }
                        else if (info.onEmptyType == OnEmptyType.OBJECT) {
                            result = new ObjectType(this.getStatement()); // return empty object value
                        }
                        else if (info.onEmptyType == OnEmptyType.ARRAY) {
                            result = new ArrayType(this.getStatement()); // return empty array value
                        }
                        else if (info.onEmptyType == OnEmptyType.NULL) {
                            result = new NullType(this.getStatement()); // return null value
                        }
                        else if (info.onEmptyType == OnEmptyType.STRING) {
                            result = new StringType(this.getStatement()); // return empty string value
                        }
                    }
                    else {
                        //System.out.println("ObjDynamicAccess :: empty");
                        result = new EmptyType(this.getStatement()); // return empty -value
                    }
                }
            }
            if (result == null) {
                if (info.valueOnEmpty != null) {
                    result = info.valueOnEmpty.evaluate();
                }
                else if (info.onEmptyType != null) {
                    if (info.onEmptyType == OnEmptyType.THROW) {
                        ErrorUtil.createErrorValueAndThrow(this.getStatement(), "OBJECT_DYNAMIC_ACCESS", "TYPE", "Field not found. Line #" + this.getSourceCodeLineNumber());
                    }
                    else if (info.onEmptyType == OnEmptyType.OBJECT) {
                        result = new ObjectType(this.getStatement()); // return empty object value
                    }
                    else if (info.onEmptyType == OnEmptyType.ARRAY) {
                        result = new ArrayType(this.getStatement()); // return empty array value
                    }
                    else if (info.onEmptyType == OnEmptyType.NULL) {
                        result = new NullType(this.getStatement()); // return null value
                    }
                    else if (info.onEmptyType == OnEmptyType.STRING) {
                        result = new StringType(this.getStatement()); // return empty string value
                    }
                }
                else {
                    result = new EmptyType(this.getStatement()); // return empty -value
                }
            }
        }
        else {
            result = new ArrayType(this.getStatement());
            //
            // Scan through the struct, and evaluate the key for each PairType
            //
            for (int i = 0; i < obj.getPairs().size(); i ++) {
                PairType pair = obj.getPairs().get(i);
                //:OFF:log.debug("    Obj key :: " + pair.getKey());
                
                //
                // Set the Pair's key as the currentValue for keyExpr:
                //
                Node dynamicKeyExpr = this.getKeyExpr();
                StringType pairKeyStr = new StringType(pair.getStatement());
                pairKeyStr.setValue(pair.getKey());
                dynamicKeyExpr.getStatement().setCurrentValue(pairKeyStr);
                OperonValue dynamicKeyExprResult = (OperonValue) dynamicKeyExpr.evaluate();
                
                OperonValue evResult = (OperonValue) this.handleResult(dynamicKeyExprResult, pair, i);
                if (evResult != null && evResult instanceof EmptyType == false) {
                    evResult = this.attachAttributes(evResult, obj);
                    ((ArrayType) result).addValue(evResult);
                }
                else {
                    OperonValue newResult = null;
                    if (info.valueOnEmpty != null) {
                        newResult = info.valueOnEmpty.evaluate();
                        ((ArrayType) result).addValue(newResult);
                    }
                    else if (info.onEmptyType != null) {
                        if (info.onEmptyType == OnEmptyType.THROW) {
                            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "OBJECT_DYNAMIC_ACCESS", "TYPE", "Field not found. Line #" + this.getSourceCodeLineNumber());
                        }
                        else if (info.onEmptyType == OnEmptyType.OBJECT) {
                            newResult = new ObjectType(this.getStatement()); // return empty object value
                            ((ArrayType) result).addValue(newResult);
                        }
                        else if (info.onEmptyType == OnEmptyType.ARRAY) {
                            newResult = new ArrayType(this.getStatement()); // return empty array value
                            ((ArrayType) result).addValue(newResult);
                        }
                        else if (info.onEmptyType == OnEmptyType.NULL) {
                            newResult = new NullType(this.getStatement()); // return null value
                            ((ArrayType) result).addValue(newResult);
                        }
                        else if (info.onEmptyType == OnEmptyType.STRING) {
                            newResult = new StringType(this.getStatement()); // return empty string value
                            ((ArrayType) result).addValue(newResult);
                        }
                    }
                    else {
                        newResult = new EmptyType(this.getStatement()); // return empty -value
                        ((ArrayType) result).addValue(newResult);
                    }
                }
            }
        }
        
        // update the currentValue from the statement
        this.getStatement().setCurrentValue(result);
        this.setEvaluatedValue(result);
        return result;
    } 
     
    // 
    // lhs is array, i.e. accessing array 
    // 
    private ArrayType evaluateArray(ArrayType array, Info info) throws OperonGenericException { 
        //:OFF:log.debug("Accessing array of objects"); 
        ArrayType resultArray = new ArrayType(this.getStatement()); 
        List<Node> arrayValues = array.getValues(); 
         
        for (int i = 0; i < arrayValues.size(); i ++) { 
            Node arrayNode = arrayValues.get(i); 
            ////:OFF:log.debug("    >> Looping: " + i); 
            if (arrayNode.evaluate() instanceof ObjectType) { 
                Node obj = evaluateObj((ObjectType) arrayNode.evaluate(), info); 
                resultArray.addValue(obj); 
            } 
        } 
         
        // update the currentValue from the statement 
        this.getStatement().setCurrentValue(resultArray); 
        this.setEvaluatedValue(resultArray ); 
        return resultArray; 
    } 
    
    public OperonValue handleResult(OperonValue dynamicKeyExprResult, PairType pair, int i) throws OperonGenericException {
        OperonValue result = null;
        if (dynamicKeyExprResult instanceof StringType) {
            String dynamicKey = ((StringType) dynamicKeyExprResult).getJavaStringValue();
            if (pair.getKey().equals("\"" + dynamicKey + "\"")) {
                result = pair.getEvaluatedValue();
            }
        }
        
        else if (dynamicKeyExprResult instanceof NumberType) {
            int position = (int) ((NumberType) dynamicKeyExprResult).getDoubleValue();
            if (position == i + 1) {
                result = pair.getEvaluatedValue();
            }
        }
        //
        // NOTE: if result is Boolean (True), then the _first_ result is only returned!
        //
        else if (dynamicKeyExprResult instanceof TrueType) {
            result = pair.getEvaluatedValue();
        }
        
        else if (dynamicKeyExprResult instanceof FalseType) {
            
        }
        
        else if (dynamicKeyExprResult instanceof FunctionRef) { 
            FunctionRef fr = (FunctionRef) dynamicKeyExprResult; 
            result = (OperonValue) fr.invoke();
            return this.handleResult((OperonValue) result, pair, i);
        }
        
        else if (dynamicKeyExprResult instanceof LambdaFunctionRef) { 
            LambdaFunctionRef lfr = (LambdaFunctionRef) dynamicKeyExprResult; 
            result = (OperonValue) lfr.invoke(); 
            return this.handleResult((OperonValue) result, pair, i);
        }
        
        else {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "OBJECT_DYNAMIC_ACCESS", "TYPE", "Unsupported type: " + dynamicKeyExprResult.getClass().getName() + ", line #" + this.getSourceCodeLineNumber());
        }
        return result;
    }
    
    //
    // Param: OperonValue obj :: the parent-object
    //
    public OperonValue attachAttributes(OperonValue result, OperonValue obj) throws OperonGenericException {
        if (result instanceof LambdaFunctionRef) { 
            LambdaFunctionRef lfr = (LambdaFunctionRef) result; 
            lfr.getStatement().getRuntimeValues().put("_", obj); 
            if (lfr.isInvokeOnAccess()) {
                result = lfr.invoke();
            }
        }
        
        else if (result instanceof FunctionRef) { 
            FunctionRef fr = (FunctionRef) result; 
            fr.getStatement().getRuntimeValues().put("_", obj); 
        } 
         
        else if (result instanceof ObjectType) { 

        }
        return result;
    }
    
    public void setKeyExpr(Node expr) {
        this.keyExpr = expr;
    }
    
    public Node getKeyExpr() {
        return this.keyExpr;
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

    public void setResolvedConfigs(Info i) {
        this.resolvedConfigs = i;
    }

    public Info getResolvedConfigs() {
        return this.resolvedConfigs;
    }

    public Info resolveConfigs(Statement stmt) throws OperonGenericException {
        Info info = new Info();
        
        //
        // We must check also for the resolvedConfigs, because Pattern sets them
        // but leaves configs as null. So if resolvedConfigs are set by Pattern
        // then we will use them. If both are null, then we use the default
        // configuration. Otherwise, if configs are set, but not resolved,
        // then we continue resolving them.
        //
        if (this.getConfigs() == null && this.getResolvedConfigs() == null) {
            return info;
        }
        else if (this.getResolvedConfigs() != null) {
            return this.getResolvedConfigs();
        }
        
        OperonValue currentValueCopy = stmt.getCurrentValue().copy();
        
        for (PairType pair : this.getConfigs().getPairs()) {
            String key = pair.getKey();
            //System.out.println("Key :: " + key);
            switch (key.toLowerCase()) {
                case "\"onemptyvalue\"":
                    Node valueOnEmptyValue = pair.getValue(); // NOTE: this could be an expr, which we won't evaluate here.
                    info.valueOnEmpty = valueOnEmptyValue;
                    break;
                case "\"onemptythrow\"":
                    OperonValue throwOnEmptyValue = pair.getEvaluatedValue();
                    if (throwOnEmptyValue instanceof FalseType) {
                        info.onEmptyType = null;
                    }
                    else {
                        info.onEmptyType = OnEmptyType.THROW;
                    }
                    break;
                case "\"maxresults\"":
                    NumberType maxResultsValue = (NumberType) pair.getEvaluatedValue();
                    info.maxResults = (int) maxResultsValue.getDoubleValue();
                    break;
                case "\"scanobject\"":
                    OperonValue scanObjectValue = pair.getEvaluatedValue();
                    if (scanObjectValue instanceof FalseType) {
                        info.scanObject = false;
                    }
                    else {
                        info.scanObject = true;
                    }
                    break;
                case "\"onemptyobject\"":
                    OperonValue objectOnEmptyValue = pair.getEvaluatedValue();
                    if (objectOnEmptyValue instanceof FalseType) {
                        info.onEmptyType = null;
                    }
                    else {
                        info.onEmptyType = OnEmptyType.OBJECT;
                    }
                    break;
                    
                case "\"onemptynull\"":
                    OperonValue nullOnEmptyValue = pair.getEvaluatedValue();
                    if (nullOnEmptyValue instanceof FalseType) {
                        info.onEmptyType = null;
                    }
                    else {
                        info.onEmptyType = OnEmptyType.NULL;
                    }
                    break;
                    
                case "\"onemptystring\"":
                    OperonValue stringOnEmptyValue = pair.getEvaluatedValue();
                    if (stringOnEmptyValue instanceof FalseType) {
                        info.onEmptyType = null;
                    }
                    else {
                        info.onEmptyType = OnEmptyType.STRING;
                    }
                    break;
                    
                case "\"onemptyarray\"":
                    OperonValue arrayOnEmptyValue = pair.getEvaluatedValue();
                    if (arrayOnEmptyValue instanceof FalseType) {
                        info.onEmptyType = null;
                    }
                    else {
                        info.onEmptyType = OnEmptyType.ARRAY;
                    }
                    break;
                default:
                    break;
            }
        }
        this.setResolvedConfigs(info);
        stmt.setCurrentValue(currentValueCopy);
        return info;
    }

    class Info {
        public Integer maxResults = null;
        
        //
        // Gather the result in an array when working with Object.
        // This can be used if expr may result 
        // in more than one result.
        //
        public boolean scanObject = false;
        
        public OnEmptyType onEmptyType = null;

        public Node valueOnEmpty = null;
    }
    
    private enum OnEmptyType {
        THROW,  // If field is not found, then throw an exception
        OBJECT, // If field is not found, then return an empty object: {}
        NULL, // If field is not found, then return null
        STRING, // If field is not found, then return ""
        ARRAY; // If field is not found, then return []
    }
    
    public String toString() { 
        return this.getEvaluatedValue().toString(); 
    }
} 