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
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

//
// Compare two paths and return a path that represents the path-parts
// that are common for both, starting from beginning.
// Example:
//   : ~.bin.bai[2].baa => commonSubPath(~.bin.bai[1].baa)
//   #> ~.bin.bai
//
public class PathCommonSubPath extends BaseArity1 implements Node, Arity1 {
    
    public PathCommonSubPath(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "commonSubPath", "path");
        this.setNs(Namespaces.PATH);
    }

    public Path evaluate() throws OperonGenericException {
        try {
            Path currentPath = (Path) this.getStatement().getCurrentValue();
            Path comparePath = (Path) this.getParam1().evaluate();
            
            //System.out.println("commonSubPath evaluate()");
            Path result = commonSubPath(currentPath, comparePath);
            
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    public static Path commonSubPath(Path a, Path b) {
        int aLen = a.getPathParts().size();
        int bLen = b.getPathParts().size();
        
        int targetLen = aLen;
        
        if (aLen > bLen) {
            targetLen = bLen;
        }
        
        //System.out.println("TargetLen: " + targetLen);
        
        List<PathPart> resultPathParts = new ArrayList<PathPart>();
        
        for (int i = 0; i < targetLen; i ++) {
            PathPart ppA = a.getPathParts().get(i);
            PathPart ppB = b.getPathParts().get(i);
            if (ppA.equals(ppB)) {
                resultPathParts.add(ppA.copy());
            }
            else {
                break;
            }
        }
        
        Path result = new Path(a.getStatement());
        result.setPathParts(resultPathParts);
        
        return result;
    }

}