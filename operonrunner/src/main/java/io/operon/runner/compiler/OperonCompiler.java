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

import io.operon.parser.*;
import io.operon.runner.processor.function.core.*;
import io.operon.runner.processor.function.core.array.*;
import io.operon.runner.processor.function.core.object.*;
import io.operon.runner.processor.function.core.string.*;
import io.operon.runner.processor.function.core.module.*;
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
import io.operon.runner.statement.SelectStatement;
import io.operon.runner.statement.ExceptionStatement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.statement.FunctionStatement;
import io.operon.runner.node.*;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.RandomUtil;
import io.operon.runner.model.InputSource;
import io.operon.runner.model.test.AssertComponent;
import io.operon.runner.model.test.MockComponent;
import io.operon.runner.node.FunctionStatementParam;
import io.operon.runner.model.ObjAccessArgument;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.path.PathPart;
import io.operon.runner.model.pathmatch.*;
import io.operon.runner.model.UpdatePair;
import io.operon.runner.system.InputSourceDriver;
import io.operon.runner.system.ComponentSystemUtil;
import io.operon.runner.ExceptionHandler;
import io.operon.runner.*;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Stack;
import java.util.Collections;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * This class listens the events that parser emits,
 * and reacts by wiring the required classes into 
 * the OperonContext.
 *
 * TODO: separate logic so that OperonModuleParser could use same methods
 * 
 * Compiler patterns:
 *   - prevent double-evaluation: see: exitUpdate_array_expr and UpdateArray (Node)
 * 
 * 
 */
public class OperonCompiler extends OperonBaseListener {
     // no logger 
    private OperonContext operonContext;
    private OperonTestsContext operonTestsContext;
    private Stack<Statement> statementStack;
    private Statement currentStatement;
    private Stack<Node> stack;
    private InputSource fromInputSource;
    private int aggregateIndex = 0; // used in exitAggregate_expr
    private int objIndex = 1; // used in exitJson_obj
    
    private long startTime = 0L;
    
    public OperonCompiler() throws Exception, IOException {
        super();
        this.stack = new Stack<Node>();
        this.statementStack = new Stack<Statement>();
        this.operonTestsContext = null;
    }
    
    public void setOperonContext(OperonContext operonContext) {
        if (operonContext.getFromStatement() == null) {
            FromStatement fromStatement = new FromStatement(operonContext);
            fromStatement.setId("FromStatement");
            this.setCurrentStatement(fromStatement);
            
            // If From-statement is not set, then we initialize the ISD
            // with JSON-isd and with an empty-value.
            InputSource inputSource = new InputSource();
            inputSource.setName("json");
            inputSource.setSystemId(":"); // json-system does not have this assigned (json:id:value but is json:value)
            OperonValue isdConfigsOrInitialValue = new EmptyType(this.getCurrentStatement());
            inputSource.setInitialValue(isdConfigsOrInitialValue);
            
            fromStatement.setInputSource(inputSource);
            operonContext.setFromStatement(fromStatement);
        }
        this.operonContext = operonContext;
    }

    public OperonContext getOperonContext() {
        return this.operonContext;
    }

    public void setOperonTestsContext(OperonTestsContext opTestsContext) {
        if (this.getOperonContext() != null) {
            this.getOperonContext().setOperonTestsContext(opTestsContext);
        }
        this.operonTestsContext = opTestsContext;
    }

    public Stack getStack() {
        return this.stack;
    }

    public OperonTestsContext getOperonTestsContext() {
        return this.operonTestsContext;
    }

    public void setCurrentStatement(Statement stmnt) {
        this.currentStatement = stmnt;
    }
    
    public Statement getCurrentStatement() {
        return this.currentStatement;
    }
    
    public Stack<Statement> getStatementStack() {
        return this.statementStack;
    }
    
    public void setFromInputSource(InputSource is) {
        this.fromInputSource = is;
    }
    
    public InputSource getFromInputSource() {
        return this.fromInputSource;
    }
    
    // 
    // This sets the strict-mode, where having syntax-error causes the
    // parser to stop parsing and we report an error.
    // 
    @Override
    public void visitErrorNode(ErrorNode node) { 
        System.err.println("Operon: syntax error :: " + node.toStringTree());
        throw new RuntimeException("Operon: syntax error :: " + node.toStringTree());
    }
    
    @Override
    public void enterOperon(OperonParser.OperonContext ctx) {
        //:OFF:log.debug("Starting to compile. ");
        startTime = System.nanoTime();
    }

    @Override
    public void exitOperon(OperonParser.OperonContext ctx) {
        //:OFF:log.debug("====== COMPILING DONE ======. Stack size: " + this.stack.size());
    }

