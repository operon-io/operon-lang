/** OPERON-LICENSE **/
package io.operon.runner.node;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.ValueRef;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.processor.function.core.SpliceLeft;
import io.operon.runner.processor.function.core.SpliceRight;
import io.operon.runner.processor.function.core.SpliceRange;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// Container for filter-expressions (predicate / number) / splicing-expressions
//
public class FilterList extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(FilterList.class);
    
    private OperonValue valueToApplyAgainst;
    private Filter.Info resolvedConfigs;

    // List of Expr
    private List<FilterListExpr> filterExprList;
    
    public FilterList(Statement stmnt) {
        super(stmnt);
    }

    public OperonValue evaluate() throws OperonGenericException {
        log.debug("ENTER FilterList.evaluate(). Stmt: " + this.getStatement().getId());
        if (this.getValueToApplyAgainst() instanceof ArrayType) {
            return this.evaluateArray();
        }
        
        else if (this.getValueToApplyAgainst() instanceof ObjectType) {
            return this.evaluateObj();
        }

        else if (this.getValueToApplyAgainst() instanceof Path) {
            return this.evaluatePath();
        }

        else if (this.getValueToApplyAgainst() instanceof StringType) {
            return this.evaluateString();
        }

        String type = "FILTER";
        String code = "INPUT";
        String message = "Cannot filter against " + this.getValueToApplyAgainst().getClass().getName();
        return ErrorUtil.createErrorValueAndThrow(this.getStatement(), type, code, message);
    }

    public OperonValue evaluatePath() throws OperonGenericException {
        Path resultPath = new Path(this.getStatement());
        
        OperonValue valueToFilter = this.getValueToApplyAgainst();
        log.debug("    Evaluate :: size :: " + this.getFilterExprList().size());

        for (FilterListExpr filterExpr : this.getFilterExprList()) {
            OperonValue currentValueCopy = valueToFilter.copy();
            
            Node filterListFilterExpr = filterExpr.getFilterExpr();
            
            //
            // TODO: improve Path-filtering by implementing the below patterns:
            //
            
            if (filterListFilterExpr instanceof SpliceLeft) {
                log.debug("FilterList :: SpliceLeft");
                SpliceLeft splicingLeft = (SpliceLeft) filterListFilterExpr;
                splicingLeft.setValueToApplyAgainst((Path) currentValueCopy);
                Path splicingResult = (Path) splicingLeft.evaluate();
                resultPath = splicingResult;
            }
            
            else if (filterListFilterExpr instanceof SpliceRight) {
                log.debug("FilterList :: SpliceRight");
                SpliceRight splicingRight = (SpliceRight) filterListFilterExpr;
                splicingRight.setValueToApplyAgainst((Path) currentValueCopy);
                Path splicingResult = (Path) splicingRight.evaluate();
                resultPath = splicingResult;
            }
            
            else if (filterListFilterExpr instanceof SpliceRange) {
                log.debug("FilterList :: SpliceRange");
                SpliceRange splicingRange = (SpliceRange) filterListFilterExpr;
                splicingRange.setValueToApplyAgainst((Path) currentValueCopy);
                Path splicingResult = (Path) splicingRange.evaluate();
                resultPath = splicingResult;
            }
            
            else if (filterListFilterExpr instanceof Range) {
                log.debug("FilterList :: Range");
                Range range = (Range) filterListFilterExpr;
                range = (Range) range.evaluate();
                
                Path rangeFilterResult = Range.filterPath((Path) currentValueCopy, range);
                resultPath = rangeFilterResult;
            }
            
            else {
                log.debug("FilterList :: Filter");
                
                log.debug(filterListFilterExpr.getClass().getName());
                
                Filter filter = new Filter(this.getStatement());
                filter.setFilterListExpression(filterListFilterExpr);
                filter.setValueToApplyAgainst((Path) currentValueCopy);
                filter.setResolvedConfigs(this.getResolvedConfigs());
                OperonValue n = filter.evaluate();

                if (this.getFilterExprList().size() > 1) {
                    resultPath = (Path) n.evaluate();
                }
                
                else {
                    return n;
                }
            }
        }
        return resultPath;
    }
    
    public OperonValue evaluateString() throws OperonGenericException {
        StringType stringResult = new StringType(this.getStatement());
        StringBuilder sbResult = new StringBuilder();
        
        OperonValue valueToFilter = this.getValueToApplyAgainst();
        log.debug("    Evaluate :: size :: " + this.getFilterExprList().size());

        for (FilterListExpr filterExpr : this.getFilterExprList()) {
            OperonValue currentValueCopy = valueToFilter.copy();
            
            Node filterListFilterExpr = filterExpr.getFilterExpr();
            
            if (filterListFilterExpr instanceof SpliceLeft) {
                log.debug("FilterList :: SpliceLeft");
                SpliceLeft splicingLeft = (SpliceLeft) filterListFilterExpr;
                splicingLeft.setValueToApplyAgainst((StringType) currentValueCopy);
                StringType splicingResult = (StringType) splicingLeft.evaluate();
                sbResult.append(splicingResult.getJavaStringValue());
            }
            
            else if (filterListFilterExpr instanceof SpliceRight) {
                log.debug("FilterList :: SpliceRight");
                SpliceRight splicingRight = (SpliceRight) filterListFilterExpr;
                splicingRight.setValueToApplyAgainst((StringType) currentValueCopy);
                StringType splicingResult = (StringType) splicingRight.evaluate();
                sbResult.append(splicingResult.getJavaStringValue());
            }
            
            else if (filterListFilterExpr instanceof SpliceRange) {
                log.debug("FilterList :: SpliceRange");
                SpliceRange splicingRange = (SpliceRange) filterListFilterExpr;
                splicingRange.setValueToApplyAgainst((StringType) currentValueCopy);
                StringType splicingResult = (StringType) splicingRange.evaluate();
                sbResult.append(splicingResult.getJavaStringValue());
            }

            else if (filterListFilterExpr instanceof Range) {
                log.debug("FilterList :: Range");
                Range range = (Range) filterListFilterExpr;
                range = (Range) range.evaluate();
                
                StringType rangeFilterResult = Range.filterString((StringType) currentValueCopy, range);
                sbResult.append(rangeFilterResult.getJavaStringValue());
            }
            
            else {
                log.debug("FilterList :: Filter");
                
                log.debug(filterListFilterExpr.getClass().getName());
                
                Filter filter = new Filter(this.getStatement());
                filter.setFilterListExpression(filterListFilterExpr);
                filter.setValueToApplyAgainst((StringType) currentValueCopy);
                filter.setResolvedConfigs(this.getResolvedConfigs());
                OperonValue n = filter.evaluate();

                if (this.getFilterExprList().size() > 1) {
                    StringType nStr = (StringType) n;
                    sbResult.append(nStr.getJavaStringValue());
                }
                
                else {
                    return n;
                }
            }
        }
        stringResult.setFromJavaString(sbResult.toString());
        return stringResult;
    }
    
    public OperonValue evaluateArray() throws OperonGenericException {
        ArrayType arrayResult = new ArrayType(this.getStatement());
        
        OperonValue valueToFilter = this.getValueToApplyAgainst();
        log.debug("    Evaluate :: size :: " + this.getFilterExprList().size());
        
        //System.out.println("FilterList parallel: " + this.getResolvedConfigs().parallel);
        //if (this.getResolvedConfigs().parallel == false) {
            for (FilterListExpr filterExpr : this.getFilterExprList()) {
                OperonValue currentValueCopy = valueToFilter.copy();
                
                Node filterListFilterExpr = filterExpr.getFilterExpr();
                
                if (filterListFilterExpr instanceof SpliceLeft) {
                    log.debug("FilterList :: SpliceLeft");
                    SpliceLeft splicingLeft = (SpliceLeft) filterListFilterExpr;
                    splicingLeft.setValueToApplyAgainst((ArrayType) currentValueCopy);
                    ArrayType splicingListResult = (ArrayType) splicingLeft.evaluate();
                    for (Node n : splicingListResult.getValues()) {
                        arrayResult.addValue(n);
                    }
                }
                
                else if (filterListFilterExpr instanceof SpliceRight) {
                    log.debug("FilterList :: SpliceRight");
                    SpliceRight splicingRight = (SpliceRight) filterListFilterExpr;
                    splicingRight.setValueToApplyAgainst((ArrayType) currentValueCopy);
                    ArrayType splicingListResult = (ArrayType) splicingRight.evaluate();
                    for (Node n : splicingListResult.getValues()) {
                        arrayResult.addValue(n);
                    }
                }
                
                else if (filterListFilterExpr instanceof SpliceRange) {
                    log.debug("FilterList :: SpliceRange");
                    log.debug("  currentValueCopy :: " + currentValueCopy);
                    SpliceRange splicingRange = (SpliceRange) filterListFilterExpr;
                    splicingRange.setValueToApplyAgainst((ArrayType) currentValueCopy);
                    ArrayType splicingListResult = (ArrayType) splicingRange.evaluate();
                    for (Node n : splicingListResult.getValues()) {
                        arrayResult.addValue(n);
                    }
                }
                
                else if (filterListFilterExpr instanceof Range) {
                    log.debug("FilterList :: Range");
                    Range range = (Range) filterListFilterExpr;
                    range = (Range) range.evaluate();
                    
                    ArrayType rangeFilterResult = Range.filterArray((ArrayType) currentValueCopy, range);
                    for (Node n : rangeFilterResult.getValues()) {
                        arrayResult.addValue(n);
                    }
                }
                
                else {
                    log.debug("FilterList :: Filter");
                    log.debug(filterListFilterExpr.getClass().getName());
                    
                    Filter filter = new Filter(this.getStatement());
                    filter.setFilterListExpression(filterListFilterExpr);
                    filter.setValueToApplyAgainst((ArrayType) currentValueCopy);
                    filter.setResolvedConfigs(this.getResolvedConfigs());
                    OperonValue n = filter.evaluate();
                    
                    if (n instanceof ArrayType && this.getFilterExprList().size() == 1) {
                        //System.out.println("RESULT IS ARRAY: " + n);
                        //System.out.println("  filterExprList size: " + this.getFilterExprList().size());
                        handleArrayResult((ArrayType) n, arrayResult);
                    }
                    
                    else {
                        if (this.getFilterExprList().size() > 1) {
                            arrayResult.addValue((OperonValue) n);
                        }
                        
                        else {
                            return n;
                        }
                    }
                }
            }
            
            return arrayResult;
        //}
    }
    
    private void handleArrayResult(ArrayType filterResult, ArrayType arrayResult) throws OperonGenericException {
        //OperonValue parentObj = filterResult.getParentObj();
        for (Node v : filterResult.getValues()) {
            //
            // FIXME: use unboxing
            //
            if (v instanceof UnaryNode) {
                v = v.evaluate();
            }
            // Handle injecting parent-objects (for resolving the object self-references, e.g. for LambdaFunctionRefs):
            //if (parentObj != null) {
                if (v instanceof ArrayType) {
                    //((ArrayType) v).setParentObj(parentObj);
                }
                else if (v instanceof ObjectType) {
                    //((ObjectType) v).setParentObj(parentObj);
                }
                // Inject the root-object scope:
                else if (v instanceof LambdaFunctionRef) {
                    LambdaFunctionRef lfr = (LambdaFunctionRef) v;
                    //while (parentObj.getParentObj() != null) {
                    //    parentObj = parentObj.getParentObj();
                    //}
                    //lfr.getStatement().getRuntimeValues().put("_", parentObj);
                    lfr.getStatement().getRuntimeValues().put("_", arrayResult);
                }
                else if (v instanceof NumberType) {
                    //((NumberType) v).setParentObj(parentObj);
                }
                else {
                    //v.setParentObj(parentObj);
                }
            //}
            arrayResult.addValue((OperonValue) v);
        }
    }
    
    public OperonValue evaluateObj() throws OperonGenericException {
        ObjectType objResult = new ObjectType(this.getStatement());
        OperonValue valueToFilter = this.getValueToApplyAgainst();

        int position = 1;
        //Path currentPath = this.getStatement().getCurrentPath();
        for (FilterListExpr filterExpr : this.getFilterExprList()) {
            OperonValue currentValueCopy = valueToFilter.copy();
            //System.out.println("FilterList :: currentPath :: " + currentPath);
            if (filterExpr.getFilterExpr() instanceof SpliceLeft) {
                log.debug("FilterList :: SpliceLeft");
                SpliceLeft splicingLeft = (SpliceLeft) filterExpr.getFilterExpr();
                splicingLeft.setValueToApplyAgainst((ObjectType) currentValueCopy);
                ObjectType splicingListResult = (ObjectType) splicingLeft.evaluate();
                for (PairType p : splicingListResult.getPairs()) {
                    objResult.addPair(p);
                }
            }
            
            else if (filterExpr.getFilterExpr() instanceof SpliceRight) {
                log.debug("FilterList :: SpliceRight");
                SpliceRight splicingRight = (SpliceRight) filterExpr.getFilterExpr();
                splicingRight.setValueToApplyAgainst((ObjectType) currentValueCopy);
                ObjectType splicingListResult = (ObjectType) splicingRight.evaluate();
                for (PairType p : splicingListResult.getPairs()) {
                    objResult.addPair(p);
                }
            }
            
            else if (filterExpr.getFilterExpr() instanceof SpliceRange) {
                log.debug("FilterList :: SpliceRange");
                SpliceRange splicingRange = (SpliceRange) filterExpr.getFilterExpr();
                splicingRange.setValueToApplyAgainst((ObjectType) currentValueCopy);
                ObjectType splicingListResult = (ObjectType) splicingRange.evaluate();
                for (PairType p : splicingListResult.getPairs()) {
                    objResult.addPair(p);
                }
            }
            
            else if (filterExpr.getFilterExpr() instanceof Range) {
                log.debug("FilterList :: Range");
                Range range = (Range) filterExpr.getFilterExpr();
                range = (Range) range.evaluate();
                
                ObjectType rangeFilterResult = Range.filterObj((ObjectType) currentValueCopy, range);
                for (PairType p : rangeFilterResult.getPairs()) {
                    objResult.addPair(p);
                }
            }
            
            else {
                log.debug("FilterList :: evaluateObj");
                Filter filter = new Filter(this.getStatement());
                filter.setFilterListExpression(filterExpr.getFilterExpr());
                filter.setValueToApplyAgainst((ObjectType) currentValueCopy);
                filter.setResolvedConfigs(this.getResolvedConfigs());
                Node n = filter.evaluate();
                
                if (n instanceof ObjectType) {
                    // Set position: array:Next -tests for Object
                    // NOTE: the filter-result is an Object. The position of Object is always 1.
                    //       The position of values inside the object is a different thing, and is based on index inside the object.
                    //System.out.println(">>>> pos");
                    //objResult.setPosition(1);
                    int subPosition = 1;
                    for (PairType p : ((ObjectType) n).getPairs()) {
                        p.setPosition(subPosition);
                        //
                        // do not add pair if it already exists in the result
                        //
                        objResult.addOrUpdatePair(p);
                        subPosition += 1;
                    }
                }
            }
            position += 1;
        }
        return objResult;
    }
    
    public void setFilterExprList(List<FilterListExpr> filterExprList) {
        this.filterExprList = filterExprList;
    }
    
    public List<FilterListExpr> getFilterExprList() {
        return this.filterExprList;
    }
    
    public void setValueToApplyAgainst(OperonValue value) {
        log.debug("  FilterList :: setting value to apply against :: " + value);
        this.valueToApplyAgainst = value;
    }
    
    public OperonValue getValueToApplyAgainst() {
        return this.valueToApplyAgainst;
    }

    public void setResolvedConfigs(Filter.Info i) {
        this.resolvedConfigs = i;
    }

    public Filter.Info getResolvedConfigs() {
        return this.resolvedConfigs;
    }

}