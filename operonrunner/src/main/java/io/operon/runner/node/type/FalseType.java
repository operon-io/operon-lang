/** OPERON-LICENSE **/
package io.operon.runner.node.type;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.Node;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;

public class FalseType extends OperonValue implements Node, AtomicOperonValue {

    public FalseType(Statement stmnt) {
        super(stmnt);
    }

    public FalseType evaluate() throws OperonGenericException {
        this.setUnboxed(true);
        return this;
    }
    
    public FalseType getValue() {
        return this;
    }

    @Override
    public String toString() {
        return "false";
    }

    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        return "false";
    }

    @Override
    public String toYamlString(YamlFormatter yf) {
        return "false";
    }

}