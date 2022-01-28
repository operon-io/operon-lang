/** OPERON-LICENSE **/
package io.operon.runner.node.type;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.Node;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;

public class NullType extends OperonValue implements Node, AtomicOperonValue {

    public NullType(Statement stmnt) {
        super(stmnt);
    }

    public NullType evaluate() throws OperonGenericException {
        this.setUnboxed(true);
        return this;
    }
    
    public NullType getValue() {
        return this;
    }

    @Override
    public String toString() {
        return "null";
    }

    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        return "null";
    }

    @Override
    public String toYamlString(YamlFormatter yf) {
        return "null";
    }

}