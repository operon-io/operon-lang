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

import io.operon.runner.node.Node;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.core.GenericUpdate;
import io.operon.runner.node.type.*;
import io.operon.runner.model.UpdatePair;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

//
// Used in Update -expr
//
public class Update extends AbstractNode implements Node, java.io.Serializable {
    private static Logger log = LogManager.getLogger(Update.class);

    private List<UpdatePair> pathUpdates;
    private Node configs;
    
    public Update(Statement stmnt) {
        super(stmnt);
        this.pathUpdates = new ArrayList<UpdatePair>();
    }

    public OperonValue evaluate() throws OperonGenericException {
        log.debug("ENTER Update.evaluate()");
        //System.out.println("Update.evaluate()");
        OperonValue result = this.getStatement().getCurrentValue();
        for (int i = 0; i < this.getPathUpdates().size(); i ++) {
            //System.out.println("Apply update");
            UpdatePair up = this.getPathUpdates().get(i);
            List<Node> params = new ArrayList<Node>();
            params.add(up.getPath()); // $target
            params.add(up.getUpdateValue()); // $value
            //System.out.println("prepare GenericUpdate");
            //System.out.println("$value=" + up.getUpdateValue());
            //System.out.println("$target=" + up.getPath());
            GenericUpdate genUp = new GenericUpdate(this.getStatement(), params);
            genUp.getStatement().setCurrentValue(result);
            result = genUp.evaluate();
        }
        return result;
    }

    public void setPathUpdates(List<UpdatePair> pU) {
        this.pathUpdates = pU;
    }

    public List<UpdatePair> getPathUpdates() {
        return this.pathUpdates;
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