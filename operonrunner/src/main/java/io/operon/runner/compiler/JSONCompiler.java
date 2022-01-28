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

import io.operon.runner.OperonContext;
import io.operon.jsonparser.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.node.*;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Stack;
import java.util.Collections;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 * This class listens the events that parser emits.
 * Goal is to return the parsed OperonValue.
 * NOTE that this is different from Operon's compiler, i.e. expressions are not supported
 * and the code is different!
 *
 */
public class JSONCompiler extends JSONBaseListener {
    private static Logger log = LogManager.getLogger(JSONCompiler.class);
    private OperonContext operonContext;
    private Stack<Statement> statementStack;
    private Stack<Node> stack;
    private OperonValue initialCurrentValue; // TODO: refactor this away (in the fromInputSource)?
    private Statement currentStatement;
    private Long startTime;
    
    public JSONCompiler() {
        super();
        this.stack = new Stack<Node>();
        this.statementStack = new Stack<Statement>();
        currentStatement = new DefaultStatement(operonContext);
    }
    
    @Override
    public void visitErrorNode(ErrorNode node) { 
        System.err.println("JSON: syntax error :: " + node.toStringTree());
        throw new RuntimeException("syntax error :: " + node.toStringTree());
    }

    public void setoperonContext(OperonContext operonContext) {
        this.operonContext = operonContext;
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
    
    @Override
    public void exitJson(JSONParser.JsonContext ctx) {
        log.debug("EXIT Json :: Stack size :: " + this.stack.size());
        log.debug("    >> TRYING TO PEEK");
        Node n = this.stack.peek();
        assert (n != null): "exitJson :: null value from stack";
        //
        // NOTE: cannot print the peeked result, because it would require that value
        //       was already evaluated, which it is not at this stage.
        //System.out.println("    >> PEEK RESULT: " + n.toString()); // was n.toString()
        if (this.currentStatement != null) {
            this.currentStatement.setNode(n);
            log.debug("    >> NODE SET");
        } else {
            log.debug("    >> CURRENT STATEMENT WAS NULL !!!!!!!");
        }
    }
    
    @Override
    public void exitValue(JSONParser.ValueContext ctx) {
        log.debug("EXIT Json_value :: Stack size :: " + this.stack.size());
        OperonValue jsonValue = new OperonValue(this.currentStatement);
        List<ParseTree> subNodes = this.getContextChildNodes(ctx);
        
        log.debug("CHILD NODES :: " + subNodes.size() + " :: CHILD NODE [0] TYPE :: " + subNodes.get(0).getClass().getName());
        
        if (subNodes.size() > 0 
            && subNodes.get(0) instanceof RuleNode 
            && subNodes.get(0) instanceof JSONParser.ObjContext) {
            
            jsonValue.setValue(this.stack.pop()); // set ObjectType from stack
        }

        else if (subNodes.size() > 0 
            && subNodes.get(0) instanceof RuleNode 
            && subNodes.get(0) instanceof JSONParser.ArrayContext) {
            log.debug("SETTING JSON VALUE --> JSON ARRAY.");
            Node value = this.stack.pop();
            if (value == null) {
                log.error("WARNING:: POPPED NULL VALUE!!!");
            }
            jsonValue.setValue(value); // set ArrayType from stack   
        }

        else if (subNodes.size() > 0 && subNodes.get(0) instanceof TerminalNode) {
            TerminalNode token = ctx.getToken(JSONParser.STRING, 0);
            
            if (token != null) {
                StringType sNode = new StringType(this.currentStatement);
                String symbolText = token.getSymbol().getText();
                log.debug("TerminalNode. Text :: " + symbolText);
                sNode.setValue(symbolText);
                jsonValue.setValue(sNode);
            }
            
            else if ( (token = ctx.getToken(JSONParser.NUMBER, 0)) != null) {
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
            
            else if ( (token = ctx.getToken(JSONParser.FALSE, 0)) != null) {
                FalseType jsonFalse = new FalseType(this.currentStatement);
                jsonValue.setValue(jsonFalse);
            }
            
            else if ( (token = ctx.getToken(JSONParser.TRUE, 0)) != null) {
                TrueType jsonTrue = new TrueType(this.currentStatement);
                jsonValue.setValue(jsonTrue);
            }
            
            else if ( (token = ctx.getToken(JSONParser.NULL, 0)) != null) {
                NullType jsonNull = new NullType(this.currentStatement);
                jsonValue.setValue(jsonNull);
            }

            else if ( (token = ctx.getToken(JSONParser.RAW_STRING, 0)) != null) {
                RawValue raw = new RawValue(this.currentStatement);
                // substring is for cutting out the ' parts, which are used for BinaryString
                String rawStr = token.getSymbol().getText().substring(1, token.getSymbol().getText().length() - 1);
                rawStr = rawStr.replaceAll("\\\\`", "`");
                byte[] valueBytes = rawStr.getBytes(StandardCharsets.UTF_8);
                raw.setValue(valueBytes);
                jsonValue.setValue(raw);
            }

            else if ( (token = ctx.getToken(JSONParser.SINGLE_QUOTED_STRING, 0)) != null) {
                StringType sNode = new StringType(this.currentStatement);
                String symbolText = token.getSymbol().getText();
                log.debug("TerminalNode: String. Text :: " + symbolText);
                //System.out.println("TerminalNode: String. Text :: " + symbolText);
                symbolText = symbolText.substring(1, symbolText.length() - 1); // remove single quotes
                symbolText = symbolText.replaceAll("\"", "\\\\\"");
                symbolText = "\"" + symbolText + "\"";
                //System.out.println("TerminalNode: String. SET Text :: " + symbolText);
                sNode.setValue(symbolText);
                jsonValue.setValue(sNode);
            }

            else if ( (token = ctx.getToken(JSONParser.MULTILINE_STRING, 0)) != null) {
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

            else if ( (token = ctx.getToken(JSONParser.MULTILINE_PADDED_STRING, 0)) != null) {
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

            else if ( (token = ctx.getToken(JSONParser.MULTILINE_PADDED_LINES_STRING, 0)) != null) {
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

            else if ( (token = ctx.getToken(JSONParser.EMPTY_VALUE, 0)) != null) {
                EmptyType jsonEmpty = new EmptyType(this.currentStatement);
                jsonValue.setValue(jsonEmpty);
            }

        }
        
        this.stack.push(jsonValue);
    }
    
    @Override
    public void exitArray(JSONParser.ArrayContext ctx) {
        log.debug("EXIT Json_array :: Stack size :: " + this.stack.size());
        List<ParseTree> ruleChildNodes = this.getContextChildRuleNodes(ctx);
        int childCount = ruleChildNodes.size();
        
        ArrayType jsonArray = new ArrayType(this.currentStatement);
        
        // For reversing traversed nodes
        List<Node> jsonValues = new ArrayList<Node>(); // Refactor: OperonValue -> Node
        
        for (int i = 0; i < childCount; i ++) {
            Node jsonValue = (Node) this.stack.pop();
            jsonValues.add(jsonValue);
        }
        
        try {
            // For reversing traversed nodes
            for (int i = jsonValues.size() - 1; i >= 0; i --) {
                Node jsonValue = jsonValues.get(i);
                jsonArray.addValue(jsonValue);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        
        this.stack.push(jsonArray);
    }
    
    @Override
    public void exitObj(JSONParser.ObjContext ctx) {
        log.debug("EXIT Json_obj :: Stack size :: " + this.stack.size());
        List<ParseTree> ruleChildNodes = this.getContextChildRuleNodes(ctx);
        int pairsCount = ruleChildNodes.size();
        
        ObjectType jsonObj = new ObjectType(this.currentStatement);
        
        List<PairType> jsonPairs = new ArrayList<PairType>();
        
        for (int i = 0; i < pairsCount; i ++) {
            PairType jsonPair = (PairType) this.stack.pop();
            jsonPairs.add(jsonPair);
        }
        
        for (int i = jsonPairs.size() - 1; i >= 0; i --) {
            PairType jsonPair = jsonPairs.get(i);
            try {
                jsonObj.addPair(jsonPair);
            } catch (OperonGenericException oge) {
                throw new RuntimeException(oge.getMessage());
            }
        }
        
        this.stack.push(jsonObj);
    }
    
    @Override
    public void exitPair(JSONParser.PairContext ctx) {
        log.debug("EXIT Json_pair :: Stack size :: " + this.stack.size());
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
        value.setValue(this.stack.pop());
        
        List<ParseTree> subNodes = this.getContextChildNodes(ctx);
        if (subNodes.size() == 4) {
            jsonPair.setOperonValueConstraint((OperonValueConstraint) this.stack.pop());
        }
        
        log.debug("   PairType subNodes :: " + subNodes.size());
        
        jsonPair.setPair(key, value);
        this.stack.push(jsonPair);
    }
    
    //
    // Helper functions
    //
    
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
