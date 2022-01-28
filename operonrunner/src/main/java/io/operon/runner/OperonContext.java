/** OPERON-LICENSE **/
package io.operon.runner;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.nio.charset.Charset;

// signaling:
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;

// operon:
import io.operon.runner.util.RandomUtil;
import io.operon.runner.model.InputSource;
import io.operon.runner.model.OperonConfigs;
import io.operon.runner.model.State;
import io.operon.runner.model.aggregate.AggregateState;
import io.operon.runner.model.signal.*;
import io.operon.runner.system.InputSourceDriver;
import io.operon.runner.statement.*;
import io.operon.runner.node.Node;
import io.operon.runner.node.Aggregate;
import io.operon.runner.node.Operator;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.*;

import org.apache.logging.log4j.LogManager;

/**
 * 
 * Context -class acts as a container for the query elements.
 * 
 */
public class OperonContext extends BaseContext implements Context, java.io.Serializable {
    private static Logger log = LogManager.getLogger(OperonContext.class);

    private FromStatement fromStatement;
    private SelectStatement selectStatement;
    
    private volatile boolean shutdown = false;

    private String contextId;
    private String transactionId;
    
    // 
    // When ready, then ISD may send new item for processing.
    // Aggregate-pattern may switch this to true/false
    // when aggregation occurs. Otherwise this is toggled 
    // only after evaluating the Select-statement.
    // 
    private boolean isReady = true;
    private boolean isTest = false;
    
    private OperonValue outputOperonValue;
    private Exception exception;
    private ErrorValue errorValue;
    private ExceptionHandler exceptionHandler;
    private String query;
    private OperonTestsContext operonTestsContext = null;
    private OperonContextManager ocm;
    
    private List<LetStatement> allLetStatements; // used to maintain references to all the LetStatements, so we can reset them correctly.
    private State state; // controlled by functions in the "state" -namespace.
    
    public static EmptyContext emptyContext = new EmptyContext();
    
    private static transient ExecutorService signalingExecutorService;
    
    public OperonContext() throws IOException {
        super();
        log.debug("create OperonContext");
        
        this.setExceptionStatement(new DefaultStatement(this));
        this.getExceptionStatement().setId("ErrorStatement");
        this.state = new State(this);
        String contextId = String.valueOf(Math.random());
        contextId = contextId.substring(2, contextId.length());
        this.setContextId(contextId);
        this.setOwnNamespace("");
        this.allLetStatements = new ArrayList<LetStatement>();
        
        //
        // This tests Redisson for distributed list.
        //  sudo yum install redis
        //  sudo systemctl start redis
        //  redis is installed into: /usr/bin/ --> redis-server, redis-cli
        //  try: redis-cli --> keys *
        //
    }

    public OperonContextManager getOperonContextManager() {
        return this.ocm;
    }

    public void start(OperonContextManager.ContextStrategy contextStrategy) {
        log.debug("OperonContext :: start()");
        this.shutdown = false;
        
        //
        // Load ISD if it is null (is null when context is loaded from serialized form)
        //
        InputSource fromInputSource = this.getFromStatement().getInputSource();
        InputSourceDriver isd = fromInputSource.getInputSourceDriver();
        if (isd == null) {
            fromInputSource.loadInputSourceDriver();
            isd = fromInputSource.getInputSourceDriver();
        }
        if (contextStrategy == null) {
            contextStrategy = OperonContextManager.ContextStrategy.SINGLETON;
        }
        try {
            ocm = new OperonContextManager(this, contextStrategy);
            
            ObjectType configs = this.getSelectStatement().getConfigs();
            
            //
            // HeartBeat is a signal that is sent with regular interval.
            // Upon receiving signal, Operon will not evaluate the SelectStatement,
            // but will do any required housekeeping, e.g. check and fire the
            // aggregation-timeouts.
            //
            if (signalingExecutorService == null && configs.hasKey("\"heartBeat\"")) {
                System.out.println("Starting HeartBeat");
                NumberType heartBeatNumber = (NumberType) configs.getByKey("heartBeat").evaluate();
                long heartBeatLong = (long) heartBeatNumber.getDoubleValue();
                System.out.println("  - interval: " + heartBeatLong);
                //
                // TODO: start background Thread, which creates HeartBeatSignal
                //       with the given interval.
                //
                signalingExecutorService = Executors.newFixedThreadPool(3);
                this.registerHeartBeat(heartBeatLong);
            }
            
            isd.start(ocm);
        } catch (Exception e) {
            log.debug("OperonContext :: start() Exception");
            System.err.println("ERROR SIGNAL: " + e.getMessage());
            throw new RuntimeException("ERROR SIGNAL " + e.getMessage());
        }
    }
    
