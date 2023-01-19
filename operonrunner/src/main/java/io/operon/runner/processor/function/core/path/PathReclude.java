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

package io.operon.runner.processor.function.core.path;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.Where;
import io.operon.runner.node.PathMatches;
import io.operon.runner.node.UpdateArray;
import io.operon.runner.node.type.*;
import io.operon.runner.model.path.*;
import io.operon.runner.model.pathmatch.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

//
// Input: Array of Paths
//
//  - Paths will be removed from the linked object
//
public class PathReclude extends BaseArity1 implements Node, Arity1 {
    
    public PathReclude(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        //this.setFunctionName("reclude");
        this.setParams(params, "reclude", "paths");
    }

    public OperonValue evaluate() throws OperonGenericException {
        try {
            if (this.getParam1() != null) {
                //
                // Input: Object or Array
                // Param1: array of paths to be recluded from the input (object or array)
                //
                OperonValue currentValue = this.getStatement().getCurrentValue();
                ArrayType removePaths = (ArrayType) this.getParam1().evaluate();
                return this.doReclude(removePaths, currentValue);
            }
            
            else {
                //
                // Input: array of paths to be recluded from the path-linked root-obj (Object or Array)
                //
                //System.out.println("path:reclude() evaluate");
                ArrayType pathArray = (ArrayType) this.getStatement().getCurrentValue();
                if (pathArray.getValues().size() > 0) {
                    return this.doReclude(pathArray, null);
                }
                
                else {
                    //System.out.println("path:reclude() :: pathsArray.size() == 0");
                    // Return empty, because we cannot find the linked object.
                    EmptyType empty = new EmptyType(this.getStatement());
                    return empty;
                }
            }
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    private OperonValue doReclude(ArrayType pathArray, OperonValue objLink) throws OperonGenericException {
        if (objLink == null) {
            // Take the objLink from the first Path:
            Path p0 = (Path) pathArray.getValues().get(0).evaluate();
            objLink = p0.getObjLink();
            if (objLink == null) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), "No root value bound to path.");
            }
        }
        
        this.getStatement().setCurrentValue(objLink);
        
        //
        // expr for: 'objLink Where ~=?* ', which will return all paths of the Object or Array (objLink).
        //
        Where w = new Where(this.getStatement());
        PathMatch pmExpr = new PathMatch(this.getStatement());
        PathMatchPart anyMatch = new AnyNoneOrMorePathMatchPart(); // '?*'
        pmExpr.getPathMatchParts().add(anyMatch);
        PathMatches pm = new PathMatches(this.getStatement());
        pm.setPathMatch(pmExpr);
        w.setPathMatches(pm);
        
        ArrayType allPaths = (ArrayType) w.evaluate();
        //System.out.println("  AllPaths :: " + allPaths);
        
        List<Node> allPathNodes = allPaths.getValues();
        List<Node> retainPathNodes = pathArray.getValues();
        List<Path> removePaths = new ArrayList<Path>();
        
        for (int i = 0; i < allPathNodes.size(); i ++) {
            Path a = (Path) allPathNodes.get(i).evaluate();
            //System.out.println("  - path a :: " + a);
            for (int j = 0; j < retainPathNodes.size(); j ++) {
                Path b = (Path) retainPathNodes.get(j).evaluate();
                //System.out.println("  - path b :: " + b);
                if (a.equals(b)) {
                    removePaths.add(a);
                }
            }
        }
        
        //System.out.println("PathRetain :: removePaths :: " + removePaths);
        
        //
        // create Update-expr, which sets value as empty for removePaths
        //
        ArrayType removePathsArray = new ArrayType(this.getStatement());
        removePathsArray.getValues().addAll(removePaths);
        UpdateArray ua = new UpdateArray(this.getStatement());
        EmptyType empty = new EmptyType(this.getStatement());
        
        ua.setUpdatePathsExpr(removePathsArray);
        ua.setUpdateValuesExpr(empty);
        
        this.getStatement().setCurrentValue(objLink);
        OperonValue result = ua.evaluate();
        //System.out.println("PathRetain :: result :: " + result);
        return result;
    }

}