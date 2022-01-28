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

package io.operon.runner.node;

import io.operon.runner.node.Node;
import io.operon.runner.statement.Statement;

//
// This is the normal argument for function, i.e. "unnamed"
// and is used with function and lambda-function calls.
// This may be used in mix with named arguments.
//

// WARNING: NOT USED ANYWHERE! MIGHT HAVE TO REMOVE THIS! --> Check this!

public class FunctionRegularArgument extends AbstractNode implements Node {
    private Node argValue;
    
    public FunctionRegularArgument(Statement stmnt) {
        super(stmnt);
    }
    
    public void setArgument(Node arg) {
        this.argValue = arg;
    }
    
    public Node getArgument() {
        return this.argValue;
    }

    @Override
    public String toString() {
        return "FunctionRegularArgument";
    }
}