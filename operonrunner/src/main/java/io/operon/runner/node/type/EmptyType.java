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

package io.operon.runner.node.type;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class EmptyType extends OperonValue implements Node, AtomicOperonValue {
    private static Logger log = LogManager.getLogger(EmptyType.class);
    
    public EmptyType(Statement stmnt) {
        super(stmnt);
    }

    public EmptyType evaluate() throws OperonGenericException {
        log.debug("EmptyType :: Evaluate");
        this.setUnboxed(true);
        return this;
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        return "";
    }

    @Override
    public String toYamlString(YamlFormatter yf) {
        return "";
    }

}