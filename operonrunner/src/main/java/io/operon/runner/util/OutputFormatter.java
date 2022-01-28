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

//
// Format the output of different types, e.g. ArrayType, ObjectType and Path.
//
public class OutputFormatter {
    
    public byte rawOutput = (byte) 0; // see from RawValue
    //
    // Current amount of spaces. This keeps track of the current indentation.
    // ArrayType and ObjectType toFormattedString() change this value when they are executed.
    //
    public short spaces = (short) 0;
    
    //
    // How many spaces are set for indentation.
    //
    public short spacing = (short) 2;
    
    public char arrayStart = '[';
    public char arrayEnd = ']';
    
    public char objectStart = '{';
    public char objectEnd = '}';
    
    //
    // NOTE:
    //
    // The default values are compatible with JSON.
    // Optionally use the commented values to output Path as Operon-type.
    //
    public String pathPrefix = null; // "Path"
    public char pathStart = Character.MIN_VALUE; // '('
    public char pathInnerStart = '\"'; // Character.MIN_VALUE
    public char pathEnd = Character.MIN_VALUE; // ')'
    public char pathInnerEnd = '\"'; // Character.MIN_VALUE
    
    //
    // output the amount of spaces for current indentation, controlled by spaces -variable.
    //
    public String spaces() {
        switch (this.spaces) {
            case 0: return  "";
            case 1: return  " ";
            case 2: return  "  ";
            case 3: return  "   ";
            case 4: return  "    ";
            case 5: return  "     ";
            case 6: return  "      ";
            case 7: return  "       ";
            case 8: return  "        ";
            case 9: return  "         ";
            case 10: return "          ";
            case 11: return "           ";
            case 12: return "            ";
            case 13: return "             ";
            case 14: return "              ";
            case 15: return "               ";
            case 16: return "                ";
            case 17: return "                 ";
            case 18: return "                  ";
            case 19: return "                   ";
            case 20: return "                    ";
            case 21: return "                     ";
            case 22: return "                      ";
            case 23: return "                       ";
            case 24: return "                        ";
            
            default:
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < spaces; i ++) {
                    sb.append(" ");
                }
                return sb.toString();
        }
    }

}