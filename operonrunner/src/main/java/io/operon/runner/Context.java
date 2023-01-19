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

package io.operon.runner;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Deque;
import java.io.IOException;
import java.io.PrintStream;

import io.operon.runner.util.RandomUtil;
import io.operon.runner.model.InputSource;
import io.operon.runner.model.OperonConfigs;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.FromStatement;
import io.operon.runner.statement.FunctionStatement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.node.Node;
import io.operon.runner.node.Operator;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 * 
 * Marker
 * 
 */
public interface Context {

    public void synchronizeState();

    public Map<String, OperonValue> getRuntimeValues();

    public Map<String, List<Operator>> getBindValues();

    public Map<String, FunctionStatement> getFunctionStatements();
    
    public Map<String, LetStatement> getLetStatements();

    public void setErrorValue(ErrorValue err);
    
    public void setException(OperonGenericException e);
    
    public void setIsReady(boolean rdy, String message);
    
    public Context getParentContext();
    
    public void setParentContext(Context parentContext);
    
    public Map<String, Context> getModules();
    
    public void setOwnNamespace(String ons);
    
    public String getOwnNamespace();
    
    public PrintStream getContextLogger();
    
    public PrintStream getContextErrorLogger();
    
    public void setConfigs(OperonConfigs configs);
    
    public OperonConfigs getConfigs();
    
    public Deque<Node> getStackTrace();
    
    public void addStackTraceElement(Node n);
    
    public void printStackTrace();
}