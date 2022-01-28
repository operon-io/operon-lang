/** OPERON-LICENSE **/
package io.operon.runner.node;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.ValueRef;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.aggregate.AggregateState;

import io.operon.runner.OperonContext;
import io.operon.runner.system.InputSourceDriver;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.exception.BreakSelect;

import org.apache.logging.log4j.LogManager;

//
// Aggregate is a pattern, which is used to aggregate json-values between multiple flows.
//  E.g. if timer triggers the Operon for 10 times, we could set the aggregate to fire every
//  5 items, therefore firing twice.
//
// The most basic aggregation-strategy is to just add json-values into an array.
//
public class Aggregate extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(Aggregate.class);

    private ObjectType configs;

    private String id;

    //
    // This may be computable expr.
    //
    // Aggregated values are mapped by correlationId.
    // 
    // NOTE: correlationId is associated with the aggregated values 
    //       ONLY when firePredicate triggers, otherwise the result 
    //       does not have correlationId.
    //
    private Node correlationIdExpr;

    // The evaluated correlationId.
    // TODO: could be optimized, if we detect that correlationId is actually constant (i.e. the correlationIdExpr is of type StringType, boolean etc.)
    //       That would mean that we don't have to calculate this at each round.
    private String correlationId; // NOTE: if not given then do not put it in the header. Otherwise: [{"correlationId": "...", "values": [...]}]

    // Inline predicate-expression, e.g. "$.Second % 2 = 0"
    // Or FunctionRef (fr), or LambdaFunctionRef (lfr).
    //
    // FR and LFR:
    //   0 params: evaluate based on whatever condition
    //   1 param: the $new message
    //   2 params: the $old (already aggregated values) and the $new message
    private Node firePredicate;
    
    // FunctionRef can be used as an aggregation-method.
    //
    // It receives two params: $old, $new
    // where $old is the old message, which has already been aggregated,
    // and $new is the new value which is to be aggregated.
    //
    // NOTE: the result of this is the aggregated-value, which could e.g.
    //       reduced to single-value.
    private Node aggregateFunction;
    
    public Boolean hasTimeout; // Compiler sets this information bit.
    public Long timeoutMillis;
    private Node timeoutContinuation; // After timeout this node will be evaluated. Should be next node from AST after aggregate.
    
    private static Map<String, AggregateState> aggregateStates;

    public Aggregate(Statement stmnt, String id) {
        super(stmnt);
        synchronized (this) {
            //System.out.println("new Aggregate");
            this.setId(id);
            
            // Initialize aggregateTimeoutState
            if (getAggregateStates() == null) {
                this.aggregateStates = Collections.synchronizedMap(new HashMap<String, AggregateState>()); // new ConcurrentHashMap<String, AggregateState>();
            }
    
            if (getAggregateStates().get(getId()) == null) {
                AggregateState aggregateState = new AggregateState(this);
                getAggregateStates().put(this.getId(), aggregateState);
                
                //System.out.println(this.getId() + " :: added AggregateState");
            }
        }
    }

    public /*synchronized*/ OperonValue evaluate() throws BreakSelect, OperonGenericException {
        log.debug("ENTER Aggregate.evaluate(). Stmt: " + this.getStatement().getId());
        //System.out.println("Aggregate id: " + this.getId() + " :: ENTER Aggregate.evaluate()");
        OperonValue currentValue = this.getStatement().getCurrentValue();
        
        //System.out.println("cv=" + currentValue); // it seems that cv gets in correctly, i.e. it is not changed before
        //OperonValue currentValueCopy = currentValue.copy();

        // Calculate the correlationId -value
        if (this.getCorrelationIdExpr() != null) {
            this.correlationIdExprToString(currentValue);
        }

        //System.out.println("AGGREGATE :: CV :: " + currentValueCopy + ", stmt id :: " + this.getStatement().getId());
        //log.debug("Aggregate :: configs :: " + this.getConfigs()); // don't log, would try to evaluate the firePredicate
        log.debug("Retrieve array for key :: " + this.getCorrelationId());
        
        //
        // Initialize aggregationResult, if not initialized:
        //
        Node agg = getAggregateStates().get(this.getId()).getResult().get(this.getCorrelationId());

        // 
        // Initialize with an ArrayType if no aggregateFunction was given
        //
        if (agg == null && this.getAggregateFunction() == null) {
            ArrayType initialArray = new ArrayType(this.getStatement());
            log.debug("Adding initial array for key :: " + this.getCorrelationId());
            getAggregateStates().get(this.getId()).getResult().put(this.getCorrelationId(), initialArray);
            agg = getAggregateStates().get(this.getId()).getResult().get(this.getCorrelationId());
        }
        
        //
        // Initialize with OperonValue if aggregateFunction was given
        //  TODO: should take the initial value from config!!!
        //
        else if (agg == null) {
            if (this.getAggregateFunction() != null) {
                log.debug("Aggregate with lfnr");
                OperonValue aggFnRefTypeTest = (OperonValue) this.getAggregateFunction().evaluate();
            
                if (aggFnRefTypeTest instanceof LambdaFunctionRef) {
                    //LambdaFunctionRef lfnr = (LambdaFunctionRef) aggFnRefTypeTest;
                    //Node initialValue = lfnr.getParams().get("$initial");
                    //NumberType initialValueJV = (NumberType) initialValue.evaluate();
                    
                    NumberType init = new NumberType(this.getStatement());
                    init.setDoubleValue(0.0);
                    OperonValue initialValue = init;// new OperonValue(this.getStatement());
                    
                    log.debug("INITIAL :: " + initialValue);
                    
                    log.debug("Adding initial value for key :: " + this.getCorrelationId());
                    getAggregateStates().get(this.getId()).getResult().put(this.getCorrelationId(), initialValue);
                    agg = getAggregateStates().get(this.getId()).getResult().get(this.getCorrelationId());
                    log.debug("Initial value set");
                }
                
                else {
                    log.debug("Aggregate :: cannot set initial value");
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "AGGREGATE", "INITIAL_VALUE", "Cannot set initial value");
                }
            }
            
            //NumberType init = new NumberType(this.getStatement());
            //init.setValue(0.0);
            //OperonValue initialValue = init;// new OperonValue(this.getStatement());
        }

        boolean isTimeout = this.getHasTimeout();

        AggregateState aggState = getAggregateStates().get(this.getId());
        log.debug(">> 1");
        
        if (aggState == null) {
            log.debug(this.getId().substring(0, 3) + " :: aggState null");
            //System.exit(1);
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "AGGREGATE", "STATE", "State was not set");
        }

        // ==========================
        //
        // firePredicate
        //
        //
        //
        // ==========================
        
        if (this.getFirePredicate() != null) {
            synchronized (this) {
                log.debug("Evaluating firePredicate");
                
                // FIXME: current-value should be set first, otherwise it could be set somewere else!
                //System.out.println("FIRE-PRED: CV=" + currentValue);
                this.getFirePredicate().getStatement().setCurrentValue(currentValue.copy());
                OperonValue predicateResult = (OperonValue) this.getFirePredicate().evaluate();
    
                // @param type
                //  0 = timeout
                //  1 = interval
                //  2 = firepredicateExpr or firepredicateFunctionRef
                short type = (short) 2;
                
                if (predicateResult instanceof FunctionRef) {
                    FunctionRef firePredicateFnRef = (FunctionRef) predicateResult;
                    //System.out.println("FunctionRef params size :: " + firePredicateFnRef.getParams().size());
                    //System.out.println("FunctionRef params :: " + firePredicateFnRef.getParams());
                    if (firePredicateFnRef.getParams().size() == 0) {
                        firePredicateFnRef.getParams().clear();
                    }
                    
                    else if (firePredicateFnRef.getParams().size() == 1) {
                        firePredicateFnRef.getParams().clear();
                        firePredicateFnRef.getParams().add(currentValue.copy());
                    }
                    
                    else if (firePredicateFnRef.getParams().size() == 2) {
                        firePredicateFnRef.getParams().clear();
                        OperonValue currentResult = this.getAggregationResult((short) 2, currentValue.copy());
                        
                        // old
                        // TODO: should we offer name?
                        firePredicateFnRef.getParams().add(currentResult.copy()); // should we copy here?
                        
                        // new
                        firePredicateFnRef.getParams().add(currentValue.copy());
                    }
                    
                    else {
                        ErrorUtil.createErrorValueAndThrow(this.getStatement(), "AGGREGATE", "ERROR", "Expected 0 or 1 params.");
                    }
                    
                    firePredicateFnRef.setCurrentValueForFunction(currentValue.copy());
                    predicateResult = (OperonValue) firePredicateFnRef.invoke();
                }
                
                //
                // TODO: different amount of params compared to FunctionRef
                //  i.e. this does not support $new / $old, $new -params yet!
                //
                else if (predicateResult instanceof LambdaFunctionRef) {
                    LambdaFunctionRef firePredicateFnRef = (LambdaFunctionRef) predicateResult;
                    firePredicateFnRef.getParams().clear();
                    firePredicateFnRef.setCurrentValueForFunction(currentValue.copy());
                    predicateResult = (OperonValue) firePredicateFnRef.invoke();
                }
                
                if (predicateResult instanceof TrueType) {
                    log.debug("firePredicate was true. Return aggregationResult");
                    log.debug(">> Pred res true -> getAggregationResult");
                    //System.out.println(">> FirePredicate was true");
                    synchronized (this) {
                        OperonValue result = this.getAggregationResult(type, currentValue.copy());
                        return this.returnAggregationResult(result, type);
                    }
                }
                
                else if (predicateResult instanceof FalseType) {
                    // noop
                    //System.out.println(">> FirePredicate was false");
                }
                
                else {
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "AGGREGATE", "ERROR", "Unsupported type for firePredicate");
                }
            }
        }

        // ==========================
        //
        // timeout And timeoutRunning
        //
        //
        //
        // ==========================

        Long timeoutStart = aggState.getTimeoutStart();

        if (isTimeout && timeoutStart == null) {
            if (this.getTimeoutMillis() == null) {
                this.resolveTimeoutMillis();
            }
            Date now = new Date();
            aggState.setTimeoutStart(now.getTime());
            aggState.setTimeoutDuration(this.getTimeoutMillis());

            this.getStatement().getOperonContext().setIsReady(true, "Aggregate"); // switch to ready, so ISD may send new items
            //System.out.println("Aggregate :: start timeout: " + this.getId());
            
            throw new BreakSelect(currentValue);
        }
        
        // ==========================
        // firePredicate was False Or
        // timeoutRunning
        //
        //
        //
        // ==========================
        
        else {
            // FirePredicate false or timeout running, therefore do-aggregate:
            // TODO: should we tell the type here as well (as in the above blocks)?
            //System.out.println("Aggregate :: doAggregate");
            log.debug(">> do aggregate");
            return this.doAggregate((OperonValue) currentValue.copy());
        }

    }

    public OperonValue continueAggregateAfterTimeout(AggregateState aggState) throws OperonGenericException {
        System.out.println("Aggregate :: timeout completed");
        aggState.setTimeoutStart(null);
        this.getStatement().getOperonContext().setIsReady(false, "Aggregate"); // switch to not ready, so ISD may not send new items
        // @param type
        //  0 = timeout
        //  1 = interval
        //  2 = firepredicateExpr or firepredicateFunctionRef
        short type = (short) 0;
        
        OperonValue result = this.getAggregationResult(type, null); // currentValue is set to null (we don't have one at this point)
        OperonValue aggregationResult = this.returnAggregationResult(result, type);
        
        Node continuation = this.getTimeoutContinuation();
        if (continuation != null) {
            continuation.getStatement().setCurrentValue(aggregationResult);
            return continuation.evaluate();
        }
        else {
            return aggregationResult;
        }
    }

    private void correlationIdExprToString(OperonValue currentValue) throws OperonGenericException {
        // Must be evaluated against currentValue
        OperonValue currentValueCopy = currentValue.copy();
        OperonValue correlationIdResult = (OperonValue) this.getCorrelationIdExpr().evaluate();
        while (correlationIdResult instanceof StringType == false) {
            correlationIdResult = (OperonValue) correlationIdResult.evaluate();
        }
        String corrId = ((StringType) correlationIdResult).getJavaStringValue();
        //log.debug("Aggregate :: calculated correlationId :: " + corrId);
        this.setCorrelationId(corrId);
        this.getStatement().setCurrentValue(currentValueCopy); // ensure that we didn't change the currentValue
    }

    private OperonValue doAggregate(OperonValue currentValueCopy) throws BreakSelect, OperonGenericException {
        //System.out.println(">>>>> Aggregate :: " + currentValueCopy);
        // aggregate with aggregateFunction, if such is defined (not null)
        if (this.getAggregateFunction() != null) {
            log.debug("Aggregate with fn");
            OperonValue aggFnRefTypeTest = (OperonValue) this.getAggregateFunction().evaluate();
            
            if (aggFnRefTypeTest instanceof FunctionRef) {
                FunctionRef aggFnRef = (FunctionRef) aggFnRefTypeTest;
                
                if (aggFnRef.getParams().size() != 2) {
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "AGGREGATE", "ERROR", "Expected two params");
                }
                
                aggFnRef.getParams().clear();
                OperonValue currentResult = this.getAggregationResult((short) 2, currentValueCopy);
                
                // old
                // TODO: should we offer name?
                aggFnRef.getParams().add(currentResult.copy()); // should we copy here?
                
                // new
                aggFnRef.getParams().add(currentValueCopy.copy());
                
                aggFnRef.setCurrentValueForFunction(currentValueCopy.copy());
                OperonValue aggFnResult = (OperonValue) aggFnRef.invoke();
                
                Node aggRes = getAggregateStates().get(this.getId()).getResult().put(this.getCorrelationId(), aggFnResult);
            }
            
            else if (aggFnRefTypeTest instanceof LambdaFunctionRef) {
                log.debug("Aggregate with LambdaFunctionRef");
                
                // DEBUG: try commenting, there's copy error later on, this could cause it
                //System.out.println("Aggregate with LambdaFunctionRef :: value to aggregate :: " + currentValueCopy);
                LambdaFunctionRef aggFnRef = (LambdaFunctionRef) aggFnRefTypeTest;
                
                if (aggFnRef.getParams().size() != 2) {
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "AGGREGATE", "ERROR", "Expected two params.");
                }
                
                log.debug("Aggregate with LambdaFunctionRef :: clear params");
                aggFnRef.getParams().clear();
                OperonValue currentResult = this.getAggregationResult((short) 2, currentValueCopy);
                
                // In the first round the result is null, therefore set with initial value
                // TODO: initial value should perhaps be given in the function-parameter!
                //  i.e. we'd had params: $initial, $old, $new
                // $initial would require set value from user, i.e. it would not be placeholder!
                if (currentResult == null) {
                    log.debug("Aggregate with LambdaFunctionRef :: currentResult was null, set with cvc");
                    currentResult = currentValueCopy.copy();
                    log.debug("Aggregate with LambdaFunctionRef :: currentResult :: " + currentResult);
                }
                
                // GOTCHA! The problem is that the initial-value is set with OperonValue, but it is not null
                //         either! It should be set to null...
                else {
                    log.debug("Aggregate with LambdaFunctionRef :: currentResult was NOT null");
                    log.debug("Aggregate with LambdaFunctionRef :: currentResult :: " + currentResult);
                }
                
                log.debug("Aggregate with LambdaFunctionRef :: set params");
                log.debug("Aggregate with LambdaFunctionRef :: currentResult :: " + currentResult);
                
                // old
                aggFnRef.getParams().put("$old", currentResult.copy()); // should we copy here? FIXME: causes error.
                
                log.debug("Aggregate with LambdaFunctionRef :: old :: " + aggFnRef.getParams().get("$old"));
                
                // new
                aggFnRef.getParams().put("$new", currentValueCopy.copy());
                
                log.debug("Aggregate with LambdaFunctionRef :: new :: " + aggFnRef.getParams().get("$new"));
                
                aggFnRef.setCurrentValueForFunction(currentValueCopy.copy());
                OperonValue aggFnResult = (OperonValue) aggFnRef.invoke();
                
                log.debug("Aggregate with LambdaFunctionRef :: got result :: " + aggFnResult);
                
                Node aggRes = getAggregateStates().get(this.getId()).getResult().put(this.getCorrelationId(), aggFnResult);
            }
        }
        
        else {
            log.debug("Aggregate fn was null");
            // The default aggregation strategy. Adds items into ArrayType.
            synchronized (this) {
            //System.out.println("correlationId :: " + this.getCorrelationId() + ", aggregateId :: " + this.getId());
            //System.out.println("ThreadId :: " + Thread.currentThread().getId());
                Map<String, Node> asResult = getAggregateStates().get(this.getId()).getResult();
                Node aggRes = asResult.get(this.getCorrelationId());
                if (this.getAggregateFunction() == null) {
                    //System.out.println(">>>>> agg :: add :: " + currentValueCopy);
                    //System.out.println(">>>>> agg :: add :: check 1 :: cv :: " + currentValueCopy);
                    if (aggRes == null) {
                        //System.out.println(">>>>> aggRes was null");
                        //System.out.println(getAggregateStates());
                        //System.out.println("asResult :: " + asResult); // not null, but empty map (no key for correlation)

                        if (asResult.isEmpty()) {
                            //System.out.println("asResult :: empty");
                            ArrayType asResultArray = new ArrayType(this.getStatement());
                            asResultArray.getValues().add(currentValueCopy);
                            asResult.put(this.getCorrelationId(), asResultArray);
                            //System.out.println("asResult :: now :: " + getAggregateStates().get(this.getId()).getResult());
                        }
                    } else {
                        ((ArrayType) aggRes).getValues().add(currentValueCopy);
                    }
                    //System.out.println(">>>>> agg :: add :: check 2");
                    //System.out.println(">>>>> agg :: after add :: " + ((ArrayType) aggRes).getValues());
                }
            }
            
        }
        
        log.debug("exit evaluateSelectStatement");
        //System.out.println("Throw BreakSelect");
        log.debug("Throw BreakSelect");
        throw new BreakSelect();
    }

    // @param type
    //  0 = timeout
    //  1 = interval
    //  2 = firepredicateExpr or firepredicateFunctionRef
    private synchronized OperonValue getAggregationResult(short type, OperonValue currentValueCopy) throws OperonGenericException {
        log.debug("getAggregationResult, type = " + type);
        //System.out.println(">>> getAggregationResult :: add cv :: " + currentValueCopy);
        // If triggered by timeout (0) or interval (1), then retrieve _all_ keys.
        // Otherwise (2) retrieve only the current correlationId.
        
        AggregateState aggState = getAggregateStates().get(this.getId());
        
        // TODO: if aggState empty, then add new.
        
        Map<String, Node> aggResult = aggState.getResult();
        
        if (aggResult.isEmpty()) {
            //System.out.println("aggResult :: empty");
            ArrayType aggResultArray = new ArrayType(this.getStatement());
            aggResult.put(this.getCorrelationId(), aggResultArray);
            //System.out.println("aggResult :: now :: " + getAggregateStates().get(this.getId()).getResult());
        }
        
        Node aggregationResult = null;
        
        //System.out.println(">>> getAggregationResult :: 1");
        
        //
        // Default result with ArrayType
        //
        if (this.getAggregateFunction() == null) {
        
            // Type: 0 (timeout) or 1 (interval)
            if (type == 0 || type == 1) {
                aggregationResult = new ArrayType(this.getStatement());
                
                // Add as an object: {"correlationId": "...", "values": []}
                // aggResult is mapped by correlationId
                for (Map.Entry<String, Node> entry : aggResult.entrySet()) {
                    ObjectType aggObj = new ObjectType(this.getStatement());
                    
                    PairType aggObjCorrelationIdPair = new PairType(this.getStatement());
                    StringType correlationIdJStr = new StringType(this.getStatement());
                    correlationIdJStr.setFromJavaString(entry.getKey());
                    aggObjCorrelationIdPair.setPair("\"correlationId\"", correlationIdJStr);
                    aggObj.addPair(aggObjCorrelationIdPair);
                    
                    PairType aggObjValuesPair = new PairType(this.getStatement());
                    
                    ArrayType entryValue = (ArrayType) entry.getValue();
                    // Add the first received input into aggregation-array aswell:
                    //System.out.println(">>> getAggregationResult :: add cv (0, 1 type):: " + currentValueCopy);
                    if (currentValueCopy != null) {
                        entryValue.getValues().add(currentValueCopy);
                    }
                    
                    aggObjValuesPair.setPair("\"values\"", entryValue);
                    aggObj.addPair(aggObjValuesPair);
                    
                    ((ArrayType) aggregationResult).getValues().add(aggObj);
                }
            }
            
            // Retrieve only by current correlationId
            else {
                log.debug("get RES :: TYPE 2");
                aggregationResult = aggResult.get(this.getCorrelationId());
        
                ArrayType aggregationResultArr = (ArrayType) aggregationResult;
                // Add the first received input into aggregation-array aswell:
                List<Node> aggregatedValues = aggregationResultArr.getValues();
                aggregatedValues.add(currentValueCopy);
            }
        }
        
        // aggregationFunction was defined, so we cannot tell what the result-type is,
        // therefore we will return it as and OperonValue
        else {
            aggregationResult = aggResult.get(this.getCorrelationId());
        }
        
        //System.out.println(">>> getAggregationResult [aggID=" + this.getId() + "] :: return :: " + aggregationResult);
        return (OperonValue) aggregationResult;
    }

    // @param type
    //  0 = timeout
    //  1 = interval
    //  2 = firepredicateExpr or firepredicateFunctionRef
    private synchronized OperonValue returnAggregationResult(OperonValue aggregationResult, short type) {
        // Remove all entries
        if (type == 0 || type == 1) {
            getAggregateStates().get(this.getId()).getResult().clear();
        }
        
        // Remove specific entry, by correlationId
        else if (type == 2) {
            //System.out.println("Removing entries");
            getAggregateStates().get(this.getId()).getResult().remove(this.getCorrelationId());
        }
        
        //this.getStatement().setCurrentValue(aggregationResult);
        return aggregationResult;
    }

    public void setConfigs(ObjectType conf) {
        this.configs = conf;
    }
    
    public ObjectType getConfigs() {
        return this.configs;
    }

    // 
    // Read configs and resolve what the timeout is in milliseconds.
    // 
    public Long resolveTimeoutMillis() throws OperonGenericException {
        //System.out.println("AGGREGATE :: Read configs");
        Double doubleValue = null;
        for (PairType pair : this.getConfigs().getPairs()) {
            if (pair.getKey().equals("\"timeoutMillis\"")) {
                OperonValue value = (OperonValue) pair.getValue();
                NumberType numberValue = (NumberType) value.evaluate();
                doubleValue = numberValue.getDoubleValue();
                this.setTimeoutMillis(doubleValue.longValue());
                break;
            }
        }
        if (doubleValue != null) {
            return doubleValue.longValue();
        }
        
        else {
            return null;
        }
    }

    public void setId(String i) {
        this.id = i;
    }
    
    public String getId() {
        return this.id;
    }

    public void setCorrelationIdExpr(Node idExpr) {
        this.correlationIdExpr = idExpr;
    }
    
    public Node getCorrelationIdExpr() {
        return this.correlationIdExpr;
    }

    public void setCorrelationId(String id) {
        this.correlationId = id;
    }
    
    public String getCorrelationId() {
        return this.correlationId;
    }

    public void setFirePredicate(Node pred) {
        this.firePredicate = pred;
    }
    
    public Node getFirePredicate() {
        return this.firePredicate;
    }

    public void setAggregateFunction(Node aggFn) {
        this.aggregateFunction = aggFn;
    }
    
    public Node getAggregateFunction() {
        return this.aggregateFunction;
    }

    public void setTimeoutMillis(Long millis) {
        this.timeoutMillis = millis;
    }
    
    public Long getTimeoutMillis() {
        return this.timeoutMillis;
    }

    public void setTimeoutContinuation(Node cont) {
        this.timeoutContinuation = cont;
    }
    
    public Node getTimeoutContinuation() {
        return this.timeoutContinuation;
    }

    public String toString() {
        return "Aggregate";
    }

    // synchronizing this method causes timeout to block indefinetely
    public static /*synchronized*/ Map<String, AggregateState> getAggregateStates() {
        return aggregateStates;
    }

    public void setHasTimeout(Boolean t) {
        this.hasTimeout = t;
    }

    public Boolean getHasTimeout() {
        return this.hasTimeout;
    }
}