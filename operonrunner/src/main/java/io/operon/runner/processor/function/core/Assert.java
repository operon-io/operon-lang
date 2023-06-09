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

package io.operon.runner.processor.function.core;

import java.util.List;
import java.util.Random;

import io.operon.runner.OperonContext;
import io.operon.runner.ModuleContext;
import io.operon.runner.Context;
import io.operon.runner.OperonTestsContext;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.TrueType;
import io.operon.runner.node.type.FalseType;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.model.test.AssertComponent;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.Main;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class Assert extends BaseArity1 implements Node, Arity1 {
     // no logger 

    private AssertComponent assertComponent;
    
    public Assert(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "assert", "test");
        this.setNs(Namespaces.CORE);
    }

    public OperonValue evaluate() throws OperonGenericException {        
        //:OFF:log.debug("EVALUATE ASSERT");
        //System.out.println("Assert :: evaluate");
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            OperonValue currentValueCopy = currentValue.copy();

            Node assertExpr = this.getParam1();

            if (assertExpr != null) {
                assertExpr.getStatement().setCurrentValue(currentValue);
                
                if (this.getAssertComponent() != null) {
                    //System.out.println("AssertComponent found!");
                    this.getAssertComponent().resolveConfigs();
                    this.getAssertComponent().setAssertRunned(true);
                }
                else {
                    //System.out.println("AssertComponent not found!");
                }
                Node evaluationResult = assertExpr.evaluate();
                
                if (evaluationResult instanceof TrueType) {
                    //System.out.println("Assert :: true");
                    TrueType result = new TrueType(this.getStatement());
                    if (this.getAssertComponent() != null) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Assert OK");
                        sb.append(": " + this.getAssertComponent().getComponentName() + ":" + this.getAssertComponent().getComponentIdentifier());
                        System.out.println(Main.ANSI_GREEN + sb.toString() + Main.ANSI_RESET);
                    }
                }
                else {
                    //System.out.println("Assert :: false");
                    FalseType result = new FalseType(this.getStatement());
                    StringBuilder sb = new StringBuilder();
                    sb.append("Assert failed");
                    
                    if (this.getAssertComponent() != null) {
                        sb.append(": " + this.getAssertComponent().getComponentName() + ":" + this.getAssertComponent().getComponentIdentifier());
                    }
                    
                    this.addFailedAssertComponent(this.getStatement());
                    System.err.println(Main.ANSI_RED + sb.toString() + Main.ANSI_RESET);
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "core:assert", sb.toString());
                }
                
                return currentValueCopy;
            }
            else {
                return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "core:assert", "Assert is missing logical-operation");
            }
        } catch (OperonGenericException oge) {
            this.addFailedAssertComponent(this.getStatement());
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "core:assert", oge.getMessage());
        } catch (Exception e) {
            this.addFailedAssertComponent(this.getStatement());
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "core:assert", e.getMessage());
        }
    }

    private void addFailedAssertComponent(Statement stmt) {
        // We could be inside ModuleContext or OperonContext (the Main-context), therefore we must
        // first ensure we have the main-context:
        Context ctx = stmt.getOperonContext();
        while (ctx.getParentContext() != null) {
            ctx = ((ModuleContext) ctx).getParentContext();
        }
        if (ctx instanceof OperonContext) {
            if (((OperonContext) ctx).getOperonTestsContext() != null) {
                ((OperonContext) ctx).getOperonTestsContext().getFailedComponents().add(assertComponent);
            }
        }
    }
    
    public void setAssertComponent(AssertComponent ac) {
        this.assertComponent = ac;
    }
    
    public AssertComponent getAssertComponent() {
        return this.assertComponent;
    }

}