    public void registerHeartBeat(long duration) {
        SignalService sigServ = new SignalService(this, duration);
        signalingExecutorService.submit(sigServ);
        System.out.println("  - signal service registered");
    }

    //
    // This is run when the signal is triggered
    //    
    public void heartBeatAction() {
        //System.out.println("  - signal action called");
        Date now = new Date();
        Long nowMillis = now.getTime();
        Map<String, AggregateState> aggregateStates = Aggregate.getAggregateStates();
        if (aggregateStates != null) {
            for (Map.Entry<String, AggregateState> entry : aggregateStates.entrySet()) {
                AggregateState aggState = entry.getValue();
                if (aggState.getTimeoutStart() != null) {
                    if (aggState.getTimeoutStart() + aggState.getTimeoutDuration() <= nowMillis) {
                        Aggregate agg = aggState.getAggregate();
                        System.out.println("Continue aggregate");
                        
                        //
                        // NOTE: not bridget with evaluateSelectStatement()
                        //
                        //  improve by refactoring evaluateSelectStatement() and its errorHandlers for reuse here.
                        // 
                        try {
                            OperonValue result = agg.continueAggregateAfterTimeout(aggState);
                        } catch (OperonGenericException oge) {
                            System.err.println("Error while evaluating aggregate: " + oge.getMessage());
                        }
                    }
                }
            }
        }
    }
    
    //
    // This is called from InputSourceDriver
    //
    public void setInitialValue(OperonValue iv) throws OperonGenericException {
        FromStatement from = this.getFromStatement();
        from.getRuntimeValues().put("$", iv); // rootValue
        from.getRuntimeValues().put("@", iv); // currentValue
        Map<String, Context> modules = this.getModules();
        // Loop through modules and set the initial value for each:
        for (Map.Entry<String, Context> moduleEntry : modules.entrySet()) {
            setModuleInitialValue(moduleEntry.getValue(), iv);
        }
    }
    
    // Recursively set the initial value for all submodules
    private void setModuleInitialValue(Context module, OperonValue iv) throws OperonGenericException {
        module.getRuntimeValues().put("$", iv); // rootValue
        module.getRuntimeValues().put("@", iv); // currentValue
        Map<String, Context> modules = module.getModules();
        if (modules != null && modules.size() > 0) {
            // Loop through modules and set the initial value for each:
            for (Map.Entry<String, Context> moduleEntry : modules.entrySet()) {
                setModuleInitialValue(moduleEntry.getValue(), iv);
            }
        }
    }
    
    //
    // OperonRunner queries this: when shutdown, then stop thread.
    //
    public boolean isShutdown() {
        return this.shutdown;
    }
    
    //
    // ISD stops sending requests to the context.
    //
    public void shutdown() {
        this.getFromStatement().getInputSource().getInputSourceDriver().stop();
        this.shutdown = true;
    }
    
