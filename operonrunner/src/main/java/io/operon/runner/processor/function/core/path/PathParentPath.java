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
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class PathParentPath extends BaseArity0 implements Node, Arity0 {
    
    public PathParentPath(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("parentPath");
    }

    public Path evaluate() throws OperonGenericException {
        try {
            Path path = (Path) this.getStatement().getCurrentValue();
            Path resultPath = PathParentPath.getParentPath(path);

            resultPath.setPathParts(resultPath.getPathParts());
            OperonValue linkRoot = path.getObjLink();
            resultPath.setObjLink(linkRoot);
            //
            // Do not set this!
            //
            //this.getStatement().setCurrentPath(resultPath);
            return resultPath;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    public static Path getParentPath(Path path) {
        Path resultPath = new Path(path.getStatement());
        
        for (int i = 0; i < path.getPathParts().size() - 1; i ++) {
            PathPart pp = path.getPathParts().get(i).copy();
            resultPath.getPathParts().add(pp);
        }
        
        return resultPath;
    }

}