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

package io.operon.runner.compiler;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;

import io.operon.runner.model.test.AssertComponent;
import io.operon.runner.model.test.MockComponent;

import io.operon.parser.*;
import io.operon.runner.Context;
import io.operon.runner.OperonTestsContext;
import io.operon.runner.processor.function.core.*;
import io.operon.runner.processor.function.core.array.*;
import io.operon.runner.processor.function.core.object.*;
import io.operon.runner.processor.function.core.string.*;
import io.operon.runner.processor.function.core.date.*;
import io.operon.runner.processor.function.core.env.*;
import io.operon.runner.processor.function.core.path.*;
import io.operon.runner.processor.function.core.resolver.*;
import io.operon.runner.processor.UnaryNodeProcessor;
import io.operon.runner.processor.BinaryNodeProcessor;
import io.operon.runner.processor.unary.*;
import io.operon.runner.processor.binary.*;
import io.operon.runner.processor.binary.logical.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.FromStatement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.statement.FunctionStatement;
import io.operon.runner.node.*;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.model.InputSource;
import io.operon.runner.model.ObjAccessArgument;
import io.operon.runner.model.pathmatch.*;
import io.operon.runner.model.path.PathPart;
import io.operon.runner.model.UpdatePair;
import io.operon.runner.system.InputSourceDriver;
import io.operon.runner.system.ComponentSystemUtil;
import io.operon.runner.ExceptionHandler;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Stack;
import java.util.Collections;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 * This class listens the events that parser emits.
 * Goal is to return the parsed OperonValue.
 * NOTE that this is different from Operon's Json, e.g. "empty" -value is not supported here,
 * and the code is different!
 *
 */
public class TestsCompiler extends OperonTestsBaseListener {
    private static Logger log = LogManager.getLogger(TestsCompiler.class);

    private Context moduleContext;
    private String moduleFilePath;

    private OperonTestsContext operonTestsContext;
    private Stack<Statement> statementStack;
    private Stack<Node> stack;
    private OperonValue initialCurrentValue; // TODO: refactor this away (in the fromInputSource)?
    private Statement currentStatement;
    private Long startTime;
    private InputSource fromInputSource;
    
    private int aggregateIndex = 0; // used in exitAggregate_expr
    private int objIndex = 0; // used in exitJson_obj
    
    public TestsCompiler() throws OperonGenericException, IOException {
        super();
        this.stack = new Stack<Node>();
        this.statementStack = new Stack<Statement>();
        if (operonTestsContext == null) {
            operonTestsContext = new OperonTestsContext(); // TODO: use TestsContext instead!
        }
        this.currentStatement = new DefaultStatement(operonTestsContext);
        log.debug("TestsCompiler :: created");
    }
    
    public void setOperonTestsContext(OperonTestsContext opTestsContext) {
        this.operonTestsContext = opTestsContext;
    }
    
    public OperonTestsContext getOperonTestsContext() {
        return this.operonTestsContext;
    }
    
    public Statement getCurrentStatement() {
        return this.currentStatement;
    }
    
    public void setCurrentStatement(Statement stmnt) {
        this.currentStatement = stmnt;
    }
    
    public Stack<Statement> getStatementStack() {
        return this.statementStack;
    }

    public void setModuleContext(Context mContext) {
        this.moduleContext = mContext;
    }

    public Context getModuleContext() {
        return this.moduleContext;
    }


    // Tells the location of this module
    // TODO: should be relative to the "./modules" -folder
    public void setModuleFilePath(String moduleFilePath) {
        this.moduleFilePath = moduleFilePath;
    }
    
    public String getModuleFilePath() {
        return this.moduleFilePath;
    }

    public void setFromInputSource(InputSource is) {
        this.fromInputSource = is;
    }
    
    public InputSource getFromInputSource() {
        return this.fromInputSource;
    }

    @Override
    public void enterFrom(OperonTestsParser.FromContext ctx) {
        log.debug("ENTER From. Stack size: " + this.stack.size());
        Statement from = new FromStatement(this.getOperonTestsContext());
        from.setId("FromStatement");
        this.setCurrentStatement(from);
    }

    @Override
    public void exitFrom(OperonTestsParser.FromContext ctx) {
        log.debug("EXIT From. Stack size: " + this.stack.size());
    }

    @Override
    public void exitInput_source(OperonTestsParser.Input_sourceContext ctx) {
        log.debug("EXIT INPUT SOURCE.");
        List<ParseTree> subNodes = this.getContextChildNodes(ctx);
        log.debug("    >> Stack size :: " + this.stack.size());
        log.debug("    >> CHILD NODES :: " + subNodes.size() + " :: CHILD NODE [0] TYPE :: " + subNodes.get(0).getText());
        
        String system = subNodes.get(0).getText().toLowerCase();
        String systemId = null;
        
        if (subNodes.size() >= 2 && subNodes.get(1) != null) {
            systemId = subNodes.get(1).getText().toLowerCase();
        }
        //System.out.println("systemId="+systemId);
        //InputSourceDriver inputSourceSystem = null;
        InputSource inputSource = new InputSource();

        inputSource.setName(system);
        inputSource.setSystemId(systemId);

        //
        // ObjectType might be missing because it is optional.
        //
        OperonValue isdConfigsOrInitialValue = null;
        
        if (system.equals("json") || system.equals("sequence")) {
            isdConfigsOrInitialValue = (OperonValue) this.stack.pop();
        }
        else {
            if (subNodes.size() == 6 && subNodes.get(5).getClass().getName().equals("io.operon.parser.OperonTestsParser$Json_objContext")) {
                isdConfigsOrInitialValue = (ObjectType) this.stack.pop();
            }
            else if (subNodes.size() == 5 && subNodes.get(4).getClass().getName().equals("io.operon.parser.OperonTestsParser$Json_objContext")) {
                isdConfigsOrInitialValue = (ObjectType) this.stack.pop();
            }
            else if (subNodes.size() == 4 && subNodes.get(3).getClass().getName().equals("io.operon.parser.OperonTestsParser$Json_objContext")) {
                isdConfigsOrInitialValue = (ObjectType) this.stack.pop();
            }
            else if (subNodes.size() == 3 && subNodes.get(2).getClass().getName().equals("io.operon.parser.OperonTestsParser$Json_objContext")) {
                isdConfigsOrInitialValue = (ObjectType) this.stack.pop();
            }
            else if (subNodes.size() == 2 && subNodes.get(1).getClass().getName().equals("io.operon.parser.OperonTestsParser$Json_objContext")) {
                isdConfigsOrInitialValue = (ObjectType) this.stack.pop();
            }
            
            //if (isdConfigsOrInitialValue == null) {
            //    System.out.println("isdConfigsOrInitialValue was NULL!!!");
            //}
        }
        
        if (system.equals("json") || system.equals("sequence")) {
            //System.out.println(">>>> json: " + isdConfigsOrInitialValue);
            inputSource.setInitialValue(isdConfigsOrInitialValue);
        }
        else {
            if (isdConfigsOrInitialValue == null) {
                isdConfigsOrInitialValue = new ObjectType(this.currentStatement);
            }
            inputSource.setConfiguration((ObjectType) isdConfigsOrInitialValue);
        }
        log.debug("Input source: " + system + ". ");
        this.setFromInputSource(inputSource);
        
        //
        // Add OperonValueConstraint, if such exists.
        //
        if (this.stack.size() == 1 && this.stack.peek() instanceof OperonValueConstraint) {
            log.debug("  Set OperonValueConstraint for FromStatement");
            OperonValueConstraint jvc = (OperonValueConstraint) this.stack.pop();
            
            int constraintIndex = subNodes.size() - 3; // for json / sequence: From json <Number>: 123 Select $
            if (system.equals("json") == false && system.equals("sequence") == false) {
                constraintIndex = subNodes.size() - 1;
                if (subNodes.get(subNodes.size() - 2).getClass().getName().equals("io.operon.parser.OperonTestsParser$Json_value_constraintContext")) {
                    constraintIndex = subNodes.size() - 2;
                }
            }
            String constraintAsString = subNodes.get(constraintIndex).getText();
            //System.out.println("  Constraint=" + constraintAsString);
            jvc.setConstraintAsString(constraintAsString);
            ((FromStatement) this.currentStatement).setOperonValueConstraint(jvc);
        }
        FromStatement fromStatement = (FromStatement) this.getCurrentStatement();
        fromStatement.setInputSource(this.getFromInputSource());
        this.getOperonTestsContext().setFromStatement(fromStatement);
        
        log.debug("    >> EXITED INPUT SOURCE.");
    }

    //  
    //root_input_source
    //    : ROOT_VALUE json_value_constraint? ':' json
    //    | ROOT_VALUE ':' input_source
    //    ;
    //
    @Override
    public void exitRoot_input_source(OperonTestsParser.Root_input_sourceContext ctx) {
        log.debug("EXIT ROOT INPUT SOURCE.");
        List<ParseTree> subNodes = this.getContextChildNodes(ctx);
        
        log.debug("    >> Stack size :: " + this.stack.size());
        log.debug("    >> CHILD NODES :: " + subNodes.size() + " :: CHILD NODE [0] TYPE :: " + subNodes.get(0).getText());
        
        String system = "json";
        String systemId = null;
        
        //
        // The exitInput_source has already handled this (second) case: ROOT_VALUE ':' input_source
        // E.g. "$: json: 123"
        // or more generally: $: ID <json_constraint> : ID json_configs
        //
        // so we can just return.
        //
        // The subnode2 is a name of ISD-component? True if starts with letter,
        // and is followed by colon (there exists a colon). This applies because
        // json-values (that are allowed here in the first rule) do not start with letter and at the same
        // time have colon (i.e. cases such as '$:true: Select ...' do not exist).
        String subNode2 = subNodes.get(2).getText();
        if (Character.isLetter(subNode2.charAt(0)) == true
            && subNode2.indexOf(':') > 0) {
            return;
        }
        
        //InputSourceDriver inputSourceSystem = null;
        InputSource inputSource = new InputSource();

        inputSource.setName(system);
        inputSource.setSystemId(systemId);

        OperonValue isdConfigsOrInitialValue = (OperonValue) this.stack.pop();

        if (system.equals("json")) {
            inputSource.setInitialValue(isdConfigsOrInitialValue);
        }
        log.debug("Input source: " + system + ". ");
        
        this.setFromInputSource(inputSource);
        
        //
        // Add OperonValueConstraint, if such exists.
        //
        if (this.stack.size() == 1) {
            log.debug("  Set OperonValueConstraint for FromStatement");
            OperonValueConstraint jvc = (OperonValueConstraint) this.stack.pop();
            String constraintAsString = subNodes.get(subNodes.size() - 3).getText();
            jvc.setConstraintAsString(constraintAsString);
            ((FromStatement) this.currentStatement).setOperonValueConstraint(jvc);
        }
        
        FromStatement fromStatement = (FromStatement) this.getCurrentStatement();
        fromStatement.setInputSource(this.getFromInputSource());
        this.getOperonTestsContext().setFromStatement(fromStatement);
        
        log.debug("    >> EXITED ROOT INPUT SOURCE.");
    }

    @Override
    public void exitAssert_component_stmt(OperonTestsParser.Assert_component_stmtContext ctx) {
        log.debug("exitAssert_component_stmt :: stack size :: " + this.stack.size());
        //System.out.println("exitAssert_component_stmt :: stack size :: " + this.stack.size());
        // extract 'Assert Component' (ID ':')+ ID expr
        List<ParseTree> subNodes = this.getContextChildNodes(ctx);
        //System.out.println("subNodes size() :: " + subNodes.size());
        Node assertExpr = this.stack.pop();

        AssertComponent assertComponent = new AssertComponent();
        Integer startPos = 2; // Assert Component out:debug With expr End
        if (this.stack.size() > 0 && subNodes.get(1).getText().charAt(0) == '{') {
            startPos = 3; // Assert {...configs...} Component out:debug With expr End
            Node peeked = this.stack.peek();
            if (peeked != null && peeked instanceof ObjectType) {
                //System.out.println("Configs found");
                Node assertConfiguration = (ObjectType) this.stack.pop();
                assertComponent.setConfigs(assertConfiguration);
            }
        }

        String componentNamespace = "";
        String componentName = "";
        String componentIdentifier = "";
        
        List<String> namespace = new ArrayList<String>();
        
        for (int i = startPos; i <= subNodes.size() - 4; i ++) {
            String item = subNodes.get(i).getText();
            //System.out.println("item :: " + i + " :: " + item);
            if (item.equals(":") == false) {
                namespace.add(item);
            }
        }
        
        //System.out.println("namespace=" + namespace);
        
        // the component namespace is before second last item
        // the component name is the second last item
        // the component identifier is the last item
        if (namespace.size() >= 3) {
            for (int i = 0; i <= namespace.size() - 3; i ++) {
                componentNamespace += namespace.get(i) + ":";
            }
        }
        
        //System.out.println("component namespace=" + componentNamespace);
        
        // Cut out the last ":"
        if (componentNamespace.length() > 0) {
            componentNamespace = componentNamespace.substring(0, componentNamespace.length() - 1);
        }
        
        componentName = namespace.get(namespace.size() - 2);
        componentIdentifier = namespace.get(namespace.size() - 1);
        
        assertComponent.setAssertName(this.getModuleFilePath());
        assertComponent.setComponentNamespace(componentNamespace);
        assertComponent.setComponentName(componentName);
        assertComponent.setComponentIdentifier(componentIdentifier);
        
        assertComponent.setAssertExpr(assertExpr);

        if (componentNamespace.isEmpty()) {
            //System.out.println("Assert putting without ns");
            List<AssertComponent> componentAsserts = this.getOperonTestsContext().getComponentAsserts().get(componentName + ":" + componentIdentifier);
            if (componentAsserts == null) {
                componentAsserts = new ArrayList<AssertComponent>();
            }
            componentAsserts.add(assertComponent);
            this.getOperonTestsContext().getComponentAsserts().put(componentName + ":" + componentIdentifier, componentAsserts);
        }
        else {
            //System.out.println("AssertComponent putting :: " + componentNamespace + ":" + componentName + ":" + componentIdentifier);
            List<AssertComponent> componentAsserts = this.getOperonTestsContext().getComponentAsserts().get(componentNamespace + ":" + componentName + ":" + componentIdentifier);
            if (componentAsserts == null) {
                componentAsserts = new ArrayList<AssertComponent>();
            }
            componentAsserts.add(assertComponent);
            this.getOperonTestsContext().getComponentAsserts().put(componentNamespace + ":" + componentName + ":" + componentIdentifier, componentAsserts);
        }
    }
    