    public OperonValue evaluateSelectStatement() throws OperonGenericException {
        log.debug("OperonContext :: ENTER SELECT.EVALUATE()");
        assert (selectStatement != null): "Context :: evaluateSelectStatement, selectStatement was null.";
        
        this.setIsReady(false, "SelectStmt");
        this.setException(null);
        long startTime = System.nanoTime();
        
        OperonValue currentValue = this.getFromStatement().getRuntimeValues().get("$");
        assert(currentValue != null): "Context :: evaluateSelectStatement, initial-value was null.";
        
        // Check the From-statement's OperonValue against possible OperonValueConstraints.
        // This throws an Exception if constraint was violated.
        // Do try-catch so we could move exception for operon.io's own errorhandler.
        if (this.getFromStatement().getOperonValueConstraint() != null) {
            try {
                OperonValueConstraint.evaluateConstraintAgainstOperonValue(currentValue, this.getFromStatement().getOperonValueConstraint());
            } catch (OperonGenericException oge) {
                OperonValue result = handleExceptionCatch(oge);
                this.setIsReady(true, "FromStmt :: exception");
                return result;
            }
        }
        
        selectStatement.setCurrentValue(currentValue);
        
        //
        // use the OperonConfigs.namedValues (Map) to set the pregiven namedValues.
        // NOTE: if e.g. Eager-evaluation is wanted, then the value must be defined in the query with this option enabled.
        //
        Map<String, OperonValue> namedValues = this.getConfigs().getNamedValues();
        
        //System.out.println("namedValues size :: " + namedValues.size());
        
        //
        // First go through the LetStatements and compare to given namedValues.
        // If no matching LetStatement-key was found, then it must be added first.
        //
        for (Map.Entry<String, OperonValue> entry : namedValues.entrySet()) {
            //System.out.println("namedValue :: " + entry.getKey());
            if (this.getLetStatements().get(entry.getKey()) == null) {
                //System.out.println("  --> not found from LetStatement's " + entry.getKey());
                LetStatement letStatement = new LetStatement(this);
                letStatement.setNode(entry.getValue());
                this.getLetStatements().put(entry.getKey(), letStatement);
            }
        }
        
        //
        // Go through LetStatements: bind named values and do eager-evaluations.
        // If named-value is already found, then binding overrides the value defined in the query.
        //
        for (Map.Entry<String, LetStatement> entry : this.getLetStatements().entrySet()) {
		    LetStatement letStatement = (LetStatement) entry.getValue();
		    String letKey = entry.getKey();
		    //System.out.println("Let key: " + letKey);
		    
		    letStatement.setCurrentValue(currentValue);
		    
		    //
		    // Do not allow setting named-value if resettype is NEVER
		    //
		    
		    // LetStatement's configs must be resolved before setting named-values,
		    // because some of them might try to override value which has resettype NEVER
		    letStatement.resolveConfigs();
		    
		    OperonValue nv = namedValues.get(letKey);
		    
		    if (nv != null) {
		        if (letStatement.getResetType() != LetStatement.ResetType.NEVER) {
    		        //System.out.println("Found matching value-binding for " + letKey + " , setting it now with: " + nv);
    		        //System.out.println("ResetType == " + letStatement.getResetType());
    		        letStatement.setCurrentValue(nv);
    		        letStatement.setNode(nv);
		        }
		        else {
		            ErrorValue err = ErrorUtil.createErrorValue(nv.getStatement(), "NAMED_VALUE", "UPDATE", "Could not bind named value when update -option is NEVER");
		            OperonGenericException oge = new OperonGenericException(err);
		            OperonValue result = handleExceptionCatch(oge);
                    this.setIsReady(true, "FromStmt :: exception");
                    return result;
		        }
		    }
		    
		    
		    //
		    // Eager-evaluate Let-statements that are so configured, i.e. {"evaluate": "eager"}:
		    //
		    if (letStatement.getEvaluateType() == LetStatement.EvaluateType.EAGER) {
		        letStatement.evaluate();
		    }
	    }
        
        OperonValue result = null;
        
        try {
            //System.out.println("Evaluate selectStatement");
            result = this.getSelectStatement().evaluate(); // possible constraint is checked in SelectStatement.
            //System.out.println("Evaluate selectStatement :: ready to output result");
        } catch (BreakSelect b) {
            log.debug("Evaluate selectStatement :: BreakSelect");
            //System.out.println("Evaluate selectStatement :: breaked :: " + b.getBreakType());
            this.synchronizeState();
            long stopTime = System.nanoTime();
            long elapsedTime = stopTime - startTime;
            log.debug("Execution time: " + elapsedTime + " ns (" + elapsedTime / 1000000 + " ms.)");
            if (b.getBreakType() == (short) 0) {
                result = new EmptyType(this.getSelectStatement());
            }
            else {
                result = b.getOperonValueOnBreak();
            }
            this.setIsReady(true, "SelectStmt breakSelect");
            log.debug("Evaluate selectStatement :: BreakSelect done");
            return result;
        } catch (BreakLoopException ble) {
            StringType err = new StringType(this.getSelectStatement());
            err.setFromJavaString("Invalid use of Break -statement: nothing to Break from.");
            OperonGenericException oge = new OperonGenericException(err);
            result = handleExceptionCatch(oge);
        } catch (ContinueLoopException cle) {
            StringType err = new StringType(this.getSelectStatement());
            err.setFromJavaString("Invalid use of Continue -statement: nothing to Continue.");
            OperonGenericException oge = new OperonGenericException(err);
            result = handleExceptionCatch(oge);
        } catch (OperonGenericException e) {
            //System.out.println("OperonContext :: Evaluate selectStatement :: an error occured :: " + e.getErrorValue());
            //System.out.println("OperonContext :: Evaluate selectStatement :: an error occured :: " + e.getMessage());
            //System.out.println("OperonContext :: Evaluate selectStatement :: an error occured :: " + e.getErrorJson());
            log.debug("Evaluate selectStatement :: an error occured");
            log.debug("  Evaluate selectStatement :: error :: " + e);
            result = handleExceptionCatch(e);
            this.setIsReady(true, "SelectStmt :: exception");
        } catch (RuntimeException e) {
            log.debug("Evaluate selectStatement :: RuntimeException");
            System.err.println("RuntimeException :: " + e);
            StringType err = new StringType(this.getSelectStatement());
            err.setFromJavaString(e.getMessage().replaceAll("\"", "\\\\\""));
            OperonGenericException oge = new OperonGenericException(err);
            result = handleExceptionCatch(oge);
            this.setIsReady(true, "SelectStmt :: exception");
            log.debug("Evaluate selectStatement :: RuntimeException handled");
        }
        
        this.synchronizeState();
        
        long stopTime = System.nanoTime();
        long elapsedTime = stopTime - startTime;
        log.debug("Execution time: " + elapsedTime + " ns (" + elapsedTime / 1000000 + " ms.)");
        if (this.getConfigs().getPrintDuration()) {
            System.out.println("Execution time: " + elapsedTime + " ns (" + elapsedTime / 1000000 + " ms.)");
        }
        
        this.setIsReady(true, "SelectStmt");
        
        return result;
    }
    
