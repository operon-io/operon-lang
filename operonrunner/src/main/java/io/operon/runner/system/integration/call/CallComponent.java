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

package io.operon.runner.system.integration.call;

import java.util.Map;
import java.util.List;

import io.operon.runner.OperonContext;
import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.system.IntegrationComponent;
import io.operon.runner.util.ErrorUtil;
//import io.operon.runner.BaseContext;
//import io.operon.runner.Context;
import io.operon.runner.OperonFunction;
import io.operon.runner.OperonRunner;

import java.lang.reflect.Method;

import io.operon.runner.system.ComponentSystemUtil;
import io.operon.runner.system.integration.BaseComponent;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.exception.OperonComponentException;

import org.apache.logging.log4j.LogManager;


public class CallComponent extends BaseComponent implements IntegrationComponent {
    private static Logger log = LogManager.getLogger(CallComponent.class);

    public CallComponent() {}
    
    public OperonValue produce(OperonValue currentValue) throws OperonComponentException {
        try {
            Info info = this.resolve(currentValue);
            Statement stmt = currentValue.getStatement();
            
            String functionId = null;
            
            if (this.getComponentId() != null) {
                functionId = this.getComponentId();
            }
            else {
                functionId = info.id;
            }

            if (functionId == null) {
                ErrorUtil.createErrorValueAndThrow(stmt, /* type */ "CALL", /* code */ "FUNCTION_ID_MISSING", /*message*/ "No function id was given");
            }
            
            Map<String, OperonFunction> registeredFunctions = OperonRunner.getRegisteredFunctions();
            
            if (registeredFunctions == null) {
                ErrorUtil.createErrorValueAndThrow(stmt, /* type */ "CALL", /* code */ "NO_REGISTERED_FUNCTIONS", /*message*/ "No registered functions were found");
            }
            
            OperonFunction func = registeredFunctions.get(functionId);
            
            if (func == null) {
                ErrorUtil.createErrorValueAndThrow(stmt, /* type */ "CALL", /* code */ "FUNCTION_NOT_FOUND", /*message*/ "Function was not found with id " + info.id);
            }
            
            OperonValue result = func.execute(stmt, info.params);
            
            return result;
        } catch (OperonGenericException e) {
            throw new OperonComponentException(e.getErrorJson());
        }
    }
    
    public Info resolve(OperonValue currentValue) throws OperonGenericException {
        OperonValue currentValueCopy = currentValue;
        
        ObjectType jsonConfiguration = this.getJsonConfiguration();
        jsonConfiguration.getStatement().setCurrentValue(currentValueCopy);
        List<PairType> jsonPairs = jsonConfiguration.getPairs();

        Info info = new Info();
        
        for (PairType pair : jsonPairs) {
            String key = pair.getKey();
            OperonValue currentValueCopy2 = currentValue;
            pair.getStatement().setCurrentValue(currentValueCopy2);
            switch (key.toLowerCase()) {
                case "\"id\"":
                    //System.out.println(">>>> 1");
                    OperonValue idNode = (OperonValue) pair.getEvaluatedValue();
                    //System.out.println("idNode class=" + msgNode.getClass().getName());
                    String sId = ((StringType) idNode).getJavaStringValue();
                    info.id = sId;
                    break;
                case "\"params\"":
                    info.params = pair.getEvaluatedValue();
                    break;
                default:
                    log.debug("call -producer: no mapping for configuration key: " + key);
                    System.err.println("call -producer: no mapping for configuration key: " + key);
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "CALL", "ERROR", "call -producer: no mapping for configuration key: " + key);
            }
        }
        
        currentValue.getStatement().setCurrentValue(currentValueCopy);
        return info;
    }

    private class Info {
        private String id = null; // required: the registered OperonFunction's id
        private OperonValue params = null; // optional. Function may be parametrized with any OperonValue. NOTE: this is _not_ pre-evaluated.
    }

}