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

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.statement.Statement;
import io.operon.runner.node.type.OperonValueConstraint;

//
// This is used with the FunctionStatement, when the param names
// are collected. This list is used to check and match the argument
// order when calling the function.
//
// From the following example this models $param1 and $param2,
// and the constraint <Number> for the $param1:
//
//     Function foo($param1 <Number>, $param2) <String>: "Foo" End
//
public class FunctionStatementParam extends AbstractNode implements Node, java.io.Serializable {
    // Parameter name, e.g. $param1
    // Used to check the argument order and their names
    private String param;

    // Optional constraint
    private OperonValueConstraint constraint;
    
    public FunctionStatementParam(Statement stmnt) {
        super(stmnt);
    }
    
    public void setParam(String p) {
        this.param = p;
    }
    
    public String getParam() {
        return this.param;
    }
    
    public void setOperonValueConstraint(OperonValueConstraint c) {
        this.constraint = c;
    }

    public OperonValueConstraint getOperonValueConstraint() {
        return this.constraint;
    }
    
}