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

package io.operon.runner;

// import ANTLR's runtime libraries
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import io.operon.parser.*;

import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.operon.runner.compiler.*;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.system.InputSourceDriver;
import io.operon.runner.model.InputSource;
import io.operon.runner.model.OperonConfigs;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.statement.FromStatement;
import io.operon.runner.node.Node;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class OperonRunner implements Runnable {
    private static Logger log = LogManager.getLogger(OperonRunner.class);
    private OperonContext operonContext;
    
    private InputSource fromInputSource;
    private InputSourceDriver isd;
    private OperonValue initialValue;
    private OperonContextManager.ContextStrategy contextStrategy = OperonContextManager.ContextStrategy.SINGLETON;
    
    private String query;
    private String testsContent;
    private String queryId;
    private boolean isTest = false;
    private boolean contextReady = false;
    private PrintStream contextLogger;
    private PrintStream contextErrorLogger;
    private OperonConfigs configs;
    private boolean isRunning = false;
    
    private static Map<String, OperonFunction> registeredFunctions = null;
    
    public Boolean isRunning() {
        return this.isRunning;
    }
    
    public void run() {
        try {
            long startTime = System.nanoTime();
            this.isRunning = true;
            //System.out.println("Started runner, running=" + this.isRunning());
            operonContext = null;
            
            if (this.getIsTest()) {
                OperonTestsContext testsContext = OperonRunner.compileTests(testsContent, "test");
                operonContext = OperonRunner.createNewOperonContextWithTests(query, testsContext, "test", this.getConfigs());
                
                //
                // Initial value may be set from the System.in, see TestsCommand,
                // NOTE: this could override the initial value set by the MockComponent.
                //
                if (this.getInitialValueForJsonSystem() != null) {
                    Main.setInitialValueForJsonSystem(operonContext, this.getInitialValueForJsonSystem());
                }
            }
            else {
                operonContext = OperonRunner.createNewOperonContext(this.getQuery(), this.getQueryId(), this.getConfigs());
                
                //
                // Initial value may be set from the System.in, see QueryCommand
                //
                if (this.getInitialValueForJsonSystem() != null) {
                    Main.setInitialValueForJsonSystem(operonContext, this.getInitialValueForJsonSystem());
                }
            }
            
            if (this.getContextLogger() != null) {
                operonContext.setContextLogger(this.getContextLogger());
            }
            if (this.getContextErrorLogger() != null) {
                operonContext.setContextErrorLogger(this.getContextErrorLogger());
            }
            
            this.contextReady = true;
            
            long compilingEndTime = System.nanoTime();
            // TODO: print only if statistics are enabled:
            //System.out.println("Compilation time: " + (compilingEndTime - startTime) + " ns (" + (compilingEndTime - startTime) / 1000000 + " ms.)");
            log.info("Starting to run query.");
            
            FromStatement fromStatement = operonContext.getFromStatement();
            if (fromStatement == null) {
                log.debug("OperonRunner :: FromStatement was null");
                throw new RuntimeException("OperonRunner :: FromStatement was null");
            }
            this.fromInputSource = fromStatement.getInputSource();
            if (this.fromInputSource == null) {
                log.debug("OperonRunner :: fromInputSource was null");
                throw new RuntimeException("OperonRunner :: fromInputSource was null");
            }
            // NOTE: isd not loaded at this point

            this.startContext(operonContext); // --> start reading frames from input device and pass them to Select -statement processor
            
            // NOTE: isd is now loaded
            this.isd = this.fromInputSource.getInputSourceDriver();

            if (this.getIsd() == null) {
                log.debug("OperonRunner :: isd was null");
                throw new RuntimeException("OperonRunner :: isd was null");
            }

            long endTime = System.nanoTime();
            //System.out.println("Total execution time: " + (endTime - startTime) + " ns (" + (endTime - startTime) / 1000000 + " ms.)");
            log.info("Done.");
 
            while (this.isRunning) {
                Thread.sleep(100);
                if (isd.isRunning() == false) {
                    this.isRunning = false;
                }
                else if (Thread.interrupted()) {
                    this.isd.stop();
                    this.isRunning = false;
                }
            }
        } catch (IOException e) {
            System.err.println("ERROR SIGNAL: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("ERROR SIGNAL: " + e.getMessage());
        }
    }
    
    public boolean isContextReady() {
        return this.contextReady;
    }
    
    //
    // TODO: compiling takes time. Therefore creating new context this way is time-consuming!
    //       Should try to optimize this, e.g. by caching objects somehow.
    //
    //       This approach is to test the Aggregate-pattern: can new context solve issues of multi-tenancy?
    public static OperonContext createNewOperonContext(String query, String queryId, OperonConfigs configs) throws Exception, OperonGenericException, IOException {
        OperonContext newCtx = new OperonContext();
        OperonRunner.compile(newCtx, query, queryId); // Happens here
        newCtx.setContextId(queryId);
        newCtx.setQuery(query);
        if (configs != null) {
            //System.out.println("Set Configs");
            newCtx.setConfigs(configs);
        }
        else {
            //System.out.println("Configs was null");
        }
        return newCtx;
    }
    
    public static OperonContext createNewOperonContextWithTests(String query, OperonTestsContext testsContext, String queryId, OperonConfigs configs) 
            throws Exception, OperonGenericException, IOException {
        OperonContext newCtx = new OperonContext();
        OperonRunner.compileWithTests(newCtx, query, queryId, testsContext); // Happens here
        newCtx.setContextId(queryId);
        newCtx.setQuery(query);
        if (configs != null){
            newCtx.setConfigs(configs);
        }
        return newCtx;
    }
    
    // 
    // Serialize the OperonContext, so it could be easily reused without recompiling the code
    // 
    //  TODO: deprecate this and use the "serializeObject" -method instead (more general)
    public static ByteArrayOutputStream saveOperonContext(OperonContext ctx) throws IOException {
        // TODO: this will be handy when saving only the compiled module
        //FileOutputStream fileOutputStream = new FileOutputStream("context.opm");
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        
        ObjectOutputStream objectOutputStream 
          = new ObjectOutputStream(bOut);
        objectOutputStream.writeObject(ctx);
        objectOutputStream.flush();
        objectOutputStream.close();
        return bOut;
    }

    public static ByteArrayOutputStream serializeObject(Object obj) throws IOException {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        
        ObjectOutputStream objectOutputStream 
          = new ObjectOutputStream(bOut);
        objectOutputStream.writeObject(obj);
        objectOutputStream.flush();
        objectOutputStream.close();
        return bOut;
    }
    
    public static FileOutputStream saveOperonContextToFile(Context ctx, String fileName) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(fileName);
        
        ObjectOutputStream objectOutputStream 
          = new ObjectOutputStream(fileOutputStream);
        objectOutputStream.writeObject(ctx);
        objectOutputStream.flush();
        objectOutputStream.close();
        return fileOutputStream;
    }
    
    public static OperonContext loadOperonContextFromFile(String operonFilePath) throws IOException {
        try {
            FileInputStream fileInputStream = new FileInputStream(operonFilePath);
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            OperonContext ctx = (OperonContext) objectInputStream.readObject();
            objectInputStream.close();
            return ctx;
        } catch (ClassNotFoundException cnfe) {
            log.debug("OperonRunner :: Could not find OperonContext from file");
            throw new RuntimeException("Could not find OperonContext from file");
        }
    }

    // TODO: catch OperonCompilerException
    public static OperonContext compile(OperonContext operonContext, String query, String queryId) throws Exception, OperonGenericException, IOException {
        InputStream is = new ByteArrayInputStream(query.getBytes(StandardCharsets.UTF_8));
        
        // Create a CharStream that reads from standard input
        org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromStream(is);

        // create a lexer that feeds off of input CharStream
        OperonLexer lexer = new OperonLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new IllegalStateException("Lexing failed at line " + line + " due to " + msg, e);
            }
        });
        
        // create a buffer of tokens pulled from the lexer
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        
        // create a parser that feeds off the tokens buffer
        OperonParser parser = new OperonParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new IllegalStateException("Failed to parse at line " + line + " due to " + msg, e);
            }
        });
        ParseTree tree = parser.operon(); // begin parsing at init rule
        
        // Create a generic parse tree walker that can trigger callbacks
        ParseTreeWalker walker = new ParseTreeWalker();
        // Walk the tree created during the parse, trigger callbacks
        OperonCompiler compiler = new OperonCompiler();
        compiler.setOperonContext(operonContext);
        walker.walk(compiler, tree);
        return operonContext;
    }

    public static Node compileExpr(Statement currentStatement, String expr) throws Exception, OperonGenericException, IOException {
        //System.out.println("OperonRunner.compileExpr - 1 currentStatement=" + currentStatement);
        InputStream is = new ByteArrayInputStream(expr.getBytes(StandardCharsets.UTF_8));
        
        // Create a CharStream that reads from standard input
        org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromStream(is);

        // create a lexer that feeds off of input CharStream
        OperonLexer lexer = new OperonLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new IllegalStateException("Lexing failed at line " + line + " due to " + msg, e);
            }
        });
        
        // create a buffer of tokens pulled from the lexer
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        
        // create a parser that feeds off the tokens buffer
        OperonParser parser = new OperonParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new IllegalStateException("Failed to parse at line " + line + " due to " + msg, e);
            }
        });
        ParseTree tree = parser.expr(); // begin parsing at expr-rule
        
        // Create a generic parse tree walker that can trigger callbacks
        ParseTreeWalker walker = new ParseTreeWalker();
        // Walk the tree created during the parse, trigger callbacks
        OperonCompiler compiler = new OperonCompiler();
        compiler.setOperonContext((OperonContext) currentStatement.getOperonContext());
        //System.out.println("OperonRunner.compileExpr - 2 currentStatement=" + currentStatement);
        compiler.setCurrentStatement(currentStatement);
        walker.walk(compiler, tree);
        java.util.Stack compilerStack = compiler.getStack();
        Node result = (Node) compilerStack.pop();
        //System.out.println("NODE: " + result.getClass().getName());
        return result;
    }

    public static OperonContext compileWithTests(OperonContext operonContext, String query, 
            String queryId, OperonTestsContext testsContext) throws Exception, OperonGenericException, IOException {
        InputStream is = new ByteArrayInputStream(query.getBytes(StandardCharsets.UTF_8));
        
        // Create a CharStream that reads from standard input
        org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromStream(is);

        // create a lexer that feeds off of input CharStream
        OperonLexer lexer = new OperonLexer(input);
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new IllegalStateException("Lexing failed at line " + line + " due to " + msg, e);
            }
        });
        
        // create a buffer of tokens pulled from the lexer
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        
        // create a parser that feeds off the tokens buffer
        OperonParser parser = new OperonParser(tokens);
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new IllegalStateException("Failed to parse at line " + line + " due to " + msg, e);
            }
        });
        ParseTree tree = parser.operon(); // begin parsing at init rule
        
        // Create a generic parse tree walker that can trigger callbacks
        ParseTreeWalker walker = new ParseTreeWalker();
        // Walk the tree created during the parse, trigger callbacks
        OperonCompiler compiler = new OperonCompiler();
        compiler.setOperonContext(operonContext);
        compiler.setOperonTestsContext(testsContext);
        walker.walk(compiler, tree);
        return operonContext;
    }
    
    public static Context compileModule(Context parentContext, OperonTestsContext operonTestsContext, 
            String moduleContent, String moduleFilePath, String moduleOwnNamespace) throws IOException {
        //System.out.println("compileModule started: " + moduleFilePath);
        InputStream is = new ByteArrayInputStream(moduleContent.getBytes(StandardCharsets.UTF_8));
        
        // Create a CharStream that reads from standard input
        org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromStream(is);

        // create a lexer that feeds off of input CharStream
        OperonModuleLexer lexer = new OperonModuleLexer(input);
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new IllegalStateException("Lexing module failed at line " + line + " due to " + msg, e);
            }
        });
        
        // create a buffer of tokens pulled from the lexer
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        
        // create a parser that feeds off the tokens buffer
        OperonModuleParser parser = new OperonModuleParser(tokens);
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new IllegalStateException("Failed to parse module at line " + line + " due to " + msg, e);
            }
        });
        ParseTree tree = parser.operonmodule(); // begin parsing at init rule
        
        // Create a generic parse tree walker that can trigger callbacks
        ParseTreeWalker walker = new ParseTreeWalker();
        
        // Walk the tree created during the parse, trigger callbacks
        ModuleCompiler compiler = new ModuleCompiler();
        Context moduleContext = new ModuleContext();
        moduleContext.setParentContext(parentContext);
        compiler.setModuleContext(moduleContext);
        compiler.setOperonTestsContext(operonTestsContext);
        compiler.setModuleNamespace(moduleOwnNamespace);
        compiler.setModuleFilePath(moduleFilePath);
        
        walker.walk(compiler, tree);
        return moduleContext;
    }
    
    public static OperonTestsContext compileTests(String testContent, String testId) throws OperonGenericException, IOException {
        InputStream is = new ByteArrayInputStream(testContent.getBytes(StandardCharsets.UTF_8));
        
        // Create a CharStream that reads from standard input
        org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromStream(is);

        // create a lexer that feeds off of input CharStream
        OperonTestsLexer lexer = new OperonTestsLexer(input);
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new IllegalStateException("Lexing test failed at line " + line + " due to " + msg, e);
            }
        });
        
        // create a buffer of tokens pulled from the lexer
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        
        // create a parser that feeds off the tokens buffer
        OperonTestsParser parser = new OperonTestsParser(tokens);
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new IllegalStateException("Failed to parse test at line " + line + " due to " + msg, e);
            }
        });
        ParseTree tree = parser.operontests(); // begin parsing at init rule
        
        // Create a generic parse tree walker that can trigger callbacks
        ParseTreeWalker walker = new ParseTreeWalker();
        // Walk the tree created during the parse, trigger callbacks
        TestsCompiler compiler = new TestsCompiler();
        OperonTestsContext operonTestsContext = new OperonTestsContext();
        compiler.setOperonTestsContext(operonTestsContext);
        walker.walk(compiler, tree);
        return operonTestsContext;
    }

    public static OperonValue doQuery(String query) throws OperonGenericException {
        try {
            OperonContext ctx = OperonRunner.createNewOperonContext(query, "dynamic", null);
            ctx.start(OperonContextManager.ContextStrategy.SINGLETON);
            OperonValue selectResult = ctx.getOutputOperonValue();
            ctx.shutdown();
            return selectResult;
        } catch (Exception e) {
            log.debug("OperonRunner :: doQuery :: Exception");
            log.debug("    OperonRunner :: doQuery :: Exception :: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static OperonValue doQuery(String query, OperonConfigs configs) throws OperonGenericException {
        try {
            OperonContext ctx = OperonRunner.createNewOperonContext(query, "dynamic", null);
            ctx.setConfigs(configs);
            ctx.start(OperonContextManager.ContextStrategy.SINGLETON);
            OperonValue selectResult = ctx.getOutputOperonValue();
            ctx.shutdown();
            return selectResult;
        } catch (Exception e) {
            log.debug("OperonRunner :: doQuery :: Exception");
            log.debug("    OperonRunner :: doQuery :: Exception :: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static OperonValue doQueryWithInitialValue(String query, OperonValue initialValue) throws OperonGenericException {
        try {
            OperonContext ctx = OperonRunner.createNewOperonContext(query, "dynamic", null);
            Main.setInitialValueForJsonSystem(ctx, initialValue);
            ctx.start(OperonContextManager.ContextStrategy.SINGLETON);
            OperonValue selectResult = ctx.getOutputOperonValue();
            ctx.shutdown();
            return selectResult;
        } catch (Exception e) {
            log.debug("OperonRunner :: doQueryWithInitialValue :: Exception");
            log.debug("    OperonRunner :: doQueryWithInitialValue :: Exception :: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static OperonValue doQueryWithInitialValue(String query, String initialValueStr) throws OperonGenericException {
        try {
            OperonContext ctx = OperonRunner.createNewOperonContext(query, "dynamic", null);
            OperonValue initialValue = JsonUtil.operonValueFromString(initialValueStr);
            Main.setInitialValueForJsonSystem(ctx, initialValue);
            ctx.start(OperonContextManager.ContextStrategy.SINGLETON);
            OperonValue selectResult = ctx.getOutputOperonValue();
            ctx.shutdown();
            return selectResult;
        } catch (Exception e) {
            log.debug("OperonRunner :: doQueryWithInitialValue :: Exception");
            log.debug("    OperonRunner :: doQueryWithInitialValue :: Exception :: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static OperonValue doQueryWithInitialValue(String query, OperonValue initialValue, OperonConfigs configs) throws OperonGenericException {
        try {
            OperonContext ctx = OperonRunner.createNewOperonContext(query, "dynamic", null);
            ctx.setConfigs(configs);
            Main.setInitialValueForJsonSystem(ctx, initialValue);
            ctx.start(OperonContextManager.ContextStrategy.SINGLETON);
            OperonValue selectResult = ctx.getOutputOperonValue();
            ctx.shutdown();
            return selectResult;
        } catch (Exception e) {
            log.debug("OperonRunner :: doQueryWithInitialValue :: Exception");
            log.debug("    OperonRunner :: doQueryWithInitialValue :: Exception :: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static OperonValue doQueryWithInitialValue(String query, String initialValueStr, OperonConfigs configs) throws OperonGenericException {
        try {
            OperonContext ctx = OperonRunner.createNewOperonContext(query, "dynamic", null);
            ctx.setConfigs(configs);
            OperonValue initialValue = JsonUtil.operonValueFromString(initialValueStr);
            Main.setInitialValueForJsonSystem(ctx, initialValue);
            ctx.start(OperonContextManager.ContextStrategy.SINGLETON);
            OperonValue selectResult = ctx.getOutputOperonValue();
            ctx.shutdown();
            return selectResult;
        } catch (Exception e) {
            log.debug("OperonRunner :: doQueryWithInitialValue :: Exception");
            log.debug("    OperonRunner :: doQueryWithInitialValue :: Exception :: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static void bindValue(OperonContext ctx, String keyName, OperonValue namedValue) throws OperonGenericException {
        try {
            Map<String, LetStatement> ctxLetStatements = ctx.getLetStatements();
            LetStatement letStatement = new LetStatement(ctx);
            letStatement.setNode(namedValue);
            ctxLetStatements.put(keyName, letStatement);
        } catch (Exception e) {
            throw new OperonGenericException("Could not bind value: " + keyName);
        }
    }

    private void startContext(OperonContext ctx) {
        ctx.start(this.getContextStrategy());
    }
    
    // FIXME!
    // OperonContext is inside the thread, and this gets nullpointer,
    // context might have been deepcopied already and the reference is
    // no longer valid.
    public void shutdown() {
        this.isd.stop();
        this.getOperonContext().shutdown();
        this.isRunning = false;
    }

    public InputSourceDriver getIsd() {
        return this.isd;
    }
    
    public OperonContext getOperonContext() {
        return this.operonContext;
    }

    public void setQueryId(String id) {
        this.queryId = id;
    }
    
    public String getQueryId() {
        return this.queryId;
    }
    
    public void setQuery(String query) {
        this.query = query;
    }
    
    public String getQuery() {
        return this.query;
    }
    
    public void setTestsContent(String tc) {
        this.testsContent = tc;
    }
    
    public String getTestsContent() {
        return this.testsContent;
    }
    
    public void setIsTest(boolean it) {
        this.isTest = it;
    }
    
    public boolean getIsTest() {
        return this.isTest;
    }
    
    public PrintStream getSystemOut() {
        return System.out;
    }
    
    public void setSystemOut(PrintStream newOut) {
        System.setOut(newOut);
    }
    
    public PrintStream getSystemErr() {
        return System.err;
    }
    
    public void setSystemErr(PrintStream newErr) {
        System.setErr(newErr);
    }
    
    public void setContextLogger(PrintStream logger) {
        this.contextLogger = logger;
    }
    
    public PrintStream getContextLogger() {
        return this.contextLogger;
    }
    
    public void setContextErrorLogger(PrintStream logger) {
        this.contextErrorLogger = logger;
    }
    
    public PrintStream getContextErrorLogger() {
        return this.contextErrorLogger;
    }
    
    public void setInitialValueForJsonSystem(OperonValue initialValue) {
        this.initialValue = initialValue;
    }
    
    public OperonValue getInitialValueForJsonSystem() {
        return this.initialValue;
    }
    
    public void setContextStrategy(OperonContextManager.ContextStrategy s) {
        this.contextStrategy = s;
    }
    
    public OperonContextManager.ContextStrategy getContextStrategy() {
        return this.contextStrategy;
    }
    
    public void setConfigs(OperonConfigs conf) {
        this.configs = conf;
    }
    
    public OperonConfigs getConfigs() {
        return this.configs;
    }
    
    public static Map<String, OperonFunction> getRegisteredFunctions() {
        return registeredFunctions;
    }
    
    public static void registerFunction(String id, OperonFunction func) {
        if (registeredFunctions == null) {
            registeredFunctions = new HashMap<String, OperonFunction>();
        }
        registeredFunctions.put(id, func);
    }
}