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

package io.operon.runner.model.path;
 
import io.operon.runner.statement.Statement; 
import io.operon.runner.node.Node; 
import io.operon.runner.node.AbstractNode; 
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 
 
public class PosPathPart implements PathPart { 
    private int pos;
    public PosPathPart() {}
    public PosPathPart(int p) {
        this.pos = p;
    }
    
    public void setPos(int p) {
        this.pos = p;
    }
    
    public int getPos() {
        return this.pos;
    }
    
    public PosPathPart copy() {
        PosPathPart ppp = new PosPathPart();
        ppp.setPos(this.getPos());
        return ppp;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        PosPathPart ppp2 = (PosPathPart) obj;
        return this.getPos() == ppp2.getPos();
    }
    
    @Override
    public String toString() {
        return new String("[" + this.pos + "]");
    }
    
}