    //
    // After catching Java-exception, do the requires setup for further exception-handling.
    // This is the global error-handler.
    //
    public OperonValue handleExceptionCatch(OperonGenericException e) {
        log.debug("OperonContext :: handleExceptionCatch()");
        this.setException(e);
        if (e.getErrorValue() != null) {
            //System.out.println("handleExceptionCatch :: " + e.getErrorValue());
            this.setErrorValue(e.getErrorValue());
        }
        
        OperonValue errorHandlingResult = null;
        
        if (this.getExceptionHandler() != null) {
            log.debug("OperonContext :: handleExceptionCatch() Global HandleError");
            //
            // set exception for ExceptionHandler, which can be used to extract context-information;
            //
            this.getExceptionHandler().setException(e);
            ErrorValue errorValue = ExceptionHandler.createErrorValue(this.getExceptionHandler().getExceptionHandlerExpr().getStatement(), e);
            this.getExceptionHandler().getExceptionHandlerExpr().getStatement().getRuntimeValues().put("$error", errorValue);
            errorHandlingResult = this.handleError(e);
        }
        else {
            errorHandlingResult = e.getErrorValue();
        }
        log.debug("OperonContext :: handleExceptionCatch() done");
        return errorHandlingResult;
    }
    
    public void setIsTest(boolean t) {
        this.isTest = t;
    }
    
    public boolean getIsTest() {
        return this.isTest;
    }
    