    @Override
    public void exitMock_stmt(OperonTestsParser.Mock_stmtContext ctx) {
        log.debug("exitMock_component_stmt :: stack size :: " + this.stack.size());
        //System.out.println("exitMock_component_stmt :: stack size :: " + this.stack.size());
        // extract 'Mock Component' (ID ':')+ ID 'With' expr 'End'
        List<ParseTree> subNodes = this.getContextChildNodes(ctx);
        //System.out.println("subNodes size() :: " + subNodes.size());
        Node mockExpr = this.stack.pop();

        String componentNamespace = "";
        String componentName = "";
        String componentIdentifier = "";
        
        List<String> namespace = new ArrayList<String>();
        
        for (int i = 1; i <= subNodes.size() - 4; i ++) {
            String item = subNodes.get(i).getText();
            //System.out.println("Mock :: item :: " + i + " :: " + item);
            if (item.equals(":") == false) {
                namespace.add(item);
            }
        }
        
        //System.out.println("Mock namespace :: " + namespace);
        
        // the component namespace is before second last item
        // the component name is the second last item
        // the component identifier is the last item
        if (namespace.size() >= 3) {
            for (int i = 0; i <= namespace.size() - 3; i ++) {
                componentNamespace += namespace.get(i) + ":";
            }
        }
        
        // Cut out the last ":"
        if (componentNamespace.length() > 0) {
            componentNamespace = componentNamespace.substring(0, componentNamespace.length() - 1);
        }
        
        componentName = namespace.get(namespace.size() - 2);
        componentIdentifier = namespace.get(namespace.size() - 1);
        
        //System.out.println("Mock Component: " + componentNamespace + ":" + componentName + ":" + componentIdentifier);
        
        MockComponent mockComponent = new MockComponent();

        mockComponent.setComponentNamespace(componentNamespace);
        mockComponent.setComponentName(componentName);
        mockComponent.setComponentIdentifier(componentIdentifier);
        mockComponent.setMockExpr(mockExpr);
        
        if (componentNamespace.length() > 0) {
            this.getOperonTestsContext().getComponentMocks().put(componentNamespace + ":" + componentName + ":" + componentIdentifier, mockComponent);
        }
        else {
            this.getOperonTestsContext().getComponentMocks().put(componentName + ":" + componentIdentifier, mockComponent);
        }
    }

    @Override
    public void enterChoice(OperonTestsParser.ChoiceContext ctx) {
        log.debug("ENTER Choice :: Stack size :: " + this.stack.size());
        Statement choiceStatement = new DefaultStatement(this.getOperonTestsContext());
        choiceStatement.setId("ChoiceStatement");
        this.setPreviousStatementForStatement(choiceStatement);
    }

