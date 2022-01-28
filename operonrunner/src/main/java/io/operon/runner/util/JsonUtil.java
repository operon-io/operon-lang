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

package io.operon.runner.util;

import java.util.Map;
import java.io.IOException;

// import ANTLR's runtime libraries
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import io.operon.parser.*;
import io.operon.jsonparser.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.operon.runner.node.*;
import io.operon.runner.node.type.*;
import io.operon.runner.compiler.OperonCompiler;
import io.operon.runner.compiler.JSONCompiler;
import io.operon.runner.Context;
import io.operon.runner.EmptyContext;
import io.operon.runner.OperonContext;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 * JsonUtil contains tools to copy the Json-value.
 * 
 */
public class JsonUtil {
    private static Logger log = LogManager.getLogger(JsonUtil.class);
    
    public static ArrayType copyArray(ArrayType json) throws OperonGenericException {
        log.debug("JsonUtil :: copyOperonValue");
        //System.out.println("JsonUtil :: copyOperonValue, parentKey = " + json.getParentKey());
        //int position = json.getPosition();
        //String attr_parentKey = json.getParentKey();
        //OperonValue attr_parentObj = json.getParentObj();
        Statement stmt = new DefaultStatement(OperonContext.emptyContext);
        //Statement stmt = json.getStatement();
        
        List<Node> arrayNodes = null;
        
        if (json instanceof ArrayType) {
            //System.out.println("Copy array1");
            arrayNodes = ((ArrayType) json).getValues();
        }
        
        else if (json.getValue() instanceof ArrayType) {
            //System.out.println("Copy array2");
            arrayNodes = ((ArrayType) json.getValue()).getValues();
        }

        else {
            ErrorUtil.createErrorValueAndThrow(stmt, "COPY ARRAY", "TYPE", "Unknown: " + json.getClass().getName());
            return null;
        }

        ArrayType result = new ArrayType(stmt);

        for (int i = 0; i < arrayNodes.size(); i ++) {
            Node n = arrayNodes.get(i);
            
            if ((n instanceof OperonValue) == false) {
                //System.out.println("  Copy non-OperonValue instance");
                Node copyValue = copyEvaluatedNode(n, true);
                //System.out.println("  Copy OK");
                result.addValue(copyValue);
            }
            
            else {
                //System.out.println("  Copy else: " + n);
                OperonValue copyValue = JsonUtil.copyOperonValueWithArray((OperonValue) n);
                result.addValue(copyValue);
            }
        }
        
        return result;
    }
    
    public static OperonValue copyOperonValue(OperonValue json) throws OperonGenericException {
        Statement stmt = new DefaultStatement(OperonContext.emptyContext);
        return JsonUtil.copyOperonValue(json, stmt, false);
    }
    
    public static OperonValue copyOperonValueWithArray(OperonValue json) throws OperonGenericException {
        Statement stmt = new DefaultStatement(OperonContext.emptyContext);
        return JsonUtil.copyOperonValue(json, stmt, true);
    }
    
