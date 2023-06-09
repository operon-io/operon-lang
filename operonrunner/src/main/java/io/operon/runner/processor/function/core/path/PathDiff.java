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
import io.operon.runner.processor.function.BaseArity2;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

//
// Compares two Objects / Arrays.
//   - outputs the diff as Array of Paths
//
public class PathDiff extends BaseArity2 implements Node, Arity2 {
    
    public PathDiff(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam2AsOptional(true);
        this.setParams(params, "diff", "value", "value2");
        this.setNs(Namespaces.PATH);
    }

    public ArrayType evaluate() throws OperonGenericException {
        try {
            ArrayType result = null;
            //System.out.println(">> 0");
            if (this.getParam2() == null) {
                //System.out.println(">> 0.1");
                OperonValue value1 = this.getStatement().getCurrentValue().copy();
                OperonValue value2 = this.getParam1().evaluate();
                //System.out.println(">> 0.2: value2 :: " + value2);
                
                //System.out.println(">> 0.3: value1 :: " + value1);
                result = this.getDiff(value1, value2);
            }
            
            else {
                OperonValue value2 = this.getParam2().evaluate();
                OperonValue value1 = this.getParam1().evaluate();
                result = this.getDiff(value1, value2);
            }
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    private ArrayType getDiff(OperonValue value1, OperonValue value2) throws OperonGenericException {
        //System.out.println(">> 1: " + value1);
        //
        // expr for: 'objLink Where ~=?* ', which will return all paths of the Object or Array.
        //
        this.getStatement().setCurrentValue(value1);
        Where w = new Where(this.getStatement());
        PathMatch pmExpr = new PathMatch(this.getStatement());
        PathMatchPart anyMatch = new AnyNoneOrMorePathMatchPart(); // '?*'
        pmExpr.getPathMatchParts().add(anyMatch);
        PathMatches pm = new PathMatches(this.getStatement());
        pm.setPathMatch(pmExpr);
        w.setPathMatches(pm);
        ArrayType allPathsValue1 = (ArrayType) w.evaluate();
        
        //System.out.println("  AllPaths1 :: " + allPathsValue1);
        
        List<Node> allPathNodes = allPathsValue1.getValues();
        
        this.getStatement().setCurrentValue(value2);
        //System.out.println(">> 2: " + value2);
        Where w2 = new Where(this.getStatement());
        w2.setPathMatches(pm);
        
        ArrayType allPathsValue2 = (ArrayType) w2.evaluate();
        //System.out.println("  AllPaths2 :: " + allPathsValue2);
        
        List<Node> allPathNodes2 = allPathsValue2.getValues();
        
        List<Node> diffList = new ArrayList<Node>();
        
        if (allPathNodes.size() >= allPathNodes2.size()) {
            //System.out.println(">> 3");
            for (int i = 0; i < allPathNodes.size(); i ++) {
                //System.out.println("===============");
                Path p1 = (Path) allPathNodes.get(i).evaluate();
                //System.out.println(">> 3.1 p1: " + p1);
                boolean found = false;
                for (int j = 0; j < allPathNodes2.size(); j ++) {
                    Path p2 = (Path) allPathNodes2.get(j).evaluate();
                    //System.out.println("   - p2: " + p2);
                    if (p1.equals(p2)) {
                        found = true;
                        //System.out.println("    - match");
                        break;
                    }
                }
                if (found == false) {
                    //System.out.println("  - not found, adding : " + p1);
                    diffList.add(p1);
                }
            }
        }
        
        else {
            for (int i = 0; i < allPathNodes2.size(); i ++) {
                Path p1 = (Path) allPathNodes2.get(i).evaluate();
                boolean found = false;
                for (int j = 0; j < allPathNodes.size(); j ++) {
                    Path p2 = (Path) allPathNodes.get(j).evaluate();
                    if (p1.equals(p2)) {
                        found = true;
                        break;
                    }
                }
                if (found == false) {
                    diffList.add(p1);
                }
            }
        }
        ArrayType result = new ArrayType(this.getStatement());
        result.setValues(diffList);
        return result;
    }
}