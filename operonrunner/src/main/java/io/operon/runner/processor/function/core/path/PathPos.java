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
import io.operon.runner.node.type.*;
import io.operon.runner.model.path.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

//
// When last-path part is PosPathPart, then path is not required to be linked with root-obj.
// When Path has last path-part as Key, then path must be linked with root-obj.
//
//
//
public class PathPos extends BaseArity0 implements Node, Arity0 {
    
    public PathPos(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("pos");
        this.setNs(Namespaces.PATH);
    }

    public OperonValue evaluate() throws OperonGenericException {
        try {
            //System.out.println("path:pos() evaluate");
            OperonValue currentValue = this.getStatement().getCurrentValue();
            Path path = null;
            
            if (currentValue instanceof Path) {
                path = (Path) currentValue;
                //System.out.println("  - currentPath1 :: " + path);
            }
            else {
                path = this.getStatement().getCurrentPath();
                //System.out.println("  - currentPath2 :: " + path);
            }
            
            //System.out.println(">> 1: currentPath=" + path);
            
            OperonValue pathRoot = path.getObjLink();
            Path resultPath = path.copy();
            OperonValue pathValue = null;
            
            //System.out.println(">> 2");
            //System.out.println("path:pos() :: pathParts size: " + path.getPathParts().size());
            
            PathPart lastPathPart = null;
            if (path.getPathParts().size() > 0) {
                lastPathPart = path.getPathParts().get(path.getPathParts().size() - 1);
            }
            else {
                // cannot deduce pos from empty path.
                return new NullType(this.getStatement());
            }
            Path parentPath = PathParentPath.getParentPath(path);
            //System.out.println(">> 3");
            if (lastPathPart instanceof PosPathPart) {
                //System.out.println(">> 4");
                int pos = ((PosPathPart) lastPathPart).getPos();
                //System.out.println(">> 4 :: " + pos);
                return NumberType.create(this.getStatement(), new Double(pos), (byte) 0);
            }
            else {
                String key = ((KeyPathPart) lastPathPart).getKey();
                if (parentPath.getPathParts().size() > 0) {
                    pathValue = PathValue.get(pathRoot, parentPath);
                }
                else {
                    pathValue = pathRoot;
                }
                //System.out.println(">>  : pathValue=" + pathValue);
                ObjectType obj = (ObjectType) pathValue.evaluate();
                boolean keyFound = false;
                String nextKey = null;
                for (int i = 0; i < obj.getPairs().size(); i ++) {
                    PairType p = obj.getPairs().get(i);
                    String pairKey = p.getKey().substring(1, p.getKey().length() - 1);
                    if (pairKey.equals(key)) {
                        //System.out.println(">> 5 :: " + (i + 1));
                        return NumberType.create(this.getStatement(), new Double(i + 1), (byte) 0);
                    }
                }
                return new NullType(this.getStatement());
            }
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}