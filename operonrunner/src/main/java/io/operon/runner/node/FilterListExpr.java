/** OPERON-LICENSE **/
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
    private static Logger log = LogManager.getLogger(FilterList.class);
    
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
        log.debug("  FilterList :: setting value to apply against :: " + value);
        this.valueToApplyAgainst = value;
    }
    
    public OperonValue getValueToApplyAgainst() {
        return this.valueToApplyAgainst;
    }
}