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

import io.operon.runner.node.type.ObjectType;

public class ComponentDefinition {

    /*
      "type": "integration",
      "name": "file",
      "version": "0.0.1",
      "resolveUri": "file:./src/test/resources/modules/withcomponents/file-integration-component-0.1.0.jar",
      "sourceUri": "",
      "description": "",
      "operonVersion": "0.5.2",
      "hash": "",
      "installedTs": 0,
      "configuration": {}
    */
    private String type;
    private String name;
    private String version;
    private String resolveUri;
    private String sourceUri;
    private String description;
    private String operonVersion;
    private String hash;
    private Long installedTs;
    private ObjectType configuration;

    public ComponentDefinition() {
        
    }

    public void setType(String x) {this.type = x;}
    public void setName(String x) {this.name = x;}
    public void setVersion(String x) {this.version = x;}
    public void setResolveUri(String x) {this.resolveUri = x;}
    public void setSourceUri(String x) {this.sourceUri = x;}
    public void setDescription(String x) {this.description = x;}
    public void setOperonVersion(String x) {this.operonVersion = x;}
    public void setHash(String x) {this.hash = x;}
    public void setInstalledTs(Long x) {this.installedTs = x;}
    public void setConfiguration(ObjectType x) {this.configuration = x;}
    
    public String getType() {return this.type;}
    public String getName() {return this.name;}
    public String getVersion() {return this.version;}
    public String getResolveUri() {return this.resolveUri;}
    public String getSourceUri() {return this.sourceUri;}
    public String getDescription() {return this.description;}
    public String getOperonVersion() {return this.operonVersion;}
    public String getHash() {return this.hash;}
    public Long getInstalledTs() {return this.installedTs;}
    public ObjectType getConfiguration() {return this.configuration;}
    
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("{");
      sb.append("\"type\": \"" + this.getType() + "\", ");
      sb.append("\"name\": \"" + this.getName() + "\", ");
      sb.append("\"version\": \"" + this.getVersion() + "\", ");
      sb.append("\"resolveUri\": \"" + this.getResolveUri() + "\", ");
      sb.append("\"sourceUri\": \"" + this.getSourceUri() + "\", ");
      sb.append("\"description\": \"" + this.getDescription() + "\", ");
      sb.append("\"operonVersion\": \"" + this.getOperonVersion() + "\", ");
      sb.append("\"hash\": \"" + this.getHash() + "\", ");
      sb.append("\"installedTs\": " + this.getInstalledTs() + ", ");
      sb.append("\"configuration\": " + this.getConfiguration());
      sb.append("}");
      return sb.toString();
    }
}