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

package io.operon.runner.node;

import io.operon.runner.BaseContext;
import io.operon.runner.Context;
import io.operon.runner.OperonContext;
import io.operon.runner.OperonTestsContext;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.statement.FunctionStatement;
import io.operon.runner.statement.SelectStatement;
import io.operon.runner.statement.ExceptionStatement;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import io.operon.runner.IrTypes;
import com.google.gson.annotations.Expose;

import org.apache.logging.log4j.LogManager;

public class Of extends AbstractNode implements Node {
     // no logger 
    @Expose private byte t = IrTypes.OF;
    
    @Expose private String typeName;
    
    public Of(Statement stmnt) {
        super(stmnt);
    }

    public OperonValue evaluate() throws OperonGenericException {
        //:OFF:log.debug("ENTER Of.evaluate()");
        //System.out.println("Of.evaluate()");
        OperonValue currentValue = this.getStatement().getCurrentValue().evaluate();
        
        if (currentValue instanceof ArrayType) {
            //System.out.println("Setting typeName for array-values");
            ArrayType cv = (ArrayType) currentValue;
            switch (this.getTypeName().toUpperCase()) {
                case "STRING":
                    cv.setArrayValueType(IrTypes.STRING_TYPE);
                    break;
                case "NUMBER":
                    cv.setArrayValueType(IrTypes.NUMBER_TYPE);
                    //System.out.println("Setting typeName for array-values: NUMBER_TYPE");
                    break;
                case "OBJECT":
                    cv.setArrayValueType(IrTypes.OBJECT_TYPE);
                    break;
                default:
                    cv.setArrayValueType(IrTypes.MISSING_TYPE);
                    break;
            }
            
            //System.out.println("CV :: " + cv.getArrayValueType());
            
            this.getStatement().setCurrentValue(cv); // currentValue is left unmodified.
            return cv;
        }

        this.getStatement().setCurrentValue(currentValue); // currentValue is left unmodified.
        return currentValue;
    }
    
    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }
    
    public String getTypeName() {
        return this.typeName;
    }
    
    public String toString() {
        return "Of <" + typeName + ">";
    }

}