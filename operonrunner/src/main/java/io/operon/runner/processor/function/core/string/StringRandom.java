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

package io.operon.runner.processor.function.core.string;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class StringRandom extends BaseArity1 implements Node, Arity1 {
    public StringRandom(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "random", "options");
        this.setNs(Namespaces.STRING);
    }

    public StringType evaluate() throws OperonGenericException {
        try {
            long seed = -1;
            ObjectType options = null;
            if (this.getParam1() != null) {
                options = (ObjectType) this.getParam1().evaluate();
    
                if (options.hasKey("\"seed\"")) {
                    seed = (long) ((NumberType) options.getByKey("seed").evaluate()).getDoubleValue();
                }
            }
    
            Random r;
    
            if (seed >= 0) {
                r = new Random(seed);
            } else {
                r = new Random();
            }
    
            int length = 1;
            int minLower = 0; // Minimum number of lower-case characters
            int minUpper = 0; // Minimum number of upper-case characters
            int minNumber = 0; // Minimum number of number characters
            int minSpecial = 0; // Minimum number of special characters
            String allowedChars = null;
            String overrideSpecial = null;
            boolean upper = true; // Allow upper-case characters
            boolean lower = true; // Allow lower-case characters
            boolean special = true; // Allow special characters
            boolean number = true; // Allow number characters
    
            if (options != null) {
                if (options.hasKey("\"length\"")) {
                    length = (int) ((NumberType) options.getByKey("length").evaluate()).getDoubleValue();
                }
    
                if (options.hasKey("\"minLower\"")) {
                    minLower = (int) ((NumberType) options.getByKey("minLower").evaluate()).getDoubleValue();
                }
    
                if (options.hasKey("\"minUpper\"")) {
                    minUpper = (int) ((NumberType) options.getByKey("minUpper").evaluate()).getDoubleValue();
                }
    
                if (options.hasKey("\"minNumber\"")) {
                    minNumber = (int) ((NumberType) options.getByKey("minNumber").evaluate()).getDoubleValue();
                }
    
                if (options.hasKey("\"minSpecial\"")) {
                    minSpecial = (int) ((NumberType) options.getByKey("minSpecial").evaluate()).getDoubleValue();
                }
    
                // This option overrides the "upper", "lower", "special", "number", and "overrideSpecial" options.
                if (options.hasKey("\"allowedChars\"")) {
                    allowedChars = ((StringType) options.getByKey("allowedChars").evaluate()).getJavaStringValue();
                }
    
                if (options.hasKey("\"overrideSpecial\"")) {
                    overrideSpecial = ((StringType) options.getByKey("overrideSpecial").evaluate()).getJavaStringValue();
                }
    
                // Allow upper-case characters?
                if (options.hasKey("\"upper\"")) {
                    OperonValue upperValue = (OperonValue) options.getByKey("upper").evaluate();
                    if (upperValue instanceof FalseType) {
                        upper = false;
                    }
                }
    
                // Allow lower-case characters?
                if (options.hasKey("\"lower\"")) {
                    OperonValue lowerValue = (OperonValue) options.getByKey("lower").evaluate();
                    if (lowerValue instanceof FalseType) {
                        lower = false;
                    }
                }
    
                // Allow special characters?
                if (options.hasKey("\"special\"")) {
                    OperonValue specialValue = (OperonValue) options.getByKey("special").evaluate();
                    if (specialValue instanceof FalseType) {
                        special = false;
                    }
                }
    
                // Allow number characters?
                if (options.hasKey("\"number\"")) {
                    OperonValue numberValue = (OperonValue) options.getByKey("number").evaluate();
                    if (numberValue instanceof FalseType) {
                        number = false;
                    }
                }
            }
    
            if (length < 0) {
                throw new Exception("Option \"length\" cannot be smaller than zero.");
            }
    
            if (upper == false && lower == false && number == false && special == false) {
                throw new Exception("Cannot produce a random string when all upper, lower, number, and special characters are not allowed.");
            }
    
            int minCount = minLower + minUpper + minNumber + minSpecial;
            if (minCount > length) {
                throw new Exception("Sum of minimum lower-case, upper-case, number, and special character counts exceeds the length of the string.");
            }
    
            StringBuilder sb = new StringBuilder();
    
            if (allowedChars == null) {
                String chars = "";
                
                if (lower) {
                    chars += "abcdefghijklmnopqrstuvwxyz";
                }
                if (upper) {
                    chars += "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
                }
                if (number) {
                    chars += "0123456789";
                }
                
                String specialChars = "_!.,\"#%&/()=?";
                if (special) {
                    if (overrideSpecial != null) {
                        specialChars = overrideSpecial;
                        chars += overrideSpecial;
                    } else {
                        chars += specialChars;
                    }
                }
                
                int countLower = 0;
                int countUpper = 0;
                int countNumber = 0;
                int countSpecial = 0;
                
                // Generate the required number of each character type before generating any character
                List<Character> characters = new ArrayList<>();
                
                for (int i = 0; i < minCount; i++) {
                    if (countLower < minLower) {
                        // Generate lower-case character
                        char c = ' ';
                        do {
                            c = chars.charAt(r.nextInt(chars.length()));
                        } while (!Character.isLowerCase(c));
                        characters.add(c);
                        countLower++;
                    } else if (countUpper < minUpper) {
                        // Generate upper-case character
                        char c = ' ';
                        do {
                            c = chars.charAt(r.nextInt(chars.length()));
                        } while (!Character.isUpperCase(c));
                        characters.add(c);
                        countUpper++;
                    } else if (countNumber < minNumber) {
                        // Generate number character
                        char c = ' ';
                        do {
                            c = chars.charAt(r.nextInt(chars.length()));
                        } while (!Character.isDigit(c));
                        characters.add(c);
                        countNumber++;
                    } else if (countSpecial < minSpecial) {
                        // Generate special character
                        char c = ' ';
                        do {
                            c = specialChars.charAt(r.nextInt(specialChars.length()));
                        } while (!isSpecialCharacter(c));
                        characters.add(c);
                        countSpecial++;
                    }
                }
                
                // Generate any remaining characters
                for (int i = minCount; i < length; i++) {
                    characters.add(chars.charAt(r.nextInt(chars.length())));
                }
                
                // Shuffle the characters
                Collections.shuffle(characters, r);
                
                // Build the final string
                for (char c : characters) {
                    sb.append(c);
                }
            } else {
                for (int i = 0; i < length; i++) {
                    sb.append(allowedChars.charAt(r.nextInt(allowedChars.length())));
                }
            }
    
            StringType result = new StringType(this.getStatement());
            result.setFromJavaString(sb.toString());
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "string:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    private boolean isSpecialCharacter(char c) {
        return !Character.isLetterOrDigit(c) && !Character.isWhitespace(c);
    }

}