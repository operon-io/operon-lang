/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.LambdaFunctionRef;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.ErrorUtil;
import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 *
 * Checks if given OperonValue is mappable to another OperonValue,
 * by checking the given constraints.
 * 
 * This function is used when evaluating the OperonValueConstraints.
 *
 * DEFINITION
 * ==========
 * Value being mappable to another value is defined by the value type:
 *    string-value is mappable to another string-value,
 *    number-value is mappable to anoother number-value, 
 *    boolean -gt; boolean, array -gt; array, object -gt; object, null -gt; null, empty -gt; empty
 *
 */
public class MappableTo extends BaseArity1 implements Node, Arity1 {
    private static Logger log = LogManager.getLogger(MappableTo.class);
    
    public MappableTo(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "mappableTo", "value");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        //
        // Note: param (mappableTo) should be a OperonValue, or ValueRef, or Function.
        //
        log.debug("MappableTo :: evaluate()");
        OperonValue currentValue = (OperonValue) this.getStatement().getCurrentValue();
        currentValue = (OperonValue) currentValue.evaluate(); // unbox
        
        Node mappableTo = this.getParam1();
        OperonValue result = null;
        
        if (mappableTo instanceof OperonValue == false) {
            OperonValue mapTo = mappableTo.evaluate();
            log.debug("MAP TO 1 :: " + mapTo.getClass().getName());
            result = this.evaluateIsMappableTo(currentValue, mapTo);
        }
        else {
            result = this.evaluateIsMappableTo(currentValue, (OperonValue) mappableTo);
        }
        
