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

public interface Arity1 {
    public String getParam1Name();
    public void setParam1(Node param1);
    public Node getParam1() throws OperonGenericException;
    public void setParams(List<Node> args, String funcName, String p1) throws OperonGenericException;
    public void setParam1AsOptional(boolean opt);
    public boolean isParam1Optional();
    public void setFunctionName(String fn);
    public String getFunctionName();
}