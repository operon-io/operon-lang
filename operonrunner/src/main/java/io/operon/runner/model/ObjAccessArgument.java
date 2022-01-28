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

package io.operon.runner.model;

import io.operon.runner.node.Node;
import io.operon.runner.node.ValueRef;
import io.operon.runner.node.FunctionArguments;

//
// Used in the ObjArrayAccess
//
public class ObjAccessArgument {

    // accessKeyId is mutually exclusive to expr
    private String accessKeyId; // ID, i.e. value without quotes

    private Node expr;

    public ObjAccessArgument() {}

    public void setAccessKeyId(String akid) {
        this.accessKeyId = akid;
    }

    public String getAccessKeyId() {
        return this.accessKeyId;
    }
    
    public void setExpr(Node exp) {
        this.expr = exp;
    }
    
    public Node getExpr() {
        return this.expr;
    }
}