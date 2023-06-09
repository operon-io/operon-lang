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

package io.operon.runner.processor.function.core.env;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.processor.function.core.raw.RawToStringType;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class EnvGet extends BaseArity1 implements Node, Arity1 {
    
    public EnvGet(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "get", "name");
        this.setNs(Namespaces.ENV);
    }

    public OperonValue evaluate() throws OperonGenericException {
        try {
            StringType envVariableNameJsonStr = (StringType) this.getParam1().evaluate();
            String envVarStr = envVariableNameJsonStr.getJavaStringValue();
            
            String resultStr = EnvGet.readEnvVariableAsString(envVarStr);
            
            if (resultStr == null) {
                return new EmptyType(this.getStatement());
            }
            
            else {
                StringType result = new StringType(this.getStatement());
                resultStr = RawToStringType.sanitizeForStringType(resultStr);
                result.setFromJavaString(resultStr);
                return result;
            }
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "env:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    public static String readEnvVariableAsString(String variableName) {
        return System.getenv(variableName);
    }

}