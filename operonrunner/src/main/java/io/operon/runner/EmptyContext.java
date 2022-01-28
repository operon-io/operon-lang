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

package io.operon.runner;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Deque;
import java.util.Iterator;
import java.util.ArrayDeque;
import java.io.IOException;
import java.io.PrintStream;

import io.operon.runner.Context;
import io.operon.runner.util.RandomUtil;
import io.operon.runner.model.InputSource;
import io.operon.runner.model.OperonConfigs;
import io.operon.runner.statement.*;
import io.operon.runner.node.Node;
import io.operon.runner.node.UnaryNode;
import io.operon.runner.node.BinaryNode;
import io.operon.runner.node.MultiNode;
import io.operon.runner.node.Operator;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.exception.BreakSelect;

import org.apache.logging.log4j.LogManager;

/**
 * 
 * EmptyContext for lightweight context-creation,
 * i.e. we don't allocate memory for different data-structures.
 * 
 * This is used only for optimization purpose on JsonUtil.
 * 
 */
public class EmptyContext implements Context, java.io.Serializable {
    
    public EmptyContext() {}

    public void synchronizeState() {}

    public Map<String, OperonValue> getRuntimeValues() {
        return null;
    }

    public Map<String, List<Operator>> getBindValues() {
        return null;
    }

    public Map<String, FunctionStatement> getFunctionStatements() {
        return null;
    }
    
    public Map<String, LetStatement> getLetStatements() {
        return null;
    }

    public void setErrorValue(ErrorValue err) {}
    
    public void setException(OperonGenericException e) {}
    
    public void setIsReady(boolean rdy, String message) {}
    
    public Context getParentContext() {
        return null;
    }
    
    public void setParentContext(Context parentContext) {}
    
    public Map<String, Context> getModules() {
        return null;
    }
    
    public void setOwnNamespace(String ons) {}
    
    public String getOwnNamespace() {
        return null;
    }
    
    public PrintStream getContextLogger() {
        return null;
    }
    
    public PrintStream getContextErrorLogger() {
        return null;
    }
    
    public void setConfigs(OperonConfigs configs) {}
    
    public OperonConfigs getConfigs() {
        return null;
    }
    
    public Deque<Node> getStackTrace() {
        return null;
    }
    
    public void addStackTraceElement(Node n) {}
    
    public void printStackTrace() {}

}