    //
    // TODO: create class for output (Producer)
    //
    public void outputResult(OperonValue result) throws OperonGenericException {
        log.debug("=== RESULT ===");
        //System.out.println("--- OUTPUT SELECT RESULT --- :: " + result);
        long startTime = System.nanoTime();
        
        //log.debug(result);
        
        if (this.getException() == null) {
            this.setOutputOperonValue(result);
            
            if (result instanceof EmptyType == false) {
                if (this.getContextLogger() == null && this.getIsTest() == false) {
                    SelectStatement selectStmt = this.getSelectStatement();
                    
                    //
                    // NOTE: we have two kinds of configs here, which do overlap:
                    //  1) configs given for the Select.
                    //  2) configs given by API (OperonConfigs)
                    //
                    //  If selectConfigs are not given, but operonConfigs are,
                    //  then operonConfigs are used. SelectConfigs override
                    //  operonConfigs.
                    //
                    ObjectType selectConfigs = selectStmt.getConfigs();
                    OperonConfigs operonConfigs = this.getConfigs();
                    
                    //
                    // Check Select -options for:
                    // "prettyPrint" <Boolean> : toFormattedString
                    // "outputResult" <Boolean> : choose to omit output
                    // "outputRaw" <Boolean> : RawValue is outputted, instead of writing Bytes(n)
                    // "rawEncoding" <String> : The byte-encoding for rawValue
                    // TODO: "serializer": "json" / "operon" (default) / yaml, ...
                    //

                    if (selectConfigs.hasKey("\"outputResult\"")) {
                        OperonValue outputResultNode = selectConfigs.getByKey("outputResult").evaluate();
                        if (outputResultNode instanceof FalseType) {
                            return;
                        }
                    }
                    
                    else if (operonConfigs.getOutputResult() == false) {
                        return;
                    }
                    
                    if (result instanceof RawValue == false) {
                        if (selectConfigs.hasKey("\"prettyPrint\"")) {
                            OperonValue prettyPrintNode = selectConfigs.getByKey("prettyPrint").evaluate();
                            if (prettyPrintNode instanceof TrueType) {
                                String prettyJson = OperonContext.serializeAsPrettyJson(result);
                                System.out.println(prettyJson);
                            }
                            else {
                                System.out.println(result);
                            }
                        }
                        
                        else if (operonConfigs.getPrettyPrint() == true) {
                            String prettyJson = OperonContext.serializeAsPrettyJson(result);
                            System.out.println(prettyJson);
                        }
                        
                        else if (selectConfigs.hasKey("\"yaml\"")) {
                            OperonValue yamlNode = selectConfigs.getByKey("yaml").evaluate();
                            if (yamlNode instanceof TrueType) {
                                String yamlOutput = OperonContext.serializeAsYaml(result);
                                System.out.println(yamlOutput);
                            }
                            else {
                                System.out.println(result);
                            }
                        }
                        
                        else {
                            System.out.println(result); // regular output
                        }
                    }
                    
                    else {
                        RawValue raw = (RawValue) result;
                        
                        if (selectConfigs.hasKey("\"outputRaw\"")) {
                            OperonValue outputRawNode = selectConfigs.getByKey("outputRaw").evaluate();
                            if (outputRawNode instanceof FalseType) {
                                System.out.println(raw.toString()); // output as: "Bytes(n)" instead of writing the full n-bytes in the output-stream.
                            }
                            
                            else {
                                byte[] uninterpretedBytes = raw.getBytes();
                                if (selectConfigs.hasKey("\"rawEncoding\"")) {
                                    StringType rawEncodingValue = (StringType) selectConfigs.getByKey("rawEncoding").evaluate();
                                    String rawEncoding = rawEncodingValue.getJavaStringValue();
                                    Charset encoding = Charset.forName(rawEncoding);
                                    String resultStr = new String(uninterpretedBytes, encoding);
                                    System.out.println(resultStr);
                                }
                                else {
                                    System.out.println(new String(uninterpretedBytes)); // print as uninterpreted bytes
                                }
                            }
                        }
                        else {
                            RawValue bvResult = (RawValue) result;
                            System.out.println(new String(raw.getBytes())); // print as uninterpreted bytes
                        }
                    }
                }
                else if (this.getContextLogger() == null && this.getIsTest() == true) {
                    //System.out.println("contextLogger was null: " + result);
                    // Do nothing
                }
                else {
                    //System.out.println("using context-logger for: " + result);
                    this.getContextLogger().println("#> " + result);
                }
            }
        }
        
        else {
            this.outputError();
            result = this.getException().getErrorValue();
            this.setOutputOperonValue(result);
        }
        
        long stopTime = System.nanoTime();
        long elapsedTime = stopTime - startTime;
        log.debug("Result printing time: " + elapsedTime + " ns (" + elapsedTime / 1000000 + " ms.)");
    }
    
    public void outputError() {
        log.debug("OperonContext :: outputError()");
        if (this.getException() != null) {
            log.debug("OperonContext :: outputError() getExceptionHandler()");
            System.err.println("Error JSON :: " + this.getException().getErrorJson());
            System.err.println("  - Exception :: " + this.getException());
            System.err.println("  - Value :: " + this.getErrorValue());
        }
        else {
            log.debug("OperonContext :: outputError() else");
        }
    }
    
