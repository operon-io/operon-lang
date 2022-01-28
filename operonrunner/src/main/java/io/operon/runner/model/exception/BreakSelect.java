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

package io.operon.runner.model.exception;

import io.operon.runner.node.type.EmptyType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.model.exception.OperonGenericException;

public class BreakSelect extends OperonGenericException {

    // 0 = break from aggregate
    // 1 = break from => stop() -function
    private short breakType = (short) 0;
    private OperonValue valueOnBreak;

    public BreakSelect() {
        super(new EmptyType(new DefaultStatement(null)));
        this.setErrorJson(null);
        this.setOperonValueOnBreak(new EmptyType(new DefaultStatement(null)));
        //System.out.println("Created BreakSelect");
    }

    public BreakSelect(OperonValue value) {
        super(new EmptyType(new DefaultStatement(null)));
        this.setErrorJson(null);
        this.setOperonValueOnBreak(value);
    }

    public void setBreakType(short t) {
        this.breakType = t;
    }
    
    public short getBreakType() {
        return this.breakType;
    }
    
    public void setOperonValueOnBreak(OperonValue v) {
        this.valueOnBreak = v;
    }
    
    public OperonValue getOperonValueOnBreak() {
        return this.valueOnBreak;
    }
}