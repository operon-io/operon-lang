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

package io.operon.runner.node.type;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import io.operon.runner.IrTypes;
import com.google.gson.annotations.Expose;

public class EndValueType extends OperonValue implements Node, AtomicOperonValue {
     // no logger 
    
    @Expose private byte t = IrTypes.END_VALUE_TYPE; // Type-name in the IR-serialized output
    
    public EndValueType(Statement stmnt) {
        super(stmnt);
    }

    public EndValueType evaluate() throws OperonGenericException {
        //:OFF:log.debug("EndValueType :: Evaluate");
        this.setUnboxed(true);
        return this;
    }

    @Override
    public String toString() {
        return "end";
    }

    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        return "end";
    }

    @Override
    public String toYamlString(YamlFormatter yf) {
        return "end";
    }

    @Override
    public String toTomlString(OutputFormatter ofmt) {
        return "end";
    }

}