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

package io.operon.runner.processor.function.core.module;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import io.operon.runner.OperonContext;
import io.operon.runner.Context;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.FunctionStatement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

/**
 *
 * List available modules from query-main context.
 *
 */
public class ModuleList extends BaseArity0 implements Arity0 {
    
    public ModuleList(Statement statement) {
        super(statement);
        this.setFunctionName("list");
    }

    public ArrayType evaluate() throws OperonGenericException {
        Context ctx = this.getStatement().getOperonContext();

        ArrayType result = new ArrayType(this.getStatement());
        
        List<StringType> moduleNames = this.getModuleNames(ctx.getModules());
        result.getValues().addAll(moduleNames);
        
        return result;
    }

    private List<StringType> getModuleNames(Map<String, Context> moduleMap) {
        List<StringType> results = new ArrayList<StringType>();
        for (Map.Entry<String, Context> moduleEntry : moduleMap.entrySet()) {
            Context module = moduleEntry.getValue();
            // prefix with module's own namespace:
            String moduleOwnNamespace = module.getOwnNamespace();
            if (moduleOwnNamespace.charAt(0) == ':') {
                moduleOwnNamespace = moduleOwnNamespace.substring(1, moduleOwnNamespace.length());
            }
            StringType jstr = new StringType(this.getStatement());
            jstr.setFromJavaString(moduleOwnNamespace);
            results.add(jstr);
        }
        return results;
    }

}