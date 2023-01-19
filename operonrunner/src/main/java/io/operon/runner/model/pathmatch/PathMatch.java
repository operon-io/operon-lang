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

package io.operon.runner.model.pathmatch;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.statement.Statement; 
import io.operon.runner.node.Node;
import io.operon.runner.node.Node; 
import io.operon.runner.node.type.*;
import io.operon.runner.node.AbstractNode; 
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 

//
// Container for parts that consist the matching expr.
//
public class PathMatch extends OperonValue implements Node { 
     // no logger  

    private List<PathMatchPart> pathMatchParts;

    public PathMatch(Statement stmnt) { 
        super(stmnt);
        this.pathMatchParts = new ArrayList<PathMatchPart>();
    }

    public int length() {
        return this.getPathMatchParts().size();
    }

    public List<PathMatchPart> getPathMatchParts() { 
        return this.pathMatchParts;
    } 
 
    public void setPathMatchParts(List<PathMatchPart> pmp) { 
        this.pathMatchParts = pmp; 
    } 
     
    public void addPathMatchPart(PathMatchPart pmp) { 
        this.pathMatchParts.add(pmp); 
    }

    public void removeLastPathMatchPart() {
        if (this.getPathMatchParts().size() > 0) {
            this.pathMatchParts.remove(this.getPathMatchParts().size() - 1);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        for (PathMatchPart pmp : this.getPathMatchParts()) {
            sb.append(pmp.toString());
        }
        sb.append("\"");
        return sb.toString();
    }
}