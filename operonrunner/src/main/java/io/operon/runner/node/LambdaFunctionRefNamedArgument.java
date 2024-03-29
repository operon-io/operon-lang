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

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.statement.Statement;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.statement.FunctionStatement;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.OperonValueConstraint;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.PairType;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// A single named argument. Form: '$a: (expr | FunctionRefArgumentPlaceholder)'.
//
public class LambdaFunctionRefNamedArgument extends AbstractNode implements Node {
     // no logger 

    private String argName;
    private Node exprNode;
    private boolean hasPlaceholder = false;
    private OperonValueConstraint constraint;
    
    public LambdaFunctionRefNamedArgument(Statement stmnt) {
        super(stmnt);
    }

    public String getArgumentName() {
        return this.argName;
    }
    
    public void setArgumentName(String aName) {
        this.argName = aName;
    }

    public Node getExprNode() {
        return this.exprNode;
    }
    
    public void setExprNode(Node e) {
        this.exprNode = e;
    }
    
    public void setHasPlaceholder(boolean b) {
        this.hasPlaceholder = b;
    }
    
    public boolean getHasPlaceholder() {
        return this.hasPlaceholder;
    }
    
    public void setOperonValueConstraint(OperonValueConstraint jvc) {
        this.constraint = jvc;
    }
    
    public OperonValueConstraint getOperonValueConstraint() {
        return this.constraint;
    }

    @Override
    public String toString() {
        return "LambdaFunctionRefNamedArgument :: " + this.getArgumentName();
    }
}