    @Override
    public void exitChoice(OperonTestsParser.ChoiceContext ctx) {
        log.debug("EXIT Choice :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        // count: io.operon.parser.OperonTestsParser$ExprContext, and apply stack pop() same amount
        int popCount = 0;
        boolean hasOtherwise = false;
        for (int i = 0; i < subNodesSize; i ++) {
            if (subNodes.get(i) instanceof TerminalNode) {
                if (subNodes.get(i).getText().equals("Otherwise")) {
                    hasOtherwise = true;
                }
            }
            
            if (subNodes.get(i) instanceof OperonTestsParser.ExprContext) {
                popCount += 1;
            }
        }
        
        log.debug("  Choice :: subNodes :: " + subNodesSize);
        Choice choice = new Choice(this.currentStatement);
        
        //System.out.println("popCount :: " + popCount);
        if (hasOtherwise) {
            Node otherwise = this.stack.pop();
            choice.setOtherwise(otherwise);
            for (int i = 0; i < (popCount - 1) / 2; i ++) {
                //System.out.println("Popping");
                Node then = this.stack.pop();
                Node when = this.stack.pop();
                choice.addWhen(when);
                choice.addThen(then);
            }
        }
        else {
            //System.out.println("No Otherwise");
            for (int i = 0; i < popCount / 2; i ++) {
                //System.out.println("Popping");
                Node then = this.stack.pop();
                Node when = this.stack.pop();
                choice.addWhen(when);
                choice.addThen(then);
            }
        }
        
        this.stack.push(choice);
        this.restorePreviousScope();
    }

    @Override
    public void exitFilter_full_expr(OperonTestsParser.Filter_full_exprContext ctx) {
        log.debug("EXIT Filter_full_expr :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        
        Filter filter = new Filter(this.getCurrentStatement());
        //
        // Could be expr or splicingExpr
        //
        Node filterList = this.stack.pop();
        filter.setFilterListExpression(filterList);
        
        if (this.stack.size() > 0 && subNodes.get(1).getText().charAt(0) == '{') {
            Node peeked = this.stack.peek();
            if (peeked != null && peeked instanceof ObjectType) {
                Node filterConfiguration = this.stack.pop();
                filter.setConfigs(filterConfiguration);
            }
            else {
                // noop
            }
        }
        this.stack.push(filter);
    }

    @Override
    public void exitFilter_expr(OperonTestsParser.Filter_exprContext ctx) {
        log.debug("EXIT Filter :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        
        Filter filter = new Filter(this.getCurrentStatement());
        //
        // Could be expr or splicingExpr
        //
        Node filterList = this.stack.pop();
        filter.setFilterListExpression(filterList);
        
        if (this.stack.size() > 0 && subNodes.get(0).getText().charAt(0) == '{') {
            Node peeked = this.stack.peek();
            if (peeked != null && peeked instanceof ObjectType) {
                Node filterConfiguration = this.stack.pop();
                filter.setConfigs(filterConfiguration);
            }
        }
        
        this.stack.push(filter);
    }
    
    @Override
    public void exitFilter_list(OperonTestsParser.Filter_listContext ctx) {
        log.debug("EXIT Filter_list :: Stack size :: " + this.stack.size());

        FilterList filterList = new FilterList(currentStatement);
        List<FilterListExpr> filterExprList = new ArrayList<FilterListExpr>();
        
        while (this.stack.size() >= 1) {
            Node filterExpr = this.stack.peek();
            if (filterExpr instanceof FilterListExpr) {
                filterExpr = this.stack.pop();
                filterExprList.add((FilterListExpr) filterExpr);
            }
            else {
                break;
            }
        }

        Collections.reverse(filterExprList);
        filterList.setFilterExprList(filterExprList);
        this.stack.push(filterList);
    }

    // 
    // Expr or SplicingExpr
    // 
    @Override
    public void exitFilter_list_expr(OperonTestsParser.Filter_list_exprContext ctx) {
        log.debug("EXIT Filter_list_expr :: Stack size :: " + this.stack.size());
        FilterListExpr filterListExpr = new FilterListExpr(this.getCurrentStatement());
        filterListExpr.setFilterExpr(this.stack.pop());
        this.stack.push(filterListExpr);
    }

    @Override
    public void exitSplicing_expr(OperonTestsParser.Splicing_exprContext ctx) {
        log.debug("EXIT Splicing_expr :: Stack size :: " + this.stack.size());

        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        log.debug("    SubTree nodes :: " + subNodesSize);
        
        // :: expr
        if (subNodesSize == 2 && subNodes.get(0) instanceof TerminalNode) {
            try {
                log.debug("    SpliceLeft");
                Node spliceUntilNode = this.stack.pop();
                List<Node> params = new ArrayList<Node>();
                params.add(spliceUntilNode);
                SpliceLeft splicingLeft = new SpliceLeft(this.getCurrentStatement(), params);
                this.stack.push(splicingLeft);
            } catch (Exception e) {
                throw new RuntimeException("Error :: SpliceLeft :: " + e.getMessage());
            }
        }
        
        // expr ::
        else if (subNodesSize == 2 && subNodes.get(1) instanceof TerminalNode) {
            try {
                log.debug("    SpliceRight");
                Node spliceUntilNode = this.stack.pop();
                List<Node> params = new ArrayList<Node>();
                params.add(spliceUntilNode);
                SpliceRight spliceRight = new SpliceRight(this.getCurrentStatement(), params);
                this.stack.push(spliceRight);
            } catch (Exception e) {
                throw new RuntimeException("Error :: SpliceRight :: " + e.getMessage());
            }
        }
        
        // expr :: expr
        else if (subNodesSize == 3 && subNodes.get(1) instanceof TerminalNode) {
            try {
                log.debug("    Splicing lhs - rhs");
                Node spliceCountNode = this.stack.pop();
                Node spliceStartNode = this.stack.pop();
                List<Node> params = new ArrayList<Node>();
                params.add(spliceStartNode);
                params.add(spliceCountNode);
                SpliceRange splicingRange = new SpliceRange(this.getCurrentStatement(), params);
                this.stack.push(splicingRange);
            } catch (Exception e) {
                throw new RuntimeException("Error :: SpliceRange :: " + e.getMessage());
            }
        }

        else if (subNodesSize > 0) {
            log.debug("    Splicing_expr :: unknown :: " + subNodes.get(0));
        }
    }

    @Override
    public void exitOperator_expr(OperonTestsParser.Operator_exprContext ctx) {
        log.debug("EXIT Operator_expr :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        
        // Get the FunctionRef
        FunctionRef funcRef = (FunctionRef) this.stack.pop();
        
        boolean isCascade = false;
        if (subNodes.get(subNodes.size() - 2) instanceof TerminalNode && 
            subNodes.get(subNodes.size() - 2).getText().toLowerCase().equals("cascade")) {
            log.debug("  >> Operator :: set cascade true :: " + subNodes.get(subNodes.size() - 2).getText());
            isCascade = true;
        }
        
        // Get the overloaded operator:
        for (int i = 0; i < subNodesSize; i ++) {
            log.debug(subNodes.get(i).getClass().getName());
            if (subNodes.get(i) instanceof TerminalNode) {
                log.debug("  >> Operator :: terminal-node found.");
            }
        }
        String operator = subNodes.get(2).getText();
        log.debug(" >> OPERATOR :: " + operator);
        Operator op = new Operator(this.getCurrentStatement()); // TODO: might not to inherit Node, therefore giving statement not required.
        op.setOperator(operator);
        op.setFunctionRef(funcRef);
        op.setCascade(isCascade);
        this.stack.push(op);
    }
    
    //
    // When exiting an Expr, then pop the stack, and wire new Node (Unary, Binary, or Multi).
    // 
    @Override
    public void exitExpr(OperonTestsParser.ExprContext ctx) {
        log.debug("EXIT Expr :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);

        String exprAsString = this.getExpressionAsString(subNodes);
        //System.out.println("EXIT EXPR=\"" + exprAsString + "\"");
        int subNodesSize = subNodes.size();

        if (subNodesSize == 1) {
            log.debug("ENTER UNARYNODE :: SubNodesSize == 1, Stack size == " + this.stack.size());
            UnaryNode unode = new UnaryNode(this.currentStatement);
            unode.setExpr(exprAsString);
            unode.setNode(this.stack.pop());
            this.stack.push(unode);
            log.debug("EXIT SubNodesSize == 1");
        }
        
        else if (subNodesSize == 3 && subNodes.get(1) instanceof TerminalNode) {
            log.debug("ENTER BINARYNODE");
            
            Node rhs = this.stack.pop();
            Node lhs = this.stack.pop();
            
            BinaryNodeProcessor op = null;
            
            // Check which op and create and assign it
            TerminalNode token = ctx.getToken(OperonTestsParser.PLUS, 0);
            
            if (token != null) {
                op = new Plus();
                log.debug("  Plus()");
            }
            
            else if ( (token = ctx.getToken(OperonTestsParser.MINUS, 0)) != null) {
                op = new Minus();
                log.debug("  Minus()");
            }
            
            else if ( (token = ctx.getToken(OperonTestsParser.MULT, 0)) != null) {
                op = new Multiplicate();
                log.debug("  Multiplicate()");
            }
            
            else if ( (token = ctx.getToken(OperonTestsParser.DIV, 0)) != null) {
                op = new Division();
                log.debug("  Division()");
            }
            
            else if ( (token = ctx.getToken(OperonTestsParser.MOD, 0)) != null) {
                op = new Modulus();
                log.debug("  Modulus()");
            }
            
            else if ( (token = ctx.getToken(OperonTestsParser.POW, 0)) != null) {
                op = new Power();
                log.debug("  Power()");
            }
            
            else if ( (token = ctx.getToken(OperonTestsParser.EQ, 0)) != null) {
                op = new Eq();
                log.debug("  Eq()");
            }
            
            else if ( (token = ctx.getToken(OperonTestsParser.IEQ, 0)) != null) {
                op = new InEq();
                log.debug("  InEq()");
            }

            else if ( (token = ctx.getToken(OperonTestsParser.GT, 0)) != null) {
                op = new Gt();
                log.debug("  Gt()");
            }

            else if ( (token = ctx.getToken(OperonTestsParser.GTE, 0)) != null) {
                op = new Gte();
                log.debug("  Gte()");
            }

            else if ( (token = ctx.getToken(OperonTestsParser.LT, 0)) != null) {
                op = new Lt();
                log.debug("  Lt()");
            }

            else if ( (token = ctx.getToken(OperonTestsParser.LTE, 0)) != null) {
                op = new Lte();
                log.debug("  Lte()");
            }

            else if ( (token = ctx.getToken(OperonTestsParser.AND, 0)) != null) {
                op = new And();
                log.debug("  And()");
            }

            else if ( (token = ctx.getToken(OperonTestsParser.OR, 0)) != null) {
                op = new Or();
                log.debug("  Or()");
            }
            
            BinaryNode bnode = new BinaryNode(this.currentStatement);
            bnode.setExpr(exprAsString);
            bnode.setLhs(lhs);
            bnode.setRhs(rhs);
            bnode.setBinaryNodeProcessor(op);
            
            this.stack.push(bnode);
            
            log.debug("EXIT BINARYNODE");
        }
        
        else if (subNodesSize == 2 
                && subNodes.get(0) instanceof TerminalNode) {
            log.debug("ENTER UnaryNode (- or Not)");

            UnaryNode unode = new UnaryNode(this.currentStatement);
            unode.setExpr(exprAsString);
            unode.setNode(this.stack.pop());

            UnaryNodeProcessor op = null;
            
            // Check which op and create and assign it
            TerminalNode token = ctx.getToken(OperonTestsParser.NOT, 0);
            
            if (token != null) {
                op = new Not();
                log.debug("  Not()");
            }
            
            else if ( (token = ctx.getToken(OperonTestsParser.MINUS, 0)) != null || (token = ctx.getToken(OperonTestsParser.NEGATE, 0)) != null) {
                op = new Negate();
                log.debug("  Negate()");
            }

            unode.setUnaryNodeProcessor(op);
            this.stack.push(unode);
            log.debug("EXIT SubNodesSize == 1");
        }
        
        else if (subNodesSize == 3 
                && subNodes.get(0) instanceof TerminalNode
                && subNodes.get(2) instanceof TerminalNode) {
            log.debug("ENTER UnaryNode (Parentheses)");

            UnaryNode unode = new UnaryNode(this.currentStatement);
            unode.setExpr(exprAsString);
            unode.setNode(this.stack.pop());
            this.stack.push(unode);
            log.debug("EXIT SubNodesSize == 1");
        }
        
        else if (subNodesSize == 2) {
            // Known cases are instances of (expr expr) --> map_expr, e.g. "[1,2,3] Map @ + 1 End" --> JsonContext, and Map_exprContext and (Not expr)
            // Should be handled as multinode
            log.debug("ENTER MultiNode, subNodesSize = 2");
            for (int i = 0; i < subNodesSize; i ++) {
                log.debug("    SubNode " + i + " :: " + subNodes.get(i).getClass().getName());
            }
            MultiNode mnode = new MultiNode(this.currentStatement);
            mnode.setExpr(exprAsString);
            log.debug("  MultiNode :: Child count :: " + subNodesSize);
            log.debug("  MultiNode :: Stack size :: " + this.stack.size());
            
            if (subNodesSize > 1) {
                for (int i = 0; i < subNodesSize; i ++) {
                    Node node = this.stack.pop();
                    log.debug("   MN :: Loop :: " + node.getClass().getName());
                    mnode.addNode(node);
                }
                log.debug("  MultiNode :: Stack size :: " + this.stack.size());
            }
            this.stack.push(mnode);
            log.debug("EXIT MultiNode");
        }
        
        else {
            log.debug("ENTER MultiNode");
            for (int i = 0; i < subNodesSize; i ++) {
                log.debug("    SubNode " + i + " :: " + subNodes.get(i).getClass().getName());
            }
            
            MultiNode mnode = new MultiNode(this.currentStatement);
            mnode.setExpr(exprAsString);
            log.debug("  MultiNode :: Child count :: " + subNodesSize);
            log.debug("  MultiNode :: Stack size :: " + this.stack.size());
            
            if (subNodesSize > 1) {
                for (int i = 0; i < subNodesSize; i ++) {
                    Node node = this.stack.pop();
                    log.debug("   MN :: Loop :: " + node.getClass().getName());
                    mnode.addNode(node);
                }
                log.debug("  MultiNode :: Stack size :: " + this.stack.size());
            }
            this.stack.push(mnode);
            
            log.debug("EXIT MultiNode");
        }
    }

    @Override
    public void exitParentheses_expr(OperonTestsParser.Parentheses_exprContext ctx) {
        log.debug("EXIT parentheses_expr");
    }
    
    @Override
    public void exitAssign_expr(OperonTestsParser.Assign_exprContext ctx) {
        log.debug("EXIT Assign_expr :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        Assign assignNode = new Assign(this.currentStatement);
        String symbol = null;
        List<TerminalNode> namespaces = new ArrayList<TerminalNode>();
        
        if (ctx.ID() != null) {
            namespaces = ctx.ID();
        }
        
        if (ctx.CONST_ID() != null) {
            symbol = ctx.CONST_ID().toString();
            assignNode.setValueRef(symbol);
        }
        
        Node assignExpr = this.stack.pop();
        assignNode.setAssignExpr(assignExpr);
        
        //System.out.println("EXIT Assign expr");
        //System.out.println("  Assign valueRef=" + symbol);
        //System.out.println("  For expr=\"" + forExpr.getExpr() + "\", class name=" + forExpr.getClass().getName());
        
        for (TerminalNode tn : namespaces) {
            String ns = tn.toString();
            assignNode.getNamespaces().add(ns);
        }
        
        this.stack.push(assignNode);
    }

    @Override
    public void exitBreak_loop(OperonTestsParser.Break_loopContext ctx) {
        log.debug("EXIT Break :: Stack size :: " + this.stack.size());
        BreakLoop breakLoop = new BreakLoop(this.currentStatement);
        this.stack.push(breakLoop);
    }

    @Override
    public void exitContinue_loop(OperonTestsParser.Continue_loopContext ctx) {
        log.debug("EXIT Continue :: Stack size :: " + this.stack.size());
        ContinueLoop continueLoop = new ContinueLoop(this.currentStatement);
        this.stack.push(continueLoop);
    }

    @Override
    public void enterLoop_expr(OperonTestsParser.Loop_exprContext ctx) {
        log.debug("ENTER Loop_expr :: Stack size :: " + this.stack.size());
        Statement loopStatement = new DefaultStatement(this.getOperonTestsContext());
        loopStatement.setId("LoopStatement");
        this.setPreviousStatementForStatement(loopStatement);
    }
    
    @Override
    public void exitLoop_expr(OperonTestsParser.Loop_exprContext ctx) {
        log.debug("EXIT Loop_expr :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        io.operon.runner.node.Loop loopNode = new io.operon.runner.node.Loop(this.currentStatement);
        String symbol = null;
        if (ctx.CONST_ID() != null) {
            symbol = ctx.CONST_ID().toString();
            loopNode.setValueRef(symbol);
        }
        Node loopExpr = this.stack.pop();
        loopNode.setLoopExpr(loopExpr);
        Node loopIteratorExpr = this.stack.pop();
        loopNode.setLoopIteratorExpr(loopIteratorExpr);
        
        //System.out.println("EXIT Loop EXPR");
        //System.out.println("  Loop valueRef=" + symbol);
        //System.out.println("  Loop iterator expr=\"" + loopIteratorExpr.getExpr() + "\", class name=" + loopIteratorExpr.getClass().getName());
        //System.out.println("  Loop expr=\"" + loopExpr.getExpr() + "\", class name=" + loopExpr.getClass().getName());
        
        if (this.stack.size() > 0 && subNodes.get(1).getText().charAt(0) == '{') {
            Node peeked = this.stack.peek();
            if (peeked != null && peeked instanceof ObjectType) {
                Node loopConfiguration = this.stack.pop();
                loopNode.setConfigs(loopConfiguration);
            }
        }
        
        this.stack.push(loopNode);
        this.restorePreviousScope();
    }

    @Override
    public void enterDo_while_expr(OperonTestsParser.Do_while_exprContext ctx) {
        log.debug("ENTER Do_while_expr :: Stack size :: " + this.stack.size());
        Statement doWhileStatement = new DefaultStatement(this.getOperonTestsContext());
        doWhileStatement.setId("DoWhileStatement");
        this.setPreviousStatementForStatement(doWhileStatement);
    }
    
    @Override
    public void exitDo_while_expr(OperonTestsParser.Do_while_exprContext ctx) {
        log.debug("EXIT Do_while_expr :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        io.operon.runner.node.DoWhile doWhileNode = new io.operon.runner.node.DoWhile(this.currentStatement);

        Node predicateExpr = this.stack.pop();
        doWhileNode.setPredicateExpr(predicateExpr);
        Node doExpr = this.stack.pop();
        doWhileNode.setDoExpr(doExpr);
        
        if (this.stack.size() > 0 && subNodes.get(1).getText().charAt(0) == '{') {
            Node peeked = this.stack.peek();
            if (peeked != null && peeked instanceof ObjectType) {
                Node doWhileConfiguration = this.stack.pop();
                doWhileNode.setConfigs(doWhileConfiguration);
            }
        }
        
        this.stack.push(doWhileNode);
        this.restorePreviousScope();
    }

    @Override
    public void enterWhile_expr(OperonTestsParser.While_exprContext ctx) {
        log.debug("ENTER While_expr :: Stack size :: " + this.stack.size());
        Statement whileStatement = new DefaultStatement(this.getOperonTestsContext());
        whileStatement.setId("WhileStatement");
        this.setPreviousStatementForStatement(whileStatement);
    }
    
    @Override
    public void exitWhile_expr(OperonTestsParser.While_exprContext ctx) {
        log.debug("EXIT While_expr :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        io.operon.runner.node.While whileNode = new io.operon.runner.node.While(this.currentStatement);

        Node whileExpr = this.stack.pop();
        whileNode.setWhileExpr(whileExpr);
        Node predicateExpr = this.stack.pop();
        whileNode.setPredicateExpr(predicateExpr);
        
        if (this.stack.size() > 0 && subNodes.get(1).getText().charAt(0) == '{') {
            Node peeked = this.stack.peek();
            if (peeked != null && peeked instanceof ObjectType) {
                Node whileConfiguration = this.stack.pop();
                whileNode.setConfigs(whileConfiguration);
            }
        }
        
        this.stack.push(whileNode);
        this.restorePreviousScope();
    }
    
    @Override
    public void enterMap_expr(OperonTestsParser.Map_exprContext ctx) {
        log.debug("ENTER Map_expr :: Stack size :: " + this.stack.size());
        Statement mapStatement = new DefaultStatement(this.getOperonTestsContext());
        mapStatement.setId("MapStatement");
        this.setPreviousStatementForStatement(mapStatement);
    }
    
    @Override
    public void exitMap_expr(OperonTestsParser.Map_exprContext ctx) {
        log.debug("EXIT Map_expr :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        io.operon.runner.node.Map map = new io.operon.runner.node.Map(this.currentStatement);
        Node mapExpr = this.stack.pop();
        map.setMapExpr(mapExpr);
        
        //System.out.println("EXIT MAP EXPR");
        //System.out.println("MAP EXPR=\"" + mapExpr.getExpr() + "\", class name=" + mapExpr.getClass().getName());
        
        if (this.stack.size() > 0 && subNodes.get(1).getText().charAt(0) == '{') {
            Node peeked = this.stack.peek();
            if (peeked != null && peeked instanceof ObjectType) {
                Node mapConfiguration = this.stack.pop();
                map.setConfigs(mapConfiguration);
            }
        }
        
        this.stack.push(map);
        this.restorePreviousScope();
    }
    
    @Override
    public void exitValue_ref(OperonTestsParser.Value_refContext ctx) {
        log.debug("TestsCompiler :: EXIT Value_ref :: Stack size :: " + this.stack.size());
        ValueRef vrNode = new ValueRef(this.getCurrentStatement());
        // Set correct symbol:
        String symbol = null;
        List<TerminalNode> namespaces = new ArrayList<TerminalNode>();
        
        if (ctx.ID() != null) {
            namespaces = ctx.ID();
        }
        
        if (ctx.CONST_ID() != null) {
            symbol = ctx.CONST_ID().toString();
        }
        
        else if (ctx.CURRENT_VALUE() != null) {
            symbol = ctx.CURRENT_VALUE().toString();
        }
        
        else if (ctx.OBJ_SELF_REFERENCE() != null) {
            symbol = ctx.OBJ_SELF_REFERENCE().toString();
            this.getOperonTestsContext().getConfigs().setSupportPos(true);
            this.getOperonTestsContext().getConfigs().setSupportParent(true);
        }
        
        else if (ctx.OBJ_ROOT_REFERENCE() != null) {
            symbol = ctx.OBJ_ROOT_REFERENCE().toString();
            this.getOperonTestsContext().getConfigs().setSupportPos(true);
            this.getOperonTestsContext().getConfigs().setSupportParent(true);
        }
        
        else if (ctx.ROOT_VALUE() != null) {
            symbol = ctx.ROOT_VALUE().toString();
        }
        vrNode.setValueRef(symbol);
        
        for (TerminalNode tn : namespaces) {
            String ns = tn.toString();
            vrNode.getNamespaces().add(ns);
        }
        
        log.debug("  >> valueRef :: " + symbol);
        this.stack.push(vrNode);
    }

    //
    // $(expr)
    //
    @Override
    public void exitComputed_value_ref(OperonTestsParser.Computed_value_refContext ctx) {
        log.debug("EXIT Computed_value_ref :: Stack size :: " + this.stack.size());
        //System.out.println("COMPILER :: EXIT Computed_value_ref, currentStatement=" + this.currentStatement);
        ValueRef vrNode = new ValueRef(this.currentStatement);
        
        List<TerminalNode> namespaces = new ArrayList<TerminalNode>();
        
        if (ctx.ID() != null) {
            namespaces = ctx.ID();
        }
        Node expr = this.stack.pop();
        vrNode.setComputedValueRef(expr);
        
        for (TerminalNode tn : namespaces) {
            String ns = tn.toString();
            //System.out.println("NS-part: " + ns);
            vrNode.getNamespaces().add(ns);
        }
        
        this.stack.push(vrNode);
    }

    @Override
    public void exitFunction_regular_argument(OperonTestsParser.Function_regular_argumentContext ctx) {
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        log.debug("EXIT Function_regular_argument :: SUBNODES :: " + subNodesSize + ". VALUE :: " + subNodes.get(0).getText());
        FunctionRegularArgument fra = new FunctionRegularArgument(this.getCurrentStatement());
        Node regArg = this.stack.pop();
        fra.setArgument(regArg);
        this.stack.push(fra);
    }

    @Override
    public void exitFunction_named_argument(OperonTestsParser.Function_named_argumentContext ctx) {
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        log.debug("EXIT Function_named_argument :: SUBNODES :: " + subNodesSize + ". VALUE :: " + subNodes.get(0).getText());
        FunctionNamedArgument fna = new FunctionNamedArgument(this.getCurrentStatement());
        String argName = "";
        if (subNodes.get(0) instanceof TerminalNode) {
            argName = subNodes.get(0).getText();
            log.debug("  >> Arg-name :: " + argName);
        }
        Node argValue = this.stack.pop();
        fna.setArgumentName(argName);
        fna.setArgumentValue(argValue);
        this.stack.push(fna);
    }

    @Override
    public void exitFunction_arguments(OperonTestsParser.Function_argumentsContext ctx) {
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        log.debug("EXIT Function_arguments :: SUBNODES :: " + subNodesSize + ". " + subNodes.get(0).getText());
        
        FunctionArguments fArgs = new FunctionArguments(this.getCurrentStatement());
        // Collect arguments:
        List<Node> functionArguments = new ArrayList<Node>();
        int startPos = 1;
        int paramsEndParenthesesIndex = 0;
        for (int i = startPos; i < subNodesSize - 1; i ++) {
            
            // Reach end of params
            if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(")")) {
                paramsEndParenthesesIndex = i;
                break;
            }
            
            // Skip argument separator
            else if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(",")) {
                continue;
            }
            
            // Add argument
            else {
                log.debug("  Adding argument");
                Node functionArgument = this.stack.pop();
                functionArguments.add(functionArgument);
            }
        }
        Collections.reverse(functionArguments);
        fArgs.getArguments().addAll(functionArguments);
        this.stack.push(fArgs);
    }

    // TODO: lambdaFunctionRef and invoke!

    @Override
    public void exitFunction_ref_argument_placeholder(OperonTestsParser.Function_ref_argument_placeholderContext ctx) {
        log.debug("EXIT Function_ref_argument_placeholder :: Stack size :: " + this.stack.size());
        FunctionRefArgumentPlaceholder frArgPlaceholder = new FunctionRefArgumentPlaceholder(this.getCurrentStatement());
        this.stack.push(frArgPlaceholder);
    }

    @Override
    public void exitFunction_ref_invoke(OperonTestsParser.Function_ref_invokeContext ctx) {
        log.debug("EXIT Function_ref_invoke :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        
        FunctionArguments fArgs = (FunctionArguments) this.stack.pop();
        Node refExpr = (Node) this.stack.pop();
        FunctionRefInvoke frInvoke = new FunctionRefInvoke(this.getCurrentStatement());
        frInvoke.setRefExpr(refExpr);
        
        if (fArgs != null) {
            frInvoke.setFunctionArguments(fArgs);
        }
        
        //
        // Configuration object is optional
        //
        if (this.stack.size() > 0 && subNodes.get(1).getText().charAt(0) == '{') {
            Node peeked = this.stack.peek();
            if (peeked != null && peeked instanceof ObjectType) {
                Node functionRefInvokeConfiguration = this.stack.pop();
                frInvoke.setConfigs(functionRefInvokeConfiguration);
            }
        }
        
        this.stack.push(frInvoke);
    }
    
    @Override
    public void exitJson(OperonTestsParser.JsonContext ctx) {
        log.debug("EXIT Json :: Stack size :: " + this.stack.size());
        log.debug("    >> TRYING TO PEEK");
        Node n = this.stack.peek();
        assert (n != null): "exitJson :: null value from stack";
        //
        // NOTE: cannot print the peeked result, because it would require that value
        //       was already evaluated, which it is not at this stage.
        if (this.getCurrentStatement() != null) {
            this.getCurrentStatement().setNode(n);
            log.debug("    >> NODE SET");
        } else {
            log.debug("    >> CURRENT STATEMENT WAS NULL");
        }
    }
    
    @Override
    public void exitJson_value(OperonTestsParser.Json_valueContext ctx) {
        log.debug("EXIT Json_value :: Stack size :: " + this.stack.size());
        OperonValue jsonValue = new OperonValue(this.getCurrentStatement());
        List<ParseTree> subNodes = this.getContextChildNodes(ctx);
        
        log.debug("CHILD NODES :: " + subNodes.size() + " :: CHILD NODE [0] TYPE :: " + subNodes.get(0).getClass().getName());
        
        if (subNodes.size() > 0 
            && subNodes.get(0) instanceof RuleNode 
            && subNodes.get(0) instanceof OperonTestsParser.Json_objContext) {
            
            jsonValue.setValue(this.stack.pop()); // set ObjectType from stack
        }

        else if (subNodes.size() > 0 
            && subNodes.get(0) instanceof RuleNode 
            && subNodes.get(0) instanceof OperonTestsParser.Json_arrayContext) {
            log.debug("SETTING JSON VALUE --> JSON ARRAY.");
            Node value = this.stack.pop();
            if (value == null) {
                log.error("WARNING:: POPPED NULL VALUE!!!");
            }
            jsonValue.setValue(value); // set ArrayType from stack   
        }

        else if (subNodes.size() > 0 
            && subNodes.get(0) instanceof RuleNode 
            && subNodes.get(0) instanceof OperonParser.Path_valueContext) {
            log.debug("SETTING VALUE --> Path-value.");
            Node value = this.stack.pop();
            if (value == null) {
                log.error("WARNING:: POPPED NULL VALUE!!!");
            }
            jsonValue.setValue(value); // set PathValue from stack   
        }

        else if (subNodes.size() > 0 
            && subNodes.get(0) instanceof RuleNode 
            && subNodes.get(0) instanceof OperonParser.Compiler_obj_config_lookupContext) {
            log.debug("SETTING VALUE --> Compiler_obj_config_lookupContext-value.");
            Node value = this.stack.pop();
            if (value == null) {
                log.error("WARNING:: POPPED NULL VALUE!!!");
            }
            jsonValue.setValue(value); // set PathValue from stack   
        }

        else if (subNodes.size() > 0 && subNodes.get(0) instanceof TerminalNode) {
            TerminalNode token = ctx.getToken(OperonTestsParser.STRING, 0);
            
            if (token != null) {
                StringType sNode = new StringType(this.getCurrentStatement());
                String symbolText = token.getSymbol().getText();
                log.debug("TerminalNode. Text :: " + symbolText);
                sNode.setValue(symbolText);
                jsonValue.setValue(sNode);
            }
            
            else if ( (token = ctx.getToken(OperonTestsParser.NUMBER, 0)) != null) {
                NumberType nNode = new NumberType(this.currentStatement);
                String symbolText = token.getSymbol().getText().toLowerCase();
                log.debug("TerminalNode. Text :: " + symbolText);
                
                int dotPos = symbolText.indexOf(".");
                int expPos = symbolText.indexOf("e");
                
                if (symbolText.length() <= 2 && dotPos == -1 && expPos == -1) {
                    nNode.setDoubleValue((double) Byte.valueOf(symbolText));
                }
                else if (symbolText.length() > 2 && symbolText.length() <= 4 && dotPos == -1 && expPos == -1) {
                    nNode.setDoubleValue((double) Short.valueOf(symbolText));
                }
                else if (symbolText.length() > 4 && symbolText.length() <= 9 && dotPos == -1 && expPos == -1) {
                    nNode.setDoubleValue((double) Integer.valueOf(symbolText));
                }
                else {
                    nNode.setDoubleValue(Double.valueOf(symbolText).doubleValue());
                }
                
                nNode.setPrecision(NumberType.getPrecisionFromStr(symbolText));
                jsonValue.setValue(nNode);
            }
            
            else if ( (token = ctx.getToken(OperonTestsParser.JSON_FALSE, 0)) != null) {
                FalseType jsonFalse = new FalseType(this.getCurrentStatement());
                jsonValue.setValue(jsonFalse);
            }
            
            else if ( (token = ctx.getToken(OperonTestsParser.JSON_TRUE, 0)) != null) {
                TrueType jsonTrue = new TrueType(this.getCurrentStatement());
                jsonValue.setValue(jsonTrue);
            }
            
            else if ( (token = ctx.getToken(OperonTestsParser.JSON_NULL, 0)) != null) {
                NullType jsonNull = new NullType(this.getCurrentStatement());
                jsonValue.setValue(jsonNull);
            }

            else if ( (token = ctx.getToken(OperonTestsParser.RAW_STRING, 0)) != null) {
                RawValue raw = new RawValue(this.currentStatement);
                // substring is for cutting out the ' parts, which are used for BinaryString
                String rawStr = token.getSymbol().getText().substring(1, token.getSymbol().getText().length() - 1);
                rawStr = rawStr.replaceAll("\\\\`", "`");
                byte[] valueBytes = rawStr.getBytes(StandardCharsets.UTF_8);
                raw.setValue(valueBytes);
                jsonValue.setValue(raw);
            }

            else if ( (token = ctx.getToken(OperonParser.MULTILINE_STRING, 0)) != null) {
                StringType sNode = new StringType(this.currentStatement);
                String symbolText = token.getSymbol().getText();
                log.debug("TerminalNode: String. Text :: " + symbolText);
                
                // replace \r \n \t
                StringBuilder sb = new StringBuilder();
                
                for (int i = 3; i < symbolText.length() - 3; i ++) {
                    char c = symbolText.charAt(i);
                    
                    if (c == '\r') {
                        sb.append("\\r");
                    }
                    else if (c == '\n') {
                        sb.append("\\n");
                    }
                    else if (c == '\t') {
                        sb.append("\\t");
                    }
                    else {
                        sb.append(c);
                    }
                }
                
                symbolText = "\"" + sb.toString() + "\"";
                //System.out.println("symbolText :: " + symbolText);
                sNode.setValue(symbolText);
                jsonValue.setValue(sNode);
            }

            else if ( (token = ctx.getToken(OperonParser.MULTILINE_PADDED_STRING, 0)) != null) {
                //System.out.println("MULTILINE_PADDED_STRING");
                StringType sNode = new StringType(this.currentStatement);
                String symbolText = token.getSymbol().getText();
                log.debug("TerminalNode: String. Text :: " + symbolText);
                
                StringBuilder sb = new StringBuilder();
                
                boolean strippingMode = false;
                for (int i = 4; i < symbolText.length() - 3; i ++) {
                    char c = symbolText.charAt(i);
                    if (c != '\r' && c != '\n') {
                        if (strippingMode) {
                            if (c != ' ' && c != '\t') {
                                sb.append(c);
                                strippingMode = false;
                            }
                        }
                        else {
                            if (c == '\t') {
                                sb.append("\\t");
                            }
                            else {
                                sb.append(c);
                            }
                        }
                    }
                    else {
                        strippingMode = true;
                    }
                }
                
                symbolText = "\"" + sb.toString() + "\"";
                //System.out.println("symbolText :: " + symbolText);
                sNode.setValue(symbolText);
                jsonValue.setValue(sNode);
            }

            else if ( (token = ctx.getToken(OperonParser.MULTILINE_PADDED_LINES_STRING, 0)) != null) {
                //System.out.println("MULTILINE_PADDED_LINES_STRING");
                StringType sNode = new StringType(this.currentStatement);
                String symbolText = token.getSymbol().getText();
                log.debug("TerminalNode: String. Text :: " + symbolText);
                
                StringBuilder sb = new StringBuilder();
                
                boolean strippingMode = false;
                for (int i = 4; i < symbolText.length() - 3; i ++) {
                    char c = symbolText.charAt(i);
                    if (c != '\r' && c != '\n') {
                        if (strippingMode) {
                            if (c != ' ' && c != '\t') {
                                sb.append(c);
                                strippingMode = false;
                            }
                        }
                        else {
                            if (c == '\t') {
                                sb.append("\\t");
                            }
                            else {
                                sb.append(c);
                            }
                        }
                    }
                    else {
                        if (c == '\r') {
                            sb.append("\\r");
                        }
                        if (c == '\n') {
                            sb.append("\\n");
                        }
                        strippingMode = true;
                    }
                }
                
                symbolText = "\"" + sb.toString() + "\"";
                //System.out.println("symbolText :: " + symbolText);
                sNode.setValue(symbolText);
                jsonValue.setValue(sNode);
            }

            else if ( (token = ctx.getToken(OperonTestsParser.EMPTY_VALUE, 0)) != null) {
                EmptyType jsonEmptyValue = new EmptyType(this.getCurrentStatement());
                jsonValue.setValue(jsonEmptyValue);
                jsonValue.setIsEmptyValue(true);
            }
            
            else if ( (token = ctx.getToken(OperonTestsParser.END_VALUE, 0)) != null) {
                String symbolText = token.getSymbol().getText().toLowerCase();
                EndValueType jsonEndValue = new EndValueType(this.currentStatement);
                jsonValue.setValue(jsonEndValue);
            }
        }
        
        this.stack.push(jsonValue);
    }
    
    @Override
    public void exitJson_array(OperonTestsParser.Json_arrayContext ctx) {
        log.debug("EXIT Json_array :: Stack size :: " + this.stack.size());
        List<ParseTree> ruleChildNodes = this.getContextChildRuleNodes(ctx);
        int childCount = ruleChildNodes.size();
        
        ArrayType jsonArray = new ArrayType(this.getCurrentStatement());
        
        // For reversing traversed nodes
        List<Node> jsonValues = new ArrayList<Node>(); // Refactor: OperonValue -> Node
        
        for (int i = 0; i < childCount; i ++) {
            Node jsonValue = (Node) this.stack.pop();
            jsonValues.add(jsonValue);
        }
        
        try {
            // For reversing traversed nodes
            for (int i = jsonValues.size() - 1; i >= 0; i --) {
                jsonArray.addValue(jsonValues.get(i));
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        
        this.stack.push(jsonArray);
    }
    
    @Override
    public void exitJson_obj(OperonTestsParser.Json_objContext ctx) {
        log.debug("EXIT Json_obj :: Stack size :: " + this.stack.size());
        List<ParseTree> ruleChildNodes = this.getContextChildRuleNodes(ctx);
        int pairsCount = ruleChildNodes.size();
        
        ObjectType jsonObj = new ObjectType(this.currentStatement);
        jsonObj.setObjId(Integer.toString(this.objIndex));
        log.debug("    Set objId :: " + Integer.toString(this.objIndex));
        this.objIndex = this.objIndex + 1;
        List<PairType> jsonPairs = new ArrayList<PairType>();
        
        for (int i = 0; i < pairsCount; i ++) {
            PairType jsonPair = (PairType) this.stack.pop();
            jsonPair.setPosition(i);
            jsonPairs.add(jsonPair);
        }
        
        for (int i = jsonPairs.size() - 1; i >= 0; i --) {
            try {
                jsonObj.addPair(jsonPairs.get(i));
            } catch (OperonGenericException oge) {
                throw new RuntimeException(oge.getMessage());
            }
        }
        
        this.stack.push(jsonObj);
    }
    
    @Override
    public void exitJson_pair(OperonTestsParser.Json_pairContext ctx) {
        log.debug("EXIT Json_pair :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = this.getContextChildNodes(ctx);
        PairType jsonPair = new PairType(this.currentStatement);

        TerminalNode sKeyTN = ctx.STRING();
        String key = null;
        if (sKeyTN != null) {
            key = sKeyTN.getSymbol().getText();
        }
        else {
            key = "\"" + ctx.ID().getSymbol().getText() + "\"";
        }
        
        OperonValue value = new OperonValue(this.currentStatement);
        
        //System.out.println("Key="+key);
        
        if (subNodes.size() > 1) {
            Node jsonValue = this.stack.pop();
            value.setValue(jsonValue);
            if (jsonValue.isEmptyValue()) {
                value.setIsEmptyValue(true);
            }
        }
        else {
            TrueType tt = new TrueType(this.currentStatement);
            value.setValue(tt);
        }
        
        //System.out.println("Setted key:: " + key);
        
        if (subNodes.size() == 4) {
            OperonValueConstraint jvc = (OperonValueConstraint) this.stack.pop();
            String constraintAsString = subNodes.get(1).getText();
            jvc.setConstraintAsString(constraintAsString);
            jsonPair.setOperonValueConstraint(jvc);
        }
        
        log.debug("   PairType subNodes :: " + subNodes.size());
        
        jsonPair.setPair(key, value);
        this.stack.push(jsonPair);
    }
    
    //
    // '<?env:' | '<?config:' ID '>'
    //
    // This is used to configure query (e.g. ISD) on compile-time.
    // Common use case is to set the password-value.
    //
    // This method requires that exitJson_value does pop the value from the stack
    //
    @Override
    public void exitCompiler_obj_config_lookup(OperonTestsParser.Compiler_obj_config_lookupContext ctx) {
        log.debug("EXIT Compiler_obj_config_lookup :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        String symbolText = null;
        OperonValue result = null;
        
        //this.stack.pop();  // remove stack-item
        if (subNodes.size() > 0 && subNodes.get(1) instanceof TerminalNode) {
            TerminalNode idToken = ctx.getToken(OperonParser.ID, 0);

            if (idToken != null) {
                symbolText = idToken.getText();
            }

            if (subNodes.get(0).getText().equals("<?env:")) {
                // Read from environment-variable, parse as JSON:
                String envVariableStr = EnvGet.readEnvVariableAsString(symbolText);
                if (envVariableStr == null) {
                    throw new RuntimeException("Could not resolve environment-value for " + symbolText);
                }
                try {
                    result = JsonUtil.operonValueFromString(envVariableStr);
                } catch (OperonGenericException oge) {
                    throw new RuntimeException("Could not parse environment-value for " + symbolText + ", is it valid JSON?");
                }
            }
            else if (subNodes.get(0).getText().equals("<?config:")) {
                // TODO: read value from config-file.
                System.err.println("config: is not implemented");
            }
            else {
                // Not supported option
                System.err.println("Could not recognize: " + subNodes.get(0).getText() + ". Supported: <?env:value>.");
            }
            this.stack.push(result);
        }
        else {}
    }
    
    @Override
    public void exitJson_value_constraint(OperonTestsParser.Json_value_constraintContext ctx) {
        log.debug("EXIT Json_value_constraint :: Stack size :: " + this.stack.size());
        OperonValueConstraint jsonValueConstraint = new OperonValueConstraint(this.getCurrentStatement());
        jsonValueConstraint.setValueConstraint(this.stack.pop());
        this.stack.push(jsonValueConstraint);
    }
    
    @Override
    public void exitObj_access(OperonTestsParser.Obj_accessContext ctx) {
        String accessKey = "";
        
        if (ctx.ID() != null) {
            accessKey = ctx.ID().getSymbol().getText();
        }
        else {
            throw new RuntimeException("exitObj_access :: unknown symbol");
        }
        log.debug("EXIT OBJ_ACCESS :: " + ctx.ID()); 
        ObjAccess objAccess = new ObjAccess(this.getCurrentStatement()); 
        objAccess.setObjAccessKey(accessKey); 
        this.stack.push(objAccess);
    }
    
    @Override
    public void exitObj_dynamic_access(OperonTestsParser.Obj_dynamic_accessContext ctx) {
        log.debug("EXIT OBJ_DYNAMIC_ACCESS :: , stack size :: " + this.stack.size()); 
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        
        ObjDynamicAccess objDynamicAccess = new ObjDynamicAccess(this.getCurrentStatement()); 
        objDynamicAccess.setKeyExpr(this.stack.pop());
        
        ObjectType configs = null;
        
        //
        // Configs-jsonobj is optional.
        //
        if (this.stack.size() > 0 && subNodes.get(1).getText().charAt(0) == '{') {
            configs = (ObjectType) this.stack.pop();
        }
        objDynamicAccess.setConfigs(configs);
        
        this.stack.push(objDynamicAccess); 
    }

    @Override
    public void exitObj_deep_scan(OperonTestsParser.Obj_deep_scanContext ctx) {
        log.debug("EXIT OBJ_DEEP_SCAN :: " + ctx.ID());
        ObjDeepScan objDeepScan = new ObjDeepScan(this.getCurrentStatement());
        objDeepScan.setObjDeepScanKey(ctx.ID().getSymbol().getText());
        this.stack.push(objDeepScan);
    }
    
    @Override
    public void exitObj_dynamic_deep_scan(OperonTestsParser.Obj_dynamic_deep_scanContext ctx) {
        log.debug("EXIT OBJ_DYNAMIC_DEEP_SCAN");
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        
        ObjDynamicDeepScan objDynamicDeepScan = new ObjDynamicDeepScan(this.currentStatement);
        objDynamicDeepScan.setKeyExpr(this.stack.pop());
        
        ObjectType configs = null;
        
        //
        // Configs-jsonobj is optional.
        //
        if (this.stack.size() > 0 && subNodes.get(1).getText().charAt(0) == '{') {
            configs = (ObjectType) this.stack.pop();
        }
        objDynamicDeepScan.setConfigs(configs);
        this.stack.push(objDynamicDeepScan);
    }

    // 
    // ~.bin ~= (.bin) --> with parentheses
    // ~.bin ~= .bin   --> without parentheses
    // 
    @Override
    public void exitPath_matches(OperonTestsParser.Path_matchesContext ctx) {
        log.debug("EXIT PathMatches, stack size=" + this.stack.size());
        PathMatches pathMatches = new PathMatches(this.currentStatement);
        List<ParseTree> nodes = getContextChildNodes(ctx);
        int startPos = 1; // 'PathMatches' '('
        List<PathMatchPart> pathMatchParts = new ArrayList<PathMatchPart>();
        
        String pathMatchPartStr = "";
        
        Boolean objectMode = null; // either null, object or array -context
        
        FilterList filterList = null;
        
        // NOTE: we must start traversing from the END because we must pop the stack
        // from that direction!
        for (int i = nodes.size() - 1; i >= startPos; i --) {
            if (nodes.get(i).toString().charAt(0) == '(') {
                //System.out.println("Reached end of PathMatcher");
                break;
            }
            else if (nodes.get(i) instanceof TerminalNode) {
                //System.out.println("TERMINAL NODE :: " + nodes.get(i).getClass().getName() + " :: " + nodes.get(i));
                if (nodes.get(i).toString().charAt(0) == '.') {
                    
                    // Yield part previous, which was known to be a key:
                    if (objectMode == null) {
                        //System.out.println("Yield KeyPathMatchPart 1");
                        KeyPathMatchPart kpmp = new KeyPathMatchPart(pathMatchPartStr);
                        pathMatchParts.add(kpmp);
                        pathMatchPartStr = "";
                        objectMode = null;
                    }
                    
                    // Yield key:
                    else if (objectMode) {
                        //System.out.println("Yield KeyPathMatchPart 2");
                        KeyPathMatchPart kpmp = new KeyPathMatchPart(pathMatchPartStr);
                        pathMatchParts.add(kpmp);
                        pathMatchPartStr = "";
                        objectMode = null;
                    }
                    
                    // Error?
                    else {
                        throw new RuntimeException("ILLEGAL STATE: PathMatches --> expected KeyPathMatchPart");
                    }
                }
                
                else if (nodes.get(i).toString().charAt(0) == '[') {
                    //System.out.println("Yield FilterListPathMatchPart");
                    FilterListPathMatchPart flpmp = new FilterListPathMatchPart(filterList);
                    pathMatchParts.add(flpmp);
                    objectMode = null;
                }
                
                else if (nodes.get(i).toString().charAt(0) == ']') {
                    objectMode = false;
                }
                
                else if (nodes.get(i).toString().charAt(0) == '?') {
                    //System.out.println("Any PathMatchPart");
                    if (nodes.get(i).toString().length() == 1) {
                        //System.out.println("  - Yield AnySinglePathMatchPart");
                        AnySinglePathMatchPart aspmp = new AnySinglePathMatchPart();
                        pathMatchParts.add(aspmp);
                        objectMode = null;
                    }
                    else {
                        if (nodes.get(i).toString().charAt(1) == '+') {
                            //System.out.println("  - Yield AnySingleOrMorePathMatchPart");
                            AnySingleOrMorePathMatchPart asompmp = new AnySingleOrMorePathMatchPart();
                            pathMatchParts.add(asompmp);
                            objectMode = null;
                        }
                        else if (nodes.get(i).toString().charAt(1) == '*') {
                            //System.out.println("  - Yield AnyNoneOrMorePathMatchPart");
                            AnyNoneOrMorePathMatchPart anompmp = new AnyNoneOrMorePathMatchPart();
                            pathMatchParts.add(anompmp);
                            objectMode = null;
                        }
                    }
                }
                
                else {
                    pathMatchPartStr = nodes.get(i).toString();
                    objectMode = true;
                }
            }
            
            else if (nodes.get(i) instanceof RuleNode) {
                //System.out.println("RULE NODE --> pop stack");
                Node peekedNode = this.stack.peek();
                if (peekedNode instanceof FilterList) {
                    filterList = (FilterList) this.stack.pop();
                }
                else {
                    //System.out.println("PathMatches :: peekedNode --> DynamicKeyPathMatchPart");
                    DynamicKeyPathMatchPart dnKeyMatchPart = new DynamicKeyPathMatchPart(this.stack.pop());
                    pathMatchParts.add(dnKeyMatchPart);
                    objectMode = null;
                }
            }
        }
        
        Collections.reverse(pathMatchParts);
        PathMatch pathMatch = new PathMatch(this.currentStatement);
        pathMatch.setPathMatchParts(pathMatchParts);
        pathMatches.setPathMatch(pathMatch);
        this.stack.push(pathMatches);
    }

    @Override
    public void exitWhere_expr(OperonTestsParser.Where_exprContext ctx) {
        log.debug("EXIT Where, stack size=" + this.stack.size());
        Where where = new Where(this.currentStatement);
        List<ParseTree> nodes = getContextChildNodes(ctx);
        Node predExprNode = this.stack.peek();
        
        if (predExprNode instanceof PathMatches == false) {
            predExprNode = this.stack.pop();
            where.setWhereExpr(predExprNode);
        }
        
        PathMatches pathMatches = (PathMatches) this.stack.pop();
        
        //
        // Configs-jsonobj is optional.
        //
        ObjectType configs = null;
        if (this.stack.size() > 0 && nodes.get(1).getText().charAt(0) == '{') {
            configs = (ObjectType) this.stack.pop();
        }
        where.setConfigs(configs);
        
        where.setPathMatches(pathMatches);
        this.stack.push(where);
    }

    @Override
    public void exitUpdate_expr(OperonTestsParser.Update_exprContext ctx) {
        log.debug("EXIT Update, stack size=" + this.stack.size());
        Update update = new Update(this.getCurrentStatement());
        List<ParseTree> nodes = getContextChildNodes(ctx);
        
        // Update Path(...) : expr End
        
        //System.out.println("nodes size=" + nodes.size());
        for (int i = 0; i < nodes.size(); i ++) {
            //System.out.println(nodes.get(i).getText());
        }
        //System.out.println("===");
        
        //
        // Configuration object is optional.
        //
        // Skip from end: "End", skip from start "Update"
        boolean popValue = true;
        UpdatePair up = new UpdatePair();
        for (int i = nodes.size() - 2; i >= 1; i --) {
            //System.out.println("Stack size=" + this.stack.size());
            if (nodes.get(i) instanceof TerminalNode) {
                //System.out.println("SKIP TerminalNode: " + nodes.get(i).getText());
                continue;
            }
            //System.out.println("popping: " + nodes.get(i).getText());
            if (popValue) {
                //System.out.println("Pop value");
                Node popUpdateValue = this.stack.pop();
                up.setUpdateValue(popUpdateValue);
                popValue = false;
            }
            else {
                //System.out.println("Pop path");
                io.operon.runner.node.type.Path popPath = (io.operon.runner.node.type.Path) this.stack.pop();
                up.setPath(popPath);
                update.getPathUpdates().add(up);
                up = new UpdatePair();
                popValue = true;
            }
        }
        
        if (this.stack.size() > 0 && nodes.get(1).getText().charAt(0) == '{') {
            //System.out.println("Peeking");
            Node peeked = this.stack.peek();
            //System.out.println("Peeked type=" + peeked.getClass().getName());
            
            if (peeked != null && peeked instanceof ObjectType) {
                Node updateConfiguration = this.stack.pop();
                update.setConfigs(updateConfiguration);
            }
        }
        
        this.stack.push(update);
    }

    //
    // Update expr1: expr2 End
    //   NOTE: expr1 might be [Paths] or [[Paths]]
    //   NOTE: expr2 might be:
    //     - a single value
    //     - [value]
    //     - [[value]]
    // 
    @Override
    public void exitUpdate_array_expr(OperonTestsParser.Update_array_exprContext ctx) {
        log.debug("EXIT Update array expr, stack size=" + this.stack.size());
        List<ParseTree> nodes = getContextChildNodes(ctx);
        
        //
        // Configuration object is optional.
        //
        // Skip from end: "End", value, ':', and ']'. Skip from start "Update" and '['
        
        //System.out.println("Exit Update array expr, pop value");
        Node popUpdateValue = this.stack.pop();
        Node popUpdatePathsValue = this.stack.pop();
        
        //System.out.println("Exit Update array expr, expr2: " + popUpdateValue.getClass().getName());
        //System.out.println("Exit Update array expr, expr1: " + popUpdatePathsValue.getClass().getName());
        
        UpdateArray updateArray = new UpdateArray(this.currentStatement);
        
        updateArray.setUpdateValuesExpr(popUpdateValue);
        
        // This is required that we can prevent the double evaluation of this expr2 in order to
        // know its type.
        if (popUpdateValue instanceof UnaryNode) {
            try {
                //System.out.println("UNARYNODE :: " + ((UnaryNode) popUpdateValue).getNode().getClass().getName());
                if (((UnaryNode) popUpdateValue).getNode() instanceof OperonValue) {
                    OperonValue unboxed = (OperonValue) ((UnaryNode) popUpdateValue).getNode().evaluate();
                    //System.out.println("UNARYNODE unboxed :: " + unboxed.getClass().getName());
                    if (unboxed instanceof ArrayType) {
                        //System.out.println("updateArray.setIsUpdateExprSingleValue(true)");
                        updateArray.setIsUpdateExprSingleValue(false);
                    } else {
                        //System.out.println("updateArray.setIsUpdateExprSingleValue(false)");
                        updateArray.setIsUpdateExprSingleValue(true);
                    }
                }
            } catch (OperonGenericException oge) {
                System.err.println("OperonCompiler :: ERROR while unboxing expr in Update");
            }
        }
        else {
            updateArray.setIsUpdateExprSingleValue(true);
        }
        
        updateArray.setUpdatePathsExpr(popUpdatePathsValue);
        
        //
        // Configuration
        //
        if (this.stack.size() > 0 && nodes.get(1).getText().charAt(0) == '{') {
            //System.out.println("Peeking");
            Node peeked = this.stack.peek();
            //System.out.println("Peeked type=" + peeked.getClass().getName());
            
            if (peeked != null && peeked instanceof ObjectType) {
                Node updateConfiguration = this.stack.pop();
                updateArray.setConfigs(updateConfiguration);
            }
        }
        
        this.stack.push(updateArray);
    }

    // 
    // Path(.bin.bai[2].baa)
    // ~.bin.bai[2].baa
    //
    @Override
    public void exitPath_value(OperonTestsParser.Path_valueContext ctx) {
        log.debug("EXIT Path_value");
        io.operon.runner.node.type.Path path = new io.operon.runner.node.type.Path(this.currentStatement);
        List<ParseTree> nodes = getContextChildNodes(ctx);
        int startPos = 2;
        StringBuilder pathStr = new StringBuilder();
        
        String symbolText = nodes.get(0).getText();
        
        // Path(...)
        if (symbolText.charAt(0) == 'P') {
            for (int i = startPos; i < nodes.size() - 1; i ++) {
                pathStr.append(nodes.get(i).toString());
            }
        }
        // ~
        else {
            if (nodes.get(1).getText().charAt(0) == '(') {
                startPos = 2; // parentheses
                for (int i = startPos; i < nodes.size() - 1; i ++) {
                    pathStr.append(nodes.get(i).toString());
                }
            }
            else {
                startPos = 1; // no parentheses
                for (int i = startPos; i < nodes.size(); i ++) {
                    pathStr.append(nodes.get(i).toString());
                }
            }
        }
        
        List<PathPart> pathParts = PathCreate.constructPathParts(pathStr.toString());
        path.setPathParts(pathParts);
        
        this.stack.push(path);
    }

    @Override
    public void exitRange_expr(OperonTestsParser.Range_exprContext ctx) {
        log.debug("EXIT Range_expr. Stack size :: " + this.stack.size());
        Range rangeExpr = new Range(this.getCurrentStatement());
        rangeExpr.setRhs(this.stack.pop());
        rangeExpr.setLhs(this.stack.pop());
        this.stack.push(rangeExpr);
    }
    
    
    @Override
    public void exitIntegration_call(OperonTestsParser.Integration_callContext ctx) {
        log.debug("EXIT Integration_call :: Stack size :: " + this.stack.size());
        List<ParseTree> nodes = getContextChildNodes(ctx);

        IntegrationCall integrationCall = new IntegrationCall(this.getCurrentStatement());
        
        // Possible combinations:
        //  0  1 2  3  4 5
        // -> out:debug:{} # nodes.size() = 6
        // -> out:debug    # nodes.size() = 4
        // -> out:{}       # nodes.size() = 4
        // -> out          # nodes.size() = 2
        String componentName = nodes.get(1).toString();
        String componentId = null;
        
        if (nodes.size() == 6) {
            componentId = nodes.get(3).toString();
        } else if (nodes.size() == 4 && nodes.get(3).getClass().getName().equals("io.operon.parser.OperonTestsParser$Json_objContext") == false) {
            componentId = nodes.get(3).toString();
        }

        log.debug("COMPILER :: Integration Call :: " + componentName + ":" + componentId);
        integrationCall.setComponentName(componentName);
        integrationCall.setComponentId(componentId);
        
        // TODO: read the components -definition file?
        //       Or should this be done from the componentsUtil?
        // Must end with components.json, so we must take the path part, not the file
        /* --> moduleFilePath is not defined for tests.
        String [] moduleFilePathParts = this.getModuleFilePath().split("/");
        String moduleFilePathAdjusted = "";
        if (moduleFilePathParts[moduleFilePathParts.length - 1].contains(".")) {
            for (int i = 0; i < moduleFilePathParts.length - 1; i ++) {
                //System.out.println("Adding :: " + moduleFilePathParts[i]);
                moduleFilePathAdjusted += moduleFilePathParts[i] + "/";
            }
            moduleFilePathAdjusted += "components.json";
        }
        else {
            moduleFilePathAdjusted = this.getModuleFilePath();
        }
        System.out.println("ComponentsPath adjusted :: " + moduleFilePathAdjusted);

        integrationCall.setComponentsDefinitionFilePath(moduleFilePathAdjusted);
        */
        log.debug("COMPILER :: Integration call :: NODES LEN :: " + nodes.size());

        if (nodes.size() == 6 && nodes.get(5).getClass().getName().equals("io.operon.parser.OperonTestsParser$Json_objContext")
            || nodes.size() == 4 && nodes.get(3).getClass().getName().equals("io.operon.parser.OperonTestsParser$Json_objContext")
            ) {
            log.debug("COMPILER :: Integration call :: pop stack (Json_obj)");

            ObjectType jsonConfigValue = (ObjectType) this.stack.pop();
            // Don't log at this point, as it would cause obj-serialization,
            // which (currently) cannot be done at compile-time (e.g. ValueRef).
            integrationCall.setJsonConfiguration(jsonConfigValue);
        }
        this.stack.push(integrationCall);
    }

    @Override
    public void enterAuto_invoke_ref(OperonTestsParser.Auto_invoke_refContext ctx) {
        log.debug("ENTER AutoInvokeRef :: Stack size :: " + this.stack.size());
        Statement functionStatement = new FunctionStatement(this.getModuleContext());
        functionStatement.setId("LambdaFunctionStatement");
        log.debug("    >> Set previousStatement: " + this.getCurrentStatement().getId());
        this.getStatementStack().push(this.getCurrentStatement());
        functionStatement.setPreviousStatement(this.getCurrentStatement());
        this.getStatementStack().push(functionStatement);
        this.setCurrentStatement(functionStatement);
    }

    //
    // NOTE: auto_invoke_ref is same as lambda_function_ref, but is is invoked
    //       automatically on: ObjAccess.
    //
    @Override
    public void exitAuto_invoke_ref(OperonTestsParser.Auto_invoke_refContext ctx) {
        log.debug("EXIT AutoInvokeRef :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        log.debug("    >> AutoInvokeRef :: SUBNODES :: " + subNodesSize + ". " + subNodes.get(0).getText());
        
        LambdaFunctionRef lfnRef = new LambdaFunctionRef(this.getCurrentStatement());
        lfnRef.setInvokeOnAccess(true);

        // Collect params:
        java.util.Map<String, Node> functionParamValueMap = new HashMap<String, Node>();
        
        Node lambdaExpr = this.stack.pop();

        lfnRef.setParams(functionParamValueMap);
        lfnRef.setLambdaExpr(lambdaExpr);
        this.stack.push(lfnRef);
        this.getStatementStack().pop(); // Remove functionStatement from the statementStack
        Statement previousStatement = this.getStatementStack().pop();
        log.debug("    >> Set currentStatement with previousStatement: " + previousStatement.getId());
        this.setCurrentStatement(previousStatement);
    }
    
    @Override
    public void enterLambda_function_call(OperonTestsParser.Lambda_function_callContext ctx) {
        log.debug("ENTER LambdaFunction Stmt :: Stack size :: " + this.stack.size());
        Statement functionStatement = new FunctionStatement(this.getModuleContext());
        log.debug("    >> Set previousStatement: " + this.getCurrentStatement().getId());
        this.getStatementStack().push(this.getCurrentStatement());
        functionStatement.setPreviousStatement(this.getCurrentStatement());
        this.getStatementStack().push(functionStatement);
        this.setCurrentStatement(functionStatement);
    }
    
    @Override
    public void enterLambda_function_ref(OperonTestsParser.Lambda_function_refContext ctx) {
        log.debug("ENTER LambdaFunctionRef :: Stack size :: " + this.stack.size());
        Statement functionStatement = new FunctionStatement(this.getModuleContext());
        functionStatement.setId("LambdaFunctionStatement");
        log.debug("    >> Set previousStatement: " + this.getCurrentStatement().getId());
        this.getStatementStack().push(this.getCurrentStatement());
        functionStatement.setPreviousStatement(this.getCurrentStatement());
        this.getStatementStack().push(functionStatement);
        this.setCurrentStatement(functionStatement);
    }
    
    @Override
    public void exitLambda_function_ref(OperonTestsParser.Lambda_function_refContext ctx) {
        log.debug("EXIT LambdaFunctionRef :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        log.debug("    >> LAMBDA FUNCTION :: SUBNODES :: " + subNodesSize + ". " + subNodes.get(0).getText());

        String exprAsString = this.getExpressionAsString(subNodes);
        LambdaFunctionRef lfnRef = new LambdaFunctionRef(this.currentStatement);
        lfnRef.setExpr(exprAsString);
        
        // Collect params:
        java.util.Map<String, Node> functionParamValueMap = new HashMap<String, Node>();
        
        int startPos = 2;
        int paramsEndParenthesesIndex = 0;
        
        log.debug("    pop lambdaExpr");
        Node lambdaExpr = this.stack.pop();

        //
        // Find the end-parentheses index,
        // which we need first for finding the function's output constraint
        //
        for (int i = startPos; i < subNodesSize - 3; i ++) {
            // Reach end of params
            if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(")")) {
                log.debug("    Reached end of params.");
                paramsEndParenthesesIndex = i;
                break;
            }
        }
        
        //
        // This constraint is the function's output-constraint:
        //
        if (subNodes.get(paramsEndParenthesesIndex + 1).getText().charAt(0) == '<') {
            // OperonValueConstraint was given.
            if (this.stack.size() > 0 && this.stack.peek() instanceof OperonValueConstraint) {
                OperonValueConstraint lambdaFunctionOutputConstraint = (OperonValueConstraint) this.stack.pop();
                lfnRef.setOperonValueConstraint(lambdaFunctionOutputConstraint);
            }
        }
        
        log.debug("    popped lambdaExpr");
        //String constId = "";
        List<Node> lambdaFunctionParams = new ArrayList<Node>();

        log.debug("  Collect params");
        for (int i = startPos; i < subNodesSize - 3; i ++) {
            log.debug("  subNode :: " + i);
            if (subNodes.get(i) instanceof TerminalNode) {
                log.debug("  subNode [" + i + "] :: " + subNodes.get(i).getText());
            }
            // Reach end of params
            if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(")")) {
                log.debug("    Reached end of params.");
                paramsEndParenthesesIndex = i;
                break;
            }
            
            // Skip param value marker
            else if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(":")) {
                log.debug("    Skip param value marker.");
                continue;
            }
            
            // Skip argument separator
            else if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(",")) {
                log.debug("    Skip argument separator.");
                continue;
            }
            
            else {
                log.debug("    pop paramExpr");
                Node paramExpr = this.stack.pop();
                log.debug("    popped paramExpr");
                // For lambda-ref, all params should be LambdaFunctionRefNamedArguments
                if (paramExpr instanceof LambdaFunctionRefNamedArgument) {
                    log.debug("    >>>> FunctionRefNamedArgument found!");
                }
                lambdaFunctionParams.add(paramExpr);
            }
        }
        
        log.debug("    Add the params");
        
        // Finally, add the params:
        Map<String, OperonValueConstraint> lfrnaConstraintMap = new HashMap<String, OperonValueConstraint>();
        
        for (Node arg : lambdaFunctionParams) {
            LambdaFunctionRefNamedArgument lfrna = (LambdaFunctionRefNamedArgument) arg;
            functionParamValueMap.put(lfrna.getArgumentName(), lfrna.getExprNode());
            lfrnaConstraintMap.put(lfrna.getArgumentName(), lfrna.getOperonValueConstraint());
        }
        
        log.debug(" lambdaFunctionParams size :: " + lambdaFunctionParams.size());
        
        log.debug(" functionParamValueMap size :: " + functionParamValueMap.size());
        
        lfnRef.setParams(functionParamValueMap);
        lfnRef.setParamConstraints(lfrnaConstraintMap);
        lfnRef.setLambdaExpr(lambdaExpr);
        this.stack.push(lfnRef);
        this.getStatementStack().pop(); // Remove functionStatement from the statementStack
        Statement previousStatement = this.getStatementStack().pop();
        log.debug("    >> Set currentStatement with previousStatement: " + previousStatement.getId());
        this.setCurrentStatement(previousStatement);
    }

    @Override
    public void exitLambda_function_ref_named_argument(OperonTestsParser.Lambda_function_ref_named_argumentContext ctx) {
        log.debug("EXIT LambdaFunctionRefNamedArgument :: Stack size :: " + this.stack.size());
        LambdaFunctionRefNamedArgument lfrna = new LambdaFunctionRefNamedArgument(this.getCurrentStatement());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        
        // Extract the arg name and expr-value and set into frna:
        String argName = subNodes.get(0).getText();
        lfrna.setArgumentName(argName);
        log.debug("   argName :: " + argName);
        if (this.stack.peek() instanceof FunctionRefArgumentPlaceholder) {
            log.debug("  Found FunctionArgumentPlaceholder");
            FunctionRefArgumentPlaceholder frap = (FunctionRefArgumentPlaceholder) this.stack.pop(); // new FunctionRefArgumentPlaceholder(this.getCurrentStatement());
            lfrna.setExprNode(frap);
            lfrna.setHasPlaceholder(true);
        }
        
        else {
            log.debug("   popping argValue");
            Node argValue = this.stack.pop();
            lfrna.setExprNode(argValue);
        }
        
        this.stack.push(lfrna);
    }

    @Override
    public void exitFunction_ref_named_argument(OperonTestsParser.Function_ref_named_argumentContext ctx) {
        log.debug("EXIT FunctionRefNamedArgument :: Stack size :: " + this.stack.size());
        FunctionRefNamedArgument frna = new FunctionRefNamedArgument(this.getCurrentStatement());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        
        // Extract the arg name and expr-value and set into frna:
        String argName = subNodes.get(0).getText();
        frna.setArgumentName(argName);
        log.debug("   argName :: " + argName);
        if (this.stack.peek() instanceof FunctionRefArgumentPlaceholder) {
            log.debug("  Found FunctionArgumentPlaceholder");
            FunctionRefArgumentPlaceholder frap = (FunctionRefArgumentPlaceholder) this.stack.pop(); // new FunctionRefArgumentPlaceholder(this.getCurrentStatement());
            frna.setExprNode(frap);
            frna.setHasPlaceholder(true);
        }
        
        else {
            log.debug("   popping argValue");
            Node argValue = this.stack.pop();
            frna.setExprNode(argValue);
        }
        
        this.stack.push(frna);
    }
    
    @Override
    public void exitLambda_function_call(OperonTestsParser.Lambda_function_callContext ctx) {
        log.debug("EXIT LambdaFunction Stmt :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        log.debug("    >> LAMBDA FUNCTION :: SUBNODES :: " + subNodesSize + ". " + subNodes.get(0).getText());

        // Collect params:
        java.util.Map<String, Node> functionParamValueMap = new HashMap<String, Node>();
        
        Node lambdaExpr = this.stack.pop();
        
        while (this.stack.size() > 0 && this.stack.peek() instanceof FunctionNamedArgument) {
            FunctionNamedArgument fna = (FunctionNamedArgument) this.stack.pop();
            functionParamValueMap.put(fna.getArgumentName(), fna.getArgumentValue());
        }

        LambdaFunctionCall lfnCall = new LambdaFunctionCall(this.getCurrentStatement());
        lfnCall.setParams(functionParamValueMap); // TODO: should just set the FunctionNamedArguments
        lfnCall.setFunctionBodyExpr(lambdaExpr);
        this.stack.push(lfnCall);
        this.getStatementStack().pop(); // Remove functionStatement from the statementStack
        Statement previousStatement = this.getStatementStack().pop();
        log.debug("    >> Set currentStatement with previousStatement: " + previousStatement.getId());
        this.setCurrentStatement(previousStatement);
    }
    
    @Override
    public void exitFunction_ref(OperonTestsParser.Function_refContext ctx) {
        //
        // TODO: the below into own structure! NOTE: does not have the '=>', which causes different indexing.
        //
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        log.debug("EXIT Function_ref :: SUBNODES :: " + subNodesSize + ". " + subNodes.get(0).getText() + ", stack size :: " + this.stack.size());
        
        String functionNamespace = "";
        String functionName = "";
        
        // The last element is the function name, all preceding all part of the namespace:
        List<TerminalNode> namespaces = new ArrayList<TerminalNode>();
        
        if (ctx.ID() != null) {
            namespaces = ctx.ID();
            functionName = namespaces.get(namespaces.size() - 1).toString();
            if (namespaces.size() > 1) {
                for (int i = 0; i < namespaces.size() - 1; i++) {
                    functionNamespace += namespaces.get(i) + ":";
                }
                // chop the last ":" from namespace:
                functionNamespace = functionNamespace.substring(0, functionNamespace.length() - 1);
            }
        }
        
        log.debug("    >> FUNCTION :: " + functionName + ". Namespace: " + functionNamespace);

        // Collect params:
        
        // 
        // There might be multiple currying params,
        // e.g. ()("bar")(1, 2, 3)
        // These are concatenated and added as function params.
        // Hmm... So these are all part of function signature or what? foo(?,?)(foo)(bar) becomes foo(?,?,"foo","bar"),
        // which means that the placeholders are _not_ resolved in place, but more args are supplied?
        // Maybe this is not semantically understandable, and have to be removed from the grammar?
        // What would be the use case!?
        // Actually, I support the idea of removal, until there is some clear case where this would be required.
        List<Node> functionParams = new ArrayList<Node>();
        
        int startPos = 2; // functionName '(' params
        if (namespaces.size() > 0) {
            startPos = namespaces.size() * 2; // E.g.: "a:b:c:", or "a:b:" or "a:"
        }

        int paramsEndParenthesesIndex = 0;
        for (int i = startPos; i < subNodesSize - 1; i ++) {
            
            // Reach end of params, but there could be more
            if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(")")) {
                paramsEndParenthesesIndex = i;
                break;
                //continue;
            }
            
            // Skip argument separator
            else if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(",")) {
                continue;
            }
            
            // Skip new start of params-list
            //else if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals("(")) {
            //    paramsEndParenthesesIndex = i;
            //    continue;
            //}
            
            else if (subNodes.get(i) instanceof FunctionRefArgumentPlaceholder) {
                log.debug("  FunctionRefArgumentPlaceholder DETECTED!!!");
                Node functionParam = this.stack.pop();
                functionParams.add(functionParam);
            }
            
            // Add params
            else {
                log.debug("adding param from stack");
                Node functionParam = this.stack.pop();
                functionParams.add(functionParam);
            }
        }
        //
        // TODO: the above into own structure!
        //
        
        String fullyQualifiedName = functionNamespace + ":" + functionName + ":" + functionParams.size();
        
        // Check if one of core-functions:
        if (this.getModuleContext().getFunctionStatements().get(fullyQualifiedName) == null &&
            CoreFunctionResolver.isCoreFunction(functionNamespace, functionName, functionParams)) {
            try {
                Node coreFunction = CoreFunctionResolver.getCoreFunction(functionNamespace, functionName, functionParams, this.getCurrentStatement());
                FunctionRef fnRef = new FunctionRef(this.getCurrentStatement());
                fnRef.setFunctionName(functionName);
                fnRef.setFunctionFQName(fullyQualifiedName);
                fnRef.setCoreFunction(coreFunction);
                this.stack.push(fnRef);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // User-defined function:
        else {
            FunctionRef fnRef = new FunctionRef(this.getCurrentStatement());
            log.debug("  FQ-name :: " + fullyQualifiedName);
            fnRef.setFunctionName(functionName);
            fnRef.setFunctionFQName(fullyQualifiedName);
            Collections.reverse(functionParams);
            log.debug("functionParams.size() = " + functionParams.size());
            fnRef.getParams().addAll(functionParams);
            this.stack.push(fnRef);
        }
    }
    
    @Override
    public void exitFunction_ref_curry(OperonTestsParser.Function_ref_curryContext ctx) {
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        log.debug("EXIT Function_ref_curry :: stack size :: " + this.stack.size() + ", SUBNODES :: " + subNodesSize + ". " + subNodes.get(0).getText());
        
        List<Node> functionParams = new ArrayList<Node>();
        int startPos = 1;
        int paramsEndParenthesesIndex = 0;
        for (int i = startPos; i < subNodesSize - 1; i ++) {
            
            // Reach end of params
            if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(")")) {
                paramsEndParenthesesIndex = i;
                log.debug("Reached possible end, there might be more");
                //break;
                continue;
            }
            
            // Skip argument separator
            else if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(",")) {
                log.debug("Skipping argument separator");
                continue;
            }
            
            // Skip start of params
            else if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals("(")) {
                log.debug("Skipping arguments start");
                continue;
            }
            
            else if (subNodes.get(i) instanceof FunctionRefArgumentPlaceholder) {
                log.debug("  FunctionRefArgumentPlaceholder detected");
                Node functionParam = this.stack.pop();
                functionParams.add(functionParam);
            }
            
            // Add params
            else {
                log.debug("Function_ref_curry :: Adding param (pop stack)");
                Node functionParam = this.stack.pop();
                log.debug("FunctionParam type :: " + functionParam.getClass().getName());
                functionParams.add(functionParam);
            }
        }
        FunctionRefCurry curry = new FunctionRefCurry(this.getCurrentStatement());
        Collections.reverse(functionParams);
        curry.getArguments().addAll(functionParams);
        log.debug("Function curry params size :: " + functionParams.size());
        
        //throw new RuntimeException("FunctionRefCurry not implemented yet!");
        this.stack.push(curry);
    }
    
    @Override
    public void exitFunction_call(OperonTestsParser.Function_callContext ctx) {
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        log.debug("EXIT Function_call :: SUBNODES :: " + subNodesSize + ". " + subNodes.get(0).getText());
        
        String functionNamespace = "";
        String functionName = "";
        
        // The last element is the function name, all preceding all part of the namespace:
        List<TerminalNode> namespaces = new ArrayList<TerminalNode>();
        
        if (ctx.ID() != null) {
            namespaces = ctx.ID();
            functionName = namespaces.get(namespaces.size() - 1).toString();
            if (namespaces.size() > 1) {
                for (int i = 0; i < namespaces.size() - 1; i++) {
                    functionNamespace += namespaces.get(i) + ":";
                }
                // chop the last ":" from namespace:
                functionNamespace = functionNamespace.substring(0, functionNamespace.length() - 1);
            }
        }
        
        log.debug("    >> FUNCTION :: " + functionName + ". Namespace: " + functionNamespace);

        // Collect params:
        
        List<Node> functionParams = new ArrayList<Node>();
        int startPos = 3 + (namespaces.size() - 1) * 2; // Was 5 ('ID' and  ':''). Now namespace might consist of multiple of these parts.
        // FIXME: namespace-logic was refactored so refactor this also!
        if (namespaces.size() == 1) {
            startPos = 3;
        }
        int paramsEndParenthesesIndex = 0;
        log.debug("FunctionCall :: looping arguments :: " + subNodesSize);
        
        for (int i = startPos; i < subNodesSize - 1; i ++) {
            
            // Reach end of params
            if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(")")) {
                log.debug("  >> )");
                paramsEndParenthesesIndex = i;
                break;
            }
            
            // Skip argument separator
            else if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(",")) {
                log.debug("  >> ,");
                continue;
            }
            
            else if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals("(")) {
                log.debug("  >> (");
                break;
            }
            
            // Add params
            else {
                log.debug("  >> Add params (pop stack)");
                Node functionParam = this.stack.pop();
                functionParams.add(functionParam);
            }
        }
        log.debug("  >> Looping done.");
        
        String fqName = functionNamespace + ":" + functionName + ":" + functionParams.size();
        if (fqName.equals(":pos:0") || fqName.equals("core:pos:0")) {
            this.getOperonTestsContext().getConfigs().setSupportPos(true);
        }
        
        else if (fqName.equals(":parent:0") || fqName.equals(":root:0") 
             || fqName.equals("core:parent:0") || fqName.equals("core:root:0")
             || fqName.equals(":valueKey:0") || fqName.equals("core:valueKey:0") || fqName.equals("object:valueKey:0") || fqName.equals("core:object:valueKey:0")
             || fqName.equals(":last:0") || fqName.equals("core:last:0") || fqName.equals("array:last:0") || fqName.equals("core:array:last:0")
             || fqName.equals(":last:1") || fqName.equals("core:last:1") || fqName.equals("array:last:1") || fqName.equals("core:array:last:1")
            ) {
            this.getOperonTestsContext().getConfigs().setSupportParent(true);
        }
        
        else if (fqName.equals(":previous:0") || fqName.equals(":next:0") 
             || fqName.equals("core:previous:0") || fqName.equals("core:next:0")
             || fqName.equals("core:array:get:1") || fqName.equals("array:get:1")
            ) {
            this.getOperonTestsContext().getConfigs().setSupportPos(true);
            this.getOperonTestsContext().getConfigs().setSupportParent(true);
        }
        
        // Check if core-function not overridden and if one of core-functions:
        //  NOTE: core-functions expect the params as List<Node>
        if (this.getOperonTestsContext().getFunctionStatements().get(fqName) == null &&
                CoreFunctionResolver.isCoreFunction(functionNamespace, functionName, functionParams)) {
            try {
                Node function = CoreFunctionResolver.getCoreFunction(functionNamespace, functionName, functionParams, this.getCurrentStatement());
                this.stack.push(function);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // User-defined function:
        else {
            try {
                FunctionCall fnCall = new FunctionCall(this.getCurrentStatement(), fqName);
                Collections.reverse(functionParams);
                fnCall.getArguments().addAll(functionParams);
                this.stack.push(fnCall);
            } catch (Exception e) {
                throw new RuntimeException("ERROR :: Could not create FunctionCall. " + e.getMessage());
            }
        }
    }

    @Override
    public void exitJson_type_function_shortcut(OperonTestsParser.Json_type_function_shortcutContext ctx) {
        log.debug("Exit Json_type_function_shortcut. Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = this.getContextChildNodes(ctx);
        log.debug("    SubNodes size :: " + subNodes.size());
        log.debug("    SubNodes :: " + subNodes);

        String jsonType = subNodes.get(0).toString();
        //System.out.println("Type :: ["+ jsonType + "], stack size :: " + this.stack.size());
        
        log.debug(jsonType);
    
        if (jsonType.equals("Boolean") == false
            && jsonType.equals("Lambda") == false
            && jsonType.equals("Function") == false) {
            try {
                //System.out.println("Rewrite Type() == "+ jsonType);
                //
                // Rewrite to: Type() = jsonType
                //
                JsonType jsonTypeFunction = new JsonType(this.getCurrentStatement());
                StringType jsonTypeNameStr = new StringType(this.currentStatement);
                jsonTypeNameStr.setFromJavaString(jsonType);
                
                BinaryNodeProcessor op = new Eq();
                BinaryNode bnode = new BinaryNode(this.currentStatement);
                bnode.setLhs(jsonTypeFunction);
                bnode.setRhs(jsonTypeNameStr);
                bnode.setBinaryNodeProcessor(op);
                this.stack.push(bnode);
            } catch (Exception e) {
                throw new RuntimeException("Error: TestsCompiler: could not create type-function.");
            }
        }
        
        else if (jsonType.equals("Boolean")) {
            try {
                //
                // Rewrite Boolean-type to: Type() = "True" Or Type() = "False", as it satisfies both:
                //
                
                // Or lhs:
                JsonType orLhsJsonTypeFunction = new JsonType(this.getCurrentStatement());
                StringType orLhsJsonTypeNameStr = new StringType(this.currentStatement);
                orLhsJsonTypeNameStr.setFromJavaString("True");
                
                BinaryNodeProcessor orLhsOp = new Eq();
                BinaryNode orLhsBnode = new BinaryNode(this.currentStatement);
                orLhsBnode.setLhs(orLhsJsonTypeFunction);
                orLhsBnode.setRhs(orLhsJsonTypeNameStr);
                orLhsBnode.setBinaryNodeProcessor(orLhsOp);
                
                // Or rhs:
                JsonType orRhsJsonTypeFunction = new JsonType(this.getCurrentStatement());
                StringType orRhsJsonTypeNameStr = new StringType(this.currentStatement);
                orRhsJsonTypeNameStr.setFromJavaString("False");
                
                BinaryNodeProcessor orRhsOp = new Eq();
                BinaryNode orRhsBnode = new BinaryNode(this.currentStatement);
                orRhsBnode.setLhs(orRhsJsonTypeFunction);
                orRhsBnode.setRhs(orRhsJsonTypeNameStr);
                orRhsBnode.setBinaryNodeProcessor(orRhsOp);
                
                // Or:
                BinaryNodeProcessor or = new Or();
                BinaryNode bnode = new BinaryNode(this.currentStatement);
                bnode.setLhs(orLhsBnode);
                bnode.setRhs(orRhsBnode);
                bnode.setBinaryNodeProcessor(or);
                
                this.stack.push(bnode);
            } catch (Exception e) {
                throw new RuntimeException("Error: TestsCompiler: could not create type-function for Boolean.");
            }
        }
        
        else if (jsonType.equals("Lambda")) {
            try {
                //
                // Rewrite to: Type() => startsWith("lambda:")
                //
                JsonType jsonTypeFunction = new JsonType(this.getCurrentStatement());
                
                StringType lambdaTypeNameStr = new StringType(this.currentStatement);
                lambdaTypeNameStr.setFromJavaString("lambda:");
                
                List<Node> functionParams = new ArrayList<Node>();
                functionParams.add(lambdaTypeNameStr);
                
                StringStartsWith strStartsWithFunction = new StringStartsWith(
                    this.getCurrentStatement(), functionParams);
                
                MultiNode mn = new MultiNode(this.getCurrentStatement());
                mn.addNode(strStartsWithFunction);
                mn.addNode(jsonTypeFunction);
                
                this.stack.push(mn);
            } catch (Exception e) {
                throw new RuntimeException("Error: TestsCompiler: could not create type-function for Lambda.");
            }
        }
        
        else if (jsonType.equals("Function")) {
            try {
                //
                // Rewrite to: Type() => startsWith("function:")
                //
                JsonType jsonTypeFunction = new JsonType(this.getCurrentStatement());
                
                StringType lambdaTypeNameStr = new StringType(this.currentStatement);
                lambdaTypeNameStr.setFromJavaString("function:");
                
                List<Node> functionParams = new ArrayList<Node>();
                functionParams.add(lambdaTypeNameStr);
                
                StringStartsWith strStartsWithFunction = new StringStartsWith(
                    this.getCurrentStatement(), functionParams);
                
                MultiNode mn = new MultiNode(this.getCurrentStatement());
                mn.addNode(strStartsWithFunction);
                mn.addNode(jsonTypeFunction);
                
                this.stack.push(mn);
            } catch (Exception e) {
                throw new RuntimeException("Error: TestsCompiler: could not create type-function for Function.");
            }
        }
        
        else {
            throw new RuntimeException("Error: TestsCompiler: could not create type-function for unknown type.");
        }
    }
    
    @Override
    public void exitThrow_exception(OperonTestsParser.Throw_exceptionContext ctx) {
        log.debug("EXIT Throw_exception :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        Node exceptionValue = this.stack.pop();
        ThrowException throwExceptionNode = new ThrowException(this.getCurrentStatement());
        throwExceptionNode.setExceptionValue(exceptionValue);
        this.stack.push(throwExceptionNode);
    }

    @Override
    public void exitTry_catch(OperonTestsParser.Try_catchContext ctx) {
        log.debug("EXIT Try_catch :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        TryCatch tc = new TryCatch(this.getCurrentStatement());
        Node catchExpr = this.stack.pop();
        Node tryExpr = this.stack.pop();
        
        if (this.stack.size() > 0 && subNodes.get(1).getText().charAt(0) == '{') {
            Node peeked = this.stack.peek();
            if (peeked != null && peeked instanceof ObjectType) {
                Node tryConfiguration = this.stack.pop();
                tc.setConfigs(tryConfiguration);
            }
        }
        
        tc.setTryExpr(tryExpr);
        tc.setCatchExpr(catchExpr);
        this.stack.push(tc);
    }

    @Override
    public void enterException_stmt(OperonTestsParser.Exception_stmtContext ctx) {
        log.debug("ENTER Exception-stmt :: Stack size :: " + this.stack.size());
        Statement exceptionStatement = new DefaultStatement(this.getModuleContext());
        exceptionStatement.setId("ExceptionStatement");
        this.setCurrentStatement(exceptionStatement);
        //this.setPreviousStatementForStatement(exceptionStatement);
        
    }
    
    // Exception -stmt is not supported in the Module
    
    @Override
    public void exitAggregate_expr(OperonTestsParser.Aggregate_exprContext ctx) {
        log.debug("EXIT aggregate_expr :: Stack size :: " + this.stack.size());
        
        Aggregate aggregate = new Aggregate(this.getCurrentStatement(), Integer.toString(aggregateIndex));
        aggregateIndex = aggregateIndex + 1;
        ObjectType configs = (ObjectType) this.stack.pop();
        aggregate.setConfigs(configs);
        aggregate.setCorrelationId("\"default\"");
        
        Node firePredicate = null;
        Node aggregateFunction = null;
        boolean hasTimeout = false;
        
        try {
            for (PairType pair : configs.getPairs()) {
                
                String key = pair.getKey();
                
                if (key.equals("\"correlationId\"")) {
                    Node correlationIdExpr = pair.getValue();
                    aggregate.setCorrelationIdExpr(correlationIdExpr);
                }
                
                else if (key.equals("\"firePredicate\"")) {
                    firePredicate = pair.getValue();
                    aggregate.setFirePredicate(firePredicate);
                }
                
                else if (key.equals("\"aggregateFunction\"")) {
                    aggregateFunction = pair.getValue();
                    aggregate.setAggregateFunction(aggregateFunction);
                    System.out.println("SET aggregateFunction");
                }
                
                else if (key.equals("\"timeoutMillis\"")) {
                    hasTimeout = true;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        aggregate.setHasTimeout(hasTimeout);
        this.stack.push(aggregate);
    }

    //
    // Helper functions
    //
    
    public String getExpressionAsString(List<ParseTree> subNodes) {
        StringBuilder exprSb = new StringBuilder();
        for (ParseTree pt : subNodes) {
            exprSb.append(pt.getText() + " ");
        }
        String exprAsString = exprSb.toString().trim();
        return exprAsString;
    }
    
    //
    // Execute on entering statement.
    //     Sets the currentStatement as previous statement for @statement, and pushes @statement to stack.
    //
    private void setPreviousStatementForStatement(Statement statement) {
        log.debug("    >> Set previousStatement: " + this.getCurrentStatement().getId());
        this.getStatementStack().push(this.getCurrentStatement());
        statement.setPreviousStatement(this.getCurrentStatement());
        this.getStatementStack().push(statement);
        this.setCurrentStatement(statement);
    }
    
    //
    // Executed on exiting function
    //
    private void restorePreviousScope() {
        this.getStatementStack().pop(); // Remove functionStatement from the statementStack
        Statement previousStatement = this.getStatementStack().pop();
        log.debug("    >> Set currentStatement with previousStatement: " + previousStatement.getId());
        this.setCurrentStatement(previousStatement);
    }
    
    public List<ParseTree> getContextChildNodes(ParserRuleContext context) {
        List<ParseTree> nodes = new ArrayList<ParseTree>();
        for (int i = 0; i < context.getChildCount(); i++) {
            nodes.add(context.getChild(i));
        }
        return nodes;
    }

    public List<ParseTree> getContextChildRuleNodes(ParserRuleContext context) {
        List<ParseTree> nodes = new ArrayList<ParseTree>();
        for (int i = 0; i < context.getChildCount(); i++) {
            if (context.getChild(i) instanceof RuleNode) {
                nodes.add(context.getChild(i));
            }
        }
        return nodes;
    }
    
    public List<ParseTree> getParseTreeChildRuleNodes(ParseTree pt) {
        List<ParseTree> nodes = new ArrayList<ParseTree>();
        for (int i = 0; i < pt.getChildCount(); i++) {
            if (pt.getChild(i) instanceof RuleNode) {
                nodes.add(pt.getChild(i));
            }
        }
        return nodes;
    }

}