    public static String serializeStringAsPrettyJson(String jsonValue) throws OperonGenericException {
        OperonValue value = JsonUtil.operonValueFromString(jsonValue);
        return value.toFormattedString(null);
    }
    
    public static String serializeAsPrettyJson(OperonValue value) throws OperonGenericException {
        return value.toFormattedString(null);
    }
    
    public static String serializeAsYaml(OperonValue value) throws OperonGenericException {
        return "---" + System.lineSeparator() + value.toYamlString(null);
    }
    
    private OperonValue handleError(OperonGenericException oge) {
        log.debug("OperonContext :: handleError()");
        OperonValue result = null;
        try {
            if (this.getExceptionHandler() != null) {
                log.debug("OperonContext :: handleError() getExceptionHandler()");
                this.getExceptionHandler().setException(oge);
                result = this.getExceptionHandler().evaluate(oge);
                this.setOutputOperonValue(result);
            }
            else {
                log.debug("No error handler defined");
            }
        } catch (OperonGenericException newOge) {
            log.debug("OperonContext :: handleError() OperonGenericException");
            this.setException(newOge);
        } catch (Exception e) {
            log.debug("OperonContext :: handleError() Exception");
            String msg = null;
            if (e.getMessage() != null) {
                msg = e.getMessage();
                OperonGenericException newOge = new OperonGenericException(msg);
                this.setException(newOge);
            }
            else {
                this.setException(oge);
            }
            //System.err.println("Uncaught error");
            this.printStackTrace();
        }
        //System.out.println("RETURNING RESULT: " + result);
        log.debug("OperonContext :: handleError() done");
        return result;
    }

    //
    // After each Query (transaction), the state must be synchronized,
    // i.e. the Let -statements must be released from stored values, if their
    // reset-strategy requires this.
    //
    @Override
    public void synchronizeState() {
        for (LetStatement lstmnt : this.getAllLetStatements()) {
            if (lstmnt.getResetType() != LetStatement.ResetType.NEVER) {
                lstmnt.reset();
            }
        }
    }

    private void setOutputOperonValue(OperonValue result) {
        this.outputOperonValue = result;
    }
    
    public OperonValue getOutputOperonValue() {
        return this.outputOperonValue;
    }
    
    public void setContextId(String id) {
        this.contextId = id;
    }
    
    public String getContextId() {
        return this.contextId;
    }
    
    public void setTransactionId(String id) {
        this.transactionId = id;
    }
    
    public String getTransactionId() {
        return this.transactionId;
    }
    
    public void setFromStatement(FromStatement from) {
        this.fromStatement = from;
    }
    
    public FromStatement getFromStatement() {
        return this.fromStatement;
    }
    
    public void setSelectStatement(SelectStatement select) {
        this.selectStatement = select;
    }
    
    public SelectStatement getSelectStatement() {
        return this.selectStatement;
    }

    public void setExceptionHandler(ExceptionHandler exHandler) {
        this.exceptionHandler = exHandler;
    }
    
    public ExceptionHandler getExceptionHandler() {
        return this.exceptionHandler;
    }
    
    public synchronized void setIsReady(boolean rdy) {
        this.isReady = rdy;
    }
    
    // For debugging purposes only
    public synchronized void setIsReady(boolean rdy, String message) {
        this.isReady = rdy;
    }
    
    public synchronized boolean isReady() {
        return this.isReady;
    }
    
    public void setQuery(String q) {
        this.query = q;
    }
    
    public String getQuery() {
        return this.query;
    }
    
    public List<LetStatement> getAllLetStatements() {
        return this.allLetStatements;
    }
    
    public void setState(State s) {
        this.state = s;
    }
    
    public State getState() {
        return this.state;
    }
    
    @Override
    public Context getParentContext() {
        return null; // this is the root-context.
    }

    // This is for keeping track with the tests, when testmode is true
    public void setOperonTestsContext(OperonTestsContext opTestsContext) {
        this.operonTestsContext = opTestsContext;
    }

    public OperonTestsContext getOperonTestsContext() {
        return this.operonTestsContext;
    }

}