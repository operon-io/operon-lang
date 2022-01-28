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
import io.operon.runner.processor.function.core.path.*;
import io.operon.runner.node.type.*;
import io.operon.runner.model.pathmatch.*;
import io.operon.runner.model.path.*;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

//
// Where -expr converts the input Object or Array into Paths,
// and uses the "path-matching expr" or "Path(): predicate-expr"
// to compare the path.
//
// If match, then returns this path as a part of the result-array.
//
/*
    $:[
        {foo: "Bar"},
        1,
        null
    ]
    Select $
        Where ~[1::].foo:
            @ = "Bar"
        End:Where
*/
public class Where extends AbstractNode implements Node, java.io.Serializable {
    private static Logger log = LogManager.getLogger(Where.class);
    
    private Node whereExpr;
    private PathMatches pathMatches;

    private boolean debug = false; // uncomment //debug -statements to use this
    private Path currentPath;
    private int currentDepth = 0;
    private OperonValue rootValue; // Array or Object
    private List<Path> matchedPaths;
    
    private Node configs;
    private Info resolvedConfigs;
    
    public Where(Statement stmnt) {
        super(stmnt);
        this.matchedPaths = new ArrayList<Path>();
    }

    public void setWhereExpr(Node wExpr) {
        this.whereExpr = wExpr;
    }
    
    public Node getWhereExpr() {
        return this.whereExpr;
    }

    public void setPathMatches(PathMatches pm) {
        this.pathMatches = pm;
    }
    
    public PathMatches getPathMatches() {
        return this.pathMatches;
    }

    public ArrayType evaluate() throws OperonGenericException {
        log.debug("ENTER Where.evaluate()");
        //debug("Where.evaluate()");
        OperonValue currentValue = this.getStatement().getCurrentValue();
        //debug("Where.evaluate() :: cv");
        
        //debug("Where.evaluate() stmt");
        
        this.setRootValue(currentValue);
        //log.debug("    >> @: " + value.toString());

        Info info = this.resolveConfigs(this.getStatement());

        this.currentPath = new Path(this.getStatement());
        
        //
        // This result is not used?
        //
        Node resultTraverse = this.evaluateSelector(currentValue, info);
        
        //debug("Where.evaluate() done");
        //debug("    Matched paths: " + this.matchedPaths);
        ArrayType result = new ArrayType(this.getStatement());
        for (Path pathResult : this.matchedPaths) {
            result.addValue(pathResult);
        }
        return result;
    }

    private ArrayType evaluateSelector(OperonValue value, Info info) throws OperonGenericException {
        OperonValue evaluatedValue = value.evaluate();
        if (evaluatedValue instanceof ObjectType) {
            //log.debug("EXIT Where.evaluate() obj");
            return evaluateObj( (ObjectType) evaluatedValue, info );
        }
        
        else if (evaluatedValue instanceof ArrayType) {
            //log.debug("EXIT Where.evaluate() array");
            return evaluateArray( (ArrayType) evaluatedValue, info );
        }
        
        //log.debug("Where: cannot apply. Wrong type: " + evaluatedValue);
        ErrorUtil.createErrorValueAndThrow(this.getStatement(), "WHERE", "TYPE", "Cannot apply. Wrong type.");
        return null;
    }

