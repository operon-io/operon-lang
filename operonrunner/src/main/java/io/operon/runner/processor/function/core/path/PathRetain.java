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
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

//
// Input: Array of Paths
//
//  - Paths will be retained for the linked object,
//    other paths will be removed.
//
public class PathRetain extends BaseArity1 implements Node, Arity1 {
    
    public PathRetain(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "retain", "paths");
        this.setNs(Namespaces.PATH);
    }

    public OperonValue evaluate() throws OperonGenericException {
        try {
            if (this.getParam1() != null) {
                //
                // Input: Object or Array
                // Param1: array of paths to be retained in the input (object or array)
                //
                OperonValue currentValue = this.getStatement().getCurrentValue();
                ArrayType retainPaths = (ArrayType) this.getParam1().evaluate();
                return this.doRetain(retainPaths, currentValue);
            }
            
            else {
                //System.out.println("path:retain() evaluate");
                ArrayType pathArray = (ArrayType) this.getStatement().getCurrentValue();
                
                if (pathArray.getValues().size() > 0) {
                    return this.doRetain(pathArray, null);
                }
                
                else {
                    EmptyType empty = new EmptyType(this.getStatement());
                    return empty;
                }
            }
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    private OperonValue doRetain(ArrayType pathArray, OperonValue objLink) throws OperonGenericException {
        if (objLink == null) {
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
        List<Path> keepPaths = new ArrayList<Path>();
        
        for (int i = 0; i < allPathNodes.size(); i ++) {
            Path a = (Path) allPathNodes.get(i).evaluate();
            //System.out.println("  - path a :: " + a);
            boolean found = false;
            for (int j = 0; j < retainPathNodes.size(); j ++) {
                Path b = (Path) retainPathNodes.get(j).evaluate();
                //System.out.println("  - path b :: " + b);
                if (a.equals(b)) {
                    found = true;
                    break;
                }
            }
            if (found == false) {
                removePaths.add(a);
                //System.out.println("PathRetain :: removePath :: " + a);
            }
            else {
                keepPaths.add(a);
            }
        }
        
        //System.out.println("PathRetain :: removePaths :: " + removePaths);
        //System.out.println("PathRetain :: keepPaths :: " + keepPaths);
        
        //
        // If removedPaths' entry contains some root-part of the keep entries,
        // then the latter will also be removed, which must be prevented
        //
        // We'll use path:commonSubPath($a, $b) to find paths that should not be removed.
        
        List<Path> finalRemovePaths = new ArrayList<Path>();
        
        //System.out.println("PathRetain :: removePaths :: " + removePaths.size());
        //System.out.println("PathRetain :: keepPaths :: " + keepPaths.size());
        
        for (int i = 0; i < removePaths.size(); i ++) {
            Path removePath = (Path) removePaths.get(i);
            boolean foundSubPath = false;
            for (int j = 0; j < keepPaths.size(); j ++) {
                Path keepPath = (Path) keepPaths.get(j).evaluate();
                //System.out.println("  - commonSubPath: " + removePath + ", " + keepPath);
                Path p = PathCommonSubPath.commonSubPath(removePath, keepPath);
                //System.out.println("    -> commonSubPath: " + p + ", keepPath=" + keepPath);
                if (p.getPathParts().size() > 0 && p.getPathParts().size() < keepPath.getPathParts().size()) {
                    if (removePath.getPathParts().size() != keepPath.getPathParts().size()) {
                        foundSubPath = true;
                        //System.out.println("  - FOUND commonSubPath");
                        break;
                    }
                }
            }
            if (foundSubPath == false) {
                finalRemovePaths.add(removePath);
            }
        }
        
        //
        // create Update-expr, which sets value as empty for finalRemovePaths
        //
        ArrayType removePathsArray = new ArrayType(this.getStatement());
        removePathsArray.getValues().addAll(finalRemovePaths);
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