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

import java.util.List;

import io.operon.runner.statement.Statement;
import io.operon.runner.model.path.*;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class ObjDynamicDeepScan extends AbstractNode implements Node {
     // no logger 
    
    private Node keyExpr;
    private Path currentPath;
    private int currentDepth = 0;
    private OperonValue rootValue; // Array or Object
    
    private Node configs;
    private Info resolvedConfigs;
    
    public ObjDynamicDeepScan(Statement stmnt) {
        super(stmnt);
    }

    public OperonValue evaluate() throws OperonGenericException {
        //:OFF:log.debug("ENTER ObjDynamicDeepScan.evaluate(). Stmt: " + this.getStatement().getId());
        
        // get currentValue from the statement
        OperonValue value = this.getStatement().getCurrentValue();
        this.setRootValue(value);
        //:OFF:log.debug("    >> @: " + value.toString());

        Info info = this.resolveConfigs(this.getStatement());

        this.currentPath = new Path(this.getStatement());
        return evaluateSelector(value, info);
    }
    
    private OperonValue evaluateSelector(OperonValue value, Info info) throws OperonGenericException {
        OperonValue evaluatedValue = value.evaluate();
        if (evaluatedValue instanceof ObjectType) {
            //:OFF:log.debug("EXIT ObjDynamicDeepScan.evaluate() obj");
            return evaluateObj( (ObjectType) evaluatedValue, info );
        }
        
        else if (evaluatedValue instanceof ArrayType) {
            //:OFF:log.debug("EXIT ObjDynamicDeepScan.evaluate() array");
            return evaluateArray( (ArrayType) evaluatedValue, info );
        }
        
        //:OFF:log.debug("ObjDynamicDeepScan: cannot scan object. Wrong type: " + evaluatedValue);
        return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "OBJECT_DYNAMIC_DEEP_SCAN", "TYPE", "Cannot scan object. Wrong type. Line #" + this.getSourceCodeLineNumber());
    }
    
    //
    // CurrentValue was Object
    //
    private OperonValue evaluateObj(ObjectType obj, Info info) throws OperonGenericException {
        ArrayType result = new ArrayType(this.getStatement());
        //System.out.println("evaluate object, currentDepth = " + currentDepth);
        //System.out.println("START PATH PART = " + this.currentPath);
        //System.out.println("   CurrentPath size = " + this.currentPath.getPathParts().size());
        
        int startPathSize = this.currentPath.getPathParts().size();
        this.currentDepth += 1;
        
        //System.out.println("maxResults="+info.maxResults);
        
        //
        // Scan through the struct, and evaluate the key for each PairType
        //
        for (int i = 0; i < obj.getPairs().size(); i ++) {
            PairType pair = obj.getPairs().get(i);
            //:OFF:log.debug("    Obj key :: " + pair.getKey());

            PathPart pp = new KeyPathPart(pair.getKey().substring(1, pair.getKey().length() - 1));
            this.currentPath.setValueLink(pair.getValue());
            this.currentPath.setObjLink(this.getRootValue());
            this.currentPath.addPathPart(pp);
            
            //System.out.println("CurrentPath = " + this.currentPath + ", CurrentDepth = " + this.currentDepth);
            
            Node dynamicKeyExpr = this.getKeyExpr();
            ObjectType cvObj = new ObjectType(this.getStatement());
            cvObj.addPair(pair);
            dynamicKeyExpr.getStatement().setCurrentValue(cvObj);
            dynamicKeyExpr.getStatement().setCurrentPath(this.currentPath);
            OperonValue dynamicKeyExprResult = dynamicKeyExpr.evaluate();
            
            OperonValue handledResult = this.handleResult(dynamicKeyExprResult, pair, i);

            boolean skippedReference = false;

            //System.out.println("  >> Check skipPath.");
            if (info.skipPaths != null) {
                boolean skipPathBool = false; 
                for (Node skipPathNode : info.skipPaths) {
                    Path skipPath = (Path) skipPathNode.evaluate();
                    //System.out.println("    >> skipPath=" + skipPath);
                    if (currentPath.equals(skipPath)) {
                        skipPathBool = true;
                        //System.out.println("      >> currentPath == skipPath");
                        break;
                    }
                    else {
                        //System.out.println("      >> currentPath != skipPath");
                    }
                }
                if (skipPathBool) {
                    this.currentPath.removeLastPathPart();
                    continue;
                }
            }

            if (info.maxResults != null && result.getValues().size() >= info.maxResults) {
                break;
            }
            if (info.maxDepth != null && this.currentDepth > info.maxDepth) {
                this.currentPath.removeLastPathPart();
                continue;
            }
            else if (handledResult != null) {
                skippedReference = this.attachAttributes(handledResult, obj, info);
                if (skippedReference == false) {
                    result.addValue(handledResult);
                }
            }
            skippedReference = false; // reset to false
            
            OperonValue subObj = pair.getEvaluatedValue();
            ArrayType subResult = null;

            if ((subObj instanceof ObjectType) || (subObj instanceof ArrayType)) {
                subResult = (ArrayType) evaluateSelector(subObj, info);
                if (subResult.getValues().size() > 0) {
                    //
                    // Recursive call creates an array, which must be flattened:
                    //
                    for (Node n : subResult.getValues()) {
                        if (info.maxResults != null && result.getValues().size() >= info.maxResults) {
                            break;
                        }
                        else {
                            if (subResult != null) {
                                skippedReference = this.attachAttributes(handledResult, obj, info);
                                if (skippedReference == false) {
                                    result.addValue(n);
                                }
                            }
                        }
                    }
                }
                else {

                }
            }
            else {
                this.currentPath.removeLastPathPart();
            }
        }
        //System.out.println("END: rm last path part");
        //System.out.println("END PathSize=" + this.currentPath.getPathParts().size());
        int endPathSize = this.currentPath.getPathParts().size();
        for (int i = 0; i < endPathSize - startPathSize; i ++) {
            this.currentPath.removeLastPathPart();
        }
        this.currentPath.removeLastPathPart();
        this.currentDepth -= 1;

        // update the currentValue from the statement
        this.getStatement().setCurrentValue(result);
        
        this.setEvaluatedValue(result);
        return result;
    }
    
    //
    // CurrentValue is Array
    //
    private OperonValue evaluateArray(ArrayType array, Info info) throws OperonGenericException {
        //:OFF:log.debug("Accessing array of objects");
        //System.out.println("Accessing array, currentDepth = " + currentDepth);
        
        ArrayType resultArray = new ArrayType(this.getStatement());
        List<Node> arrayValues = array.getValues();
        
        for (int i = 0; i < arrayValues.size(); i ++) {
            Node arrayNode = arrayValues.get(i);
            //:OFF:log.debug("    >> Looping: " + i);
            //System.out.println("Array Looping i=" + i);

            PathPart pp = new PosPathPart(i + 1);
            //this.currentPath.setValueLink(arrayNodeEvaluated);
            this.currentPath.setValueLink(array);
            this.currentPath.setObjLink(this.getRootValue());
            this.currentPath.addPathPart(pp);            
            arrayNode.getStatement().setCurrentPath(this.currentPath);
            
            OperonValue arrayNodeEvaluated = arrayNode.evaluate();

            //System.out.println("ARRAY CurrentPath = " + this.currentPath + ", CurrentDepth = " + this.currentDepth);
            
            if (arrayNodeEvaluated instanceof ObjectType) {
                ArrayType arrayNodeResult = (ArrayType) evaluateObj((ObjectType) arrayNodeEvaluated, info);
                if (arrayNodeResult.getValues().size() > 0) {
                    //
                    // Recursive call creates an array, which must be flattened:
                    //
                    for (Node n : arrayNodeResult.getValues()) {
                        if (info.maxResults != null && resultArray.getValues().size() >= info.maxResults) {
                            break;
                        }
                        else {
                            resultArray.addValue(n);
                        }
                    }
                }
            }
            
            // Added this so we can traverse nested arrays [[]]
            else if (arrayNodeEvaluated instanceof ArrayType) {
                ArrayType arrayNodeResult = (ArrayType) evaluateArray((ArrayType) arrayNodeEvaluated, info);
                if (arrayNodeResult.getValues().size() > 0) {
                    //
                    // Recursive call creates an array, which must be flattened:
                    //
                    for (Node n : arrayNodeResult.getValues()) {
                        if (info.maxResults != null && resultArray.getValues().size() >= info.maxResults) {
                            break;
                        }
                        else {
                            resultArray.addValue(n);
                        }
                    }
                }
            }
            
            else {
                this.currentPath.removeLastPathPart();
            }
        }
        
        this.currentPath.removeLastPathPart();
        
        // update the currentValue from the statement
        this.getStatement().setCurrentValue(resultArray);
        this.setEvaluatedValue(resultArray);
        return resultArray;
    }
    
    public OperonValue handleResult(OperonValue dynamicKeyExprResult, PairType pair, int i) throws OperonGenericException {
        OperonValue result = null;
        
        if (dynamicKeyExprResult instanceof StringType) {
            String dynamicKey = ((StringType) dynamicKeyExprResult).getJavaStringValue();
            if (pair.getKey().equals("\"" + dynamicKey + "\"")) {
                result = pair.getEvaluatedValue();
            }
            else {
                
            }
        }
        
        else if (dynamicKeyExprResult instanceof NumberType) {
            int position = (int) ((NumberType) dynamicKeyExprResult).getDoubleValue();
            if (position == i + 1) {
                result = pair.getEvaluatedValue();
            }
        }
        
        else if (dynamicKeyExprResult instanceof TrueType) {
            result = currentPath.copy();
        }
        
        else if (dynamicKeyExprResult instanceof FalseType) {

        }
        
        else if (dynamicKeyExprResult instanceof FunctionRef) { 
            FunctionRef fr = (FunctionRef) dynamicKeyExprResult; 
            result = fr.invoke();
            return this.handleResult(result, pair, i);
        }
        
        else if (dynamicKeyExprResult instanceof LambdaFunctionRef) { 
            LambdaFunctionRef lfr = (LambdaFunctionRef) dynamicKeyExprResult; 
            result = lfr.invoke(); 
            return this.handleResult(result, pair, i);
        }
        
        else {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "OBJECT_DYNAMIC_DEEP_SCAN", "TYPE", "Cannot scan object. Wrong key type: " + dynamicKeyExprResult.getClass().getName() + ", line #" + this.getSourceCodeLineNumber());
        }
        return result;
    }
    
    //
    // The return value:
    //  true: reference was skipped
    //  false: reference was not skipped or not evaluated
    //
    private boolean attachAttributes(OperonValue result, ObjectType obj, Info info) throws OperonGenericException {
        if (result instanceof LambdaFunctionRef) { 
            LambdaFunctionRef lfr = (LambdaFunctionRef) result;
            lfr.getStatement().getRuntimeValues().put("_", obj);
            if (info.skipReferences == true) {
                return true;
            }
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
        return false;
    }
    
    public void setKeyExpr(Node expr) {
        this.keyExpr = expr;
    }
    
    public Node getKeyExpr() {
        return this.keyExpr;
    }
    
    public void setRootValue(OperonValue rv) {
        this.rootValue = rv;
    }
    
    public OperonValue getRootValue() {
        return this.rootValue;
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
        // We must check also for the resolvedConfigs, because FilterList sets them
        // but leaves configs as null. So if resolvedConfigs are set by FilterList
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
        
        OperonValue currentValueCopy = stmt.getCurrentValue();
        
        for (PairType pair : this.getConfigs().getPairs()) {
            String key = pair.getKey();
            switch (key.toLowerCase()) {
                case "\"maxresults\"":
                    NumberType maxResultsValue = (NumberType) pair.getEvaluatedValue();
                    info.maxResults = (int) ((NumberType) maxResultsValue).getDoubleValue();
                    break;
                case "\"maxdepth\"":
                    NumberType maxDepthValue = (NumberType) pair.getEvaluatedValue();
                    info.maxDepth = (int) ((NumberType) maxDepthValue).getDoubleValue();
                    break;
                case "\"skipreferences\"":
                    OperonValue skipReferencesValue = pair.getEvaluatedValue();
                    if (skipReferencesValue instanceof FalseType) {
                        info.skipReferences = false;
                    }
                    else {
                        info.skipReferences = true;
                    }
                    break;
                case "\"skippaths\"":
                    ArrayType skipPathsValue = (ArrayType) pair.getEvaluatedValue();
                    info.skipPaths = ((ArrayType) skipPathsValue).getValues();
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
        public Integer maxDepth = null;
        public boolean skipReferences = false;
        public List<Node> skipPaths = null;
    }

    public String toString() {
        return this.getEvaluatedValue().toString();
    }
}