    //
    // CurrentValue was Object
    //
    private ArrayType evaluateObj(ObjectType obj, Info info) throws OperonGenericException {
        ArrayType result = new ArrayType(this.getStatement());
        
        //debug("OBJ START PATH PART = " + this.currentPath);
        //debug("   CurrentPath size = " + this.currentPath.getPathParts().size());
        
        int startPathSize = this.currentPath.getPathParts().size();
        
        //
        // Scan through the struct, and evaluate the key for each PairType
        //
        for (int i = 0; i < obj.getPairs().size(); i ++) {
            PairType pair = obj.getPairs().get(i);
            //log.debug("    Obj key :: " + pair.getKey());

            PathPart pp = new KeyPathPart(pair.getKey().substring(1, pair.getKey().length() - 1));
            //this.currentPath.setValueLink(pair.getValue());
            //this.currentPath.setObjLink(this.getRootValue());
            this.currentPath.addPathPart(pp);
            this.currentDepth += 1;
            
            //debug("  - Obj CurrentPath = " + this.currentPath + ", CurrentDepth = " + this.currentDepth);
            
            PathMatch pathMatch = this.getPathMatches().getPathMatch();
            
            //debug("   - PathMatch :: " + pathMatch);
            OperonValue isMatch = PathMatches.matchPath(obj.getStatement(), this.currentPath, pathMatch);
            
            //debug("    Is match: " + isMatch);
            if (isMatch instanceof TrueType) {
                //debug("    - Match: adding to matchedPaths");
                this.matchedPaths.add(this.currentPath.copy());
            }

            if (info.maxResults != null && matchedPaths.size() >= info.maxResults) {
                break;
            }

            OperonValue subObj = pair.getEvaluatedValue();
            ArrayType subResult = null;
            //boolean recursed = false;
            
            if (((subObj instanceof ObjectType) || (subObj instanceof ArrayType))) {
                if (info.maxDepth == null || currentDepth < info.maxDepth) {
                    //recursed = true;
                    subResult = (ArrayType) evaluateSelector(subObj, info);
                    if (subResult.getValues().size() > 0) {
                        //
                        // Recursive call creates an array, which must be flattened:
                        //
                        for (Node n : subResult.getValues()) {
                            if (subResult != null) {
                                result.addValue(n);
                            }
                        }
                    }
                    else {
    
                    }
                }
            }
            
            //
            // TODO: do not backtrack after last iteration!
            //
            if (i < obj.getPairs().size() - 1 && this.currentPath.getPathParts().size() > 0 && 
                    this.currentPath.getPathParts().get(this.currentPath.getPathParts().size() - 1) instanceof KeyPathPart) {
                //debug("  - Object :: backtracking from obj pair");
                this.currentPath.removeLastPathPart();
                this.currentDepth -= 1;
                //debug("    - After backtrack :: Obj CurrentPath = " + this.currentPath + ", CurrentDepth = " + this.currentDepth);
            }
        }
        
        //debug("Obj ENDed: will now remove last path part");
        //debug("  - PathSize=" + this.currentPath.getPathParts().size());
        //debug("  - currentPath: " + this.currentPath);
        //debug("  - currentDepth: " + this.currentDepth);
        
        int endPathSize = this.currentPath.getPathParts().size();
        
        //
        // FIXME: in certain case this does one removal too much!
        //
        
        //
        // Remove the last path-part only if the path was not returned to
        // the original size (these are mutually exlusive operations).
        //
        boolean rmLast = true;
        //debug("  - Path size when this routine started: startPathSize=" + startPathSize + " :: size after this routine: endPathSize=" + endPathSize);
        for (int i = 0; i < endPathSize - startPathSize; i ++) {
            //debug("    - Sizes differ: will chop back to start-path size");
            this.currentPath.removeLastPathPart();
            rmLast = false;
        }
        
        if (rmLast == true) {
            //debug("  - Will now remove last part");
            this.currentPath.removeLastPathPart();
            this.currentDepth -= 1;
            //debug("Obj -data after removing last path-part:");
            //debug("  - PathSize=" + this.currentPath.getPathParts().size());
            //debug("  - currentPath: " + this.currentPath);
            //debug("  - currentDepth: " + this.currentDepth);
        }
        else {
            //debug("Obj -data after chopping back to start-path size:");
            //debug("  - PathSize=" + this.currentPath.getPathParts().size());
            //debug("  - currentPath: " + this.currentPath);
            //debug("  - currentDepth: " + this.currentDepth);
        }

        return result;
    }
    
