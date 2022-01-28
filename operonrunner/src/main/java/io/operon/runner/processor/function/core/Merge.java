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

package io.operon.runner.processor.function.core;

import java.util.List;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.ObjDynamicDeepScan;
import io.operon.runner.node.type.*;
import io.operon.runner.node.Build;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.core.path.PathCommonSubPath;
import io.operon.runner.processor.function.core.path.PathSubPath;
import io.operon.runner.model.path.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

// Merge two paths into actual value:
// ~.[1] => path:merge(~[2])
// #> Array: [value1, value2]
//
public class Merge extends BaseArity1 implements Node, Arity1 {
    private static Logger log = LogManager.getLogger(Merge.class);

    public Merge(Statement stmnt, List<Node> params) throws OperonGenericException {
        super(stmnt);
        this.setParams(params, "merge", "with");
    }

    public OperonValue evaluate() throws OperonGenericException {
        log.debug("ENTER Merge.evaluate(). Stmt: " + this.getStatement().getId());
        
        Path a = (Path) this.getStatement().getCurrentValue().evaluate();
        Path b = (Path) this.getParam1().evaluate();
        
        OperonValue result = merge(a, b);
        
        return result;
    }

    public static OperonValue merge(Path a, Path b) throws OperonGenericException {
        System.out.println("Merging a and b");
        Path commonPath = PathCommonSubPath.commonSubPath(a, b);
        
        // TODO: take the subPath of b, and add it into a.
        
        // take subPath of b
        int commonPathLen = commonPath.getPathParts().size();
        System.out.println("commonPathLen: " + commonPathLen);
        Path bSubPath = b;
        if (commonPathLen > 0) {
            bSubPath = PathSubPath.subPath(b, commonPathLen);
        }
        
        // build a
        OperonValue aValue = Build.buildSingleValue(a);
        
        // build subPath b
        OperonValue bSubValue = Build.buildSingleValue(bSubPath);
        System.out.println("bSubPath: " + bSubPath);
        
        // update subPath b to a.commonPath
        System.out.println("a: " + aValue);
        System.out.println("b: " + bSubValue);
        
        //OperonValue updatedValue = GenericUpdate.updatePathValue(OperonValue valueToUpdate, Path pathToUpdate, Node updateValueNode)
        
        // if commonPathLen = 0, then if a | b Array, then update against LONGER path!
        
        OperonValue result = aValue;
        
        if (commonPathLen == 0) {
            if (aValue.evaluate() instanceof ArrayType) {
                System.out.println("a: ArrayType");
                ArrayType aArray = (ArrayType) aValue.evaluate();
                if (bSubValue.evaluate() instanceof ArrayType) {
                    ArrayType bSubArray = (ArrayType) bSubValue.evaluate();
                    System.out.println("b: ArrayType");
                    
                    int aLen = a.getPathParts().size();
                    int bSubLen = bSubPath.getPathParts().size();
                    
                    int aSize = aArray.getValues().size();
                    System.out.println("aSize: " + aSize);
                    //
                    // NOTE: a / b might have root-value linked, which might affect the actual sizes!
                    //
                    int bSubSize = bSubArray.getValues().size();
                    System.out.println("bSubSize: " + bSubSize);
                    
                    if (bSubSize > aSize) {
                        // Update b with a path
                        System.out.println("b > a");
                        
                        //int aPos = ((PosPathPart) a.getPathParts().get(0)).getPos();
                        //int bPos = ((PosPathPart) b.getPathParts().get(0)).getPos();
                        //System.out.println("aPos=" + aPos);
                        //System.out.println("bPos=" + bPos);
                        
                        for (int ai = 0; ai < aSize; ai ++) {
                            OperonValue aVal = (OperonValue) aArray.getValues().get(ai);
                            OperonValue bVal = (OperonValue) bSubArray.getValues().get(ai);
                            if (aVal instanceof NullType == false) {
                                System.out.println("a not null at pos " + ai);
                                // override b-value with a-value
                                bSubArray.getValues().set(ai, aVal);
                                System.out.println("b pos set with value");
                            }
                            else {
                                System.out.println("a null at pos " + ai);
                                // Let b-value remain
                            }
                        }
                        
                        result = bSubArray;
                    }
                    else {
                        // Update a with b path
                        System.out.println("a >= b");
                        //result = GenericUpdate.updatePathValue(aValue, commonPath, bSubValue);
                        
                        for (int bi = 0; bi < bSubSize; bi ++) {
                            OperonValue aVal = (OperonValue) aArray.getValues().get(bi);
                            OperonValue bVal = (OperonValue) bSubArray.getValues().get(bi);
                            System.out.println("bVal: " + bVal);
                            if (bVal instanceof NullType == false) {
                                System.out.println("b not null at pos " + bi);
                                // override a-value with b-value
                                aArray.getValues().set(bi, bVal);
                                System.out.println("a pos set with value");
                            }
                            else {
                                System.out.println("a null at pos " + bi);
                                // Let a-value remain
                            }
                        }
                        
                        result = aArray;
                    }
                    
                }
                
            }
        }
        else {
            result = GenericUpdate.updatePathValue(aValue, commonPath, bSubValue);
        }
        

        // TODO: finally we should update both values, if they are linked with the paths.
        // TODO: we should also check if paths have root-linked, then what does that mean?
        
        return result;
    }

}