    public static OperonValue copyOperonValue(OperonValue json, Statement stmt, boolean deepCopyArrays) throws OperonGenericException {
        log.debug("JsonUtil :: copyOperonValue");
        //System.out.println("JsonUtil :: copyOperonValue, parentKey = " + json.getParentKey());
        if (stmt == null) {
            //stmt = json.getStatement();
            stmt = new DefaultStatement(OperonContext.emptyContext);
        }
        
        if (json instanceof ObjectType) {
            //System.out.println("copy ObjectType 1");
            List<PairType> pairs = ((ObjectType) json).getPairs();
            ObjectType result = new ObjectType(stmt);
            log.debug("  >> copy ObjectType instance, pairs :: " + pairs.size());

            for (PairType pair : pairs) {
                PairType copyPair = new PairType(stmt);
                copyPair.setIsEmptyValue(pair.isEmptyValue());
                copyPair.setPreventReEvaluation(pair.getPreventReEvaluation());
                copyPair.setUnboxed(pair.getUnboxed());

                // Copy the object-value constraints:
                if (pair.getOperonValueConstraint() != null) {
                    copyPair.setOperonValueConstraint(pair.getOperonValueConstraint());
                }
                
                OperonValue copyPairValue = pair.getValue().copy(deepCopyArrays);
                copyPairValue.setUnboxed(pair.getValue().getUnboxed());
                copyPairValue.setPreventReEvaluation(pair.getValue().getPreventReEvaluation());
                copyPair.setPair(pair.getKey(), copyPairValue);
                copyPair.setEvaluatedValue(pair.getEvaluatedValue().copy());
                result.addPair(copyPair);
            }
            result.setUnboxed(((ObjectType) json).getUnboxed());
            result.setPreventReEvaluation(((ObjectType) json).getPreventReEvaluation());
            return result;
        }
        
        else if (json.getValue() instanceof ObjectType) {
            //System.out.println("copy ObjectType 2");
            List<PairType> pairs = ((ObjectType) json.getValue()).getPairs();
            ObjectType result = new ObjectType(stmt);
            log.debug("  >> copy ObjectType evaluated instance, pairs :: " + pairs.size());
            
            for (PairType pair : pairs) {
                PairType copyPair = new PairType(stmt);
                copyPair.setIsEmptyValue(pair.isEmptyValue());
                copyPair.setPreventReEvaluation(pair.getPreventReEvaluation());
                copyPair.setUnboxed(pair.getUnboxed());
                
                // Copy the object-value constraints:
                if (pair.getOperonValueConstraint() != null) {
                    copyPair.setOperonValueConstraint(pair.getOperonValueConstraint());
                }
                
                OperonValue copyPairValue = pair.getValue().copy(deepCopyArrays);
                copyPairValue.setUnboxed(pair.getValue().getUnboxed());
                copyPairValue.setPreventReEvaluation(pair.getValue().getPreventReEvaluation());
                copyPair.setPair(pair.getKey(), copyPairValue);
                copyPair.setEvaluatedValue(pair.getEvaluatedValue().copy());
                result.addPair(copyPair);
            }
            result.setUnboxed(((ObjectType) json.getValue()).getUnboxed());
            result.setPreventReEvaluation(((ObjectType) json.getValue()).getPreventReEvaluation());
            return result;
        }
        
        else if (json instanceof StringType) {
            StringType result = new StringType(stmt);
            result.setValue(((StringType) json).getStringValue());
            result.setUnboxed(((StringType) json).getUnboxed());
            result.setPreventReEvaluation(((StringType) json).getPreventReEvaluation());
            return result;
        }
        
        else if (json.getValue() instanceof StringType) {
            StringType result = new StringType(stmt);
            result.setValue(((StringType) json.getValue()).getStringValue());
            result.setUnboxed(((StringType) json.getValue()).getUnboxed());
            result.setPreventReEvaluation(((StringType) json.getValue()).getPreventReEvaluation());
            return result;
        }
        
        else if (json instanceof RawValue) {
            //System.out.println("COPY RawValue");
            RawValue bvNode = new RawValue(stmt);
            bvNode.setValue(((RawValue) json).getBytes());
            return bvNode;
        }
        
        else if (json.getValue() instanceof RawValue) {
            RawValue bvNode = new RawValue(stmt);
            bvNode.setValue(((RawValue) json.getValue()).getBytes());
            return bvNode;
        }
        
        else if (json instanceof StreamValue) {
            //System.out.println("COPY StreamValue");
            StreamValue svNode = new StreamValue(stmt);
            svNode.setValue(((StreamValue) json).getStreamValueWrapper());
            return svNode;
        }
        
        else if (json.getValue() instanceof StreamValue) {
            StreamValue svNode = new StreamValue(stmt);
            svNode.setValue(((StreamValue) json.getValue()).getStreamValueWrapper());
            return svNode;
        }
        
        else if (json instanceof NumberType) {
            log.debug("JsonUtil :: copy NumberType 1");
            NumberType nnode = new NumberType(stmt);
            nnode.setDoubleValue(((NumberType) json).getDoubleValue());
            nnode.setPrecision( ((NumberType) json.getValue()).getPrecision() );
            return nnode;
        }
        
        else if (json.getValue() instanceof NumberType) {
            log.debug("JsonUtil :: copy NumberType 2");
            NumberType nnode = new NumberType(stmt);
            nnode.setDoubleValue(((NumberType) json.getValue()).getDoubleValue());
            nnode.setPrecision( ((NumberType) json.getValue()).getPrecision() );
            //copyBindings((OperonValue) json.getValue(), nnode);
            return nnode;
        }
        
        else if (json instanceof NullType) {
            NullType nullNode = new NullType(stmt);
            return nullNode;
        }
        
        else if (json.getValue() instanceof NullType) {
            NullType nullNode = new NullType(stmt);
            return nullNode;
        }
        
        else if (json instanceof TrueType) {
            TrueType tNode = new TrueType(stmt);
            return tNode;
        }
        
        else if (json.getValue() instanceof TrueType) {
            TrueType tNode = new TrueType(stmt);
            return tNode;
        }
        
        else if (json instanceof FalseType) {
            FalseType fNode = new FalseType(stmt);
            return fNode;
        }

        else if (json.getValue() instanceof FalseType) {
            FalseType fNode = new FalseType(stmt);
            return fNode;
        }
        
        else if (json instanceof EmptyType) {
            EmptyType jeNode = new EmptyType(stmt);
            return jeNode;
        }

        else if (json.getValue() instanceof EmptyType) {
            //System.out.println("Copy EmptyType2");
            EmptyType jeNode = new EmptyType(stmt);
            return jeNode;
        }
        
        else if (json instanceof EndValueType) {
            EndValueType jendNode = new EndValueType(stmt);
            return jendNode;
        }

        else if (json.getValue() instanceof EndValueType) {
            //System.out.println("Copy EndValueType2");
            EndValueType jendNode = new EndValueType(stmt);
            return jendNode;
        }
        
        else if (json instanceof ErrorValue) {
            ErrorValue evNode = new ErrorValue(stmt);
            ErrorValue fromErrorValue = (ErrorValue) json;
            evNode.setCode(fromErrorValue.getCode());
            evNode.setType(fromErrorValue.getType());
            evNode.setMessage(fromErrorValue.getMessage());
            evNode.setErrorJson(fromErrorValue.getErrorJson());
            return evNode;
        }

        else if (json.getValue() instanceof ErrorValue) {
            //System.out.println("Copy ErrorValue2");
            ErrorValue evNode = new ErrorValue(stmt);
            ErrorValue fromErrorValue = (ErrorValue) json.getValue();
            evNode.setCode(fromErrorValue.getCode());
            evNode.setType(fromErrorValue.getType());
            evNode.setMessage(fromErrorValue.getMessage());
            evNode.setErrorJson(fromErrorValue.getErrorJson());
            return evNode;
        }
        
        else if (json instanceof io.operon.runner.node.type.Path) {
            io.operon.runner.node.type.Path fromValue = (io.operon.runner.node.type.Path) json;
            io.operon.runner.node.type.Path pathNode = fromValue.copy();
            return pathNode;
        }

        else if (json.getValue() instanceof io.operon.runner.node.type.Path) {
            //System.out.println("Copy io.operon.runner.model.Path2");
            io.operon.runner.node.type.Path fromValue = (io.operon.runner.node.type.Path) json.getValue();
            io.operon.runner.node.type.Path pathNode = fromValue.copy();
            return pathNode;
        }
        
        else if (json.getValue() instanceof UnaryNode) {
            OperonValue result = ((UnaryNode) json.getValue()).getEvaluatedValue().copy(deepCopyArrays);
            return result;
        }

        else if (json.getValue() instanceof BinaryNode) {
            OperonValue result = ((BinaryNode) json.getValue()).getEvaluatedValue().copy(deepCopyArrays);
            return result;
        }
        
        else if (json.getValue() instanceof MultiNode) {
            log.debug("  >> copy MultiNode");
            OperonValue result = ((MultiNode) json.getValue()).getEvaluatedValue().copy(deepCopyArrays);
            return result;
        }

        else if (json instanceof FunctionRef) {
            log.debug("  >> copy FunctionRef instance");
            OperonValue result = json;
            return result;
        }
        
        else if (json.getValue() instanceof FunctionRef) {
            log.debug("  >> copy FunctionRef");
            OperonValue result = (OperonValue) json.getValue();
            return result;
        }
        
        else if (json instanceof LambdaFunctionRef) {
            log.debug("  >> copy LambdaFunctionRef instance");
            OperonValue result = (OperonValue) json;
            return result;
        }
        
        else if (json.getValue() instanceof LambdaFunctionRef) {
            log.debug("  >> copy LambdaFunctionRef");
            OperonValue result = (OperonValue) json.getValue();
            return result;
        }
        
        else if (json.getValue() instanceof FunctionCall) {
            OperonValue result = ((FunctionCall) json.getValue()).getEvaluatedValue().copy(deepCopyArrays);
            return result;
        }
        
        else if (json.getValue() instanceof LambdaFunctionCall) {
            OperonValue result = ((LambdaFunctionCall) json.getValue()).getEvaluatedValue().copy(deepCopyArrays);
            return result;
        }
        
        else if (json instanceof ArrayType) {
            if (deepCopyArrays) {
                return JsonUtil.copyArray((ArrayType) json);
            }
            else {
                return (ArrayType) json;
            }
        }
        
        else if (json.getValue() instanceof ArrayType) {
            if (deepCopyArrays) {
                return JsonUtil.copyArray((ArrayType) json.getValue());
            }
            else {
                return (ArrayType) json.getValue();
            }
        }
        
        else if (json.getValue() instanceof OperonValue) {
            log.debug("  >> unbox json");
            OperonValue copyFrom = (OperonValue) json.getValue();
            //System.out.println("    >>>COPY From parentKey :: " + json.getParentObj());
            OperonValue copyResult = JsonUtil.copyOperonValue(copyFrom);
            return copyResult;
        }

        return ErrorUtil.createErrorValueAndThrow(stmt, "COPY", "TYPE", "Unknown: " + json.getClass().getName());
    }
    
