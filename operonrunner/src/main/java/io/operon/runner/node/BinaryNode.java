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

import io.operon.runner.processor.BinaryNodeProcessor;
import io.operon.runner.statement.Statement;
import io.operon.runner.Context;
import io.operon.runner.node.type.*;
import io.operon.runner.model.exception.OperonGenericException;

import com.google.gson.annotations.Expose;
import io.operon.runner.IrTypes;

public class BinaryNode extends AbstractNode implements Node {
    
    @Expose private byte t = IrTypes.BINARY_NODE;
    @Expose private Node lhs;
    @Expose private Node rhs;
    @Expose private BinaryNodeProcessor proc;
    
    public BinaryNode(Statement stmnt) {
        super(stmnt);
    }

    public OperonValue evaluate() throws OperonGenericException {
        Context ctx = this.getStatement().getOperonContext();
        ctx.addStackTraceElement(this);
        OperonValue result = proc.process(this.getStatement(), lhs, rhs);
        this.setEvaluatedValue(result);
        // update the currentValue from the statement
        this.getStatement().setCurrentValue(result);
        return result;
    }
    
    public void setLhs(Node lhs) { this.lhs = lhs; }
    public Node getLhs() { return this.lhs; }
    public void setRhs(Node rhs) { this.rhs = rhs; }
    public Node getRhs() { return this.rhs; }
    public void setBinaryNodeProcessor(BinaryNodeProcessor proc) {this.proc = proc; }
    
    public String toString() {
        return this.getEvaluatedValue().toString();
    }
}
