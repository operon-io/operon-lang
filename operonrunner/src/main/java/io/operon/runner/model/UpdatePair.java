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

package io.operon.runner.model;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.Path;

public class UpdatePair {

    private Path path;
    private boolean isObject = false; // set true by compiler if update value is object, e.g. Select: $ << {foo: 123};
    private Node updateValue;
    
    public UpdatePair() {
    }

    public void setIsObject(boolean isObj) {
        this.isObject = isObj;
    }
    
    public boolean getIsObject() {
        return this.isObject;
    }
    
    public Path getPath() {
        return this.path;
    }
    
    public void setPath(Path up) {
        this.path = up;
    }
    
    public Node getUpdateValue() {
        return this.updateValue;
    }
    
    public void setUpdateValue(Node uv) {
        this.updateValue = uv;
    }

}