    //
    // CurrentValue is Array
    //
    private ArrayType evaluateArray(ArrayType array, Info info) throws OperonGenericException {
        //log.debug("Accessing array of objects");
        //debug("Accessing array");
        
        ArrayType resultArray = new ArrayType(this.getStatement());
        List<Node> arrayValues = array.getValues();
        
        for (int i = 0; i < arrayValues.size(); i ++) {
            Node arrayNode = arrayValues.get(i);
            //log.debug("    >> Looping: " + i);
            //debug("Array Looping i=" + i);
            
            OperonValue arrayNodeEvaluated = arrayNode.evaluate();
            
            PathPart pp = new PosPathPart(i + 1);
            //this.currentPath.setValueLink(arrayNodeEvaluated);
            //this.currentPath.setObjLink(this.getRootValue());
            this.currentPath.addPathPart(pp);
            
            //debug("ARRAY CurrentPath = " + this.currentPath + ", CurrentDepth = " + this.currentDepth);
            
            PathMatch pathMatch = this.getPathMatches().getPathMatch();
            
            //debug("   - PathMatch :: " + pathMatch);
            OperonValue isMatch = PathMatches.matchPath(array.getStatement(), this.currentPath, pathMatch);
            
            //debug("    Is match: " + isMatch);
            if (isMatch instanceof TrueType /*&& truncated == false*/) {
                this.matchedPaths.add(this.currentPath.copy());
            }
            
            if (info.maxResults != null && matchedPaths.size() >= info.maxResults) {
                break;
            }
            
            if (arrayNodeEvaluated instanceof ObjectType) {
                if (info.maxDepth != null && currentDepth >= info.maxDepth) {
                    System.out.println("ARR: maxDepth, currentDepth = " + currentDepth);
                    continue;
                }
                ArrayType arrayNodeResult = (ArrayType) evaluateObj((ObjectType) arrayNodeEvaluated, info);
                if (arrayNodeResult.getValues().size() > 0) {
                    //
                    // Recursive call creates an array, which must be flattened:
                    //
                    for (Node n : arrayNodeResult.getValues()) {
                        resultArray.addValue(n);
                    }
                }
                // ADDED while fixing:
                this.currentPath.removeLastPathPart();
                this.currentDepth -= 1;
            }
            
            // Added this so we can traverse nested arrays [[]]
            else if (arrayNodeEvaluated instanceof ArrayType) {
                if (info.maxDepth != null && currentDepth >= info.maxDepth) {
                    System.out.println("ARR: maxDepth, currentDepth = " + currentDepth);
                    continue;
                }
                //debug("Array recurse");
                ArrayType arrayNodeResult = (ArrayType) evaluateArray((ArrayType) arrayNodeEvaluated, info);
                if (arrayNodeResult.getValues().size() > 0) {
                    //
                    // Recursive call creates an array, which must be flattened:
                    //
                    for (Node n : arrayNodeResult.getValues()) {
                        resultArray.addValue(n);
                    }
                }
                //debug("Array :: tracking back");
                //debug("  - ARRAY CurrentPath = " + this.currentPath + ", CurrentDepth = " + this.currentDepth);
                this.currentPath.removeLastPathPart();
                this.currentDepth -= 1;
            }
            
            else {
                //debug("Array :: last tracking back");
                //debug("  - ARRAY CurrentPath = " + this.currentPath + ", CurrentDepth = " + this.currentDepth);
                this.currentPath.removeLastPathPart();
            }
            
        }
        
        return resultArray;
    }
    
    private void debug(String value) {
        if (this.debug == true) {
            System.out.println(value);
        }
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
        if (this.configs == null && this.getResolvedConfigs() == null) {
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
                    info.maxResults = (int) maxResultsValue.getDoubleValue();
                    break;
                case "\"maxdepth\"":
                    NumberType maxDepthValue = (NumberType) pair.getEvaluatedValue();
                    info.maxDepth = (int) maxDepthValue.getDoubleValue();
                    break;
                case "\"skippaths\"":
                    ArrayType skipPathsValue = (ArrayType) pair.getEvaluatedValue();
                    info.skipPaths = skipPathsValue.getValues();
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
        public List<Node> skipPaths = null;
    }

}