    public static Node copyEvaluatedNode(Node n, boolean deepCopyArrays) throws OperonGenericException {
        //System.out.println("Copy EvaluatedNode");
        assert (n != null) : "JsonUtil :: copyEvaluatedNode :: node was null";
        log.debug("JsonUtil :: copyEvaluatedNode :: " + n.getClass().getName());
        if (n instanceof UnaryNode) {
            //System.out.println("Copy UnaryNode");
            Node result = null;
            OperonValue evaluatedValue = ((UnaryNode) n).getEvaluatedValue();
            if (evaluatedValue != null) {
                result = (OperonValue) evaluatedValue.copy(deepCopyArrays);
                return result;
            }
            else {
                /*
                try {
                    Node copyValue = AbstractNode.deepCopyNode(n);
                    return copyValue;
                } catch (java.io.IOException | ClassNotFoundException e) {
                    throw new OperonGenericException(e.getMessage());
                }*/
                return n;
            }
        }

        else if (n instanceof BinaryNode) {
            //System.out.println("Copy BinaryNode");
            Node result = null;
            OperonValue evaluatedValue = ((BinaryNode) n).getEvaluatedValue();
            if (evaluatedValue != null) {
                //System.out.println("  >> Evaluated value found ");
                result = (OperonValue) evaluatedValue.copy(deepCopyArrays);
                return result;
            }
            else {
                /*
                try {
                    System.out.println("  >> Value was not evaluated, deep-copying the Node");
                    Node copyValue = AbstractNode.deepCopyNode(n);
                    System.out.println("  >> Node copied");
                    return copyValue;
                } catch (java.io.IOException | ClassNotFoundException e) {
                    throw new OperonGenericException(e.getMessage());
                }*/
                return n;
            }
        }
        
        else if (n instanceof MultiNode) {
            //System.out.println("Copy MultiNode");
            Node result = null;
            OperonValue evaluatedValue = ((MultiNode) n).getEvaluatedValue();
            if (evaluatedValue != null) {
                result = (OperonValue) evaluatedValue.copy(deepCopyArrays);
                return result;
            }
            else {
                /*
                try {
                    Node copyValue = AbstractNode.deepCopyNode(n);
                    return copyValue;
                } catch (java.io.IOException | ClassNotFoundException e) {
                    throw new OperonGenericException(e.getMessage());
                }*/
                return n;
            }
        }
        
        else if (n instanceof FunctionCall) {
            Node result = null;
            OperonValue evaluatedValue = ((FunctionCall) n).getEvaluatedValue();
            if (evaluatedValue != null) {
                result = (OperonValue) evaluatedValue.copy(deepCopyArrays);
                return result;
            }
            else {
                /*
                try {
                    Node copyValue = AbstractNode.deepCopyNode(n);
                    return copyValue;
                } catch (java.io.IOException | ClassNotFoundException e) {
                    throw new OperonGenericException(e.getMessage());
                }*/
                return n;
            }
        }
        
        else if (n instanceof LambdaFunctionCall) {
            Node result = null;
            OperonValue evaluatedValue = ((LambdaFunctionCall) n).getEvaluatedValue();
            if (evaluatedValue != null) {
                result = (OperonValue) evaluatedValue.copy(deepCopyArrays);
                return result;
            }
            else {
                /*
                try {
                    Node copyValue = AbstractNode.deepCopyNode(n);
                    return copyValue;
                } catch (java.io.IOException | ClassNotFoundException e) {
                    throw new OperonGenericException(e.getMessage());
                }*/
                return n;
            }
        }
        
        return ErrorUtil.createErrorValueAndThrow(n.getStatement(), "COPY", "TYPE", "Unknown: " + n.getClass().getName());
    }
    
