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
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

//  NOTE: returns Empty when no value is available.
//        This is better than returning Null, because
//        null might be defined in the array/object,
//        but Empty is not.
public class PathValue extends BaseArity0 implements Node, Arity0 {
    
    public PathValue(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("value");
    }

    public OperonValue evaluate() throws OperonGenericException {
        try {
            //System.out.println("PathValue.evaluate()");
            OperonValue currentValue = this.getStatement().getCurrentValue().evaluate();
            
            OperonValue result = new EmptyType(this.getStatement());
            if (currentValue instanceof NullType) {
                return result;
            }
            
            Path path = (Path) currentValue;
            
            //System.out.println("PathValue.evaluate() >> 1");
            OperonValue linkedValue = path.getValueLink();
            //System.out.println("PathValue.evaluate() >> 1, linkedValue=" + linkedValue);
            if (linkedValue != null) {
                result = linkedValue;
            }
            else {
                //System.out.println("Value was not linked, linking root-obj");
                OperonValue linkedObj = path.getObjLink();
                //System.out.println("Linked root :: " + linkedObj);
                if (linkedObj != null) {
                    if (path.getPathParts().size() > 0) {
                        result = PathValue.get(linkedObj, path);
                    }
                    else {
                        result = linkedObj;
                    }
                }
            }
            //System.out.println("PathValue.evaluate() >> 2: " + result);
            return result;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), e.getMessage());
        }
    }

    // 
    // Fetch the value of the Path, given the root-value
    // Return EmptyValue if path has no value.
    // 
    // This is a convenience-method to call easily from Java-code.
    // 
    public static OperonValue get(OperonValue root, String pathStr) throws OperonGenericException {
        Path path = new Path(root.getStatement());
        List<PathPart> pathParts = PathCreate.constructPathParts(pathStr);
        path.setPathParts(pathParts);
        return PathValue.get(root, path);
    }

    // 
    // Fetch the value of the Path, given the root-value
    // Return EmptyValue if path has no value.
    // 
    public static OperonValue get(OperonValue root, Path path) throws OperonGenericException {
        //System.out.println("Traversing path: " + path);
        //System.out.println("Root value: " + root);
        List<PathPart> pathParts = path.getPathParts();
        if (pathParts.size() == 0) {
            return root;
        }
        OperonValue result = new EmptyType(path.getStatement());
        OperonValue pathCurrentValue = null;
        root = (OperonValue) root.evaluate();
        if (root instanceof ObjectType == false && root instanceof ArrayType == false) {
            root = (OperonValue) root.evaluate();
            //System.out.println("root class: " + root.getClass().getName());
        }
        //System.out.println("PathParts size="+pathParts.size());
        for (int i = 0; i < pathParts.size(); i ++) {
            //System.out.println("i=" + i + ", pathParts[i]=" + pathParts.get(i));
            
            //
            // initialCase:
            //
            if (i == 0 && root instanceof ObjectType) {
                //System.out.println(">> 1");
                if (pathParts.get(i) instanceof KeyPathPart) {
                    //System.out.println(">> 2");
                    String key = ((KeyPathPart) pathParts.get(i)).getKey();
                    pathCurrentValue = ((ObjectType) root).getByKey(key);
                    //System.out.println("pathCurrentValue(obj)="+pathCurrentValue);
                }
            }
            else if (i == 0 && root instanceof ArrayType) {
                //System.out.println("pathCurrentValue="+pathCurrentValue);
                if (pathParts.get(i) instanceof PosPathPart) {
                    int pos = ((PosPathPart) pathParts.get(i)).getPos() - 1;
                    pathCurrentValue = (OperonValue) ((ArrayType) root).getValues().get(pos);
                    //System.out.println("pathCurrentValue(array)="+pathCurrentValue);
                }
            }
            //
            // after initial case:
            //
            else {
                if (pathCurrentValue instanceof ObjectType) {
                    if (pathParts.get(i) instanceof KeyPathPart) {
                        String key = ((KeyPathPart) pathParts.get(i)).getKey();
                        pathCurrentValue = ((ObjectType) pathCurrentValue).getByKey(key);
                        //System.out.println("after initial case: pathCurrentValue(obj)="+pathCurrentValue);
                    }
                }
                else if (pathCurrentValue instanceof ArrayType) {
                    if (pathParts.get(i) instanceof PosPathPart) {
                        int pos = ((PosPathPart) pathParts.get(i)).getPos() - 1;
                        pathCurrentValue = (OperonValue) ((ArrayType) pathCurrentValue).getValues().get(pos);
                        //System.out.println("after initial case: pathCurrentValue(array)="+pathCurrentValue);
                    }
                }
            }
        }
        if (pathCurrentValue != null) {
            //System.out.println("set pathCurrentValue=" + pathCurrentValue);
            result = pathCurrentValue;
        }
        //System.out.println("PathValue: return result=" + result);
        return result;
    }

}