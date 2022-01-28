/** OPERON-LICENSE **/
package io.operon.runner.node.type;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.Node;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;

public class TrueType extends OperonValue implements Node, AtomicOperonValue {

    public TrueType(Statement stmnt) {
        super(stmnt);
    }

    public TrueType evaluate() throws OperonGenericException {
        this.setUnboxed(true);
        return this;
    }
    
    public TrueType getValue() {
        return this;
    }

    @Override
    public String toString() {
        return "true";
    }

    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        return "true";
    }

    @Override
    public String toYamlString(YamlFormatter yf) {
        return "true";
    }

}
