/** OPERON-LICENSE **/
package io.operon.runner.model.exception;

import io.operon.runner.node.type.EmptyType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.model.exception.OperonGenericException;

public class BreakSelect extends OperonGenericException {

    // 0 = break from aggregate
    // 1 = break from => stop() -function
    private short breakType = (short) 0;
    private OperonValue valueOnBreak;

    public BreakSelect() {
        super(new EmptyType(new DefaultStatement(null)));
        this.setErrorJson(null);
        this.setOperonValueOnBreak(new EmptyType(new DefaultStatement(null)));
        //System.out.println("Created BreakSelect");
    }

    public BreakSelect(OperonValue value) {
        super(new EmptyType(new DefaultStatement(null)));
        this.setErrorJson(null);
        this.setOperonValueOnBreak(value);
    }

    public void setBreakType(short t) {
        this.breakType = t;
    }
    
    public short getBreakType() {
        return this.breakType;
    }
    
    public void setOperonValueOnBreak(OperonValue v) {
        this.valueOnBreak = v;
    }
    
    public OperonValue getOperonValueOnBreak() {
        return this.valueOnBreak;
    }
}