    //
    // Example: Import {mylib:"file://op/lib.opm"}
    //
	@Override
	public void exitImport_stmt(OperonParser.Import_stmtContext ctx) {
	    //:OFF:log.debug("EXIT Import. Stack size: " + this.stack.size());
        OperonValue imports = (OperonValue) this.stack.pop();
        ObjectType importsObj = (ObjectType) imports;
        for (int i = 0; i < importsObj.getPairs().size(); i ++) {
            String importToNamespace = importsObj.getPairs().get(i).getKey();
            importToNamespace = importToNamespace.substring(1, importToNamespace.length() - 1); // remove double-quotes
            if (importToNamespace.equals("core")) {
                throw new RuntimeException("Compiler :: cannot import to protected namespace: core");
            }
            
            String importSourceUri = null;
            try {
                importSourceUri = ((StringType) importsObj.getPairs().get(i).getValue().evaluate()).getJavaStringValue();
            } catch (OperonGenericException oge) {
                throw new RuntimeException("Compiler :: cannot read import-statement, invalid value");
            }
            
            //:OFF:log.debug("Import source uri :: " + importSourceUri);
            String moduleAsString = null;
            String moduleFilePath = null;
            
            try {
                if (importSourceUri.startsWith("file://")) {
                    moduleFilePath = importSourceUri.substring(7, importSourceUri.length());
                    //:OFF:log.debug("LOAD MODULE FROM :: " + moduleFilePath);
                    moduleAsString =  new String(Files.readAllBytes(Paths.get(moduleFilePath)));
                    if (moduleAsString.isEmpty()) {
                        throw new RuntimeException("Compiler :: empty module: " + moduleFilePath);
                    }
                }
                else {
                    // E.g. "http://"
                    throw new RuntimeException("Compiler :: unsupported import-protocol");
                }
                
            } catch (IOException ioe) {
                System.err.println("Compiler :: could not read module: " + importSourceUri + ". Error: " + ioe.getMessage());
                throw new RuntimeException("Compiler :: could not read module: " + importSourceUri + ". Error: " + ioe.getMessage());
            } catch (Exception e) {
                System.err.println("Compiler :: could not read module: " + importSourceUri + ". Error: " + e.getMessage());
                throw new RuntimeException("Compiler :: could not read module: " + importSourceUri + ". Error: " + e.getMessage());
            }
            //:OFF:log.debug("=== Load module ===");
            
            try {
                ModuleAdd.addModule(this.getOperonContext(), this.getOperonTestsContext(), moduleAsString, moduleFilePath, importToNamespace, false);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }
	}

    @Override
    public void enterFrom(OperonParser.FromContext ctx) {
        //:OFF:log.debug("ENTER From. Stack size: " + this.stack.size());
        // From-statement is also created in the constructor already
        // so that we can set the default empty-value,
        // in case the query does not have a from-statement.
        Statement from = new FromStatement(this.getOperonContext());
        from.setId("FromStatement");
        this.setCurrentStatement(from);
    }

    @Override
    public void exitFrom(OperonParser.FromContext ctx) {
        //:OFF:log.debug("EXIT From. Stack size: " + this.stack.size());
    }

    // : 'json' json_value_constraint? ':' json
    // | 'sequence' json_value_constraint? ':' json_array
    // | ID (':' ID)? json_value_constraint? ':' json_obj?
    @Override
    public void exitInput_source(OperonParser.Input_sourceContext ctx) {
        //:OFF:log.debug("EXIT INPUT SOURCE.");
        List<ParseTree> subNodes = this.getContextChildNodes(ctx);
        //:OFF:log.debug("    >> Stack size :: " + this.stack.size());
        //:OFF:log.debug("    >> CHILD NODES :: " + subNodes.size() + " :: CHILD NODE [0] TYPE :: " + subNodes.get(0).getText());
        
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
            if (subNodes.size() == 6 && subNodes.get(5).getClass().getName().equals("io.operon.parser.OperonParser$Json_objContext")) {
                isdConfigsOrInitialValue = (ObjectType) this.stack.pop();
            }
            else if (subNodes.size() == 5 && subNodes.get(4).getClass().getName().equals("io.operon.parser.OperonParser$Json_objContext")) {
                isdConfigsOrInitialValue = (ObjectType) this.stack.pop();
            }
            else if (subNodes.size() == 4 && subNodes.get(3).getClass().getName().equals("io.operon.parser.OperonParser$Json_objContext")) {
                isdConfigsOrInitialValue = (ObjectType) this.stack.pop();
            }
            else if (subNodes.size() == 3 && subNodes.get(2).getClass().getName().equals("io.operon.parser.OperonParser$Json_objContext")) {
                isdConfigsOrInitialValue = (ObjectType) this.stack.pop();
            }
            else if (subNodes.size() == 2 && subNodes.get(1).getClass().getName().equals("io.operon.parser.OperonParser$Json_objContext")) {
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
        //:OFF:log.debug("Input source: " + system + ". ");
        this.setFromInputSource(inputSource);

        FromStatement fromStatement = null;

        if (this.getOperonTestsContext() != null && this.getOperonTestsContext().getFromStatement() != null) {
            // Use the From from the tests.
            fromStatement = this.getOperonTestsContext().getFromStatement();
        }
        
        else {
            fromStatement = (FromStatement) this.getCurrentStatement();
            fromStatement.setInputSource(this.getFromInputSource());
        }
        
        //
        // Add OperonValueConstraint, if such exists.
        //
        if (this.stack.size() == 1 && this.stack.peek() instanceof OperonValueConstraint) {
            //:OFF:log.debug("  Set OperonValueConstraint for FromStatement");
            OperonValueConstraint ovc = (OperonValueConstraint) this.stack.pop();
            ovc.setSourceCodeLineNumber(ctx.start.getLine());
            
            int constraintIndex = subNodes.size() - 3; // for json / sequence: From json <Number>: 123 Select $
            
            if (system.equals("json") == false && system.equals("sequence") == false) {
                //System.out.println("Recalculate ConstraintIndex"); // $ : operon <Object> : {configs}
                //constraintIndex = subNodes.size() - 1;
                //if (subNodes.get(subNodes.size() - 2).getClass().getName().equals("io.operon.parser.OperonParser$Json_value_constraintContext")) {
                //    System.out.println("FOUND constraintNode!!!");
                //    constraintIndex = subNodes.size() - 2;
                //}
                for (int i = 0 ; i < subNodes.size(); i ++ ) {
                    //System.out.println("subNode: " + i + ", " + subNodes.get(i).getText());
                    
                    if (subNodes.get(i).getClass().getName().equals("io.operon.parser.OperonParser$Json_value_constraintContext")) {
                        //System.out.println("FOUND constraintNode!!! ---> i = " + i);
                        constraintIndex = i;
                        break;
                    }
                }
            }
            
            String constraintAsString = subNodes.get(constraintIndex).getText();
            //System.out.println("  Constraint=" + constraintAsString);
            //System.out.println("  ConstraintIndex=" + constraintIndex);
            ovc.setConstraintAsString(constraintAsString);
            fromStatement.setOperonValueConstraint(ovc);
        }
        
        this.getOperonContext().setFromStatement(fromStatement);
    
        //:OFF:log.debug("    >> EXITED INPUT SOURCE.");
    }
  
    //  
    //root_input_source
    //    : ROOT_VALUE json_value_constraint? ':' json
    //    | ROOT_VALUE ':' input_source
    //    ;
    //
    @Override
    public void exitRoot_input_source(OperonParser.Root_input_sourceContext ctx) {
        //:OFF:log.debug("EXIT ROOT INPUT SOURCE.");
        List<ParseTree> subNodes = this.getContextChildNodes(ctx);
        
        //:OFF:log.debug("    >> Stack size :: " + this.stack.size());
        //:OFF:log.debug("    >> CHILD NODES :: " + subNodes.size() + " :: CHILD NODE [0] TYPE :: " + subNodes.get(0).getText());
        
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
        //:OFF:log.debug("Input source: " + system + ". ");
        
        this.setFromInputSource(inputSource);
        
        if (this.getOperonTestsContext() != null && this.getOperonTestsContext().getFromStatement() != null) {
            // Use the From from the tests.
            //System.out.println("Setting FromStatement from Tests");
            FromStatement fromStatementTest = this.getOperonTestsContext().getFromStatement();
            this.getOperonContext().setFromStatement(fromStatementTest);
        }
        else {
            //
            // Add OperonValueConstraint, if such exists.
            //
            if (this.stack.size() == 1) {
                //:OFF:log.debug("  Set OperonValueConstraint for FromStatement");
                OperonValueConstraint ovc = (OperonValueConstraint) this.stack.pop();
                ovc.setSourceCodeLineNumber(ctx.start.getLine());
                String constraintAsString = subNodes.get(subNodes.size() - 3).getText();
                ovc.setConstraintAsString(constraintAsString);
                ((FromStatement) this.currentStatement).setOperonValueConstraint(ovc);
            }
            
            FromStatement fromStatement = (FromStatement) this.getCurrentStatement();
            fromStatement.setInputSource(this.getFromInputSource());
            this.getOperonContext().setFromStatement(fromStatement);
        }
        
        //:OFF:log.debug("    >> EXITED ROOT INPUT SOURCE.");
    }
    
    @Override
    public void enterSelect(OperonParser.SelectContext ctx) {
        //:OFF:log.debug("ENTER Select");
        //System.out.println("ENTER Select :: Stack size :: " + this.stack.size());
        Statement select = new SelectStatement(this.getOperonContext());
        select.setId("SelectStatement");
        this.setPreviousStatementForStatement(select);
    }

    @Override
    public void exitSelect(OperonParser.SelectContext ctx) {
        //:OFF:log.debug("EXIT Select :: Stack size :: " + this.stack.size());
        //System.out.println("EXIT Select :: Stack size :: " + this.stack.size());
        
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        //:OFF:log.debug("  SubNodes :: " + subNodesSize);
        
        SelectStatement selectStatement = (SelectStatement) this.currentStatement;
        
        //for (int i = 0; i < subNodes.size(); i ++) {
        //    System.out.println("--> subNode " + i + ": " + subNodes.get(i).getText());
        //}
        
        Node selectExpr = this.stack.pop();
        
        //:OFF:log.debug("    >> Select expr :: " + selectExpr.getClass().getName());

        //         
        // Add OperonValueConstraint, if such exists
        // 
        if (this.stack.size() > 0 && this.stack.peek() instanceof OperonValueConstraint) {
            OperonValueConstraint ovc = (OperonValueConstraint) this.stack.pop();
            ovc.setSourceCodeLineNumber(ctx.start.getLine());
            //:OFF:log.debug("    >> Select constraint :: " + ovc.getClass().getName());
            String constraintAsString = subNodes.get(subNodes.size() - 3).getText();
            ovc.setConstraintAsString(constraintAsString);
            selectStatement.setOperonValueConstraint(ovc);
        }
        
        //
        // Configuration object is optional. Controls:
        //  "prettyPrint": Boolean
        //  "serializer": "operon" or other possible serializations (e.g. JSON / XML / CSV)
        //
        if (this.stack.size() > 0 && (
                    subNodes.get(0).getText().charAt(0) == '{' || // Select {"prettyPrint": trye}: --> i.e. with "Select" -word
                    subNodes.get(1).getText().charAt(0) == '{'    // : {"prettyPrint": true}:
                )
            ) {
            Node peeked = this.stack.peek();
            if (peeked != null && peeked instanceof ObjectType) {
                Node selectConfiguration = this.stack.pop();
                selectStatement.setConfigs(selectConfiguration);
            }
        }
        
        selectStatement.setNode(selectExpr);
        //:OFF:log.debug("set currentStatement node.");
        this.getOperonContext().setSelectStatement(selectStatement);
        //:OFF:log.debug("Restoring scope");
        this.restorePreviousScope();
    }

    @Override
    public void exitFunction_stmt_param(OperonParser.Function_stmt_paramContext ctx) {
        //:OFF:log.debug("EXIT Function-stmt-param :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);

        FunctionStatementParam fsParam = new FunctionStatementParam(this.getCurrentStatement());
        
        if (this.stack.size() > 0 && this.stack.peek() instanceof OperonValueConstraint) {
            OperonValueConstraint ovc = (OperonValueConstraint) this.stack.pop();
            ovc.setSourceCodeLineNumber(ctx.start.getLine());
            String constraintAsString = subNodes.get(subNodes.size() - 1).getText();
            ovc.setConstraintAsString(constraintAsString);
            fsParam.setOperonValueConstraint(ovc);
        }
        String paramName = subNodes.get(0).getText();
        fsParam.setParam(paramName);
        this.stack.push(fsParam);
    }

    @Override
    public void enterFunction_stmt(OperonParser.Function_stmtContext ctx) {
        //:OFF:log.debug("ENTER Function-stmt :: Stack size :: " + this.stack.size());
        Statement functionStatement = new FunctionStatement(this.getOperonContext());
        //this.setCurrentStatement(functionStatement); // This causes infinite loop somewhere
        this.setPreviousStatementForStatement(functionStatement);
    }

    @Override
    public void exitFunction_stmt(OperonParser.Function_stmtContext ctx) {
        //:OFF:log.debug("EXIT Function-stmt :: Stack size :: " + this.stack.size());
        this.currentStatement.setNode(this.stack.pop()); // Function Expression
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        //:OFF:log.debug("  SubNodes :: " + subNodesSize);

        FunctionStatement functionStatement = (FunctionStatement) this.getCurrentStatement();

        //
        // Add the OperonValueConstraint if such exists:
        //
        OperonValueConstraint functionOutputovc = null;
        if (this.stack.size() > 0 && this.stack.peek() instanceof OperonValueConstraint) {
            functionOutputovc = (OperonValueConstraint) this.stack.pop();
        }

        String functionNamespace = "";
        String functionName = "";
        int argumentsStartIndex = 3;
        
        // Function namespace used:
        if (subNodes.get(2).getText().equals(":")) {
            functionNamespace = subNodes.get(1).getText();
            if (functionNamespace.equals("core")) {
                throw new RuntimeException("Illegal function-declaration. Namespace \"core\" is reserved.");
            }
            functionName = subNodes.get(3).getText();
            argumentsStartIndex = 4;
            //:OFF:log.debug("FUNCTION :: namespace :: " + functionNamespace);
            //:OFF:log.debug("FUNCTION :: name :: " + functionName);
        }
        
        //
        // No function namespace
        //
        else {
            functionName = subNodes.get(1).getText();
        }
        
        List<FunctionStatementParam> functionStatementParams = new ArrayList<FunctionStatementParam>();
        int argumentsEndParenthesesIndex = 0;
        
        //
        // Find the end-index (used for next for-loop):
        //
        for (int i = argumentsStartIndex; i < subNodesSize - 1; i ++) {
            // Reach end of arguments
            if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(")")) {
                argumentsEndParenthesesIndex = i;
                break;
            }
            else {
                continue;
            }
        }

        //
        // TODO: the text-form should be collected in the ExitOperonValueConstraint !!!
        //
        if (functionOutputovc != null) {
            String constraintAsString = subNodes.get(argumentsEndParenthesesIndex + 1).getText();
            functionOutputovc.setConstraintAsString(constraintAsString);
            functionStatement.setOperonValueConstraint(functionOutputovc);
        }

        while (this.stack.size() > 0 && this.stack.peek() instanceof FunctionStatementParam) {
            FunctionStatementParam functionStatementParam = (FunctionStatementParam) this.stack.pop();
            functionStatementParams.add(functionStatementParam);
        }

        Collections.reverse(functionStatementParams);
        functionStatement.getParams().addAll(functionStatementParams);

        //:OFF:log.debug("  " + functionName + " :: params set :: " + functionStatement.getParams().size());

        // Add functionStatement into OperonContext
        String fqName = functionNamespace + ":" + functionName + ":" + functionStatementParams.size();
        this.getOperonContext().getFunctionStatements().put(fqName, functionStatement);
        this.restorePreviousScope();
    }
    
    @Override
    public void enterLet_stmt(OperonParser.Let_stmtContext ctx) {
        Statement letStatement = new LetStatement(this.getOperonContext());
        letStatement.setId("LetStatement");
        this.setPreviousStatementForStatement(letStatement);
    }
    
    @Override
    public void exitLet_stmt(OperonParser.Let_stmtContext ctx) {
        //:OFF:log.debug("EXIT Let :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = this.getContextChildNodes(ctx);
        
        //for (int i = 0; i < subNodes.size(); i ++) {
        //    System.out.println("--> subNode " + i + ": " + subNodes.get(i).getText());
        //}
        
        Node letExpr = this.stack.pop();
        LetStatement letStatement = (LetStatement) this.getCurrentStatement();
        
        letStatement.setNode(letExpr);
        String constId = ctx.CONST_ID().getSymbol().getText();
        String namespace = null;
        if (ctx.ID() != null) {
            namespace = ctx.ID().getSymbol().getText();
            if (namespace.toLowerCase().equals("core")) {
                throw new RuntimeException("Cannot assign named value \"" + constId + "\" into \"core\" -namespace.");
            }
        }
        //:OFF:log.debug("  >> constId :: " + constId);
        //
        // Add the OperonValueConstraint if such exists:
        //
        //:OFF:log.debug("  >> subNodes.size() :: " + subNodes.size());
        
        // Set OperonValueConstraint
        // Cannot just peek stack, because contraint could belong to other stmt
        if (subNodes.get(subNodes.size() - 4).getText().charAt(0) == '<') {
            if (this.stack.size() > 0 && this.stack.peek() instanceof OperonValueConstraint) {
                OperonValueConstraint ovc = (OperonValueConstraint) this.stack.pop();
                ovc.setSourceCodeLineNumber(ctx.start.getLine());
                String constraintAsString = subNodes.get(subNodes.size() - 4).getText();
                ovc.setConstraintAsString(constraintAsString);
                letStatement.setOperonValueConstraint(ovc);
            }
        }
        
        //
        // Configuration object is optional. Controls:
        //  "update": LetStatement.ResetType
        //  "bind": {"operator": "Plus", "functionRef": foo($rhs), "cascade": Boolean}
        //
        // Must check two different positions because there can also be constraint:
        if (this.stack.size() > 0 &&
                (subNodes.get(1).getText().charAt(0) == '{' // case when 'Let' is not used (shortform)
                 || subNodes.get(2).getText().charAt(0) == '{'
                 || subNodes.get(subNodes.size() - 4).getText().charAt(0) == '{') ) {
            Node peeked = this.stack.peek();
            if (peeked != null && peeked instanceof ObjectType) {
                Node letConfiguration = this.stack.pop();
                letStatement.setConfigs(letConfiguration);
            }
        }
        
        String valueKey = "";
        if (namespace != null) {
            valueKey = namespace + ":" + constId;
        }
        else {
            valueKey = constId;
        }

        // Used for "bind" -feature only. Otherwise the valueRef's are bound to statements.
        letStatement.setValueRefStr(valueKey);

        if (letStatement.getPreviousStatement() instanceof FromStatement) {
            if (letStatement.getResetType() == null) {
                letStatement.setResetType(LetStatement.ResetType.ALWAYS);
            }
            Object mapReturn = this.getOperonContext().getLetStatements().put(valueKey, letStatement);
            if (mapReturn != null) {
                throw new RuntimeException("Compiler :: value " + valueKey + " has already been defined in the OperonContext.");
            }
            this.getOperonContext().getAllLetStatements().add(letStatement);
        }

        else if (letStatement.getPreviousStatement() instanceof FunctionStatement) {
            if (letStatement.getResetType() == null) {
                letStatement.setResetType(LetStatement.ResetType.ALWAYS);
            }
            Object mapReturn = ((FunctionStatement) letStatement.getPreviousStatement()).getLetStatements().put(valueKey, letStatement);
            if (mapReturn != null) {
                throw new RuntimeException("Compiler :: value " + valueKey + " has already been defined in the Function.");
            }
            this.getOperonContext().getAllLetStatements().add(letStatement);
        }

        else if (letStatement.getPreviousStatement() instanceof SelectStatement) {
            if (letStatement.getResetType() == null) {
                letStatement.setResetType(LetStatement.ResetType.ALWAYS);
            }
            Object mapReturn = ((SelectStatement) letStatement.getPreviousStatement()).getLetStatements().put(valueKey, letStatement);
            if (mapReturn != null) {
                throw new RuntimeException("Compiler :: value " + valueKey + " has already been defined in the Function.");
            }
            this.getOperonContext().getAllLetStatements().add(letStatement);
        }

        else if (letStatement.getPreviousStatement() instanceof ExceptionStatement) {
            if (letStatement.getResetType() == null) {
                letStatement.setResetType(LetStatement.ResetType.ALWAYS);
            }
            Object mapReturn = ((ExceptionStatement) letStatement.getPreviousStatement()).getLetStatements().put(valueKey, letStatement);
            if (mapReturn != null) {
                throw new RuntimeException("Compiler :: value " + valueKey + " has already been defined in the Function.");
            }
            this.getOperonContext().getAllLetStatements().add(letStatement);
        }

        else if (letStatement.getPreviousStatement() instanceof DefaultStatement) {
            if (letStatement.getResetType() == null) {
                letStatement.setResetType(LetStatement.ResetType.ALWAYS);
            }
            Object mapReturn = ((DefaultStatement) letStatement.getPreviousStatement()).getLetStatements().put(valueKey, letStatement);
            if (mapReturn != null) {
                throw new RuntimeException("Compiler :: value " + valueKey + " has already been defined in the Function.");
            }
            this.getOperonContext().getAllLetStatements().add(letStatement);
        }

        else {
            System.err.println("PREV: " + letStatement.getPreviousStatement().getClass().getName());
            throw new RuntimeException("Compiler :: Let, unknown statement");
        }        

        this.restorePreviousScope();
    }

    // From json:[10, 20, 30] Select $ [2] | [3] #> [3]
    @Override
    public void exitFlow_break(OperonParser.Flow_breakContext ctx) {
        //:OFF:log.debug("EXIT Flow_break :: Stack size :: " + this.stack.size());
        Node expr = this.stack.pop();
        FlowBreak flowBreak = new FlowBreak(this.currentStatement);
        flowBreak.setExprNode(expr);
        this.stack.push(flowBreak);
    }
    
    @Override
    public void enterChoice(OperonParser.ChoiceContext ctx) {
        //:OFF:log.debug("ENTER Choice :: Stack size :: " + this.stack.size());
        Statement choiceStatement = new DefaultStatement(this.getOperonContext());
        choiceStatement.setId("ChoiceStatement");
        this.setPreviousStatementForStatement(choiceStatement);
    }
    
    @Override
    public void exitChoice(OperonParser.ChoiceContext ctx) {
        //:OFF:log.debug("EXIT Choice :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        // count: io.operon.parser.OperonParser$ExprContext, and apply stack pop() same amount
        int popCount = 0;
        boolean hasOtherwise = false;
        for (int i = 0; i < subNodesSize; i ++) {
            if (subNodes.get(i) instanceof TerminalNode) {
                if (subNodes.get(i).getText().equals("Otherwise")) {
                    hasOtherwise = true;
                }
            }
            
            if (subNodes.get(i) instanceof OperonParser.ExprContext) {
                popCount += 1;
            }
        }
        
        //:OFF:log.debug("  Choice :: subNodes :: " + subNodesSize);
        Choice choice = new Choice(this.currentStatement);
        
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
    public void exitFilter_expr(OperonParser.Filter_exprContext ctx) {
        //:OFF:log.debug("EXIT Filter_expr :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        
        //for (int i = 0; i < subNodes.size(); i ++) {
        //    System.out.println("--> subNode " + i + ": " + subNodes.get(i).getText());
        //}
        
        Filter filter = new Filter(this.currentStatement);
        //
        // Could be expr or splicingExpr
        //
        Node filterList = this.stack.pop();
        filter.setFilterListExpression(filterList);
        filter.setSourceCodeLineNumber(ctx.start.getLine());
        
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
    public void exitFilter_full_expr(OperonParser.Filter_full_exprContext ctx) {
        //:OFF:log.debug("EXIT Filter_full_expr :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        
        Filter filter = new Filter(this.currentStatement);
        //
        // Could be expr or splicingExpr
        //
        Node filterList = this.stack.pop();
        filter.setFilterListExpression(filterList);
        filter.setSourceCodeLineNumber(ctx.start.getLine());
        
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
    public void exitFilter_list(OperonParser.Filter_listContext ctx) {
        //:OFF:log.debug("EXIT Filter_list :: Stack size :: " + this.stack.size());
        FilterList filterList = new FilterList(currentStatement);
        filterList.setSourceCodeLineNumber(ctx.start.getLine());
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
    public void exitFilter_list_expr(OperonParser.Filter_list_exprContext ctx) {
        //:OFF:log.debug("EXIT Filter_list_expr :: Stack size :: " + this.stack.size());
        FilterListExpr filterListExpr = new FilterListExpr(this.currentStatement);
        filterListExpr.setSourceCodeLineNumber(ctx.start.getLine());
        filterListExpr.setFilterExpr(this.stack.pop());
        this.stack.push(filterListExpr);
    }

    @Override
    public void exitSplicing_expr(OperonParser.Splicing_exprContext ctx) {
        //:OFF:log.debug("EXIT Splicing_expr :: Stack size :: " + this.stack.size());

        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        //:OFF:log.debug("    SubTree nodes :: " + subNodesSize);
        
        // :: expr
        if (subNodesSize == 2 && subNodes.get(0) instanceof TerminalNode) {
            try {
                //:OFF:log.debug("    SpliceLeft");
                Node spliceUntilNode = this.stack.pop();
                List<Node> params = new ArrayList<Node>();
                params.add(spliceUntilNode);
                SpliceLeft splicingLeft = new SpliceLeft(this.currentStatement, params);
                this.stack.push(splicingLeft);
            } catch (Exception e) {
                throw new RuntimeException("Error :: SpliceLeft :: " + e.getMessage());
            }
        }
        
        // expr ::
        else if (subNodesSize == 2 && subNodes.get(1) instanceof TerminalNode) {
            try {
                //:OFF:log.debug("    SpliceRight");
                Node spliceUntilNode = this.stack.pop();
                List<Node> params = new ArrayList<Node>();
                params.add(spliceUntilNode);
                SpliceRight spliceRight = new SpliceRight(this.currentStatement, params);
                this.stack.push(spliceRight);
            } catch (Exception e) {
                throw new RuntimeException("Error :: SpliceRight :: " + e.getMessage());
            }
        }
        
        // expr :: expr
        else if (subNodesSize == 3 && subNodes.get(1) instanceof TerminalNode) {
            try {
                //:OFF:log.debug("    Splicing lhs - rhs");
                Node spliceCountNode = this.stack.pop();
                Node spliceStartNode = this.stack.pop();
                List<Node> params = new ArrayList<Node>();
                params.add(spliceCountNode);
                params.add(spliceStartNode);
                SpliceRange splicingRange = new SpliceRange(this.currentStatement, params);
                this.stack.push(splicingRange);
            } catch (Exception e) {
                throw new RuntimeException("Error :: SpliceRange :: " + e.getMessage());
            }
        }

        else if (subNodesSize > 0) {
            //:OFF:log.debug("    Splicing_expr :: unknown :: " + subNodes.get(0));
        }
    }


    @Override
    public void exitBind_value_expr(OperonParser.Bind_value_exprContext ctx) {
        //:OFF:log.debug("EXIT Bind_value_expr :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        //:OFF:log.debug(">> subNodes.size :: " + subNodes.size());
        List<Operator> operators = new ArrayList<Operator>();
        
        // TODO: loop through Operators and pop-stack enough times!
        for (int i = subNodes.size() - 1; i > 2; i --) {
            if (subNodes.get(i) instanceof TerminalNode) {
                //:OFF:log.debug(">> subNode: " + i + " :: " + subNodes.get(i).getText());
            }
            
            else {
                Operator op = (Operator) this.stack.pop();
                operators.add(op);
            }
        }
        
        
        ValueRef valueRef = (ValueRef) this.stack.pop();
        String valueRefStr = valueRef.getValueRef();
        //:OFF:log.debug("  >> ValueRefStr :: " + valueRefStr);
        
        Map<String, List<Operator>> bindValues = (Map<String, List<Operator>>) this.getOperonContext().getBindValues();
        
        ArrayList<Operator> bindValuesList = null;
        
        if (bindValues.get(valueRefStr) == null) {
            bindValuesList = new ArrayList<Operator>();
        }
        
        else {
            bindValuesList = (ArrayList<Operator>) bindValues.get(valueRefStr);
        }
        
        bindValuesList.addAll(operators);
        bindValues.put(valueRefStr, bindValuesList);
        //:OFF:log.debug("  >> Bind_value_expr :: added value binding");
    }



    @Override
    public void exitOperator_expr(OperonParser.Operator_exprContext ctx) {
        //:OFF:log.debug("EXIT Operator_expr :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        
        // Get the FunctionRef
        FunctionRef funcRef = (FunctionRef) this.stack.pop();
        
        boolean isCascade = false;
        if (subNodes.get(subNodes.size() - 2) instanceof TerminalNode && 
            subNodes.get(subNodes.size() - 2).getText().toLowerCase().equals("cascade")) {
            //:OFF:log.debug("  >> Operator :: set cascade true :: " + subNodes.get(subNodes.size() - 2).getText());
            isCascade = true;
        }
        
        // Get the overloaded operator:
        for (int i = 0; i < subNodesSize; i ++) {
            //:OFF:log.debug(subNodes.get(i).getClass().getName());
            if (subNodes.get(i) instanceof TerminalNode) {
                //:OFF:log.debug("  >> Operator :: terminal-node found.");
            }
        }
        String operator = subNodes.get(2).getText();
        //:OFF:log.debug(" >> OPERATOR :: " + operator);
        Operator op = new Operator(this.currentStatement); // TODO: might not to inherit Node, therefore giving statement not required.
        op.setOperator(operator);
        op.setFunctionRef(funcRef);
        op.setCascade(isCascade);
        this.stack.push(op);
    }


    /**
     * When exiting an Expr, then pop the stack, and wire new Node (Unary, Binary, or Multi).
     * 
     */
    @Override
    public void exitExpr(OperonParser.ExprContext ctx) {
        //:OFF:log.debug("EXIT Expr :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        String exprAsString = this.getExpressionAsString(subNodes);
        ////:OFF:log.debug("=== COMPILE EXPR ===");
        ////:OFF:log.debug("  EXPR=\"" + exprAsString + "\"");
        //System.out.println("EXIT EXPR=\"" + exprAsString + "\"");
        int subNodesSize = subNodes.size();

        if (subNodesSize == 1) {
            //:OFF:log.debug("ENTER UNARYNODE :: SubNodesSize == 1, Stack size == " + this.stack.size());
            UnaryNode unode = new UnaryNode(this.currentStatement);
            unode.setExpr(exprAsString);
            unode.setNode(this.stack.pop());
            this.stack.push(unode);
            //:OFF:log.debug("EXIT SubNodesSize == 1");
        }
        
        else if (subNodesSize == 3 && subNodes.get(1) instanceof TerminalNode) {
            //:OFF:log.debug("ENTER BINARYNODE");
            
            Node rhs = this.stack.pop();
            Node lhs = this.stack.pop();
            
            BinaryNodeProcessor op = null;
            
            // Check which op and create and assign it
            TerminalNode token = ctx.getToken(OperonParser.PLUS, 0);
            
            if (token != null) {
                op = new Plus();
                op.setSourceCodeLineNumber(ctx.start.getLine());
                //:OFF:log.debug("  Plus()");
            }
            
            else if ( (token = ctx.getToken(OperonParser.MINUS, 0)) != null) {
                op = new Minus();
                op.setSourceCodeLineNumber(ctx.start.getLine());
                //:OFF:log.debug("  Minus()");
            }
            
            else if ( (token = ctx.getToken(OperonParser.MULT, 0)) != null) {
                op = new Multiplicate();
                op.setSourceCodeLineNumber(ctx.start.getLine());
                //:OFF:log.debug("  Multiplicate()");
            }
            
            else if ( (token = ctx.getToken(OperonParser.DIV, 0)) != null) {
                op = new Division();
                op.setSourceCodeLineNumber(ctx.start.getLine());
                //:OFF:log.debug("  Division()");
            }
            
            else if ( (token = ctx.getToken(OperonParser.MOD, 0)) != null) {
                op = new Modulus();
                op.setSourceCodeLineNumber(ctx.start.getLine());
                //:OFF:log.debug("  Modulus()");
            }
            
            else if ( (token = ctx.getToken(OperonParser.POW, 0)) != null) {
                op = new Power();
                op.setSourceCodeLineNumber(ctx.start.getLine());
                //:OFF:log.debug("  Power()");
            }
            
            else if ( (token = ctx.getToken(OperonParser.EQ, 0)) != null) {
                op = new Eq();
                op.setSourceCodeLineNumber(ctx.start.getLine());
                //:OFF:log.debug("  Eq()");
            }
            
            else if ( (token = ctx.getToken(OperonParser.IEQ, 0)) != null) {
                op = new InEq();
                op.setSourceCodeLineNumber(ctx.start.getLine());
                //:OFF:log.debug("  InEq()");
            }

            else if ( (token = ctx.getToken(OperonParser.GT, 0)) != null) {
                op = new Gt();
                op.setSourceCodeLineNumber(ctx.start.getLine());
                //:OFF:log.debug("  Gt()");
            }

            else if ( (token = ctx.getToken(OperonParser.GTE, 0)) != null) {
                op = new Gte();
                op.setSourceCodeLineNumber(ctx.start.getLine());
                //:OFF:log.debug("  Gte()");
            }

            else if ( (token = ctx.getToken(OperonParser.LT, 0)) != null) {
                op = new Lt();
                op.setSourceCodeLineNumber(ctx.start.getLine());
                //:OFF:log.debug("  Lt()");
            }

            else if ( (token = ctx.getToken(OperonParser.LTE, 0)) != null) {
                op = new Lte();
                op.setSourceCodeLineNumber(ctx.start.getLine());
                //:OFF:log.debug("  Lte()");
            }

            else if ( (token = ctx.getToken(OperonParser.AND, 0)) != null) {
                op = new And();
                op.setSourceCodeLineNumber(ctx.start.getLine());
                //:OFF:log.debug("  And()");
            }

            else if ( (token = ctx.getToken(OperonParser.OR, 0)) != null) {
                op = new Or();
                op.setSourceCodeLineNumber(ctx.start.getLine());
                //:OFF:log.debug("  Or()");
            }
            
            BinaryNode bnode = new BinaryNode(this.currentStatement);
            bnode.setExpr(exprAsString);
            bnode.setLhs(lhs);
            bnode.setRhs(rhs);
            bnode.setBinaryNodeProcessor(op);
            
            this.stack.push(bnode);
            
            //:OFF:log.debug("EXIT BINARYNODE");
        }
        
        else if (subNodesSize == 2 
                && subNodes.get(0) instanceof TerminalNode) {
            //:OFF:log.debug("ENTER UnaryNode (- or Not)");

            UnaryNode unode = new UnaryNode(this.currentStatement);
            unode.setExpr(exprAsString);
            unode.setNode(this.stack.pop());

            UnaryNodeProcessor op = null;
            
            // Check which op and create and assign it
            TerminalNode token = ctx.getToken(OperonParser.NOT, 0);
            
            if (token != null) {
                op = new Not();
                op.setSourceCodeLineNumber(ctx.start.getLine());
                //:OFF:log.debug("  Not()");
            }
            
            else if ( (token = ctx.getToken(OperonParser.MINUS, 0)) != null || (token = ctx.getToken(OperonParser.NEGATE, 0)) != null) {
                op = new Negate();
                op.setSourceCodeLineNumber(ctx.start.getLine());
                //:OFF:log.debug("  Negate()");
            }

            unode.setUnaryNodeProcessor(op);
            this.stack.push(unode);
            //:OFF:log.debug("EXIT SubNodesSize == 1");
        }
        
        else if (subNodesSize == 3 
                && subNodes.get(0) instanceof TerminalNode
                && subNodes.get(2) instanceof TerminalNode) {
            //:OFF:log.debug("ENTER UnaryNode (Parentheses)");

            UnaryNode unode = new UnaryNode(this.currentStatement);
            unode.setExpr(exprAsString);
            unode.setNode(this.stack.pop());
            this.stack.push(unode);
            //:OFF:log.debug("EXIT SubNodesSize == 1");
        }
        
        else if (subNodesSize == 2) {
            // Known cases are instances of (expr expr) --> map_expr, e.g. "[1,2,3] Map @ + 1 End" --> JsonContext, and Map_exprContext and (Not expr)
            // Should be handled as multinode
            //:OFF:log.debug("ENTER MultiNode, subNodesSize = 2");
            for (int i = 0; i < subNodesSize; i ++) {
                //:OFF:log.debug("    SubNode " + i + " :: " + subNodes.get(i).getClass().getName());
            }
            MultiNode mnode = new MultiNode(this.currentStatement);
            mnode.setExpr(exprAsString);
            //:OFF:log.debug("  MultiNode :: Child count :: " + subNodesSize);
            //:OFF:log.debug("  MultiNode :: Stack size :: " + this.stack.size());
            
            if (subNodesSize > 1) {
                for (int i = 0; i < subNodesSize; i ++) {
                    Node node = this.stack.pop();
                    //:OFF:log.debug("   MN :: Loop :: " + node.getClass().getName());
                    mnode.addNode(node);
                }
                //:OFF:log.debug("  MultiNode :: Stack size :: " + this.stack.size());
            }
            this.stack.push(mnode);
            //:OFF:log.debug("EXIT MultiNode");
        }
        
        else {
            //:OFF:log.debug("ENTER MultiNode");
            for (int i = 0; i < subNodesSize; i ++) {
                //:OFF:log.debug("    SubNode " + i + " :: " + subNodes.get(i).getClass().getName());
            }
            
            MultiNode mnode = new MultiNode(this.currentStatement);
            mnode.setExpr(exprAsString);
            //:OFF:log.debug("  MultiNode :: Child count :: " + subNodesSize);
            //:OFF:log.debug("  MultiNode :: Stack size :: " + this.stack.size());
            
            if (subNodesSize > 1) {
                for (int i = 0; i < subNodesSize; i ++) {
                    Node node = this.stack.pop();
                    //:OFF:log.debug("   MN :: Loop :: " + node.getClass().getName());
                    mnode.addNode(node);
                }
                //:OFF:log.debug("  MultiNode :: Stack size :: " + this.stack.size());
            }
            this.stack.push(mnode);
            
            //:OFF:log.debug("EXIT MultiNode");
        }
        //:OFF:log.debug("=== COMPILE EXPR DONE ===");
    }

    @Override
    public void exitParentheses_expr(OperonParser.Parentheses_exprContext ctx) {
        //:OFF:log.debug("EXIT parentheses_expr");
    }

    @Override
    public void exitAssign_expr(OperonParser.Assign_exprContext ctx) {
        //:OFF:log.debug("EXIT Assign_expr :: Stack size :: " + this.stack.size());
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
    public void exitBreak_loop(OperonParser.Break_loopContext ctx) {
        //:OFF:log.debug("EXIT Break :: Stack size :: " + this.stack.size());
        BreakLoop breakLoop = new BreakLoop(this.currentStatement);
        this.stack.push(breakLoop);
    }

    @Override
    public void exitContinue_loop(OperonParser.Continue_loopContext ctx) {
        //:OFF:log.debug("EXIT Continue :: Stack size :: " + this.stack.size());
        ContinueLoop continueLoop = new ContinueLoop(this.currentStatement);
        this.stack.push(continueLoop);
    }

    @Override
    public void enterLoop_expr(OperonParser.Loop_exprContext ctx) {
        //:OFF:log.debug("ENTER Loop_expr :: Stack size :: " + this.stack.size());
        Statement loopStatement = new DefaultStatement(this.getOperonContext());
        loopStatement.setId("LoopStatement");
        this.setPreviousStatementForStatement(loopStatement);
    }
    
    @Override
    public void exitLoop_expr(OperonParser.Loop_exprContext ctx) {
        //:OFF:log.debug("EXIT Loop_expr :: Stack size :: " + this.stack.size());
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
    public void enterDo_while_expr(OperonParser.Do_while_exprContext ctx) {
        //:OFF:log.debug("ENTER Do_while_expr :: Stack size :: " + this.stack.size());
        Statement doWhileStatement = new DefaultStatement(this.getOperonContext());
        doWhileStatement.setId("DoWhileStatement");
        this.setPreviousStatementForStatement(doWhileStatement);
    }
    
    @Override
    public void exitDo_while_expr(OperonParser.Do_while_exprContext ctx) {
        //:OFF:log.debug("EXIT Do_while_expr :: Stack size :: " + this.stack.size());
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
    public void enterWhile_expr(OperonParser.While_exprContext ctx) {
        //:OFF:log.debug("ENTER DoWhile_expr :: Stack size :: " + this.stack.size());
        Statement whileStatement = new DefaultStatement(this.getOperonContext());
        whileStatement.setId("WhileStatement");
        this.setPreviousStatementForStatement(whileStatement);
    }
    
    @Override
    public void exitWhile_expr(OperonParser.While_exprContext ctx) {
        //:OFF:log.debug("EXIT While_expr :: Stack size :: " + this.stack.size());
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
    public void enterMap_expr(OperonParser.Map_exprContext ctx) {
        //:OFF:log.debug("ENTER Map_expr :: Stack size :: " + this.stack.size());
        Statement mapStatement = new DefaultStatement(this.getOperonContext());
        mapStatement.setId("MapStatement");
        this.setPreviousStatementForStatement(mapStatement);
    }
    
    @Override
    public void exitMap_expr(OperonParser.Map_exprContext ctx) {
        //:OFF:log.debug("EXIT Map_expr :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        io.operon.runner.node.Map map = new io.operon.runner.node.Map(this.currentStatement);
        Node mapExpr = this.stack.pop();
        map.setMapExpr(mapExpr);
        map.setSourceCodeLineNumber(ctx.start.getLine());
        
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
    public void exitValue_ref(OperonParser.Value_refContext ctx) {
        //:OFF:log.debug("EXIT Value_ref :: Stack size :: " + this.stack.size());
        //System.out.println("COMPILER :: EXIT Value_ref, currentStatement=" + this.currentStatement);
        ValueRef vrNode = new ValueRef(this.currentStatement);
        
        // Set correct symbol:
        String symbol = null;
        List<TerminalNode> namespaces = new ArrayList<TerminalNode>();
        
        if (ctx.ID() != null) {
            namespaces = ctx.ID();
        }
        
        if (ctx.CONST_ID() != null) {
            symbol = ctx.CONST_ID().toString();
        }
        
        // @
        else if (ctx.CURRENT_VALUE() != null) {
            symbol = ctx.CURRENT_VALUE().toString();
        }
        
        // ._
        else if (ctx.OBJ_SELF_REFERENCE() != null) {
            symbol = ctx.OBJ_SELF_REFERENCE().toString();
            this.getOperonContext().getConfigs().setSupportPos(true);
            this.getOperonContext().getConfigs().setSupportParent(true);
        }
        
        // $
        else if (ctx.ROOT_VALUE() != null) {
            symbol = ctx.ROOT_VALUE().toString();
        }
        
        vrNode.setValueRef(symbol);
        
        for (TerminalNode tn : namespaces) {
            String ns = tn.toString();
            vrNode.getNamespaces().add(ns);
        }
        
        //:OFF:log.debug("  >> valueRef :: " + symbol);
        this.stack.push(vrNode);
    }

    //
    // $(expr)
    //
    @Override
    public void exitComputed_value_ref(OperonParser.Computed_value_refContext ctx) {
        //:OFF:log.debug("EXIT Computed_value_ref :: Stack size :: " + this.stack.size());
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
    public void exitFunction_regular_argument(OperonParser.Function_regular_argumentContext ctx) {
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        //:OFF:log.debug("EXIT Function_regular_argument :: SUBNODES :: " + subNodesSize + ". VALUE :: " + subNodes.get(0).getText());
        FunctionRegularArgument fra = new FunctionRegularArgument(this.currentStatement);
        Node regArg = this.stack.pop();
        fra.setArgument(regArg);
        this.stack.push(fra);
    }

    @Override
    public void exitFunction_named_argument(OperonParser.Function_named_argumentContext ctx) {
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        //:OFF:log.debug("EXIT Function_named_argument :: SUBNODES :: " + subNodesSize + ". VALUE :: " + subNodes.get(0).getText());
        FunctionNamedArgument fna = new FunctionNamedArgument(this.currentStatement);
        String argName = "";
        if (subNodes.get(0) instanceof TerminalNode) {
            argName = subNodes.get(0).getText();
            //:OFF:log.debug("  >> Arg-name :: " + argName);
        }
        Node argValue = this.stack.pop();
        fna.setArgumentName(argName);
        fna.setArgumentValue(argValue);
        this.stack.push(fna);
    }

    @Override
    public void exitFunction_arguments(OperonParser.Function_argumentsContext ctx) {
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        //:OFF:log.debug("EXIT Function_arguments :: SUBNODES :: " + subNodesSize + ". " + subNodes.get(0).getText());
        
        FunctionArguments fArgs = new FunctionArguments(this.currentStatement);
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
                //:OFF:log.debug("  Adding argument");
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
    public void exitFunction_ref_argument_placeholder(OperonParser.Function_ref_argument_placeholderContext ctx) {
        //:OFF:log.debug("EXIT Function_ref_argument_placeholder :: Stack size :: " + this.stack.size());
        FunctionRefArgumentPlaceholder frArgPlaceholder = new FunctionRefArgumentPlaceholder(this.currentStatement);
        this.stack.push(frArgPlaceholder);
    }

    @Override
    public void exitFunction_ref_invoke(OperonParser.Function_ref_invokeContext ctx) {
        //:OFF:log.debug("EXIT Function_ref_invoke :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        
        FunctionArguments fArgs = (FunctionArguments) this.stack.pop();
        Node refExpr = (Node) this.stack.pop();
        FunctionRefInvoke frInvoke = new FunctionRefInvoke(this.currentStatement);
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
    public void exitFunction_ref_invoke_full(OperonParser.Function_ref_invoke_fullContext ctx) {
        //:OFF:log.debug("EXIT Function_ref_invoke_full :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        
        FunctionArguments fArgs = (FunctionArguments) this.stack.pop();
        Node refExpr = (Node) this.stack.pop();
        FunctionRefInvoke frInvoke = new FunctionRefInvoke(this.currentStatement);
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
    public void exitJson(OperonParser.JsonContext ctx) {
        //:OFF:log.debug("EXIT Json :: Stack size :: " + this.stack.size());
        //:OFF:log.debug("    >> TRYING TO PEEK");
        
        Node n = this.stack.peek();
        assert (n != null): "exitJson :: null value from stack";
        //
        // NOTE: cannot print the peeked result, because it would require that value
        //       was already evaluated, which it is not at this stage.
        //System.out.println("    >> PEEK RESULT: " + n.toString()); // was n.toString()
        if (this.currentStatement != null) {
            this.currentStatement.setNode(n);
            //:OFF:log.debug("    >> NODE SET");
        } else {
            //:OFF:log.debug("    >> CURRENT STATEMENT WAS NULL !!!!!!!");
        }
    }
    
    @Override
    public void exitJson_value(OperonParser.Json_valueContext ctx) {
        //:OFF:log.debug("EXIT Json_value :: Stack size :: " + this.stack.size());
        OperonValue jsonValue = new OperonValue(this.currentStatement);
        List<ParseTree> subNodes = this.getContextChildNodes(ctx);
        
        //:OFF:log.debug("CHILD NODES :: " + subNodes.size() + " :: CHILD NODE [0] TYPE :: " + subNodes.get(0).getClass().getName());
        
        if (subNodes.size() > 0 
            && subNodes.get(0) instanceof RuleNode 
            && subNodes.get(0) instanceof OperonParser.Json_objContext) {
            jsonValue.setValue(this.stack.pop()); // set ObjectType from stack
        }

        else if (subNodes.size() > 0 
            && subNodes.get(0) instanceof RuleNode 
            && subNodes.get(0) instanceof OperonParser.Json_arrayContext) {
            //:OFF:log.debug("SETTING JSON VALUE --> JSON ARRAY.");
            Node value = this.stack.pop();
            if (value == null) {
                //:OFF:log.error("WARNING:: POPPED NULL VALUE!!!");
            }
            jsonValue.setValue(value); // set ArrayType from stack   
        }

        else if (subNodes.size() > 0 
            && subNodes.get(0) instanceof RuleNode 
            && subNodes.get(0) instanceof OperonParser.Path_valueContext) {
            //:OFF:log.debug("SETTING VALUE --> Path-value.");
            Node value = this.stack.pop();
            if (value == null) {
                //:OFF:log.error("WARNING:: POPPED NULL VALUE!!!");
            }
            jsonValue.setValue(value); // set PathValue from stack   
        }

        else if (subNodes.size() > 0 
            && subNodes.get(0) instanceof RuleNode 
            && subNodes.get(0) instanceof OperonParser.Compiler_obj_config_lookupContext) {
            //:OFF:log.debug("SETTING VALUE --> Compiler_obj_config_lookupContext-value.");
            Node value = this.stack.pop();
            if (value == null) {
                //:OFF:log.error("WARNING:: POPPED NULL VALUE!!!");
            }
            jsonValue.setValue(value); // set PathValue from stack   
        }

        else if (subNodes.size() > 0 && subNodes.get(0) instanceof TerminalNode) {
            TerminalNode token = ctx.getToken(OperonParser.STRING, 0);
            
            if (token != null) {
                StringType sNode = new StringType(this.currentStatement);
                String symbolText = token.getSymbol().getText();
                //:OFF:log.debug("TerminalNode: String. Text :: " + symbolText);
                sNode.setValue(symbolText);
                jsonValue.setValue(sNode);
            }
            
            else if ( (token = ctx.getToken(OperonParser.NUMBER, 0)) != null) {
                NumberType nNode = new NumberType(this.currentStatement);
                String symbolText = token.getSymbol().getText().toLowerCase();
                //:OFF:log.debug("TerminalNode. Text :: " + symbolText);
                
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
            
            else if ( (token = ctx.getToken(OperonParser.JSON_FALSE, 0)) != null) {
                FalseType jsonFalse = new FalseType(this.currentStatement);
                jsonValue.setValue(jsonFalse);
            }
            
            else if ( (token = ctx.getToken(OperonParser.JSON_TRUE, 0)) != null) {
                TrueType jsonTrue = new TrueType(this.currentStatement);
                jsonValue.setValue(jsonTrue);
            }
            
            else if ( (token = ctx.getToken(OperonParser.JSON_NULL, 0)) != null) {
                NullType jsonNull = new NullType(this.currentStatement);
                jsonValue.setValue(jsonNull);
            }

            else if ( (token = ctx.getToken(OperonParser.RAW_STRING, 0)) != null) {
                RawValue raw = new RawValue(this.currentStatement);
                // substring is for cutting out the ` parts, which are used for RawString
                String rawStr = token.getSymbol().getText().substring(1, token.getSymbol().getText().length() - 1);
                //System.out.println("RAW :: " + rawStr);
                rawStr = rawStr.replaceAll("\\\\`", "`");
                byte[] valueBytes = rawStr.getBytes(StandardCharsets.UTF_8);
                raw.setValue(valueBytes);
                jsonValue.setValue(raw);
            }

            //
            // """
            //
            else if ( (token = ctx.getToken(OperonParser.MULTILINE_STRING, 0)) != null) {
                StringType sNode = new StringType(this.currentStatement);
                String symbolText = token.getSymbol().getText();
                //:OFF:log.debug("TerminalNode: String. Text :: " + symbolText);
                
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
                    else if (c == '"') {
                        sb.append("\\\"");
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

            //
            // """|
            // 
            else if ( (token = ctx.getToken(OperonParser.MULTILINE_STRIPPED_STRING, 0)) != null) {
                StringType sNode = new StringType(this.currentStatement);
                String symbolText = token.getSymbol().getText();
                //:OFF:log.debug("TerminalNode: String. Text :: " + symbolText);
                
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
                                if (c == '"') {
                                    sb.append("\\\"");
                                }
                                else {
                                    sb.append(c);
                                }
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

            //
            // """>
            //
            else if ( (token = ctx.getToken(OperonParser.MULTILINE_PADDED_LINES_STRING, 0)) != null) {
                //System.out.println("MULTILINE_PADDED_LINES_STRING");
                StringType sNode = new StringType(this.currentStatement);
                String symbolText = token.getSymbol().getText();
                //:OFF:log.debug("TerminalNode: String. Text :: " + symbolText);
                
                StringBuilder sb = new StringBuilder();
                
                // strip the whitespace and tabs from the beginning of each line
                boolean strippingMode = false;
                int initialStrippedCounter = 0;
                int lineStrippedCounter = 0;
                boolean initialStrippedCounterSet = false;
                for (int i = 4; i < symbolText.length() - 3; i ++) {
                    char c = symbolText.charAt(i);
                    if (c != '\r' && c != '\n') {
                        if (strippingMode) {
                            if (c != ' ' && c != '\t') {
                                strippingMode = false;
                                initialStrippedCounterSet = true;
                                // Set rest of the white-spaces:
                                //System.out.println("Line stripped counter2=" + lineStrippedCounter);
                                for (int s = 0; s < lineStrippedCounter - initialStrippedCounter; s ++) {
                                    sb.append(" ");
                                }
                                sb.append(c);
                            }
                            else {
                                if (initialStrippedCounterSet == false) {
                                    initialStrippedCounter += 1;
                                }
                                lineStrippedCounter += 1;
                                //System.out.println("Line stripped counter1=" + lineStrippedCounter);
                                
                            }
                        }
                        else {
                            if (c == '\t') {
                                sb.append("\\t");
                            }
                            else {
                                if (c == '"') {
                                    sb.append("\\\"");
                                }
                                else {
                                    sb.append(c);
                                }
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
                        lineStrippedCounter = 0;
                        strippingMode = true;
                    }
                }
                
                symbolText = "\"" + sb.toString() + "\"";
                //System.out.println("symbolText :: " + symbolText);
                sNode.setValue(symbolText);
                jsonValue.setValue(sNode);
            }

            else if ( (token = ctx.getToken(OperonParser.EMPTY_VALUE, 0)) != null) {
                String symbolText = token.getSymbol().getText().toLowerCase();
                ////:OFF:log.debug("TerminalNode: Empty. Text :: " + symbolText);
                EmptyType jsonEmptyValue = new EmptyType(this.currentStatement);
                jsonValue.setValue(jsonEmptyValue);
                jsonValue.setIsEmptyValue(true);
            }
            
            else if ( (token = ctx.getToken(OperonParser.END_VALUE, 0)) != null) {
                String symbolText = token.getSymbol().getText().toLowerCase();
                EndValueType jsonEndValue = new EndValueType(this.currentStatement);
                jsonValue.setValue(jsonEndValue);
            }
        }
        
        this.stack.push(jsonValue);
    }
    
    @Override
    public void exitJson_array(OperonParser.Json_arrayContext ctx) {
        //:OFF:log.debug("EXIT Json_array :: Stack size :: " + this.stack.size());
        List<ParseTree> ruleChildNodes = this.getContextChildRuleNodes(ctx);
        int childCount = ruleChildNodes.size();
        
        ArrayType jsonArray = new ArrayType(this.currentStatement);
        jsonArray.setArrayId(this.objIndex);
        
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
    public void exitJson_obj(OperonParser.Json_objContext ctx) {
        //:OFF:log.debug("EXIT Json_obj :: Stack size :: " + this.stack.size());
        List<ParseTree> ruleChildNodes = this.getContextChildRuleNodes(ctx);
        int pairsCount = ruleChildNodes.size();
        
        ObjectType jsonObj = new ObjectType(this.currentStatement);
        jsonObj.setObjId(this.objIndex);
        //:OFF:log.debug("    Set objId :: " + Integer.toString(this.objIndex));
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
    public void exitJson_pair(OperonParser.Json_pairContext ctx) {
        //:OFF:log.debug("EXIT Json_pair :: Stack size :: " + this.stack.size());
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
        
        //System.out.println("subNodes size: " + subNodes.size());
        
        //         
        // Add OperonValueConstraint, if such exists
        // 
        if (subNodes.size() >= 4 && this.stack.peek() instanceof OperonValueConstraint) {
            //System.out.println("Constraint found.");
            OperonValueConstraint ovc = (OperonValueConstraint) this.stack.pop();
            ovc.setSourceCodeLineNumber(ctx.start.getLine());
            String constraintAsString = subNodes.get(1).getText();
            ovc.setConstraintAsString(constraintAsString);
            jsonPair.setOperonValueConstraint(ovc);
        }
        
        //
        // Add configs-object for Pair.
        // Available options:
        //    "hidden" --> controls if the value is serialized or not.
        //
        if (subNodes.size() >= 4 && this.stack.size() > 0 && this.stack.peek() instanceof ObjectType) {
            //System.out.println("ConfigObject found.");
            Node pairConfigs = this.stack.pop();
            jsonPair.setConfigs(pairConfigs);
            //System.out.println("ConfigObject set.");
        }
        
        //:OFF:log.debug("   PairType subNodes :: " + subNodes.size());
        
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
    public void exitCompiler_obj_config_lookup(OperonParser.Compiler_obj_config_lookupContext ctx) {
        //:OFF:log.debug("EXIT Compiler_obj_config_lookup :: Stack size :: " + this.stack.size());
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
    public void exitJson_value_constraint(OperonParser.Json_value_constraintContext ctx) {
        //:OFF:log.debug("EXIT Json_value_constraint :: Stack size :: " + this.stack.size());
        OperonValueConstraint jsonValueConstraint = new OperonValueConstraint(this.currentStatement);
        jsonValueConstraint.setValueConstraint(this.stack.pop());
        this.stack.push(jsonValueConstraint);
    }
    
    @Override
    public void exitObj_access(OperonParser.Obj_accessContext ctx) {
        //:OFF:log.debug("exitObj_access");
        String accessKey = "";
        
        if (ctx.ID() != null) {
            accessKey = ctx.ID().getSymbol().getText();
        }
        else {
            throw new RuntimeException("exitObj_access :: unknown symbol");
        }
        //:OFF:log.debug("EXIT OBJ_ACCESS :: " + ctx.ID()); 
        ObjAccess objAccess = new ObjAccess(this.currentStatement); 
        objAccess.setObjAccessKey(accessKey);
        objAccess.setSourceCodeLineNumber(ctx.start.getLine());
        this.stack.push(objAccess);
    }
    
    @Override
    public void exitObj_dynamic_access(OperonParser.Obj_dynamic_accessContext ctx) {
        //:OFF:log.debug("EXIT OBJ_DYNAMIC_ACCESS :: stack size :: " + this.stack.size()); 
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        
        ObjDynamicAccess objDynamicAccess = new ObjDynamicAccess(this.currentStatement); 
        objDynamicAccess.setKeyExpr(this.stack.pop());
        objDynamicAccess.setSourceCodeLineNumber(ctx.start.getLine());
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
    public void exitObj_deep_scan(OperonParser.Obj_deep_scanContext ctx) {
        //:OFF:log.debug("EXIT OBJ_DEEP_SCAN :: " + ctx.ID());
        ObjDeepScan objDeepScan = new ObjDeepScan(this.currentStatement);
        objDeepScan.setObjDeepScanKey(ctx.ID().getSymbol().getText());
        objDeepScan.setSourceCodeLineNumber(ctx.start.getLine());
        this.stack.push(objDeepScan);
    }
    
    @Override
    public void exitObj_dynamic_deep_scan(OperonParser.Obj_dynamic_deep_scanContext ctx) {
        //:OFF:log.debug("EXIT OBJ_DYNAMIC_DEEP_SCAN");
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        
        ObjDynamicDeepScan objDynamicDeepScan = new ObjDynamicDeepScan(this.currentStatement);
        objDynamicDeepScan.setKeyExpr(this.stack.pop());
        objDynamicDeepScan.setSourceCodeLineNumber(ctx.start.getLine());
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
    public void exitPath_matches(OperonParser.Path_matchesContext ctx) {
        //:OFF:log.debug("EXIT PathMatches, stack size=" + this.stack.size());
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
    public void exitWhere_expr(OperonParser.Where_exprContext ctx) {
        //:OFF:log.debug("EXIT Where, stack size=" + this.stack.size());
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
    public void exitObj_update_expr(OperonParser.Obj_update_exprContext ctx) {
        //:OFF:log.debug("EXIT Obj_update, stack size=" + this.stack.size());
        Update update = new Update(this.currentStatement);
        List<ParseTree> nodes = getContextChildNodes(ctx);
        
        //
        // Update {foo: 123} End
        // << {foo: 123};
        //
        //
        // << ~.bin: "foo";
        //
        //System.out.println("nodes size=" + nodes.size());
        //for (int i = 0; i < nodes.size(); i ++) {
        //    System.out.println(nodes.get(i).getText());
        //}
        //System.out.println("===");
        
        // Skip from end: "End", skip from start "Update"
        UpdatePair up = new UpdatePair();

        //System.out.println("Stack size=" + this.stack.size());
        //System.out.println("Pop value");
        Node popUpdateValue = this.stack.pop();
        
        up.setIsObject(true);
        up.setUpdateValue(popUpdateValue);
        
        update.getPathUpdates().add(up);
        
        //
        // Configuration object is optional.
        //
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

    @Override
    public void exitUpdate_expr(OperonParser.Update_exprContext ctx) {
        //:OFF:log.debug("EXIT Update, stack size=" + this.stack.size());
        Update update = new Update(this.currentStatement);
        List<ParseTree> nodes = getContextChildNodes(ctx);
        
        //
        // Update Path(...) : expr End
        // << ~(...): expr;
        // << ~.bin: 100, ~.baa: 200;
        // << {}: ~.bin: "baa";
        // << {upsert}: ~.bin: 100, ~.baa: 200;
        //System.out.println("nodes size=" + nodes.size());
        //for (int i = 0; i < nodes.size(); i ++) {
        //    System.out.println(nodes.get(i).getText());
        //}
        //System.out.println("===");
        
        // Skip from end: "End", skip from start "Update"
        boolean popValue = true;
        UpdatePair up = new UpdatePair();
        for (int i = nodes.size() - 2; i >= 1; i --) {
            //System.out.println("Stack size=" + this.stack.size());
            if (nodes.get(i) instanceof TerminalNode) {
                //System.out.println("SKIP TerminalNode: " + nodes.get(i).getText());
                continue;
            }
            //System.out.println("processing: " + nodes.get(i).getText());
            //
            // i must be > 1 to prevent popping the config-obj.
            //
            if (popValue && i > 1) {
                //System.out.println("Pop value, i=" + i);
                Node popUpdateValue = this.stack.pop();
                up.setUpdateValue(popUpdateValue);
                popValue = false;
                continue;
            }
            //
            // i must be >= 1 and node should not be an object,
            // both to prevent popping the config-obj.
            //
            if (i >= 1 && nodes.get(i).getText().charAt(0) != '{') {
                //System.out.println("Pop path, i=" + i + ", i text=" + nodes.get(i).getText());
                io.operon.runner.node.type.Path popPath = (io.operon.runner.node.type.Path) this.stack.pop();
                up.setPath(popPath);
                update.getPathUpdates().add(up);
                up = new UpdatePair();
                popValue = true;
            }
        }
        
        //
        // Configuration object is optional.
        //
        if (this.stack.size() > 0 && nodes.get(1).getText().charAt(0) == '{') {
            //System.out.println("Peeking for configs: " + nodes.get(1).getText());
            Node peeked = this.stack.peek();
            //System.out.println("Peeked type=" + peeked.getClass().getName());
            
            if (peeked != null && peeked instanceof ObjectType) {
                Node updateConfiguration = this.stack.pop();
                update.setConfigs(updateConfiguration);
            }
        }
        
        this.stack.push(update);
    }

    @Override
    public void exitBuild_expr(OperonParser.Build_exprContext ctx) {
        //:OFF:log.debug("EXIT Build, stack size=" + this.stack.size());
        Build build = new Build(this.currentStatement);
        List<ParseTree> nodes = getContextChildNodes(ctx);
        
        //System.out.println("Build-statement");
        
        if (this.stack.size() > 0 && nodes.size() > 1 && nodes.get(1).getText().charAt(0) == '{') {
            //System.out.println("Peeking");
            Node peeked = this.stack.peek();
            //System.out.println("Peeked type=" + peeked.getClass().getName());
            
            if (peeked != null && peeked instanceof ObjectType) {
                Node buildConfiguration = this.stack.pop();
                build.setConfigs(buildConfiguration);
            }
        }
        
        //System.out.println("Build-statement: push stack");
        this.stack.push(build);
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
    public void exitUpdate_array_expr(OperonParser.Update_array_exprContext ctx) {
        //:OFF:log.debug("EXIT Update array expr, stack size=" + this.stack.size());
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
    public void exitPath_value(OperonParser.Path_valueContext ctx) {
        //:OFF:log.debug("EXIT Path_value");
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
    public void exitRange_expr(OperonParser.Range_exprContext ctx) {
        //:OFF:log.debug("EXIT Range_expr. Stack size :: " + this.stack.size());
        Range rangeExpr = new Range(this.currentStatement);
        rangeExpr.setRhs(this.stack.pop());
        rangeExpr.setLhs(this.stack.pop());
        this.stack.push(rangeExpr);
    }
    
    
    @Override
    public void exitIo_call(OperonParser.Io_callContext ctx) {
        //:OFF:log.debug("EXIT Integration_call :: Stack size :: " + this.stack.size());
        List<ParseTree> nodes = getContextChildNodes(ctx);

        IOCall ioCall = new IOCall(this.currentStatement);
        ioCall.setSourceCodeLineNumber(ctx.start.getLine());
        
        //System.out.println("NODES SIZE=" + nodes.size());
        
        // Possible combinations:
        //  0  1 2  3  4 5
        // -> out:debug:{} # nodes.size() = 6
        // -> out:debug    # nodes.size() = 4
        // -> out:{}       # nodes.size() = 4
        // -> out          # nodes.size() = 2
        String componentNamespace = this.getOperonContext().getOwnNamespace();
        String componentName = nodes.get(1).toString();
        String componentId = null;
        
        if (nodes.size() == 6) {
            componentId = nodes.get(3).toString();
        } else if (nodes.size() == 4 && nodes.get(3).getClass().getName().equals("io.operon.parser.OperonParser$Json_objContext") == false) {
            componentId = nodes.get(3).toString();
        }
        
        //System.out.println("componentName: " + componentName);
        //System.out.println("componentId: " + componentId);
        
        //:OFF:log.debug("COMPILER :: Integration Call :: " + componentName + ":" + componentId);
        
        ioCall.setComponentName(componentName);
        ioCall.setComponentId(componentId);
        //:OFF:log.debug("COMPILER :: Integration call :: NODES LEN :: " + nodes.size());
        
        //if (nodes.size() >= 5) {
        //    System.out.println("NODE 5=" + nodes.get(5));
        //}
        
        //System.out.println("Check for configs");
        if (nodes.size() == 6 && nodes.get(5).getClass().getName().equals("io.operon.parser.OperonParser$Json_objContext")
            || nodes.size() == 4 && nodes.get(3).getClass().getName().equals("io.operon.parser.OperonParser$Json_objContext")
            ) {
            //System.out.println("configs found!");
            //:OFF:log.debug("COMPILER :: Integration call :: pop stack (Json_obj)");

            ObjectType jsonConfigValue = (ObjectType) this.stack.pop();
            // Don't log at this point, as it would cause obj-serialization,
            // which (currently) cannot be done at compile-time (e.g. ValueRef).
            ioCall.setJsonConfiguration(jsonConfigValue);
        } 
        //else {
        //    System.out.println("configs NOT found!");
        //}

        // This does not work yet:
        //try {
        //    ioCall.loadComponent();
        //} catch (Exception e) {
        //    System.out.println("Compiler :: could not load component during compile-time.");
        //}

        if (this.getOperonTestsContext() != null) {
            List<AssertComponent> assertComponents = null;
            MockComponent mockComponent = null;
            
            if (componentNamespace.isEmpty()) {
                assertComponents = this.getOperonTestsContext().getComponentAsserts().get(componentName + ":" + componentId);
                mockComponent = this.getOperonTestsContext().getComponentMocks().get(componentName + ":" + componentId);
            }
            else {
                assertComponents = this.getOperonTestsContext().getComponentAsserts().get(componentNamespace + ":" + componentName + ":" + componentId);
                mockComponent = this.getOperonTestsContext().getComponentMocks().get(componentNamespace + ":" + componentName + ":" + componentId);
            }
            
            if (assertComponents != null && assertComponents.size() > 0 && mockComponent != null) {
                throw new RuntimeException("Both assert and mock cannot be defined for component " + componentNamespace + ":" + componentName + ":" + componentId);
            }
            
            if (mockComponent != null) {
                // TODO: create Mock -component, which could accept more configuration

                //
                // create MultiNode, with mock-function call.
                //
                // NOTE: we do not create here just:
                //   Node mockExpr = mockComponent.getMockExpr();
                //   mockExpr.setStatement(this.currentStatement);
                //   this.stack.push(mockExpr);
                //
                // The reason is that this mockExpr does not have the correct currentStatement
                // e.g. if it is: UnaryNode(ValueRef), then setting currentStatement sets the
                // currentStatement only for the outermost node (UnaryNode), but the childnodes
                // still have the currentStatement which was generated by the TestsCompiler, which
                // does not have access to OperonContext.
                //
                // Creating MultiNode as wrapper ensures that the node has access to correct
                // OperonContext.
                // 
                MultiNode mnode = new MultiNode(this.currentStatement);
                
                //
                // function params
                //
                List<Node> functionParams = new ArrayList<Node>();
                functionParams.add(mockComponent.getMockExpr());
                
                try {
                    Node mockFunctionCall = CoreFunctionResolver.getCoreFunction("core", "mock", functionParams, this.currentStatement);
                    ((Mock) mockFunctionCall).setMockComponent(mockComponent);
                    mnode.addNode(mockFunctionCall);
                }
                catch (Exception e) {
                    throw new RuntimeException("Compiler :: IOCall :: failed to create mock");
                }
                this.stack.push(mnode);
            }
            
            if (assertComponents != null && assertComponents.size() > 0) {
                //
                // create MultiNode, with (IOCall, FunctionCall(s))
                //
                MultiNode mnode = new MultiNode(this.currentStatement);
                mnode.addNode(ioCall);
                
                for (AssertComponent assertComponent : assertComponents) {
                    //
                    // function params
                    //
                    List<Node> functionParams = new ArrayList<Node>();
                    functionParams.add(assertComponent.getAssertExpr());
                    
                    try {
                        Node assertFunctionCall = CoreFunctionResolver.getCoreFunction("core", "assert", functionParams, this.currentStatement);
                        ((Assert) assertFunctionCall).setAssertComponent(assertComponent);
                        mnode.addNode(assertFunctionCall);
                    }
                    catch (Exception e) {
                        throw new RuntimeException("Compiler :: IOCall :: failed to create assert");
                    }
                }
                this.stack.push(mnode);
            }
            
            if (mockComponent == null && (assertComponents == null || assertComponents.size() == 0)) {
                this.stack.push(ioCall);
            }
        }
        else {
            this.stack.push(ioCall);
        }
    }
    
    @Override
    public void enterAuto_invoke_ref(OperonParser.Auto_invoke_refContext ctx) {
        //:OFF:log.debug("ENTER AutoInvokeRef :: Stack size :: " + this.stack.size());
        Statement functionStatement = new FunctionStatement(this.getOperonContext());
        functionStatement.setId("LambdaFunctionStatement");
        //:OFF:log.debug("    >> Set previousStatement: " + this.getCurrentStatement().getId());
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
    public void exitAuto_invoke_ref(OperonParser.Auto_invoke_refContext ctx) {
        //:OFF:log.debug("EXIT AutoInvokeRef :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        //:OFF:log.debug("    >> AutoInvokeRef :: SUBNODES :: " + subNodesSize + ". " + subNodes.get(0).getText());
        
        LambdaFunctionRef lfnRef = new LambdaFunctionRef(this.currentStatement);
        lfnRef.setInvokeOnAccess(true);

        // Collect params:
        java.util.Map<String, Node> functionParamValueMap = new HashMap<String, Node>();
        
        Node lambdaExpr = this.stack.pop();

        lfnRef.setParams(functionParamValueMap);
        lfnRef.setLambdaExpr(lambdaExpr);
        this.stack.push(lfnRef);
        this.getStatementStack().pop(); // Remove functionStatement from the statementStack
        Statement previousStatement = this.getStatementStack().pop();
        //:OFF:log.debug("    >> Set currentStatement with previousStatement: " + previousStatement.getId());
        this.setCurrentStatement(previousStatement);
    }
    
    @Override
    public void enterLambda_function_ref(OperonParser.Lambda_function_refContext ctx) {
        //:OFF:log.debug("ENTER LambdaFunctionRef :: Stack size :: " + this.stack.size());
        Statement functionStatement = new FunctionStatement(this.getOperonContext());
        functionStatement.setId("LambdaFunctionStatement");
        //:OFF:log.debug("    >> Set previousStatement: " + this.getCurrentStatement().getId());
        this.getStatementStack().push(this.getCurrentStatement());
        functionStatement.setPreviousStatement(this.getCurrentStatement());
        this.getStatementStack().push(functionStatement);
        this.setCurrentStatement(functionStatement);
    }
    
    @Override
    public void exitLambda_function_ref(OperonParser.Lambda_function_refContext ctx) {
        //:OFF:log.debug("EXIT LambdaFunctionRef :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        //:OFF:log.debug("    >> LAMBDA FUNCTION :: SUBNODES :: " + subNodesSize + ". " + subNodes.get(0).getText());

        String exprAsString = this.getExpressionAsString(subNodes);
        LambdaFunctionRef lfnRef = new LambdaFunctionRef(this.currentStatement);
        lfnRef.setExpr(exprAsString);
        
        // Collect params:
        java.util.Map<String, Node> functionParamValueMap = new HashMap<String, Node>();
        
        int startPos = 2;
        int paramsEndParenthesesIndex = 0;
        
        //:OFF:log.debug("    pop lambdaExpr");
        Node lambdaExpr = this.stack.pop();

        //
        // Find the end-parentheses index,
        // which we need first for finding the function's output constraint
        //
        for (int i = startPos; i < subNodesSize - 3; i ++) {
            // Reach end of params
            if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(")")) {
                //:OFF:log.debug("    Reached end of params.");
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
        
        //:OFF:log.debug("    popped lambdaExpr");
        //String constId = "";
        List<Node> lambdaFunctionParams = new ArrayList<Node>();

        //:OFF:log.debug("  Collect params");
        for (int i = startPos; i < subNodesSize - 3; i ++) {
            //:OFF:log.debug("  subNode :: " + i);
            if (subNodes.get(i) instanceof TerminalNode) {
                //:OFF:log.debug("  subNode [" + i + "] :: " + subNodes.get(i).getText());
            }
            // Reach end of params
            if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(")")) {
                //:OFF:log.debug("    Reached end of params.");
                paramsEndParenthesesIndex = i;
                break;
            }
            
            // Skip param value marker
            else if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(":")) {
                //:OFF:log.debug("    Skip param value marker.");
                continue;
            }
            
            // Skip argument separator
            else if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(",")) {
                //:OFF:log.debug("    Skip argument separator.");
                continue;
            }
            
            else {
                //:OFF:log.debug("    pop paramExpr");
                Node paramExpr = this.stack.pop();
                //:OFF:log.debug("    popped paramExpr");
                // For lambda-ref, all params should be LambdaFunctionRefNamedArguments
                if (paramExpr instanceof LambdaFunctionRefNamedArgument) {
                    //:OFF:log.debug("    >>>> FunctionRefNamedArgument found!");
                }
                lambdaFunctionParams.add(paramExpr);
            }
        }
        
        //:OFF:log.debug("    Add the params");
        
        // Finally, add the params:
        Map<String, OperonValueConstraint> lfrnaConstraintMap = new HashMap<String, OperonValueConstraint>();
        
        for (Node arg : lambdaFunctionParams) {
            LambdaFunctionRefNamedArgument lfrna = (LambdaFunctionRefNamedArgument) arg;
            functionParamValueMap.put(lfrna.getArgumentName(), lfrna.getExprNode());
            lfrnaConstraintMap.put(lfrna.getArgumentName(), lfrna.getOperonValueConstraint());
        }
        
        //:OFF:log.debug(" lambdaFunctionParams size :: " + lambdaFunctionParams.size());
        
        //:OFF:log.debug(" functionParamValueMap size :: " + functionParamValueMap.size());
        
        lfnRef.setParams(functionParamValueMap);
        lfnRef.setParamConstraints(lfrnaConstraintMap);
        lfnRef.setLambdaExpr(lambdaExpr);
        this.stack.push(lfnRef);
        this.getStatementStack().pop(); // Remove functionStatement from the statementStack
        Statement previousStatement = this.getStatementStack().pop();
        //:OFF:log.debug("    >> Set currentStatement with previousStatement: " + previousStatement.getId());
        this.setCurrentStatement(previousStatement);
    }

    @Override
    public void exitLambda_function_ref_named_argument(OperonParser.Lambda_function_ref_named_argumentContext ctx) {
        //:OFF:log.debug("EXIT LambdaFunctionRefNamedArgument :: Stack size :: " + this.stack.size());
        LambdaFunctionRefNamedArgument lfrna = new LambdaFunctionRefNamedArgument(this.currentStatement);
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        
        // Extract the arg name and expr-value and set into frna:
        String argName = subNodes.get(0).getText();
        lfrna.setArgumentName(argName);
        //:OFF:log.debug("   argName :: " + argName);
        if (this.stack.peek() instanceof FunctionRefArgumentPlaceholder) {
            //:OFF:log.debug("  Found FunctionArgumentPlaceholder");
            FunctionRefArgumentPlaceholder frap = (FunctionRefArgumentPlaceholder) this.stack.pop(); // new FunctionRefArgumentPlaceholder(this.currentStatement);
            lfrna.setExprNode(frap);
            lfrna.setHasPlaceholder(true);
        }
        
        else {
            //:OFF:log.debug("   popping argValue");
            Node argValue = this.stack.pop();
            lfrna.setExprNode(argValue);
        }
        
        if (subNodes.get(subNodes.size() - 3).getText().charAt(0) == '<') {
            if (this.stack.size() > 0 && this.stack.peek() instanceof OperonValueConstraint) {
                OperonValueConstraint ovc = (OperonValueConstraint) this.stack.pop();
                ovc.setSourceCodeLineNumber(ctx.start.getLine());
                lfrna.setOperonValueConstraint(ovc);
            }
        }
        
        this.stack.push(lfrna);
    }
    
    @Override
    public void exitFunction_ref_named_argument(OperonParser.Function_ref_named_argumentContext ctx) {
        //:OFF:log.debug("EXIT FunctionRefNamedArgument :: Stack size :: " + this.stack.size());
        FunctionRefNamedArgument frna = new FunctionRefNamedArgument(this.currentStatement);
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        
        // Extract the arg name and expr-value and set into frna:
        String argName = subNodes.get(0).getText();
        frna.setArgumentName(argName);
        //:OFF:log.debug("   argName :: " + argName);
        if (this.stack.peek() instanceof FunctionRefArgumentPlaceholder) {
            //:OFF:log.debug("  Found FunctionArgumentPlaceholder");
            FunctionRefArgumentPlaceholder frap = (FunctionRefArgumentPlaceholder) this.stack.pop(); // new FunctionRefArgumentPlaceholder(this.currentStatement);
            frna.setExprNode(frap);
            frna.setHasPlaceholder(true);
        }
        
        else {
            //:OFF:log.debug("   popping argValue");
            Node argValue = this.stack.pop();
            frna.setExprNode(argValue);
        }
        
        this.stack.push(frna);
    }

    @Override
    public void enterLambda_function_call(OperonParser.Lambda_function_callContext ctx) {
        //:OFF:log.debug("ENTER LambdaFunction Stmt :: Stack size :: " + this.stack.size());
        Statement functionStatement = new FunctionStatement(this.getOperonContext());
        //:OFF:log.debug("    >> Set previousStatement: " + this.getCurrentStatement().getId());
        this.getStatementStack().push(this.getCurrentStatement());
        functionStatement.setPreviousStatement(this.getCurrentStatement());
        this.getStatementStack().push(functionStatement);
        this.setCurrentStatement(functionStatement);
    }

    @Override
    public void exitLambda_function_call(OperonParser.Lambda_function_callContext ctx) {
        //:OFF:log.debug("EXIT LambdaFunction Stmt :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        //:OFF:log.debug("    >> LAMBDA FUNCTION :: SUBNODES :: " + subNodesSize + ". " + subNodes.get(0).getText());

        // Collect params:
        java.util.Map<String, Node> functionParamValueMap = new HashMap<String, Node>();
        
        Node lambdaExpr = this.stack.pop();
        
        while (this.stack.size() > 0 && this.stack.peek() instanceof FunctionNamedArgument) {
            FunctionNamedArgument fna = (FunctionNamedArgument) this.stack.pop();
            functionParamValueMap.put(fna.getArgumentName(), fna.getArgumentValue());
        }

        LambdaFunctionCall lfnCall = new LambdaFunctionCall(this.currentStatement);
        lfnCall.setParams(functionParamValueMap); // TODO: should just set the FunctionNamedArguments
        lfnCall.setFunctionBodyExpr(lambdaExpr);
        this.stack.push(lfnCall);
        this.getStatementStack().pop(); // Remove functionStatement from the statementStack
        Statement previousStatement = this.getStatementStack().pop();
        //:OFF:log.debug("    >> Set currentStatement with previousStatement: " + previousStatement.getId());
        this.setCurrentStatement(previousStatement);
    }
    
    @Override
    public void exitFunction_ref(OperonParser.Function_refContext ctx) {
        //
        // TODO: the below into own structure! NOTE: does not have the '=>', which causes different indexing.
        //
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        //:OFF:log.debug("EXIT Function_ref :: SUBNODES :: " + subNodesSize + ". " + subNodes.get(0).getText() + ", stack size :: " + this.stack.size());
        
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
        
        //:OFF:log.debug("    >> FUNCTION :: " + functionName + ". Namespace: " + functionNamespace);

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
                //:OFF:log.debug("  FunctionRefArgumentPlaceholder DETECTED!!!");
                Node functionParam = this.stack.pop();
                functionParams.add(functionParam);
            }
            
            // Add params
            else {
                //:OFF:log.debug("adding param from stack");
                Node functionParam = this.stack.pop();
                functionParams.add(functionParam);
            }
        }
        //
        // TODO: the above into own structure!
        //
        
        String fullyQualifiedName = functionNamespace + ":" + functionName + ":" + functionParams.size();
        
        // Check if one of core-functions:
        if (this.getOperonContext().getFunctionStatements().get(fullyQualifiedName) == null &&
            CoreFunctionResolver.isCoreFunction(functionNamespace, functionName, functionParams)) {
            try {
                Node coreFunction = CoreFunctionResolver.getCoreFunction(functionNamespace, functionName, functionParams, this.currentStatement);
                FunctionRef fnRef = new FunctionRef(this.currentStatement);
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
            FunctionRef fnRef = new FunctionRef(this.currentStatement);
            //:OFF:log.debug("  FQ-name :: " + fullyQualifiedName);
            fnRef.setFunctionName(functionName);
            fnRef.setFunctionFQName(fullyQualifiedName);
            Collections.reverse(functionParams);
            //:OFF:log.debug("functionParams.size() = " + functionParams.size());
            fnRef.getParams().addAll(functionParams);
            this.stack.push(fnRef);
        }
    }
    
    @Override
    public void exitFunction_ref_curry(OperonParser.Function_ref_curryContext ctx) {
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        //:OFF:log.debug("EXIT Function_ref_curry :: stack size :: " + this.stack.size() + ", SUBNODES :: " + subNodesSize + ". " + subNodes.get(0).getText());
        
        List<Node> functionParams = new ArrayList<Node>();
        int startPos = 1;
        int paramsEndParenthesesIndex = 0;
        for (int i = startPos; i < subNodesSize - 1; i ++) {
            
            // Reach end of params
            if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(")")) {
                paramsEndParenthesesIndex = i;
                //:OFF:log.debug("Reached possible end, there might be more");
                //break;
                continue;
            }
            
            // Skip argument separator
            else if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(",")) {
                //:OFF:log.debug("Skipping argument separator");
                continue;
            }
            
            // Skip start of params
            else if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals("(")) {
                //:OFF:log.debug("Skipping arguments start");
                continue;
            }
            
            else if (subNodes.get(i) instanceof FunctionRefArgumentPlaceholder) {
                //:OFF:log.debug("  FunctionRefArgumentPlaceholder detected");
                Node functionParam = this.stack.pop();
                functionParams.add(functionParam);
            }
            
            // Add params
            else {
                //:OFF:log.debug("Function_ref_curry :: Adding param (pop stack)");
                Node functionParam = this.stack.pop();
                //:OFF:log.debug("FunctionParam type :: " + functionParam.getClass().getName());
                functionParams.add(functionParam);
            }
        }
        FunctionRefCurry curry = new FunctionRefCurry(this.currentStatement);
        Collections.reverse(functionParams);
        curry.getArguments().addAll(functionParams);
        //:OFF:log.debug("Function curry params size :: " + functionParams.size());
        
        //throw new RuntimeException("FunctionRefCurry not implemented yet!");
        this.stack.push(curry);
    }
    
    @Override
    public void exitFunction_call(OperonParser.Function_callContext ctx) {
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        int subNodesSize = subNodes.size();
        //:OFF:log.debug("EXIT Function_call :: SUBNODES :: " + subNodesSize + ". " + subNodes.get(0).getText());
        
        String functionNamespace = "";
        String functionName = "";
        
        // The last element is the function name, all preceding all part of the namespace:
        List<TerminalNode> namespaces = new ArrayList<TerminalNode>();
        //:OFF:log.debug("    >> FUNCTION :: " + functionName + ". Namespaces: " + namespaces);
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
        
        //:OFF:log.debug("    >> FUNCTION :: " + functionName + ". Namespace: " + functionNamespace);

        // Collect params: --> TODO: rename to arguments!
        
        List<Node> functionParams = new ArrayList<Node>();
        int startPos = 3 + (namespaces.size() - 1) * 2; // Was 5 ('ID' and  ':''). Now namespace might consist of multiple of these parts.
        // FIXME: namespace-logic was refactored so refactor this also!
        if (namespaces.size() == 1) {
            startPos = 3;
        }
        int paramsEndParenthesesIndex = 0;
        //:OFF:log.debug("FunctionCall :: looping arguments :: " + subNodesSize);
        
        for (int i = startPos; i < subNodesSize - 1; i ++) {
            
            // Reach end of params
            if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(")")) {
                //:OFF:log.debug("  >> )");
                paramsEndParenthesesIndex = i;
                break;
            }
            
            // Skip argument separator
            else if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals(",")) {
                //:OFF:log.debug("  >> ,");
                continue;
            }
            
            else if (subNodes.get(i) instanceof TerminalNode && subNodes.get(i).getText().equals("(")) {
                //:OFF:log.debug("  >> (");
                break;
            }
            
            // Add params
            else {
                //:OFF:log.debug("  >> Add params (pop stack)");
                Node functionParam = this.stack.pop();
                functionParams.add(functionParam);
            }
        }
        //:OFF:log.debug("  >> Looping done.");
        
        String fqName = functionNamespace + ":" + functionName + ":" + functionParams.size();
        
        //System.out.println("FQName :: " + fqName);
        
        //
        // FIXME: overriding these functions causes this not to work:
        //
        if (fqName.equals(":pos:0") || fqName.equals("core:pos:0")) {
            this.getOperonContext().getConfigs().setSupportPos(true);
        }
        
        else if (fqName.equals(":parent:0") || fqName.equals(":root:0") 
             || fqName.equals("core:parent:0") || fqName.equals("core:root:0")
             || fqName.equals(":valueKey:0") || fqName.equals("core:valueKey:0") || fqName.equals("object:valueKey:0") || fqName.equals("core:object:valueKey:0")
             || fqName.equals(":last:0") || fqName.equals("core:last:0") || fqName.equals("array:last:0") || fqName.equals("core:array:last:0")
             || fqName.equals(":last:1") || fqName.equals("core:last:1") || fqName.equals("array:last:1") || fqName.equals("core:array:last:1")
            ) {
            this.getOperonContext().getConfigs().setSupportParent(true);
        }
        
        else if (fqName.equals(":previous:0") || fqName.equals(":next:0") 
             || fqName.equals("core:previous:0") || fqName.equals("core:next:0")
             || fqName.equals("core:array:get:1") || fqName.equals("array:get:1")
            ) {
            this.getOperonContext().getConfigs().setSupportPos(true);
            this.getOperonContext().getConfigs().setSupportParent(true);
        }
        
        String possibleModuleName = ":" + functionName + ":" + functionParams.size();
        
        // 
        //System.out.println("CHECK FUNCTION: ns=[" + functionNamespace + "], fq=[" + fqName + "]");
        // 
        // If function is found from Import:ed modules, then that function should be used instead
        // Check if core-function not overridden and if one of core-functions:
        // 
        if (CoreFunctionResolver.isCoreFunction(functionNamespace, functionName, functionParams) &&
            
            this.getOperonContext().getFunctionStatements().get(fqName) == null &&
            
            (
                functionNamespace.isEmpty() == true ||
                this.getOperonContext().getModules().get(functionNamespace) == null ||
                (
                    this.getOperonContext().getModules().get(functionNamespace) != null && 
                    this.getOperonContext().getModules().get(functionNamespace).getFunctionStatements().get(possibleModuleName) == null
                )
            )
        ) {
            //System.out.println("WAS CORE");
            try {
                // NOTE: core-functions expect the params as List<Node>
                //System.out.println("Getting core-function");
                Node function = CoreFunctionResolver.getCoreFunction(functionNamespace, functionName, functionParams, this.currentStatement);
                //System.out.println("Getting core-function done");
                this.stack.push(function);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // User-defined function:
        else {
            //System.out.println("WAS USER-DEFINED: " + fqName);
            try {
                FunctionCall fnCall = new FunctionCall(this.currentStatement, fqName);
                Collections.reverse(functionParams);
                fnCall.getArguments().addAll(functionParams);
                this.stack.push(fnCall);
            } catch (Exception e) {
                throw new RuntimeException("ERROR :: Could not create FunctionCall. " + e.getMessage());
            }
        }
    }

    @Override
    public void exitJson_type_function_shortcut(OperonParser.Json_type_function_shortcutContext ctx) {
        //:OFF:log.debug("Exit Json_type_function_shortcut. Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = this.getContextChildNodes(ctx);
        //:OFF:log.debug("    SubNodes size :: " + subNodes.size());
        //:OFF:log.debug("    SubNodes :: " + subNodes);

        String jsonType = subNodes.get(0).toString();
        //System.out.println("Type :: ["+ jsonType + "], stack size :: " + this.stack.size());
        
        //:OFF:log.debug(jsonType);
    
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
                throw new RuntimeException("Error: OperonCompiler: could not create type-function.");
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
                throw new RuntimeException("Error: OperonCompiler could not create type-function for Boolean.");
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
                throw new RuntimeException("Error: OperonCompiler could not create type-function for Lambda.");
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
                throw new RuntimeException("Error: OperonCompiler could not create type-function for Function.");
            }
        }
        
        else {
            throw new RuntimeException("Error: OperonCompiler could not create type-function for unknown type.");
        }
    }
    
    @Override
    public void exitThrow_exception(OperonParser.Throw_exceptionContext ctx) {
        //:OFF:log.debug("EXIT Throw_exception :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        Node exceptionValue = this.stack.pop();
        ThrowException throwExceptionNode = new ThrowException(this.currentStatement);
        throwExceptionNode.setExceptionValue(exceptionValue);
        this.stack.push(throwExceptionNode);
    }

    @Override
    public void exitTry_catch(OperonParser.Try_catchContext ctx) {
        //:OFF:log.debug("EXIT Try_catch :: Stack size :: " + this.stack.size());
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
    public void enterException_stmt(OperonParser.Exception_stmtContext ctx) {
        //:OFF:log.debug("ENTER Exception-stmt :: Stack size :: " + this.stack.size());
        Statement exceptionStatement = new ExceptionStatement(this.getOperonContext());
        exceptionStatement.setId("ExceptionStatement");
        //this.setCurrentStatement(exceptionStatement);
        this.setPreviousStatementForStatement(exceptionStatement); // Why was this commented out?
    }
    
    @Override
    public void exitException_stmt(OperonParser.Exception_stmtContext ctx) {
        //:OFF:log.debug("EXIT Exception_stmt :: Stack size :: " + this.stack.size());
        List<ParseTree> subNodes = getContextChildNodes(ctx);
        Node exceptionHandlerExpr = this.stack.pop();
        this.currentStatement.setNode(exceptionHandlerExpr);
        
        // NOT USED:
        //ValueRef vRef = (ValueRef) this.stack.pop();
        
        ExceptionHandler eh = new ExceptionHandler();
        
        // NOT USED:
        //eh.setValueRef(vRef);
        
        eh.setExceptionHandlerExpr(exceptionHandlerExpr);
        
        // Global, is this needed..?
        //Statement exceptionStatement = this.getCurrentStatement();
        
        if (this.getCurrentStatement().getPreviousStatement() instanceof FromStatement) {
            // Set the global ExceptionHandler:
            //:OFF:log.debug("Setting exceptionHandler to FromStatement");
            
            // REMOVE THIS, EH should create it!
            //this.getOperonContext().setExceptionStatement(exceptionStatement);
            
            this.getOperonContext().setExceptionHandler(eh);
        }
        else if (this.getCurrentStatement().getPreviousStatement() instanceof LetStatement) {
            ((LetStatement) this.getCurrentStatement().getPreviousStatement()).setExceptionHandler(eh);
        }
        else if (this.getCurrentStatement().getPreviousStatement() instanceof SelectStatement) {
            //:OFF:log.debug("Setting exceptionHandler to SelectStatement");
            ((SelectStatement) this.getCurrentStatement().getPreviousStatement()).setExceptionHandler(eh);
        }
        else if (this.getCurrentStatement().getPreviousStatement() instanceof FunctionStatement) {
            ((FunctionStatement) this.getCurrentStatement().getPreviousStatement()).setExceptionHandler(eh);
        }
        
        this.restorePreviousScope();
    }
    
    @Override
    public void exitAggregate_expr(OperonParser.Aggregate_exprContext ctx) {
        //:OFF:log.debug("EXIT aggregate_expr :: Stack size :: " + this.stack.size());
        
        String aggregateId = Integer.toString(aggregateIndex) + RandomUtil.randomAlphaNumeric(5);
        Aggregate aggregate = new Aggregate(this.getCurrentStatement(), aggregateId);
        aggregateIndex = aggregateIndex + 1;
        ObjectType configs = (ObjectType) this.stack.pop();
        aggregate.setConfigs(configs);
        aggregate.setCorrelationId("\"default\"");
        
        Node firePredicate = null;
        Node aggregateFunction = null;
        boolean hasTimeout = false;
        
        try {
            for (PairType pair : configs.getPairs()) {
                
                String key = pair.getKey().toLowerCase();
                //System.out.println("key=" + key);
                if (key.equals("\"correlationid\"")) {
                    Node correlationIdExpr = pair.getValue();
                    aggregate.setCorrelationIdExpr(correlationIdExpr);
                }
                
                else if (key.equals("\"firepredicate\"")) {
                    firePredicate = pair.getValue();
                    aggregate.setFirePredicate(firePredicate);
                }
                
                else if (key.equals("\"aggregatefunction\"")) {
                    aggregateFunction = pair.getValue();
                    aggregate.setAggregateFunction(aggregateFunction);
                    //System.out.println("SET aggregateFunction");
                }
                
                else if (key.equals("\"timeoutmillis\"")) {
                    //System.out.println("SET timeout");
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
        //:OFF:log.debug("    >> Set previousStatement: " + this.getCurrentStatement().getId());
        this.getStatementStack().push(this.getCurrentStatement());
        // If statement is LetStatement, and currentStatement is also LetStatement, then this would
        // try to set the statement (Let) as previous statement for current (Let), which is not ok.
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
        //:OFF:log.debug("    >> Set currentStatement with previousStatement: " + previousStatement.getId());
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
