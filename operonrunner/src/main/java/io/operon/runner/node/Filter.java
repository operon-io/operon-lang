/** OPERON-LICENSE **/
package io.operon.runner.node;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.model.path.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.processor.function.SupportsAttributes;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class Filter extends AbstractNode implements Node, SupportsAttributes {
    private static Logger log = LogManager.getLogger(Filter.class);

    private OperonValue valueToApplyAgainst;
    
    private Node filterListExpression;
    private Node configs;
    private Info resolvedConfigs;
    
    public Filter(Statement stmnt) {
        super(stmnt);
    }

    public void setFilterListExpression(Node filterListExpression) {
        this.filterListExpression = filterListExpression;
    }
    
    public Node getFilterListExpression() {
        return this.filterListExpression;
    }

    public OperonValue evaluate() throws OperonGenericException {
        log.debug("ENTER Filter.evaluate()");
        OperonValue currentValue = this.getValueToApplyAgainst();
        if (currentValue == null) {
            currentValue = this.getStatement().getCurrentValue();
        }
        //System.out.println("ENTER Filter.evaluate() :: 0.2 :: CV=" + currentValue + ", type="+currentValue.getClass().getName());
        Node evaluatedCurrentValue = currentValue.evaluate();
        //
        // Resolve configs always when evaluating (configs may change, based on calculated expressions, so we don't cache them)
        //
        // Problem: FilterList creates new Filter, with no configs. When configs are missing, then
        // the default Info-object is created. We could bypass this here, by checking that 
        // resolvedConfigs is _also_ null (so we know that FilterList didn't set it).
        Info info = this.resolveConfigs(this.getStatement());
        this.setResolvedConfigs(info);

        Path resetPath = this.getStatement().getCurrentPath().copy();
        OperonValue objLink = this.getStatement().getCurrentPath().getObjLink();
        resetPath.setObjLink(objLink);
        
        if (evaluatedCurrentValue instanceof ArrayType) {
            ArrayType arrayToFilter = (ArrayType) evaluatedCurrentValue;
            OperonValue result = evaluateArray(arrayToFilter);
            this.getStatement().setCurrentValue(result);
            this.getStatement().setCurrentPath(resetPath);
            return result;
        }
        
        else if (evaluatedCurrentValue instanceof ObjectType) {
            log.debug("    Filter.evaluate() :: ObjectType");
            ObjectType objToFilter = (ObjectType) evaluatedCurrentValue;
            //System.out.println("    Filter.evaluate() :: ObjectType :: " + objToFilter);
            //System.out.println("      Filter.evaluate() :: CV :: " + currentValue);
            OperonValue result = evaluateObj(objToFilter);
            this.getStatement().setCurrentValue((OperonValue) result);
            this.getStatement().setCurrentPath(resetPath);
            return result;
        }

        else if (evaluatedCurrentValue instanceof Path) {
            log.debug("    Filter.evaluate() :: Path");
            Path pathToFilter = (Path) evaluatedCurrentValue;
            OperonValue result = evaluatePath(pathToFilter);
            this.getStatement().setCurrentValue((OperonValue) result);
            return result;
        }
        
        else if (evaluatedCurrentValue instanceof StringType) {
            log.debug("    Filter.evaluate() :: StringType");
            StringType strToFilter = (StringType) evaluatedCurrentValue;
            OperonValue result = evaluateString(strToFilter);
            this.getStatement().setCurrentValue((OperonValue) result);
            return result;
        }
        return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FILTER", "TYPE", "Wrong type for filter-expression :: " + evaluatedCurrentValue);
    }

    private OperonValue evaluateArray(ArrayType arrayToFilter) throws OperonGenericException {
        ArrayType arrayResult = new ArrayType(this.getStatement());
        
        // FilterList e.g. [1,3, @ > 7]
        if (this.getFilterListExpression() instanceof FilterList) {
            //System.out.println("Filter::evaluateArray::filterList");
            log.debug("FILTER :: FilterList");
            FilterList filterList = (FilterList) this.getFilterListExpression();
            log.debug("     FilterExprList size :: " + filterList.getFilterExprList().size());
            filterList.setValueToApplyAgainst(arrayToFilter);
            filterList.setResolvedConfigs(this.getResolvedConfigs());
            OperonValue result = filterList.evaluate();
            //System.out.println("  --> result: " + result);
            return result;
        }

        else {
            log.debug("   FILTER :: Entered else. ");
            //System.out.println("FILTER Array, iterate");
            int arraySize = arrayToFilter.getValues().size();
            Info info = this.getResolvedConfigs();
            if (info.parallel == false) {
                Path currentPath = this.getStatement().getCurrentPath();
                for (int i = 0;  i < arraySize; i ++) {
                    if (info.maxResults != -1 && arrayResult.getValues().size() >= info.maxResults) {
                        break;
                    }
                    Node n = arrayToFilter.getValues().get(i);
                    OperonValue nOperonValue = (OperonValue) n;
                    //System.out.println("FILTER Array, set pos=" + (i+1));
                    
                    //
                    // ATTRIBUTES
                    //
                    Path newPath = (Path) currentPath.copy();
                    PathPart pp = new PosPathPart(i + 1);
                    newPath.getPathParts().add(pp);
                    if (currentPath.getObjLink() == null) {
                        newPath.setObjLink(arrayToFilter);
                    }
                    this.getStatement().setCurrentPath(newPath);
                    //
                    // END ATTRIBUTES
                    //
                    //nOperonValue.setPosition(i + 1);
                    
                    this.getFilterListExpression().getStatement().setCurrentValue(nOperonValue);
                    OperonValue evaluated = this.getFilterListExpression().evaluate();
                    
                    if (evaluated instanceof NumberType) {
                        log.debug("    FILTER :: Evaluating against number");
                        int index = (int) ((NumberType) evaluated).getDoubleValue();
                        //System.out.println("    FILTER :: Evaluating against number :: " + index + ", i = " + i);
                        if (index < 0) {
                            index = arraySize + index + 1;
                            //System.out.println("    FILTER :: index < 0, setting new :: " + index);
                            if (index == 0) {
                                arrayResult.addValue(nOperonValue); // gather all
                                continue;
                            }
                        }
                        
                        if (index == 0) {
                            return arrayResult; // return none
                        }
                        // If i+1 == index, then return new ArrayType, with this element.
                        else if (i + 1 == index) {
                            this.getStatement().setCurrentValue(nOperonValue);
                            return nOperonValue;
                        }
                    }
                    
                    else if (evaluated instanceof TrueType) {
                        arrayResult.addValue(nOperonValue);
                    }
                    
                    else if (evaluated instanceof FalseType) {
                        continue;
                    }
                    
                    else if (evaluated instanceof FunctionRef) {
                        FunctionRef filterFnRef = (FunctionRef) evaluated;
                        filterFnRef.setCurrentValueForFunction(nOperonValue);
                        OperonValue filterResult = filterFnRef.invoke();
                        
                        if (filterResult instanceof TrueType) {
                            arrayResult.addValue(nOperonValue);
                        }
                        
                        else if (filterResult instanceof FalseType) {
                            continue;
                        }
                    }
                    
                    else if (evaluated instanceof LambdaFunctionRef) {
                        //
                        // Invoke the LambdaFunctionRef
                        //
                        LambdaFunctionRef filterFnRef = (LambdaFunctionRef) evaluated;
                        filterFnRef.setCurrentValueForFunction(nOperonValue);
                        OperonValue filterResult = filterFnRef.invoke();
                        
                        if (filterResult instanceof TrueType) {
                            arrayResult.addValue(nOperonValue);
                        }
                        
                        else if (filterResult instanceof FalseType) {
                            continue;
                        }
                    }
                    
                    else {
                        ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FILTER", "TYPE", "Array-filter predicate did not evaluate into boolean or number.");
                    }
                }
            }
            else {
                // "indexify" the list first:
                List<PairWithIndex> arrayToFilterWithIndices = new ArrayList<PairWithIndex>();
                for (int i = 0; i < arrayToFilter.getValues().size(); i ++) {
                    PairWithIndex pwi = new PairWithIndex();
                    pwi.index = i;
                    pwi.value = (OperonValue) arrayToFilter.getValues().get(i);
                    arrayToFilterWithIndices.add(pwi);
                }
                
                List<OperonValue> parallelResults = arrayToFilterWithIndices.parallelStream().map(
                    pwi -> {
                        try {
                            OperonValue nOperonValue = (OperonValue) pwi.value;
                            //nOperonValue.setPosition(pwi.index + 1);
                            
                            boolean linkScope = false;
                            Node filterExprCopy = AbstractNode.deepCopyNode(nOperonValue.getStatement(), this.getFilterListExpression(), linkScope);
                            filterExprCopy.getStatement().setCurrentValue(nOperonValue);
                            OperonValue evaluated = filterExprCopy.evaluate();

                            if (evaluated instanceof NumberType) {
                                log.debug("    FILTER :: Evaluating against number");
                                int index = (int) ((NumberType) evaluated).getDoubleValue();
                                //System.out.println("    FILTER :: Evaluating against number :: " + index + ", i = " + i);
                                if (index < 0) {
                                    index = arraySize + index + 1;
                                    //System.out.println("    FILTER :: index < 0, setting new :: " + index);
                                    if (index == 0) {
                                        return nOperonValue;
                                    }
                                }
                                else if (index == 0) {
                                    return new EmptyType(this.getStatement());
                                }
                                // If i+1 == index, then return new ArrayType, with this element.
                                if (pwi.index + 1 == index) {
                                    //this.getStatement().setCurrentValue(nOperonValue); // Commented for parallel
                                    return nOperonValue;
                                }
                                else {
                                    return new EmptyType(this.getStatement());
                                }
                            }
                            
                            else if (evaluated instanceof TrueType) {
                                //arrayResult.addValue(nOperonValue);
                                return nOperonValue;
                            }
                            
                            else if (evaluated instanceof FalseType) {
                                //continue;
                                return new EmptyType(this.getStatement());
                            }

                            else if (evaluated instanceof FunctionRef) {
                                FunctionRef filterFnRefCopy = (FunctionRef) AbstractNode.deepCopyNode(nOperonValue.getStatement(), evaluated, linkScope);
                                //FunctionRef filterFnRef = (FunctionRef) evaluated;
                                
                                filterFnRefCopy.setCurrentValueForFunction(nOperonValue);
                                OperonValue filterResult = filterFnRefCopy.invoke();
                                
                                if (filterResult instanceof TrueType) {
                                    //arrayResult.addValue(nOperonValue);
                                    return nOperonValue;
                                }
                                
                                else if (filterResult instanceof FalseType) {
                                    //continue;
                                    return new EmptyType(this.getStatement());
                                }
                                
                                else {
                                    return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FILTER", "TYPE", "Array-filter predicate did not evaluate into boolean or number.");
                                }
                            }
                            
                            else if (evaluated instanceof LambdaFunctionRef) {
                                //
                                // Invoke the LambdaFunctionRef
                                //
                                LambdaFunctionRef filterFnRefCopy = (LambdaFunctionRef) AbstractNode.deepCopyNode(nOperonValue.getStatement(), evaluated, linkScope);
                                //LambdaFunctionRef filterFnRef = (LambdaFunctionRef) evaluated;
                                filterFnRefCopy.setCurrentValueForFunction(nOperonValue);
                                OperonValue filterResult = filterFnRefCopy.invoke();
                                
                                if (filterResult instanceof TrueType) {
                                    //arrayResult.addValue(nOperonValue);
                                    return nOperonValue;
                                }
                                
                                else if (filterResult instanceof FalseType) {
                                    //continue;
                                    return new EmptyType(this.getStatement());
                                }
                                
                                else {
                                    return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FILTER", "TYPE", "Array-filter predicate did not evaluate into boolean or number.");
                                }
                            }

                            else {
                                return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FILTER", "TYPE", "Array-filter predicate did not evaluate into boolean or number.");
                            }
                        } catch (Exception e) {
                            // Do smthg
                            System.err.println("Error: " + e.getMessage());
                            return null;
                        }
                    }
                ).collect(Collectors.toList());
                arrayResult.getValues().addAll(parallelResults);
            }
        }
        
        //log.debug("    >> RETURN :: " + arrayResult);
        return arrayResult;
    }
    
    private OperonValue evaluateString(StringType strToFilter) throws OperonGenericException {
        StringType resultJsonStr = new StringType(this.getStatement());
        StringBuilder sbResult = new StringBuilder();
        
        if (this.getFilterListExpression() instanceof FilterList) {
            log.debug("FILTER :: FilterList");
            FilterList filterList = (FilterList) this.getFilterListExpression();
            log.debug("     FilterExprList size :: " + filterList.getFilterExprList().size());
            filterList.setValueToApplyAgainst(strToFilter);
            filterList.setResolvedConfigs(this.getResolvedConfigs());
            OperonValue result = filterList.evaluate();
            return result;
        }
        
        else {
            String javaString = strToFilter.getJavaStringValue();
            int javaStringLenght = javaString.length();
            
            for (int i = 0; i < javaStringLenght; i ++) {
                OperonValue evaluated = this.getFilterListExpression().evaluate();

                // Position-based filter
                if (evaluated instanceof NumberType) {
                    int n = (int) ((NumberType) evaluated).getDoubleValue();
                    if (n < 0) {
                        if (-n - 1 == javaStringLenght) {
                            sbResult.append(javaString.charAt(i)); // gather all
                        }
                        else if (-n > javaStringLenght) {
                            continue;
                        }
                        else if (i == javaStringLenght + n) {
                            sbResult.append(javaString.charAt(i));
                        }
                    }
                    else if (n == 0) {
                        break;
                    }
                    else if (n > javaStringLenght) {
                        continue;
                    }
                    else if ((n - 1) == i) {
                        sbResult.append(javaString.charAt(i));
                    }
                }

                else if (evaluated instanceof TrueType) {
                    sbResult.append(javaString.charAt(i));
                }
                
                else if (evaluated instanceof FalseType) {
                    continue;
                }
                
                else {
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FILTER", "TYPE", "String-filter predicate did not evaluate into supported type.");
                }                
            }
        }
        resultJsonStr.setFromJavaString(sbResult.toString());
        return resultJsonStr;
    }
    
    private OperonValue evaluatePath(Path pathToFilter) throws OperonGenericException {
        Path resultPath = new Path(this.getStatement());
        
        if (this.getFilterListExpression() instanceof FilterList) {
            log.debug("FILTER :: FilterList");
            FilterList filterList = (FilterList) this.getFilterListExpression();
            log.debug("     FilterExprList size :: " + filterList.getFilterExprList().size());
            filterList.setValueToApplyAgainst(pathToFilter);
            filterList.setResolvedConfigs(this.getResolvedConfigs());
            OperonValue result = filterList.evaluate();
            return result;
        }
        
        else {
            List<PathPart> pathParts = pathToFilter.getPathParts();
            int pathPartsSize = pathParts.size();
            
            for (int i = 0; i < pathPartsSize; i ++) {
                OperonValue evaluated = this.getFilterListExpression().evaluate();

                // Position-based filter
                if (evaluated instanceof NumberType) {
                    int n = (int) ((NumberType) evaluated).getDoubleValue();
                    if (n < 0) {
                        if (-n - 1 == pathPartsSize) {
                            resultPath.getPathParts().add(pathParts.get(i)); // gather all
                        }
                        else if (-n > pathPartsSize) {
                            continue;
                        }
                        else if (i == pathPartsSize + n) {
                            resultPath.getPathParts().add(pathParts.get(i));
                        }
                    }
                    else if (n == 0) {
                        break;
                    }
                    else if (n > pathPartsSize) {
                        continue;
                    }
                    else if ((n - 1) == i) {
                        resultPath.getPathParts().add(pathParts.get(i));
                    }
                }

                /*
                //
                // NOTE: StringType is not supported because Filter is applied
                //       on each iteration, which would cause multiple matches.
                //
                else if (evaluated instanceof StringType) {
                    System.out.println("KEY");
                    String key = ((StringType) evaluated).getJavaStringValue();
                    //key = key.substring(1, key.length() - 1); // remove double-quotes
                    System.out.println("KEY :: " + key);
                    int foundIndex = -1;
                    for (int j = 0; j < pathPartsSize; j ++) {
                        PathPart pp = pathParts.get(j);
                        if (pp instanceof KeyPathPart) {
                            String pathPartKey = ((KeyPathPart) pp).getKey();
                            if (key.equals(pathPartKey)) {
                                foundIndex = j;
                                break;
                            }
                        }
                    }
                    if (foundIndex >= 0) {
                        for (int j = 0; j < foundIndex; j ++) {
                            resultPath.getPathParts().add(pathParts.get(j));
                        }
                    }
                }*/

                else if (evaluated instanceof TrueType) {
                    resultPath.getPathParts().add(pathParts.get(i));
                }
                
                else if (evaluated instanceof FalseType) {
                    continue;
                }
                
                else if (evaluated instanceof Path) {
                    resultPath = (Path) evaluated;
                }
                
                else {
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FILTER", "TYPE", "Path-filter predicate did not evaluate into supported type.");
                }                
            }
        }
        return resultPath;
    }
    
    private OperonValue evaluateObj(ObjectType objToFilter) throws OperonGenericException {
        //System.out.println("FILTER :: evaluateObj");
        ObjectType objResult = new ObjectType(this.getStatement());
        
        if (this.getFilterListExpression() instanceof FilterList) {
            log.debug("FILTER :: FilterList");
            //System.out.println("FILTER obj :: FilterList");
            FilterList filterList = (FilterList) this.getFilterListExpression();
            log.debug("     FilterExprList size :: " + filterList.getFilterExprList().size());
            filterList.setValueToApplyAgainst(objToFilter);
            filterList.setResolvedConfigs(this.getResolvedConfigs());
            OperonValue result = filterList.evaluate();
            return result;
        }
        
        else {
            int objSize = objToFilter.getPairs().size();
            //System.out.println("BEFORE for-loop: objToFilter=" + objToFilter);
            Path currentPath = this.getStatement().getCurrentPath();
            for (int i = 0;  i < objSize; i ++) {
                PairType jsonPair = objToFilter.getPairs().get(i);
                //System.out.println("jsonPair.getPosition() == " + jsonPair.getPosition() + ", objSize == " + objSize);
                if (objSize > 1) {
                    //System.out.println("SETTING POSITION (objSize > 1): " + i);
                    jsonPair.setPosition(i + 1);
                }
                else {
                    if (jsonPair.getPosition() == 0) {
                        //System.out.println("SETTING POSITION: " + i);
                        jsonPair.setPosition(i + 1);
                    }
                    else {
                        //System.out.println("POSITION: " + jsonPair.getPosition() + ", PAIR: " + jsonPair.getKey());
                    }
                }
                
                String key = jsonPair.getKey();
                OperonValue value = jsonPair.getValue();
                ObjectType jsonObj = new ObjectType(this.getStatement());
                PairType pairCopy = new PairType(this.getStatement());
                pairCopy.setPair(jsonPair.getKey(), jsonPair.getValue());
                jsonObj.addPair(pairCopy);
                OperonValue jsonObjValue = jsonObj;
                //System.out.println("objValue setPosition=" + (i+1));
                
                // 
                // ATTRIBUTES
                // 
                Path newPath = (Path) currentPath.copy();
                PathPart kpp = new KeyPathPart(key.substring(1, key.length() - 1));
                newPath.getPathParts().add(kpp);
                if (currentPath.getObjLink() == null) {
                    newPath.setObjLink(objToFilter);
                }
                this.getStatement().setCurrentPath(newPath);
                //
                // END ATTRIBUTES
                //
                
                this.getFilterListExpression().getStatement().setCurrentValue(jsonObjValue);
                OperonValue evaluated = this.getFilterListExpression().evaluate();

                if (evaluated instanceof TrueType) {
                    objResult.addPair(jsonPair);
                }
                
                else if (evaluated instanceof FalseType) {
                    continue;
                }
                
                else if (evaluated instanceof NullType && value instanceof NullType) {
                    objResult.addPair(jsonPair);
                }
                
                else if (evaluated instanceof NullType && (value instanceof NullType == false)) {
                    continue;
                }
                
                // Position-based filter
                else if (evaluated instanceof NumberType) {
                    int targetPosition = (int) ((NumberType) evaluated).getDoubleValue();
                    //System.out.println("Position based filter: " + targetPosition + ", jsonPair pos=" + jsonPair.getPosition());
                    
                    if (targetPosition != 0) {
                        if (targetPosition < 0) {
                            targetPosition = objSize + targetPosition + 1;
                            //System.out.println("new targetPosition == " + targetPosition);
                            //
                            // #filterObjByNumberIndex4Test
                            if (targetPosition == 0) {
                                objResult.addPair(jsonPair);
                                continue;
                            }
                        }
                        
                        if (targetPosition == jsonPair.getPosition()) {
                            objResult.addPair(jsonPair);
                        }
                    }
                    else {
                        continue;
                    }
                }
                
                else if (evaluated instanceof StringType) {
                    StringType evaluatedJsonStr = (StringType) evaluated;
                    if (key.equals(evaluatedJsonStr.getStringValue())) {
                        objResult.addPair(jsonPair);
                    }
                    else {
                        continue;
                    }
                }
                
                /*
                else if (evaluated instanceof RawValue) {
                    RawValue evaluatedBV = (RawValue) evaluated;
                    if (key.equals(new String("\"" + evaluatedBV.getBytes() + "\""))) {
                        objResult.addPair(jsonPair);
                    }
                    else {
                        continue;
                    }
                }
                */
                
                else if (evaluated instanceof FunctionRef) {
                    //
                    // Invoke the FunctionRef
                    //
                    FunctionRef filterFnRef = (FunctionRef) evaluated;
                    // setup currentValue as ObjectType with the given Pair
                    ObjectType currentValueObjectType = new ObjectType(this.getStatement());
                    currentValueObjectType.addPair(jsonPair);
                    
                    filterFnRef.setCurrentValueForFunction(currentValueObjectType);
                    
                    OperonValue filterResult = filterFnRef.invoke();
                    
                    if (filterResult instanceof TrueType) {
                        objResult.addPair(jsonPair);
                    }
                    
                    else if (filterResult instanceof FalseType) {
                        continue;
                    }
                    
                    else {
                        Filter subFilter = new Filter(this.getStatement());
                        
                        // Sets the initial value for the filter (setValueToApplyAgainst does not do this)
                        filterResult.getStatement().setCurrentValue(currentValueObjectType); // added this to fix #37. This fix works.
                        subFilter.setFilterListExpression(filterResult);
                        subFilter.setValueToApplyAgainst(currentValueObjectType);
                        
                        ObjectType subFilterResult = (ObjectType) subFilter.evaluateObj(currentValueObjectType);
    
                        if (subFilterResult.getPairs().size() > 0) {
                            objResult.addPair(jsonPair);
                        }
                    }
                }
                
                else if (evaluated instanceof LambdaFunctionRef) {
                    //
                    // Invoke the LambdaFunctionRef
                    //
                    LambdaFunctionRef filterLfnRef = (LambdaFunctionRef) evaluated;
                    // setup currentValue as ObjectType with the given Pair
                    ObjectType currentValueObjectType = new ObjectType(this.getStatement());
                    currentValueObjectType.addPair(jsonPair);
                    filterLfnRef.setCurrentValueForFunction(currentValueObjectType);
                    OperonValue filterResult = filterLfnRef.invoke();
                    
                    if (filterResult instanceof TrueType) {
                        objResult.addPair(jsonPair);
                    }
                    
                    else if (filterResult instanceof FalseType) {
                        continue;
                    }
                    
                    else {
                        Filter subFilter = new Filter(this.getStatement());
                        
                        // Sets the initial value for the filter (setValueToApplyAgainst does not do this)
                        filterResult.getStatement().setCurrentValue(currentValueObjectType); // added this to fix #37. This fix works.
                        subFilter.setFilterListExpression(filterResult);
                        subFilter.setValueToApplyAgainst(currentValueObjectType);
                        
                        ObjectType subFilterResult = (ObjectType) subFilter.evaluateObj(currentValueObjectType);
    
                        if (subFilterResult.getPairs().size() > 0) {
                            objResult.addPair(jsonPair);
                        }
                    }
                }
                
                else if (evaluated instanceof ObjectType) {
                    ObjectType evaluatedObj = (ObjectType) evaluated;
                    for (int pairIndex = 0;  pairIndex < evaluatedObj.getPairs().size(); pairIndex ++) {
                        if (key.equals(evaluatedObj.getPairs().get(pairIndex).getKey())) {
                            objResult.addPair(jsonPair);
                        }
                    }
                }
                
                else if (evaluated instanceof ArrayType) {
                    ArrayType evaluatedArray = (ArrayType) evaluated;
                    
                    //System.out.println("CURRENT VALUE BEFORE LOOP :: " + this.getStatement().getCurrentValue());
                    
                    for (int valueIndex = 0; valueIndex < evaluatedArray.getValues().size(); valueIndex ++) {
                        ObjectType currentValueObjectType = new ObjectType(this.getStatement());
                        currentValueObjectType.addPair(jsonPair); // Does this have position?
                        //System.out.println("CV ::" + currentValueCopy);
                        Node evaluatedArrayItem = evaluatedArray.getValues().get(valueIndex);
                        
                        //System.out.println("TODO: sending value " + evaluatedArrayItem + " to filter. Against Key :: " + currentValueObjectType);
                        
                        // TODO: try against position (OperonValue has getPosition and setPosition)
                        Filter subFilter = new Filter(this.getStatement());
                        subFilter.setFilterListExpression(evaluatedArrayItem);
                        subFilter.setValueToApplyAgainst(currentValueObjectType);
                        
                        ObjectType subFilterResult = (ObjectType) subFilter.evaluateObj(currentValueObjectType);
    
                        //System.out.println("Subfilter result size :: " + subFilterResult.getPairs().size());
                        if (subFilterResult.getPairs().size() > 0) {
                            objResult.addPair(jsonPair);
                        }
                        //this.getStatement().setCurrentValue(currentValueCopy);
                    }
                    
                }
                
                else {
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FILTER", "TYPE", "Object-filter predicate did not evaluate into supported type. Got: " + this.getFilterListExpression().getClass().getName());
                }
            }

            return objResult;
        }
    }
    
    public void setValueToApplyAgainst(OperonValue value) {
        this.valueToApplyAgainst = value;
    }
    
    public OperonValue getValueToApplyAgainst() {
        return this.valueToApplyAgainst;
    }

    public void setConfigs(Node conf) {
        this.configs = conf;
    }
    
    public ObjectType getConfigs() throws OperonGenericException {
        if (this.configs == null) {
            return new ObjectType(this.getStatement());
        }
        this.configs = (ObjectType) this.configs.evaluate();
        return (ObjectType) this.configs;
    }

    public void setResolvedConfigs(Info i) {
        this.resolvedConfigs = i;
    }

    public Info getResolvedConfigs() {
        return this.resolvedConfigs;
    }

    public Info resolveConfigs(Statement stmt) throws OperonGenericException {
        Info info = new Info();
        
        //
        // We must check also for the resolvedConfigs, because FilterList sets them
        // but leaves configs as null. So if resolvedConfigs are set by FilterList
        // then we will use them. If both are null, then we use the default
        // configuration. Otherwise, if configs are set, but not resolved,
        // then we continue resolving them.
        //
        if (this.configs == null && this.getResolvedConfigs() == null) {
            return info;
        }
        else if (this.getResolvedConfigs() != null) {
            return this.getResolvedConfigs();
        }
        
        OperonValue currentValueCopy = stmt.getCurrentValue().copy();
        
        for (PairType pair : this.getConfigs().getPairs()) {
            String key = pair.getKey();
            switch (key.toLowerCase()) {
                case "\"parallel\"":
                    OperonValue parallelValue = pair.getEvaluatedValue();
                    if (parallelValue instanceof FalseType) {
                        info.parallel = false;
                    }
                    else {
                        info.parallel = true;
                    }
                    break;
                case "\"maxresults\"":
                    int maxResults = (int) ((NumberType) pair.getEvaluatedValue()).getDoubleValue();
                    info.maxResults = maxResults;
                    break;
                default:
                    break;
            }
        }
        this.setResolvedConfigs(info);
        stmt.setCurrentValue(currentValueCopy);
        return info;
    }

    class Info {
        public boolean parallel = false;
        public int maxResults = -1;
    }
    
    // Used to add the index when doing parallel-operations
    class PairWithIndex {
        int index;
        OperonValue value;
    }
}
