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

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.ValueRef;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.PairType;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.processor.function.core.SpliceLeft;
import io.operon.runner.processor.function.core.SpliceRight;
import io.operon.runner.processor.function.core.SpliceRange;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// Single expressiong in the FilterList.
//
public class FilterListExpr extends AbstractNode implements Node {
     // no logger 
    
    private OperonValue valueToApplyAgainst; // TODO: check if required
    
    private Node filterExpr;
    
    public FilterListExpr(Statement stmnt) {
        super(stmnt);
    }

    //
    // This is not called.
    //
    public OperonValue evaluate() throws OperonGenericException {
        return null;
    }
    
    public void setFilterExpr(Node filterExpr) {
        // TODO: should we set current value for node?
        this.filterExpr = filterExpr;
    }
    
    public Node getFilterExpr() {
        return this.filterExpr;
    }
    
    public void setValueToApplyAgainst(OperonValue value) {
        //:OFF:log.debug("  FilterList :: setting value to apply against :: " + value);
        this.valueToApplyAgainst = value;
    }
    
    public OperonValue getValueToApplyAgainst() {
        return this.valueToApplyAgainst;
    }
}