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
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.LambdaFunctionRef;
import io.operon.runner.node.type.*;
import io.operon.runner.model.path.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class PathCreate extends BaseArity1 implements Node, Arity1 {
    
    public PathCreate(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "create", "path");
    }

    public Path evaluate() throws OperonGenericException {
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            OperonValue pathValue = (OperonValue) this.getParam1().evaluate();
            
            StringType pathStr = null;
            
            if (pathValue instanceof StringType) {
                pathStr = (StringType) pathValue;
            }
            
            if (pathValue instanceof FunctionRef) {
                FunctionRef pathFnRef = (FunctionRef) pathValue;
                pathFnRef.getParams().clear();
                pathFnRef.setCurrentValueForFunction(currentValue);
                pathStr = (StringType) pathFnRef.invoke();
            }
            
            else if (pathValue instanceof LambdaFunctionRef) {
                LambdaFunctionRef pathFnRef = (LambdaFunctionRef) pathValue;
                pathFnRef.getParams().clear();
                pathFnRef.setCurrentValueForFunction(currentValue);
                pathStr = (StringType) pathFnRef.invoke();
            }
            
            Path resultPath = new Path(this.getStatement());
            String pathJavaStr = pathStr.getJavaStringValue();
            List<PathPart> pathParts = PathCreate.constructPathParts(pathJavaStr);

            //System.out.println("Constructed path=" + pathParts);
            resultPath.setPathParts(pathParts);
            
            return resultPath;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    public static List<PathPart> constructPathParts(String pathJavaStr) {
        List<PathPart> pathParts = new ArrayList<PathPart>();
        StringBuilder currentPathPart = new StringBuilder();
        boolean objMode = false; // false=ArrayMode, true=ObjecMode
        for (int i = 0; i < pathJavaStr.length(); i ++) {
            char currentChar = pathJavaStr.charAt(i);
            if (currentChar == '.') {
                PathPart pp = PathCreate.yieldPathPart(currentPathPart, objMode);
                if (pp != null) {pathParts.add(pp);}
                objMode = true;
                currentPathPart.setLength(0);
            }
            else if (currentChar == '[') {
                PathPart pp = PathCreate.yieldPathPart(currentPathPart, objMode);
                if (pp != null) {pathParts.add(pp);}
                objMode = false;
                currentPathPart.setLength(0);
            }
            else if (currentChar == ']') {
                // skip
            }
            else {
                currentPathPart.append(currentChar);
            }
        }
        PathPart pp = PathCreate.yieldPathPart(currentPathPart, objMode);
        if (pp != null) {pathParts.add(pp);}
        return pathParts;
    }

    private static PathPart yieldPathPart(StringBuilder currentPathPart, boolean objMode) {
        // yield
        if (currentPathPart.toString().isEmpty() == false) {
            if (objMode == false) {
                // yield array-position
                int pos = (Integer.parseInt(currentPathPart.toString()));
                PathPart ppp = new PosPathPart(pos);
                return ppp;
            }
            else {
                // yield object-key
                String key = currentPathPart.toString();
                PathPart kpp = new KeyPathPart(key);
                return kpp;
            }
        }
        return null;
    }

}