    //
    // TODO: More tests for comparing objects, comparison logic not implemented yet.
    //
    public static boolean isIdentical(OperonValue a, OperonValue b) throws OperonGenericException {
        try {
            // Try to unbox value (if boxed)
            a = (OperonValue) a.evaluate();
            b = (OperonValue) b.evaluate();
            
            if (a instanceof NumberType && b instanceof NumberType) {
                if ( ((NumberType) a.getValue()).getDoubleValue() == ((NumberType) b.getValue()).getDoubleValue() ) {
                    return true;
                }
                
                else {
                    return false;
                }
            }
            
            else if (a instanceof ArrayType && b instanceof ArrayType) {
                ArrayType aa = (ArrayType) a.evaluate();
                ArrayType bb = (ArrayType) b.evaluate();
                if (aa.getValues().size() != bb.getValues().size()) {
                    return false;
                }
                
                for (int i = 0; i < aa.getValues().size(); i ++) {
                    if (isIdentical((OperonValue) aa.getValues().get(i), (OperonValue) bb.getValues().get(i)) == false) {
                        return false;
                    }
                }
                return true;
            }
        
            else if (a instanceof ObjectType && b instanceof ObjectType) {
                ObjectType aObj = (ObjectType) a;
                ObjectType bObj = (ObjectType) b;
                List<PairType> aObjPairs = aObj.getPairs();
                List<PairType> bObjPairs = bObj.getPairs();
                if (aObjPairs.size() != bObjPairs.size()) {
                    return false;
                }
                // Sort the keys (now done with findPairByKey) and compare each key, recursively.
                // Loop throgh a:
                for (PairType aPair : aObjPairs) {
                    PairType bPair = JsonUtil.findPairByKey(aPair.getKey(), bObjPairs);
                    if (bPair == null) {
                        return false;
                    }
                    boolean pairResult = JsonUtil.isIdentical(aPair.getValue(), bPair.getValue());
                    if (pairResult == false) {
                        return false;
                    }
                }
                
                return true;
            }
        
            else if (a instanceof TrueType && b instanceof TrueType) {
                return true;
            }
        
            else if (a instanceof FalseType && b instanceof FalseType) {
                return true;
            }
        
            else if (a instanceof NullType && b instanceof NullType) {
                return true;
            }
        
            else if (a instanceof EmptyType && b instanceof EmptyType) {
                return true;
            }
        
            else if (a.toString().equals(b.toString())) {
                return true;
            }
            
            else {
                return false;
            }
        } catch (Exception e) {
            //
            // This actually throws an exception, return statement is required for parsing.
            //
            ErrorUtil.createErrorValue(a.getStatement(), "IDENTICAL", "ERROR", "Unknown: " + e.getMessage());
            return false;
        }
    }
    
