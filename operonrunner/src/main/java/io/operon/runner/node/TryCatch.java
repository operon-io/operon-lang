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

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.statement.ExceptionStatement;
import io.operon.runner.node.Node;
import io.operon.runner.node.ValueRef;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// Throws the Java Exception with given JSON-value
//
public class TryCatch extends AbstractNode implements Node {
     // no logger 
    
    private Node tryExpr;
    private Node catchExpr;
    private Node configs;
    private long retryCount = -1;
    
    public TryCatch(Statement stmnt) {
        super(stmnt);
    }

    public OperonValue evaluate() throws OperonGenericException {
        //:OFF:log.debug("ENTER TryCatch.evaluate(). Stmt: " + this.getStatement().getId());
        
        //
        // Create a new stmt where the tryExpr is executed in.
        //
        Statement stmt = new DefaultStatement(this.getStatement().getOperonContext());
        stmt.setNode(this.getTryExpr());
        
        OperonValue result = new OperonValue(stmt);
        Info info = this.resolveConfigs(this.getStatement());
        this.retryCount += 1;
        
        //System.out.println("Try :: retries = " + info.retries);
        
        try {
            result = this.doTry(info, stmt, this.getStatement().getCurrentValue());
        } catch (OperonGenericException oge) {
            //System.out.println("TryCatch: " + oge.getErrorJson());
            ExceptionStatement es = new ExceptionStatement(this.getStatement().getOperonContext());
            this.getTryExpr().getStatement().getRuntimeValues().put("$error", oge.getErrorJson());
            es.setCurrentValue(oge.getErrorJson());
            es.setNode(this.getCatchExpr());
            //
            // This might rethrow:
            //
            result = es.evaluate();
            // ExceptionStatement checks if error was handled and rethrows if not.
        } catch (Exception ex) {
            System.err.println("ERROR :: Catch unhandled exception: " + ex.getMessage());
            throw ex;
        }
        this.getStatement().setCurrentValue(result);
        return result;
    }
    
    private OperonValue doTry(Info info, Statement stmt, OperonValue currentValue) throws OperonGenericException {
        while (true) {
            //System.out.println("doTry, retry=" + this.retryCount);
            stmt.setCurrentValue(currentValue); // This might be wrong (add to Node?)
            try {
                OperonValue result = stmt.evaluate();
                return result;
            } catch (OperonGenericException oge) {
                if (info.retries != null && (this.retryCount < info.retries || info.retries == -1)) {
                    this.retryCount += 1;
                    if (info.logRetry) {
                        if (info.logMessage == null) {
                            System.out.println("Retry: " + this.retryCount);
                        }
                        else {
                            System.out.println("Retry: " + this.retryCount + " :: " + info.logMessage);
                        }
                    }
                    try {
                        if (info.retryDelays == null) {
                            Thread.sleep(info.retryDelay);
                        }
                        else {
                            int delaysSize = info.retryDelays.size();
                            if (delaysSize <= this.retryCount) {
                                Thread.sleep(info.retryDelays.get(delaysSize - 1));
                            }
                            else if (this.retryCount - 1 > delaysSize) {
                                Thread.sleep(info.retryDelays.get(delaysSize - 1));
                            }
                            else {
                                Thread.sleep(info.retryDelays.get((int) this.retryCount - 1));
                            }
                        }
                    } catch (InterruptedException ie) {
                        throw oge;
                    }
                }
                else {
                    throw oge;
                }
            }
        }
    }
    
    public void setTryExpr(Node t) {
        this.tryExpr = t;
    }

    public Node getTryExpr() {
        return this.tryExpr;
    }

    public void setCatchExpr(Node c) {
        this.catchExpr = c;
    }

    public Node getCatchExpr() {
        return this.catchExpr;
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

    public Info resolveConfigs(Statement stmt) throws OperonGenericException {
        Info info = new Info();
        
        if (this.configs == null) {
            return info;
        }
        
        OperonValue currentValue = stmt.getCurrentValue();
        
        for (PairType pair : this.getConfigs().getPairs()) {
            String key = pair.getKey();
            switch (key.toLowerCase()) {
                case "\"retries\"":
                    double retries = ((NumberType) pair.getEvaluatedValue()).getDoubleValue();
                    info.retries = (long) retries;
                    break;
                case "\"retrydelay\"":
                    double retryDelay = ((NumberType) pair.getEvaluatedValue()).getDoubleValue();
                    info.retryDelay = (long) retryDelay;
                    break;
                case "\"retrydelays\"":
                    List<Node> retryDelaysArray = ((ArrayType) pair.getEvaluatedValue()).getValues();
                    List<Long> retryDelays = new ArrayList<Long>();
                    for (int i = 0; i < retryDelaysArray.size(); i ++) {
                        Node n = retryDelaysArray.get(i);
                        Double retryDelayDouble = ((NumberType) n.evaluate()).getDoubleValue();
                        retryDelays.add(retryDelayDouble.longValue());
                    }
                    info.retryDelays = retryDelays;
                    break;
                case "\"logretry\"":
                    OperonValue logretryValue = pair.getEvaluatedValue();
                    if (logretryValue instanceof FalseType) {
                        info.logRetry = false;
                    }
                    else {
                        info.logRetry = true;
                    }
                    break;
                case "\"logmessage\"":
                    String logMessage = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.logMessage = logMessage;
                    break;
                default:
                    break;
            }
        }
        stmt.setCurrentValue(currentValue);
        return info;
    }

    private class Info {
        // How many retries.
        // null or 0 means none.
        // -1 (minus one) means infinite.
        public Long retries = null;
        
        // The delay (in milliseconds) between retries.
        public long retryDelay = 100;
        public boolean logRetry = false;
        public String logMessage = null;
        
        // If this is set, then overrides value from retryDelay.
        // 
        // Example: [0, 0, 100, 200, 1000]
        // This means that for first two retries don't wait,
        // for the third retry wait for 100 ms, fourth 200 ms and all the next 1000 ms.
        public List<Long> retryDelays = null;
    }

    public String toString() {
        return "TryCatch";
    }
}