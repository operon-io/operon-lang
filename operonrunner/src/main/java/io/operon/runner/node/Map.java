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
import java.util.ArrayList; 
import java.util.stream.Collectors;
import java.util.Collections;
import java.io.IOException;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.ModuleContext;
import io.operon.runner.model.exception.BreakLoopException;
import io.operon.runner.model.exception.ContinueLoopException;
import io.operon.runner.model.streamvaluewrapper.*;
import io.operon.runner.model.path.*;
import io.operon.runner.processor.BinaryNodeProcessor; 
import io.operon.runner.statement.Statement; 
import io.operon.runner.statement.LetStatement; 
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 
 
/** 
 *  Similar to array:Foreach
 *  Example: "[1,2,3] Map 1 End" results: [1, 1, 1]
 *  
 */ 
public class Map extends AbstractNode implements Node {
     // no logger  

    private Node mapExpr; // i.e. "[1,2,3] Map map_expr End" (here the mapExpr is the map_expr)

    // SplicingLeft, SplicingRight, or SplicingRange
    //private Node mappingRange; // e.g. [1,2,3] Map(::2): 0 End ==> [0, 0, 3]
    private Node configs;

    public Map(Statement stmnt) {
        super(stmnt);
        //this.evaluated = false;
    }

    public OperonValue evaluate() throws OperonGenericException {
        //System.out.println("Map :: evaluate()");
        //System.out.println("  Map :: Stmt=" + this.getStatement().getId());
        assert (this.getMapExpr() != null) : "Map.evaluate() : mapExpr was null";
        assert (this.getMapExpr().getExpr() != null) : "Map.evaluate() : mapExpr.expr was null";
        
        OperonValue currentValue = this.getStatement().getPreviousStatement().getCurrentValue();
        OperonValue result = currentValue.evaluate();

        Info info = this.resolveConfigs(this.getStatement().getPreviousStatement());

        if (result instanceof ArrayType) {
            result = this.handleArray(result, info);
        }
        else if (result instanceof ObjectType) {
            try {
                result = this.handleObject(result, info);
            } catch (IOException | ClassNotFoundException e) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "MAP", "OBJECT", e.getMessage() + ", line #" + this.getSourceCodeLineNumber());
            }
        }
        else if (result instanceof StreamValue) {
            result = this.handleStream((StreamValue) result, info);
        }
        else if (result instanceof StringType) {
            result = this.handleString(result, info);
        }
        else if (result instanceof NumberType) {
            result = this.handleNumber(result, info);
        }
        else {
            //System.out.println(">> CV=error cannot iterate");
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "MAP", "ITERATE", "Map: cannot iterate the value, line #" + this.getSourceCodeLineNumber());
        }
        
        this.getStatement().getPreviousStatement().setCurrentValue(result);
        return result;
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

    public ArrayType handleNumber(OperonValue currentValueCopy, Info info) throws OperonGenericException {
        NumberType n = (NumberType) currentValueCopy;
        long ln = (long) n.getDoubleValue();
        ArrayType resultArray = new ArrayType(this.getStatement());
        boolean positiveDirection = true;
        if (ln < 0) {
            positiveDirection = false;
            ln = -ln;
        }
        
        if (info.direction == Direction.RIGHT) {
            positiveDirection = !positiveDirection;
        }
        
        // create for-loop
        ArrayType window = null;
        int currentWindowSize = 0;
        
        for (long i = 0; i < ln; i ++) {
            //System.out.println("i = " + i + ", ln = " + ln);
            NumberType indexValue = new NumberType(this.getStatement());
            
            if (positiveDirection) {
                indexValue.setDoubleValue((double) (i + 1));
            }
            else {
               indexValue.setDoubleValue((double) (ln - i)); 
            }
            
            //
            // Gather the "window" for evaluation:
            //
            if (info.windowSize > 1) {
                if (window == null) {
                    window = new ArrayType(this.getStatement());
                }
                
                if (currentWindowSize < info.windowSize) {
                    window.addValue(indexValue);
                    currentWindowSize += 1;
                    //System.out.println("add value and decide if continue");
                    if (currentWindowSize < info.windowSize && i + 1 < ln) {
                        //System.out.println(">> continue");
                        continue;
                    }
                    else {
                        //System.out.println(">> evaluate :: " + window);
                    }
                }
                else if (i + 1 < ln) {
                    //System.out.println("gather next");
                    continue; // gather next item into window
                }
                
                // allow to evaluate
                this.getMapExpr().getStatement().setCurrentValue(window);
            }
            else {
                this.getMapExpr().getStatement().setCurrentValue(indexValue);
            }
            
            OperonValue mapExprResult  = null;
            this.eagerEvaluateLetStatements();
            try {
                mapExprResult = (OperonValue) this.getMapExpr().evaluate();
                //System.out.println("result from iteration :: " + mapExprResult);
                if (window != null) {
                    if (info.slidingWindow == false) {
                        currentWindowSize = 0;
                        window = null;
                    }
                    else {
                        //System.out.println("window after evaluation and before left-stripping: " + window);
                        currentWindowSize -= 1;
                        ArrayType windowCopy = (ArrayType) JsonUtil.copyArray((ArrayType) window);
                        windowCopy.getValues().remove(0);
                        window = windowCopy;
                        //System.out.println("window after evaluation and left-stripping: " + window);
                    }
                }
            } catch (BreakLoopException ble) {
                break;
            } catch (ContinueLoopException cle) {
                this.synchronizeState();
                continue;
            }
             
            if (mapExprResult instanceof EmptyType) { 
                this.synchronizeState();
                continue; 
            }
            
            OperonValue resultCopy = ((OperonValue) mapExprResult).copy();
            //System.out.println("result again from iteration :: " + resultCopy);
            resultArray.addValue(resultCopy); // Add the deep-copy instead of the value-reference to prevent modifation from memory.
            
            if (info.retainFirst != -1 && resultArray.getValues().size() > info.retainFirst) {
                resultArray.getValues().remove(resultArray.getValues().size() - 1);
            }
            
            else if (info.retainLast != -1 && resultArray.getValues().size() > info.retainLast) {
                // drop first element from an array:
                resultArray.getValues().remove(0);
            }
            this.synchronizeState();
        }
        
        return resultArray;
    }

    public ArrayType handleArray(OperonValue currentValueCopy, Info info) throws OperonGenericException {
        //System.out.println("Map :: handleArray");
        //System.out.println("  Map :: expr=" + this.getMapExpr().getExpr()); // OK this far
        ArrayType arr = (ArrayType) currentValueCopy;
        List<Node> arrayValues = arr.getValues();
        //System.out.println("  >> " + arr);
        ArrayType resultArray = new ArrayType(this.getStatement());
        
        if (info.parallel == false) {
            ArrayType window = null;
            int currentWindowSize = 0;
            int k = 0; // this is used as index to get the value. "i" is not used as it is used to calculate the "k" based on direction.
            
            for (int i = 0; i < arrayValues.size(); i ++) {
                if (info.direction == Direction.RIGHT) {
                    k = arrayValues.size() - i - 1;
                }
                else {
                    k = i;
                }
                Node arrayValue = arrayValues.get(k);
                ////:OFF:log.debug("    >> MAP OP i=["  + i + "], arrayValue :: "+ arrayValue); 
        
                if ((arrayValue instanceof OperonValue) == false) {
                    arrayValue = arrayValue.getEvaluatedValue();
                }
                
                //
                // ATTRIBUTES
                //
                
                if (this.getStatement().getPreviousStatement() != null) {
                    Statement prevStmt = this.getStatement().getPreviousStatement();
                    Path prevCurrentPath = (Path) prevStmt.getCurrentPath();
                    PathPart pp = new PosPathPart(k + 1);
                    Path newPath = new Path(this.getStatement());
                    newPath.setObjLink(prevCurrentPath.getObjLink());
                    newPath = (Path) prevCurrentPath.copy();
                    newPath.getPathParts().add(pp);
                    this.getStatement().setCurrentPath(newPath);
                }
                else {
                    PathPart pp = new PosPathPart(k + 1);
                    Path newPath = new Path(this.getStatement());
                    newPath.getPathParts().add(pp);
                    newPath.setObjLink(arr);
                    this.getStatement().setCurrentPath(newPath);
                }
                
                //System.out.println("MAP :: Array :: currentPath :: " + this.getStatement().getCurrentPath());

                //System.out.println("    >> MAP OP k=["  + k + "], arrayValue :: "+ arrayValue);  

                //
                // Gather the "window" for evaluation:
                //
                if (info.windowSize > 1) {
                    if (window == null) {
                        window = new ArrayType(this.getStatement());
                    }
                    
                    if (currentWindowSize < info.windowSize) {
                        window.addValue(arrayValue);
                        currentWindowSize += 1;
                        //System.out.println("add value and decide if continue");
                        if (currentWindowSize < info.windowSize && i + 1 < arrayValues.size()) {
                            //System.out.println(">> continue");
                            continue;
                        }
                        else {
                            //System.out.println(">> evaluate");
                        }
                    }
                    else if (i + 1 < arrayValues.size()) {
                        //System.out.println("gather next");
                        continue; // gather next item into window
                    }
                    
                    // allow to evaluate
                    //System.out.println("allow to evaluate: " + window);
                    this.getStatement().setCurrentValue(window); // Set CV also for currentStatement because lfr sets cv from previous-stmt
                    this.getMapExpr().getStatement().setCurrentValue(window);
                }
                else {
                    this.getStatement().setCurrentValue((OperonValue) arrayValue);
                    this.getMapExpr().getStatement().setCurrentValue((OperonValue) arrayValue);
                }
                
                Node mapExprResult = null;
                this.eagerEvaluateLetStatements();
                try {
                    mapExprResult = this.getMapExpr().evaluate();
                    if (window != null) {
                        if (info.slidingWindow == false) {
                            currentWindowSize = 0;
                            window = null;
                        }
                        else {
                            //System.out.println("window after evaluation and before left-stripping: " + window);
                            currentWindowSize -= 1;
                            ArrayType windowCopy = (ArrayType) JsonUtil.copyArray((ArrayType) window);
                            windowCopy.getValues().remove(0);
                            window = windowCopy;
                            //System.out.println("window after evaluation and left-stripping: " + window);
                        }
                    }
                    //System.out.println("Map :: after :: currentPath :: " + this.getStatement().getCurrentPath());
                } catch (BreakLoopException ble) {
                    break;
                } catch (ContinueLoopException cle) {
                    this.synchronizeState();
                    continue;
                }
                if (mapExprResult instanceof EmptyType) { 
                    this.synchronizeState();
                    continue; 
                }
                //System.out.println("  >> Map res: " + mapExprResult);
                resultArray.addValue(((OperonValue) mapExprResult).copy()); // Add the deep-copy instead of the value-reference to prevent modifation from memory.
                this.synchronizeState();
            }
        }
        
        else {
            
            //
            // NOTES for parallel-mapping:
            //
            // - cannot assign position
            // - cannot use previous/next, because missing position
            // - cannot access values / functions outside of Map's scope
            // - windowing is not possible
            //
            
            //System.out.println("parallel was true");
            if (info.direction == Direction.RIGHT) {
                Collections.reverse(arrayValues);
            }
            List<OperonValue> parallelResults = arrayValues.parallelStream().map(
                arrayValue -> {
                    try {
                        if ((arrayValue instanceof OperonValue) == false) {
                            arrayValue = arrayValue.getEvaluatedValue();
                        }
                        
                        //
                        // Each forked process would use the same statement-context,
                        // and therefore one process would affect other process' statement,
                        // and thus the currentValue, causing wrong results.
                        //
                        // Therefore we must copy the mapExpr -node, so each would have their own statement.
                        //System.out.println("  Parallel Map");
                        //System.out.println("    Parallel Map :: expr=" + this.getMapExpr().getExpr());
                        
                        //
                        // CHECK: we should COMPILE the expr with NEW DefaultStatement, so NONE of the state will leak into the new expression!
                        //
                        boolean linkScope = false;
                        Node mapExprCopy = AbstractNode.deepCopyNode(this.getStatement(), this.getMapExpr(), linkScope); // this.getMapExpr(); // AbstractNode.deepCopyNode(this.getMapExpr());
                        
                        mapExprCopy.getStatement().setCurrentValue((OperonValue) arrayValue);
                        //System.out.println("map-expr: set prototype to true");
                        //mapExprCopy.getStatement().setPrototype(true);
                        
                        Node mapExprResult = mapExprResult = mapExprCopy.evaluate();

                        if (mapExprResult instanceof EmptyType) { 
                            this.synchronizeState();
                            return new EmptyType(this.getStatement()); // TODO: should we filter these out?
                        }
                        this.synchronizeState();
                        return ((OperonValue) mapExprResult).copy(); // Return the deep-copy instead of the value-reference to prevent modifation from memory.
                    } catch (Exception e) {
                        // Do smthg
                        System.err.println("Error: " + e.getMessage());
                        return null;
                    }
                }    
            ).collect(Collectors.toList());
            resultArray.getValues().addAll(parallelResults);
        }
        return resultArray;
    }

    public OperonValue handleObject(OperonValue currentValueCopy, Info info) throws OperonGenericException, IOException, ClassNotFoundException {
        //System.out.println("Map :: handleObject");
        ObjectType jsonObj = (ObjectType) currentValueCopy;
        List<PairType> pairs = jsonObj.getPairs();
        
        ObjectType resultObj = new ObjectType(this.getStatement());
        ArrayType resultArray = new ArrayType(this.getStatement());
        
        if (info.parallel == false) {
            ObjectType window = null;
            int currentWindowSize = 0;
            int k = 0; // this is used as index to get the value. "i" is not used as it is used to calculate the "k" based on direction.
            //System.out.println("parallel was false");
            
            Path prevPath = null;
            Path resetPath = new Path(this.getStatement());
            if (this.getStatement().getPreviousStatement() != null) {
                Statement prevStmt = this.getStatement().getPreviousStatement();
                Path prevCurrentPath = (Path) prevStmt.getCurrentPath();
                PathPart pp = new PosPathPart(k + 1);
                Path newPath = new Path(this.getStatement());
                newPath.setObjLink(prevCurrentPath.getObjLink());
                newPath = (Path) prevCurrentPath.copy();
                newPath.getPathParts().add(pp);
                this.getStatement().setCurrentPath(newPath);
            }
            else {
                PathPart pp = new PosPathPart(k + 1);
                Path newPath = new Path(this.getStatement());
                newPath.getPathParts().add(pp);
                newPath.setObjLink(currentValueCopy);
                this.getStatement().setCurrentPath(newPath);
            }
            //System.out.println(">> start for");
            for (int i = 0; i < pairs.size(); i ++) {
                //
                // Reset prevPath
                //  TODO: inspect where the prevPath gets overwritten, so this would not be needed
                //        Refer: MapTests#mapOpPrevious
                prevPath = resetPath.copy();
                prevPath.setObjLink(resetPath.getObjLink());
                //System.out.println("RESET :: " + resetPath);
                if (info.direction == Direction.RIGHT) {
                    k = pairs.size() - i - 1;
                }
                else {
                    k = i;
                }
                //System.out.println("k = " + k);
                PairType pair = pairs.get(k); 
                //System.out.println("got pair");
                
                OperonValue pairOperonValue = null;
                if (info.pairs == false) {
                    pairOperonValue = (OperonValue) pair.getValue();
                }
                else {
                    ObjectType pairObj = new ObjectType(this.getStatement());
                    pairObj.addPair(pair);
                    pairOperonValue = pairObj;
                }
                
                ////:OFF:log.debug("    >> MAP OP k=["  + k + "], pairOperonValue :: "+ pairOperonValue); 
                //System.out.println("    >> MAP OP k=["  + k + "], pairOperonValue :: "+ pairOperonValue); 
                
                //System.out.println(">> Map, add attributes");
                
                String key = pair.getKey().substring(1, pair.getKey().length() - 1);

                //
                // ATTRIBUTES
                //
                if (prevPath != null) {
                    //System.out.println(">> Map, add attributes :: newPath1 :: " + prevPath);
                    Path prevCurrentPathCopy = (Path) prevPath.copy();
                    //System.out.println(">> Map, add attributes :: newPath COPY :: " + prevCurrentPathCopy);
                    PathPart kpp = new KeyPathPart(key);
                    prevCurrentPathCopy.getPathParts().add(kpp);
                    
                    if (prevPath.getObjLink() == null) {
                        prevCurrentPathCopy.setObjLink(jsonObj);
                    }
                    else {
                        prevCurrentPathCopy.setObjLink(prevPath.getObjLink());
                    }
                    this.getStatement().setCurrentPath(prevCurrentPathCopy);
                }
                else {
                    PathPart kpp = new KeyPathPart(key);
                    Path newPath = new Path(this.getStatement());
                    newPath.getPathParts().add(kpp);
                    newPath.setObjLink(jsonObj);
                    //System.out.println(">> Map, add attributes :: newPath :: " + newPath);
                    this.getStatement().setCurrentPath(newPath);
                }
                
                //System.out.println(">>>>>>> Map :: Object :: currentPath :: " + this.getStatement().getCurrentPath());
                
                //pairOperonValue.getStatement().getRuntimeValues().put("_", jsonObj);
                //System.out.println("MAP: " + pairOperonValue + ", parent=" + pairOperonValue.getParentKey());
                
                Node mapExpr = this.getMapExpr();
                
                //
                // Gather the "window" for evaluation:
                //
                if (info.windowSize > 1) {
                    if (window == null) {
                        window = new ObjectType(this.getStatement());
                    }
                    
                    if (currentWindowSize < info.windowSize) {
                        window.addPair(pair);
                        currentWindowSize += 1;
                        // System.out.println("add value and decide if continue");
                        if (currentWindowSize < info.windowSize && i + 1 < pairs.size()) {
                            //System.out.println(">> continue");
                            continue;
                        }
                        else {
                            //System.out.println(">> evaluate");
                        }
                    }
                    else if (i + 1 < pairs.size()) {
                        //System.out.println("gather next");
                        continue; // gather next item into window
                    }
                    
                    // allow to evaluate
                    //System.out.println("allow to evaluate: " + window);
                    this.getMapExpr().getStatement().setCurrentValue(window);
                }
                else {
                    this.getMapExpr().getStatement().setCurrentValue(pairOperonValue);
                }

                OperonValue mapExprResult = null;
                this.eagerEvaluateLetStatements();
                try {
                    mapExprResult = (OperonValue) mapExpr.evaluate();
                    if (window != null) {
                        if (info.slidingWindow == false) {
                            currentWindowSize = 0;
                            window = null;
                        }
                        else {
                            //System.out.println("window after evaluation and before left-stripping: " + window);
                            currentWindowSize -= 1;
                            ObjectType windowCopy = (ObjectType) JsonUtil.copyOperonValue((ObjectType) window);
                            windowCopy.getPairs().remove(0);
                            window = windowCopy;
                            //System.out.println("window after evaluation and left-stripping: " + window);
                        }
                    }
                } catch (BreakLoopException ble) {
                    break;
                } catch (ContinueLoopException cle) {
                    this.synchronizeState();
                    continue;
                }
                
                if (info.windowSize == 1) {
                    // 
                    // Check object-constraints, if they exist. 
                    // 
                    PairType resultPair = new PairType(this.getStatement()); 
                     
                    if (pair.getOperonValueConstraint() != null) { 
                        // Apply constraint-check: 
                        OperonValueConstraint c = pair.getOperonValueConstraint(); 
                        c.setValueToEvaluateAgainst((OperonValue) mapExprResult); 
                        OperonValue constraintResult = (OperonValue) c.evaluate(); 
                        if (constraintResult instanceof FalseType) {
                            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "CONSTRAINT", "VIOLATION", "Field: " + pair.getKey().substring(1, pair.getKey().length() - 1) + ", line #" + this.getSourceCodeLineNumber());
                        } 
                        resultPair.setOperonValueConstraint(c); 
                    } 
        
                    if (mapExprResult instanceof EmptyType) { 
                        this.synchronizeState();
                        continue;
                    }
                    resultPair.setPair(pair.getKey(), ((OperonValue) mapExprResult).copy()); 
                    resultObj.addPair(resultPair);
                }
                else {
                    // constraints are not applied here
                    resultArray.addValue(((OperonValue) mapExprResult).copy());
                }
                
                this.synchronizeState();
            }
        }
        
        else {
            //System.out.println("parallel was true");
            
            //
            // NOTES for parallel-mapping:
            //
            // - cannot assign position
            // - cannot use previous/next, because missing position
            // - cannot access values / functions outside of Map's scope
            // - windowing is not possible
            //
            
            // "indexify" the pairs first:
            if (info.direction == Direction.RIGHT) {
                Collections.reverse(pairs);
            }
            List<PairWithIndex> pairsWithIndices = new ArrayList<PairWithIndex>();
            // pairs = List<PairType>
            for (int i = 0; i < pairs.size(); i ++) {
                PairWithIndex pwi = new PairWithIndex();
                pwi.index = i;
                pwi.value = pairs.get(i);
                pairsWithIndices.add(pwi);
            }
            
            List<OperonValue> parallelResults = pairsWithIndices.parallelStream().map(
                pwi -> {
                    try {
                        PairType pair = (PairType) pwi.value;
                        int i = pwi.index;
                        ////:OFF:log.debug("    >> MAP OP i=["  + i + "], arrayValue :: "+ arrayValue); 
                        OperonValue pairOperonValue = (OperonValue) pair.getValue();

                        //pairOperonValue.getStatement().getRuntimeValues().put("_", jsonObj);
            
                        //
                        // Each forked process would use the same statement-context,
                        // and therefore one process would affect other process' statement,
                        // and thus the currentValue, causing wrong results.
                        //
                        // Therefore we must copy the mapExpr -node, so each would have their own statement.
                        //System.out.println("  Parallel Object Map");
                        //System.out.println("    Parallel Object Map :: expr=" + this.getMapExpr().getExpr());
                        boolean linkScope = false;
                        Node mapExprCopy = AbstractNode.deepCopyNode(this.getStatement(), this.getMapExpr(), linkScope); // AbstractNode.deepCopyNode(this.getMapExpr());
                        
                        mapExprCopy.getStatement().setCurrentValue(pairOperonValue);
                        
                        Node mapExprResult = mapExprCopy.evaluate();

                        // 
                        // Check object-constraints, if they exist. 
                        // 
                        PairType resultPair = new PairType(this.getStatement()); 
                         
                        if (pair.getOperonValueConstraint() != null) { 
                            // Apply constraint-check: 
                            OperonValueConstraint c = pair.getOperonValueConstraint(); 
                            c.setValueToEvaluateAgainst((OperonValue) mapExprResult); 
                            OperonValue constraintResult = (OperonValue) c.evaluate(); 
                            if (constraintResult instanceof FalseType) { 
                                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "CONSTRAINT", "VIOLATION", "Field: " + pair.getKey().substring(1, pair.getKey().length() - 1) + ", line #" + this.getSourceCodeLineNumber());
                            } 
                            resultPair.setOperonValueConstraint(c); 
                        } 
            
                        if (mapExprResult instanceof EmptyType) { 
                            this.synchronizeState();
                            return new EmptyType(this.getStatement());
                        } 
                        resultPair.setPair(pair.getKey(), ((OperonValue) mapExprResult).copy());
                        this.synchronizeState();
                        return resultPair;
                    } catch (Exception e) {
                        // Do smthg
                        System.err.println("Error: " + e.getMessage());
                        return null;
                    }
                }    
            ).collect(Collectors.toList());
            
            for (OperonValue resultValue : parallelResults) {
                if (resultValue instanceof PairType) {
                    resultObj.addPair((PairType) resultValue);
                }
                else {
                    continue; // Skip empty values
                }
            }
        }
        
        //System.out.println(">> result");
        //System.out.println("  >> " + resultObj);
        if (info.windowSize == 1) {
            return resultObj;
        }
        else {
            return resultArray;
        }
    }

    public StringType handleString(OperonValue currentValueCopy, Info info) throws OperonGenericException {
        StringType jsonString = (StringType) currentValueCopy; 
        ////:OFF:log.debug("    >> MAP OP lhsResult :: " + lhsResult); 
        char [] chars = jsonString.getJavaStringValue().toCharArray(); 
         
        StringBuilder sb = new StringBuilder();
        
        if (info.parallel == false) {
            StringBuilder windowString = null;
            int currentWindowSize = 0;
            int k = 0;
            for (int i = 0; i < chars.length; i ++) { 
                if (info.direction == Direction.RIGHT) {
                    k = chars.length - i - 1;
                }
                else {
                    k = i;
                }
                char c = chars[k]; 
                StringType jStr = new StringType(this.getStatement()); 
                jStr.setFromJavaString(Character.toString(c)); 

                //
                // Gather the "window" for evaluation:
                //
                if (info.windowSize > 1) {
                    if (windowString == null) {
                        windowString = new StringBuilder();
                    }
                    
                    if (currentWindowSize < info.windowSize) {
                        windowString.append(c);
                        currentWindowSize += 1;
                        //System.out.println("add value and decide if continue");
                        if (currentWindowSize < info.windowSize && i + 1 < chars.length) {
                            //System.out.println(">> continue");
                            continue;
                        }
                        else {
                            //System.out.println(">> evaluate");
                        }
                    }
                    else if (i + 1 < chars.length) {
                        //System.out.println("gather next");
                        continue; // gather next item into window
                    }
                    
                    // allow to evaluate
                    //System.out.println("allow to evaluate: " + window);
                    StringType window = new StringType(this.getStatement());
                    window.setFromJavaString(windowString.toString());
                    this.getMapExpr().getStatement().setCurrentValue(window);
                }
                else {
                    this.getMapExpr().getStatement().setCurrentValue((StringType) jStr);
                }

                Node mapExprResult = null;
                this.eagerEvaluateLetStatements();
                try {
                    mapExprResult = this.getMapExpr().evaluate();
                    if (windowString != null) {
                        if (info.slidingWindow == false) {
                            currentWindowSize = 0;
                            windowString = null;
                        }
                        else {
                            currentWindowSize -= 1;
                            windowString = windowString.deleteCharAt(0);
                        }
                    }
                } catch (BreakLoopException ble) {
                    break;
                } catch (ContinueLoopException cle) {
                    this.synchronizeState();
                    continue;
                }
                
                if (mapExprResult instanceof EmptyType) { 
                    this.synchronizeState();
                    continue; 
                } 
                sb.append( ((StringType) mapExprResult).getJavaStringValue() );
                this.synchronizeState();
            }
        }
        else {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "PARALLEL", "TYPE", "String cannot be mapped in parallel, line #" + this.getSourceCodeLineNumber());
        }
        ////:OFF:log.debug("RESULT >> " + resultString);
        StringType result = new StringType(this.getStatement()); 
        result.setFromJavaString(sb.toString()); 
        return result;
    }

    //
    // NOTE: does not support "direction" -option.
    //
    public OperonValue handleStream(StreamValue it, Info info) throws OperonGenericException {
        //System.out.println("Map :: handleStream");
        Node mapExpression = this.getMapExpr();
        StreamValueWrapper svw = it.getStreamValueWrapper();
        if (svw == null) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "Map", "Stream", "Stream is not correctly wrapped, line #" + this.getSourceCodeLineNumber());
        }
        
        ArrayType resultArray = new ArrayType(this.getStatement());
        
        if (svw.supportsJson()) {
            //:OFF:log.debug("Stream supportsJson");
            OperonValue nextValue = null;
            int i = 0;
            do {
                i += 1;
                nextValue = svw.readJson();
                if (nextValue instanceof EndValueType) {
                    break;
                }

                //
                // ATTRIBUTES
                //
                PathPart pp = new PosPathPart(i);
                Path newPath = new Path(this.getStatement());
                newPath.getPathParts().add(pp);
                newPath.setObjLink(nextValue);
                //System.out.println(">> Map stream, add attributes :: newPath :: " + newPath);
                this.getStatement().setCurrentPath(newPath);
    
                this.getStatement().setCurrentValue(nextValue); // Set CV also for currentStatement because lfr sets cv from previous-stmt
                mapExpression.getStatement().setCurrentValue(nextValue);
                Node mapExprResult = null;
                this.eagerEvaluateLetStatements();
                try {
                    mapExprResult = mapExpression.evaluate();
                } catch (BreakLoopException ble) {
                    break;
                } catch (ContinueLoopException cle) {
                    this.synchronizeState();
                    continue;
                }
                 
                if (mapExprResult instanceof EmptyType) { 
                    this.synchronizeState();
                    continue; 
                }
                //System.out.println("  >> Map res: " + mapExprResult);
                resultArray.addValue(((OperonValue) mapExprResult).copy()); // Add the deep-copy instead of the value-reference to prevent modifation from memory.
                this.synchronizeState();
            } while (nextValue != null && nextValue instanceof EndValueType == false);
        }
        
        else {
            System.out.println("Map: Stream does NOT support Json");
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "Map", "Stream", "Stream is not JSON-iterable, line #" + this.getSourceCodeLineNumber());
        }
        return resultArray;
    }

    //
    // For non-iterable (e.g. number), the Map -operation must specify the mapping range.
    //
    public OperonValue handleNonIterable(OperonValue currentValueCopy) throws OperonGenericException {
        // TODO: implement logic (if required)
        return null;
    }
    
    public void setMapExpr(Node mExpr) {
        this.mapExpr = mExpr;
    }
    
    public Node getMapExpr() {
        return this.mapExpr;
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

    public Info resolveConfigs(Statement stmt) throws OperonGenericException {
        Info info = new Info();
        
        if (this.configs == null) {
            return info;
        }
        
        OperonValue currentValueCopy = stmt.getCurrentValue().copy();
        
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
                case "\"retainlast\"":
                    NumberType retainLastValue = (NumberType) pair.getEvaluatedValue();
                    info.retainLast = (int) retainLastValue.getDoubleValue();
                    break;
                case "\"retainfirst\"":
                    NumberType retainFirstValue = (NumberType) pair.getEvaluatedValue();
                    info.retainFirst = (int) retainFirstValue.getDoubleValue();
                    break;
                case "\"windowsize\"":
                    NumberType windowSizeValue = (NumberType) pair.getEvaluatedValue();
                    info.windowSize = (int) windowSizeValue.getDoubleValue();
                    break;
                case "\"slidingwindow\"":
                    OperonValue slidingWindowValue = pair.getEvaluatedValue();
                    if (slidingWindowValue instanceof FalseType) {
                        info.slidingWindow = false;
                    }
                    else {
                        info.slidingWindow = true;
                    }
                    break;
                case "\"pairs\"":
                    OperonValue pairsValue = pair.getEvaluatedValue();
                    if (pairsValue instanceof FalseType) {
                        info.pairs = false;
                    }
                    else {
                        info.pairs = true;
                    }
                    break;
                case "\"direction\"":
                    String directionValue = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    try {
                        info.direction = Direction.valueOf(directionValue.toUpperCase());
                    } catch(Exception e) {
                        System.err.println("ERROR SIGNAL: direction-property");
                    }
                    break;
                default:
                    break;
            }
        }
        
        stmt.setCurrentValue(currentValueCopy);
        return info;
    }

    private class Info {
        public boolean parallel = false;
        public int retainLast = -1; // how many last results to retain when mapping against number. null = retain all.
        public int retainFirst = -1; // how many first results to retain when mapping against number. null = retain all.
        public int windowSize = 1; // how many items are gathered for evaluation at a time
        
        // If set, then the items defined by window size (must be > 1) are gathered so that the previous window's first item is dropped for the next evaluation
        // Sliding ends when the boundary is first hit.
        public boolean slidingWindow = false;
        
        //
        // When true, then Map sets the whole pair (as Object) as currentValue.
        // 
        public boolean pairs = false;
        
        // TODO: boolean explodeResult --> if iteration's result is an array, that would be flatten and added into main-result (for arrays).
        // TODO: optional reduce-function (reduces in-time)?
        
        //
        // from which direction to start the mapping? LEFT = from left to right.
        // NOTE: the final result reflects this, so that [1,2,3] Map {"direction": "right"} @; will become [3,2,1]
        //
        public Direction direction = Direction.LEFT;
    }
    
    //
    // Used to add the index when doing parallel-operations
    //
    class PairWithIndex {
        int index = -1;
        OperonValue value;
    }
    
    private enum Direction {
        LEFT("left"), RIGHT("right");
        private String direction = "left";
        Direction(String d) {
            this.direction = d;
        }
        public String getDirection() { return this.direction; }
    }
}