    private static PairType findPairByKey(String findByKey, List<PairType> findFrom) {
        for (PairType p : findFrom) {
            if (p.getKey().equals(findByKey)) {
                return p;
            }
        }
        return null;
    }
    
    private static void copyBindings(OperonValue source, OperonValue target) {
        Map<String, Operator> bindings = source.getBindings();
        target.getBindings().putAll(bindings);
        target.setDoBindings(source.getDoBindings());
    }
    
    //
    // lightweight-parser for other types than Array and Object
    // 
    public static OperonValue lwOperonValueFromString(String json) throws OperonGenericException {
        if (json.isEmpty()) {
            try {
                Context jsonContext = new EmptyContext();
                Statement stmt = new DefaultStatement(jsonContext);
                EmptyType result = new EmptyType(stmt);
                return result;
            } catch (Exception e) {
                return ErrorUtil.createErrorValueAndThrow(null, "JSONPARSE", "ERROR", "Unknown: " + e.getMessage());
            }
        }
        
        // first char:
        char fc = json.charAt(0);
        if (fc == '"') {
            try {
                Context jsonContext = new EmptyContext();
                Statement stmt = new DefaultStatement(jsonContext);
                StringType result = new StringType(stmt);
                result.setValue(json);
                return result;
            } catch (Exception e) {
                return ErrorUtil.createErrorValueAndThrow(null, "JSONPARSE", "ERROR", "Unknown: " + e.getMessage());
            }
        }
        else if (fc == 't') {
            try {
                Context jsonContext = new EmptyContext();
                Statement stmt = new DefaultStatement(jsonContext);
                TrueType result = new TrueType(stmt);
                return result;
            } catch (Exception e) {
                return ErrorUtil.createErrorValueAndThrow(null, "JSONPARSE", "ERROR", "Unknown: " + e.getMessage());
            }
        }
        else if (fc == 'f') {
            try {
                Context jsonContext = new EmptyContext();
                Statement stmt = new DefaultStatement(jsonContext);
                FalseType result = new FalseType(stmt);
                return result;
            } catch (Exception e) {
                return ErrorUtil.createErrorValueAndThrow(null, "JSONPARSE", "ERROR", "Unknown: " + e.getMessage());
            }
        }
        else if (fc == 'n') {
            try {
                Context jsonContext = new EmptyContext();
                Statement stmt = new DefaultStatement(jsonContext);
                NullType result = new NullType(stmt);
                return result;
            } catch (Exception e) {
                return ErrorUtil.createErrorValueAndThrow(null, "JSONPARSE", "ERROR", "Unknown: " + e.getMessage());
            }
        }
        else if (fc == 'e') {
            try {
                if (json.charAt(1) == 'm') {
                    Context jsonContext = new EmptyContext();
                    Statement stmt = new DefaultStatement(jsonContext);
                    EmptyType result = new EmptyType(stmt);
                    return result;
                }
                else {
                    Context jsonContext = new EmptyContext();
                    Statement stmt = new DefaultStatement(jsonContext);
                    EndValueType result = new EndValueType(stmt);
                    return result;
                }
            } catch (Exception e) {
                return ErrorUtil.createErrorValueAndThrow(null, "JSONPARSE", "ERROR", "Unknown: " + e.getMessage());
            }
        }
        else {
            return JsonUtil.operonValueFromString(json);
        }
    }

