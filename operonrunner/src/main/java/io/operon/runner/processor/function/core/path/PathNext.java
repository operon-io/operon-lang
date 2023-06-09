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
// This returns the next path or Null when no next path is available.
//
// NOTE: Requires that rootObj has been linked to the Path.
//       (Otherwise it would be impossible to tell next Obj-key or boundaries for Array).
//
//
// NOTE: current implementation considers the "next" only from the same depth,
//       which is a sensible default-behavior.
//
// TODO: consider to take options which define how to actually traverse the array/object.
//       Specifically:
//         - is next only defined from the same depth?
//         - is next defined also from the deeper depth?
//           - if from deeper, shall we recurse eagerly or lazyly?
//         - is next defined also from shallower depth (after current depth has been consumed)?
//
// => When no options are given, the default-behavior is to consider next only from the same depth.
//
public class PathNext extends BaseArity0 implements Node, Arity0 {
    
    public PathNext(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("next");
        this.setNs(Namespaces.PATH);
    }

    public OperonValue evaluate() throws OperonGenericException {
        try {
            //System.out.println("path:next() evaluate");
            Path path = (Path) this.getStatement().getCurrentValue();
            //System.out.println("  - path: " + path);
            OperonValue pathRoot = path.getObjLink();
            
            Path resultPath = path.copy();

            OperonValue pathValue = null;
            
            //System.out.println("path:next() :: pathParts size: " + path.getPathParts().size());
            
            if (path.getPathParts().size() == 0) {
                return new NullType(this.getStatement());
            }
            
            PathPart lastPathPart = path.getPathParts().get(path.getPathParts().size() - 1);
            Path parentPath = PathParentPath.getParentPath(path);
            
            //System.out.println("  - parentPath: " + parentPath);
            
            if (lastPathPart instanceof PosPathPart) {
                int pos = ((PosPathPart) lastPathPart).getPos();
                
                //System.out.println(">> pos=" + pos);
                //System.out.println(">> " + resultPath);
                
                if (pathRoot != null) {
                    //System.out.println(">> 4.1 parentPath=" + parentPath);
                    if (parentPath.getPathParts().size() > 0) {
                        pathValue = PathValue.get(pathRoot, parentPath);
                    }
                    else {
                        pathValue = pathRoot;
                    }
                    //System.out.println(">> 4.2");
                    //System.out.println(">> 4.2 pathValue=" + pathValue);
                    ArrayType pathValueArray = (ArrayType) pathValue.evaluate();
                    int maxSize = pathValueArray.getValues().size();
                    //System.out.println(">> 4.2: maxSize=" + maxSize);
                    if (pos < 0) {
                        return new NullType(this.getStatement()); 
                    }
                    else if (pos < maxSize) {
                        PosPathPart newLastPos = new PosPathPart(pos + 1);
                        resultPath.getPathParts().set(resultPath.getPathParts().size() - 1, newLastPos);
                        //
                        // Do not set this!
                        //
                        //this.getStatement().setCurrentPath(resultPath);
                        return resultPath;
                    }
                    else {
                        return new NullType(this.getStatement()); 
                    }
                }
                else {
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), "No root-value linked for Path(" + path + ")");
                    return null;
                }
            }
            else {
                //System.out.println(">> else: KeyPathPart");
                //
                // KeyPathPart
                // --> requires that we fetch value and find the next pair-key
                //     from the obj.
                // set the lastKeyPart with new key.
                String key = ((KeyPathPart) lastPathPart).getKey();
                //System.out.println(">> KeyPathPart: key=" + key);
                //System.out.println(">>  : parentPath=" + parentPath);
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
                for (PairType p : obj.getPairs()) {
                    String pairKey = p.getKey().substring(1, p.getKey().length() - 1);
                    //System.out.println("  - pairKey=" + pairKey);
                    if (keyFound) {
                        nextKey = pairKey;
                        break;
                    }
                    
                    if (pairKey.equals(key)) {
                        keyFound = true;
                    }
                }
                
                if (nextKey != null) {
                    //System.out.println("next key :: " + nextKey);
                    KeyPathPart newLastKey = new KeyPathPart(nextKey);
                    resultPath.getPathParts().set(resultPath.getPathParts().size() - 1, newLastKey);
                    //
                    // Do not set this!
                    //
                    //this.getStatement().setCurrentPath(resultPath);
                    return resultPath;
                }
                else {
                    //System.out.println("next key :: null");
                    return new NullType(this.getStatement());
                }
            }
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}