        return result;
    }

    public OperonValue evaluateIsMappableTo(OperonValue currentValue, OperonValue mappableTo) throws OperonGenericException {
        TrueType resultTrue = new TrueType(this.getStatement());
        FalseType resultFalse = new FalseType(this.getStatement());
        OperonValue result = resultFalse;
        
        if (currentValue instanceof StringType
                && mappableTo instanceof StringType) {
            log.debug("MappableTo :: evaluate() :: StringType");
            result = resultTrue;
            StringType mapTo = (StringType) mappableTo;
            if (mapTo.getOperonValueConstraint() != null) {
                log.debug(" MapTo :: StringType :: CONSTRAINT EXISTS. Evaluate against :: " + currentValue);
                mapTo.getOperonValueConstraint().setValueToEvaluateAgainst(currentValue.copy());
                OperonValue constraintResult = (OperonValue) mapTo.getOperonValueConstraint().evaluate();
                
                if (constraintResult instanceof FalseType) {
                    log.debug("MappableTo :: constraint violation in StringType");
                    result = resultFalse;
                }
            }
        }
        
        else if (currentValue instanceof NumberType
                && mappableTo instanceof NumberType) {
            log.debug("MappableTo :: evaluate() :: NumberType");
            result = resultTrue;
        }
        
        else if (currentValue instanceof TrueType
                && mappableTo instanceof TrueType) {
            log.debug("MappableTo :: evaluate() :: TrueType");
            result = resultTrue;
        }
        
        else if (currentValue instanceof FalseType
                && mappableTo instanceof FalseType) {
            log.debug("MappableTo :: evaluate() :: FalseType");
            result = resultTrue;
        }
        
        else if (currentValue instanceof TrueType
                && mappableTo instanceof FalseType) {
            log.debug("MappableTo :: evaluate() :: TrueType");
            result = resultTrue;
        }
        
        else if (currentValue instanceof FalseType
                && mappableTo instanceof TrueType) {
            log.debug("MappableTo :: evaluate() :: FalseType");
            result = resultTrue;
        }
        
        else if (currentValue instanceof ArrayType
                && mappableTo instanceof ArrayType) {
            log.debug("MappableTo :: evaluate() :: ArrayType");
            //
            // TODO: loop through the array and evaluate each element? NOTE: this might be unnecessary!
            //
            result = resultTrue;
        }
        
        else if (currentValue instanceof ObjectType
            && mappableTo instanceof ObjectType) {
            log.debug("MappableTo :: evaluate() :: ObjectType");
            ObjectType curObj = (ObjectType) currentValue;
            result = resultTrue;
            
            int i = 0;
            for (PairType mappablePair : ((ObjectType) mappableTo).getPairs()) {
                i += 1;
                log.debug(" >> MAPPABLE_TO OBJ KEY :: " + mappablePair.getKey());
                OperonValueConstraint constraint = mappablePair.getOperonValueConstraint();
                boolean hasConstraint = false;
                
                if (constraint != null) {
                    log.debug(" >> " + mappablePair.getKey() + " CONSTRAINT FOUND !!!");
                    hasConstraint = true;
                }
                    
                // Apply constraint-check (when constraint exists):
                boolean found = false;
                for (PairType pair : curObj.getPairs()) {
                    log.debug(" >> CUR OBJ KEY :: " + pair.getKey());
                    if (pair.getKey().equals(mappablePair.getKey())) {
                        found = true;
                        if (hasConstraint) {
                            OperonValue valueCopy = pair.getValue().copy();
                            constraint.setValueToEvaluateAgainst(valueCopy);
                            OperonValue constraintResult = constraint.evaluate();
                            
                            if (constraintResult instanceof FalseType) {
                                log.debug("MappableTo :: constraint violation :: " + pair.getKey());
                                result = resultFalse;
                                return result;
                            }
                        }
                        
                        // If mappablePair is ObjectType, then evaluate the sub-fields
                        log.debug(">>>>>> Evaluate sub-field :: " + mappablePair.getValue().getClass().getName() + " :: " + pair.getValue());
                        if (mappablePair.getValue().evaluate() instanceof ObjectType) {
                            OperonValue pairValue = pair.getValue().copy();
                            if (pairValue instanceof ObjectType) {
                                ObjectType p1 = (ObjectType) pairValue;
                                ObjectType p2 = (ObjectType) mappablePair.getValue().evaluate();
                                if (this.evaluateIsMappableTo(p1, p2) instanceof FalseType) {
                                    log.debug("MappableTo :: constraint violation in sub-field :: " + pairValue + " / " + mappablePair.getValue());
                                    result = resultFalse;
                                }
                            }
                            else {
                                log.debug(" >>> pairValue was not ObjectType :: " + pairValue.getClass().getName());
                                // The constraint-check was ok, so this must be ok as well.
                                result = resultTrue;
                            }
                            
                            log.debug(">>>>>> Evaluation of sub-field done :: " + mappablePair.getValue().getClass().getName());
                        }
                        break;
                    }
                }

                if (hasConstraint && !found) {
                    //
                    // If constraint says that the field can be empty, then the field may also be missing.
                    // To solve this is to create the field (in the for-loop above),
                    // but assign it with EmptyType, when the field does not actually exist (not found).
                    // After this, run the constraint-check:
                    //
                    constraint.setValueToEvaluateAgainst(new EmptyType(this.getStatement()));
                    OperonValue constraintResult = constraint.evaluate();
                    
                    if (constraintResult instanceof FalseType) {
                        log.debug("MappableTo :: constraint violation :: " + mappablePair.getKey());
                        result = resultFalse;
                        return result;
                    }
                }
            }
        }
        
        else if (currentValue instanceof NullType
                && mappableTo instanceof NullType) {
            log.debug("MappableTo :: evaluate() :: NullType");
            result = resultTrue;
        }
        
        else if (currentValue instanceof EmptyType
                && mappableTo instanceof EmptyType) {
            log.debug("MappableTo :: evaluate() :: EmptyType");
            result = resultTrue;
        }
        
        else if (currentValue instanceof LambdaFunctionRef
                && mappableTo instanceof LambdaFunctionRef) {
            log.debug("MappableTo :: evaluate() :: LambdaFunctionRef");
            result = resultTrue;
        }
        
        else if (currentValue instanceof FunctionRef
                && mappableTo instanceof FunctionRef) {
            log.debug("MappableTo :: evaluate() :: FunctionRef");
            result = resultTrue;
        }
        
        else if (currentValue instanceof Path
                && mappableTo instanceof Path) {
            log.debug("MappableTo :: evaluate() :: Path");
            result = resultTrue;
        }
        
        else if (currentValue instanceof RawValue
                && mappableTo instanceof RawValue) {
            log.debug("MappableTo :: evaluate() :: RawValue");
            result = resultTrue;
        }
        
        else if (currentValue instanceof StreamValue
                && mappableTo instanceof StreamValue) {
            log.debug("MappableTo :: evaluate() :: RawValue");
            result = resultTrue;
        }
        
        else {
            log.debug("MappableTo :: evaluate() :: ELSE >>>>>>>>>>>>>>>>>>> RETURN FALSE");
            log.debug("    >> Current Value :: " + currentValue.getClass().getName() + ", mappableTo :: " + mappableTo.getClass().getName());
            result = resultFalse;
        }
        
        return result;
    }

}