    public static OperonValue operonValueFromString(String json) throws OperonGenericException {
        try {
            InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            
            // Create a CharStream that reads from standard input
            org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromStream(is);

            // create a lexer that feeds off of input CharStream
            JSONLexer lexer = new JSONLexer(input);
            lexer.removeErrorListeners();
            lexer.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                    throw new IllegalStateException("lexing failed at line " + line + " due to " + msg, e);
                }
            });
            
            // create a buffer of tokens pulled from the lexer
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            
            // create a parser that feeds off the tokens buffer
            JSONParser parser = new JSONParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                    throw new IllegalStateException("failed to parse at line " + line + " due to " + msg, e);
                }
            });
            
            ParseTree tree = parser.json(); // begin parsing at json rule
            
            // Create a generic parse tree walker that can trigger callbacks
            ParseTreeWalker walker = new ParseTreeWalker();
            
            // Walk the tree created during the parse, trigger callbacks
            JSONCompiler compiler = new JSONCompiler();
            
            Context jsonContext = new EmptyContext();
            Statement jsonStatement = new DefaultStatement(jsonContext);
            jsonStatement.setId("jsonStmt");
            compiler.setCurrentStatement(jsonStatement);
            walker.walk(compiler, tree);
            
            // Extract the compiled value.
            return (OperonValue) compiler.getCurrentStatement().getNode();
        } catch (IOException e) {
            String message = e.getMessage();
            if (message == null) {
                message = "";
            }
            return ErrorUtil.createErrorValueAndThrow(null, "JSONPARSE", "JSONPARSE-001", "Unknown: " + message);
        } catch (IndexOutOfBoundsException e) {
            String message = e.getMessage();
            if (message == null) {
                message = "";
            }
            return ErrorUtil.createErrorValueAndThrow(null, "JSONPARSE", "JSONPARSE-002", "Unknown: " + message);
        } catch (IllegalStateException e) { // This error occurs when lexing fails.
            String message = e.getMessage();
            if (message == null) {
                message = "";
            }
            return ErrorUtil.createErrorValueAndThrow(null, "JSONPARSE", "JSONPARSE-003", message);
        } 
        catch (Exception e) {
            String message = e.getMessage();
            if (message == null) {
                message = "";
            }
            return ErrorUtil.createErrorValueAndThrow(null, "JSONPARSE", "JSONPARSE-004", "Unknown: " + message);
        }
    }

}