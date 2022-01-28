/** OPERON-LICENSE **/
package io.operon.runner.model;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.Path;

public class UpdatePair {

    private Path path;
    private Node updateValue;
    
    public UpdatePair() {
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