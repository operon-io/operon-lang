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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import io.operon.runner.node.Node;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.core.GenericUpdate;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.model.path.*;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

//
// ('Build' | '>>') (':' | pattern_configs)?
//
//   Realizes the input Paths (from Array, i.e. [~]) into Array or Object.
//
public class Build extends AbstractNode implements Node, java.io.Serializable {
    private static Logger log = LogManager.getLogger(Build.class);

    private Node configs;
    
    public Build(Statement stmnt) {
        super(stmnt);
    }
// Merge object?
    public OperonValue evaluate() throws OperonGenericException {
        log.debug("ENTER Build.evaluate()");
        System.out.println("Build.evaluate()");
        OperonValue currentValue = this.getStatement().getCurrentValue();
        
        System.out.println("CurrentValue: " + currentValue);
        
        // expected input: [~]
        ArrayType inputPaths = (ArrayType) currentValue.evaluate();
        System.out.println("Evaluated currentValue into array");

        // TODO: merge each value
        //for (int i = 0; i < inputPaths.getValues().size(); i ++) {
        //    System.out.println("Path: " + i);
        //}

        Path p = (Path) inputPaths.getValues().get(0).evaluate();
        return buildSingleValue(p);
    }

    public static OperonValue buildSingleValue(Path p) throws OperonGenericException {
        List<PathPart> pathParts = p.getPathParts();
        StringBuilder sb = new StringBuilder();
        Stack<String> types = new Stack<String>();
        
        for (int pi = 0; pi < pathParts.size(); pi ++) {
            //System.out.println("PI: " + pathParts.get(pi));
            PathPart pp = pathParts.get(pi);
            
            //System.out.println("pi=" + pi);
            //System.out.println("ppSize=" + pathParts.size());
            
            if (pp instanceof KeyPathPart) {
                String key = ((KeyPathPart) pp).getKey();
                sb.append("{" + key + ":");
                if (pi == pathParts.size()-1) {
                    // leaf:
                    sb.append("null");
                }
                types.push("}");
            }
            else if (pp instanceof PosPathPart) {
                int pos = ((PosPathPart) pp).getPos();
                //System.out.println("Append [, pos=" + pos);
                sb.append("[");
                if (pos >= 1) {
                    // fill with null -values until the pos is reached
                    for (int ai = 0; ai < pos - 1; ai ++) {
                        //System.out.println("append null,");
                        sb.append("null,");
                    }
                    
                    // expect value after null in the array
                    if (pi == pathParts.size() - 1) {
                        // this is the last value in the array (leaf)
                        //System.out.println("append last null");
                        sb.append("null");
                    }
                    else if (pi > 0) {
                        //System.out.println("remove last comma");
                        sb.setLength(sb.length() - 1); // remove last comma
                    }
                }
                types.push("]");
            }
        }
        while (types.size() > 0) {
            sb.append(types.pop());
        }
        String valStr = sb.toString();
        //System.out.println("VALUE: " + valStr);
        OperonValue builtValue = JsonUtil.operonValueFromString(valStr);
        OperonValue vlink = p.getValueLink();
        if (vlink == null) {
            return builtValue;
        }
        else {
            // Update vlink to builtValue
            return null; // TODO
        }
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

}