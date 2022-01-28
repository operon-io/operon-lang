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

package io.operon.runner.system;

import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.model.exception.OperonComponentException;

// 
// IntegrationComponents are used (e.g.) to write the final or intermediary results into
// different kinds of endpoints.
// 
// IntegrationComponent may wait for return value (blocking), or work with "fire-and-forget" -pattern,
// depending on the given implementation, guided by given JsonConfiguration.
// 
public interface IntegrationComponent {

    public void setComponentId(String cid);
    public String getComponentId();
    public void setComponentName(String cName);
    public String getComponentName();
    public OperonValue produce(OperonValue value) throws OperonComponentException;
    
    public void setJsonConfiguration(ObjectType jsonConfig);

}