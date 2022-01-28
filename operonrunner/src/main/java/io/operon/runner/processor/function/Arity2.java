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

package io.operon.runner.processor.function;

import java.util.List;

import io.operon.runner.node.Node;
import io.operon.runner.model.exception.OperonGenericException;

public interface Arity2 {
    public String getParam1Name();
    public String getParam2Name();
    public String getFunctionName();
    
    public void setParam1(Node param1);
    public Node getParam1() throws OperonGenericException;
    
    public void setParam2(Node param2);
    public Node getParam2() throws OperonGenericException;
    
    public void setParams(List<Node> args, String funcName, String p1, String p2) throws OperonGenericException;
    
    public void setParam2AsOptional(boolean opt);
    public boolean isParam2Optional();
    
    // 
    // For arity-2 functions the current-value must be set between
    // param1 and param2 -node evaluations, otherwise they change
    // state (because current-value is in the same scope).
    //
    // This method is a reminder for developer and enforces this check.
    // Set true in function, when above is satisfied.
    // 
    //public boolean ensuredCurrentValueSetBetweenParamEvaluations() throws OperonGenericException;
}