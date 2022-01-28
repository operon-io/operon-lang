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

package io.operon.runner.system.integration.robot.keyboard.layout;

import static java.awt.event.KeyEvent.*;
import io.operon.runner.system.integration.robot.keyboard.Keyboard;

public class KeyboardLayoutFI implements KeyboardLayout {
    public KeyboardLayoutFI() {}

	//
	// FI-layout
	//
    public int[] getKeyCodes(char character) {
        //System.out.println("TYPE: " + character);
        int [] result = {};
		switch (character) {
			case 'a': return Keyboard.emit(VK_A);
			case 'b': return Keyboard.emit(VK_B);
			case 'c': return Keyboard.emit(VK_C);
			case 'd': return Keyboard.emit(VK_D);
			case 'e': return Keyboard.emit(VK_E);
			case 'f': return Keyboard.emit(VK_F);
			case 'g': return Keyboard.emit(VK_G);
			case 'h': return Keyboard.emit(VK_H);
			case 'i': return Keyboard.emit(VK_I);
			case 'j': return Keyboard.emit(VK_J);
			case 'k': return Keyboard.emit(VK_K);
			case 'l': return Keyboard.emit(VK_L);
			case 'm': return Keyboard.emit(VK_M);
			case 'n': return Keyboard.emit(VK_N);
			case 'o': return Keyboard.emit(VK_O);
			case 'p': return Keyboard.emit(VK_P);
			case 'q': return Keyboard.emit(VK_Q);
			case 'r': return Keyboard.emit(VK_R);
			case 's': return Keyboard.emit(VK_S);
			case 't': return Keyboard.emit(VK_T);
			case 'u': return Keyboard.emit(VK_U);
			case 'v': return Keyboard.emit(VK_V);
			case 'w': return Keyboard.emit(VK_W);
			case 'x': return Keyboard.emit(VK_X);
			case 'y': return Keyboard.emit(VK_Y);
			case 'z': return Keyboard.emit(VK_Z);
			case 'A': return Keyboard.emit(VK_SHIFT, VK_A);
			case 'B': return Keyboard.emit(VK_SHIFT, VK_B);
			case 'C': return Keyboard.emit(VK_SHIFT, VK_C);
			case 'D': return Keyboard.emit(VK_SHIFT, VK_D);
			case 'E': return Keyboard.emit(VK_SHIFT, VK_E);
			case 'F': return Keyboard.emit(VK_SHIFT, VK_F);
			case 'G': return Keyboard.emit(VK_SHIFT, VK_G);
			case 'H': return Keyboard.emit(VK_SHIFT, VK_H);
			case 'I': return Keyboard.emit(VK_SHIFT, VK_I);
			case 'J': return Keyboard.emit(VK_SHIFT, VK_J);
			case 'K': return Keyboard.emit(VK_SHIFT, VK_K);
			case 'L': return Keyboard.emit(VK_SHIFT, VK_L);
			case 'M': return Keyboard.emit(VK_SHIFT, VK_M);
			case 'N': return Keyboard.emit(VK_SHIFT, VK_N);
			case 'O': return Keyboard.emit(VK_SHIFT, VK_O);
			case 'P': return Keyboard.emit(VK_SHIFT, VK_P);
			case 'Q': return Keyboard.emit(VK_SHIFT, VK_Q);
			case 'R': return Keyboard.emit(VK_SHIFT, VK_R);
			case 'S': return Keyboard.emit(VK_SHIFT, VK_S);
			case 'T': return Keyboard.emit(VK_SHIFT, VK_T);
			case 'U': return Keyboard.emit(VK_SHIFT, VK_U);
			case 'V': return Keyboard.emit(VK_SHIFT, VK_V);
			case 'W': return Keyboard.emit(VK_SHIFT, VK_W);
			case 'X': return Keyboard.emit(VK_SHIFT, VK_X);
			case 'Y': return Keyboard.emit(VK_SHIFT, VK_Y);
			case 'Z': return Keyboard.emit(VK_SHIFT, VK_Z);
			case '`': return Keyboard.emit(VK_BACK_QUOTE);
			case '0': return Keyboard.emit(VK_0);
			case '1': return Keyboard.emit(VK_1);
			case '2': return Keyboard.emit(VK_2);
			case '3': return Keyboard.emit(VK_3);
			case '4': return Keyboard.emit(VK_4);
			case '5': return Keyboard.emit(VK_5);
			case '6': return Keyboard.emit(VK_6);
			case '7': return Keyboard.emit(VK_7);
			case '8': return Keyboard.emit(VK_8);
			case '9': return Keyboard.emit(VK_9);
			case '-': return Keyboard.emit(VK_MINUS);
			case '=': return Keyboard.emit(VK_EQUALS);
			case '~': return Keyboard.emit(VK_SHIFT, VK_BACK_QUOTE);
			case '!': return Keyboard.emit(VK_SHIFT, VK_1); // VK_EXCLAMATION_MARK causes problem
			case '@': return Keyboard.emit(VK_AT);
			case '#': return Keyboard.emit(VK_NUMBER_SIGN);
			case '$': return Keyboard.emit(VK_DOLLAR);
			case '%': return Keyboard.emit(VK_SHIFT, VK_5);
			case '^': return Keyboard.emit(VK_CIRCUMFLEX);
			case '&': return Keyboard.emit(VK_AMPERSAND);
			case '*': return Keyboard.emit(VK_ASTERISK);
			case '(': return Keyboard.emit(VK_LEFT_PARENTHESIS);
			case ')': return Keyboard.emit(VK_RIGHT_PARENTHESIS);
			case '_': return Keyboard.emit(VK_UNDERSCORE);
			case '+': return Keyboard.emit(VK_PLUS);
			case '\t': return Keyboard.emit(VK_TAB);
			case '\n': return Keyboard.emit(VK_ENTER);
			case '[': return Keyboard.emit(VK_OPEN_BRACKET);
			case ']': return Keyboard.emit(VK_CLOSE_BRACKET);
			case '\\': return Keyboard.emit(VK_BACK_SLASH);
			case '{': return Keyboard.emit(VK_SHIFT, VK_OPEN_BRACKET);
			case '}': return Keyboard.emit(VK_SHIFT, VK_CLOSE_BRACKET);
			case '|': return Keyboard.emit(VK_SHIFT, VK_BACK_SLASH);
			case ';': return Keyboard.emit(VK_SEMICOLON);
			case ':': return Keyboard.emit(VK_COLON);
			case '\'': return Keyboard.emit(VK_QUOTE);
			case '"': return Keyboard.emit(VK_QUOTEDBL);
			case ',': return Keyboard.emit(VK_COMMA);
			case '<': return Keyboard.emit(VK_SHIFT, VK_COMMA);
			case '.': return Keyboard.emit(VK_PERIOD);
			case '>': return Keyboard.emit(VK_SHIFT, VK_PERIOD);
			case '/': return Keyboard.emit(VK_SLASH);
			case '?': return Keyboard.emit(VK_SHIFT, VK_PLUS);
			case ' ': return Keyboard.emit(VK_SPACE);
			default:
				System.out.println("|| Cannot type character " + character + " ||");
        }
        return result;
    }

}