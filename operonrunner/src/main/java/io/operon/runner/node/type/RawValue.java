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

package io.operon.runner.node.type;

import java.util.Base64;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.Node;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;

import io.operon.runner.IrTypes;
import com.google.gson.annotations.Expose;

public class RawValue extends OperonValue implements Node, AtomicOperonValue {

    @Expose private byte t = IrTypes.RAW_VALUE; // Type-name in the IR-serialized output

    private byte[] value;
    
    public RawValue(Statement stmnt) {
        super(stmnt);
    }

    public RawValue evaluate() throws OperonGenericException {
        this.setUnboxed(true);
        return this;
    }
    
    public static RawValue createFromString(Statement stmt, String value) {
        RawValue result = new RawValue(stmt);
        result.setValue(value.getBytes());
        return result;
    }
    
    public void setValue(byte[] value) {
        this.value = value;
    }
    
    public RawValue getValue() {
        return this;
    }
    
    public byte[] getBytes() {
        return this.value;
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    
    public static String bytesToBase64(byte[] bytes, boolean padding) {
        if (padding) {
            byte[] encoded = Base64.getEncoder().encode(bytes);
            return new String(encoded);
        }
        else {
            byte[] encoded = Base64.getEncoder().withoutPadding().encode(bytes);
            return new String(encoded);
        }
    }
    
    public static String bytesToBase64UrlSafe(byte[] bytes, boolean padding) {
        if (padding) {
            byte[] encoded = Base64.getUrlEncoder().encode(bytes);
            return new String(encoded);
        }
        else {
            byte[] encoded = Base64.getUrlEncoder().withoutPadding().encode(bytes);
            return new String(encoded);
        }
    }
    
    public static byte[] base64ToBytes(byte[] encodedBytes) {
        byte[] decodedBytes = Base64.getDecoder().decode(encodedBytes);
        return decodedBytes;
    }
    
    public static byte[] base64UrlSafeToBytes(byte[] encodedBytes) {
        byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedBytes);
        return decodedBytes;
    }
    
    public static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] result = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            result[i / 2] = (byte) (
                (Character.digit(s.charAt(i), 16) << 4) +
                 Character.digit(s.charAt(i + 1), 16)
            );
        }
        return result;
    }
    
    public static byte[] hexToBytes(byte[] bytes) {
        int len = bytes.length;
        byte[] result = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            result[i / 2] = (byte) (
                (Character.digit(bytes[i], 16) << 4) +
                 Character.digit(bytes[i + 1], 16)
            );
        }
        return result;
    }
    
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public String toBase64() {
        return RawValue.bytesToBase64(this.getBytes(), true);
    }

    public String toHex() {
        return RawValue.bytesToHex(this.getBytes());
    }

    public String toRawString() {
        return new String(this.getBytes());
    }

    @Override
    public String toString() {
        return "\"Bytes(" + this.getBytes().length + ")\"";
    }

    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        if (ofmt == null) {ofmt = new OutputFormatter();}
        if (ofmt.rawOutput == 0) {
            return "\"Bytes(" + this.getBytes().length + ")\"";
        }
        else if (ofmt.rawOutput == 1) {
            return "`" + new String(this.getBytes()) + "`";
        }
        else if (ofmt.rawOutput == 2) {
            return new String(this.getBytes());
        }
        else {
            return "\"Bytes(" + this.getBytes().length + ")\"";
        }
    }

    @Override
    public String toYamlString(YamlFormatter yf) {
        if (yf == null) {yf = new YamlFormatter();}
        if (yf.rawOutput == 0) {
            return "\"Bytes(" + this.getBytes().length + ")\"";
        }
        else if (yf.rawOutput == 1) {
            return "`" + new String(this.getBytes()) + "`";
        }
        else if (yf.rawOutput == 2) {
            return new String(this.getBytes());
        }
        else {
            return "\"Bytes(" + this.getBytes().length + ")\"";
        }
    }

    @Override
    public String toTomlString(OutputFormatter ofmt) {
        if (ofmt == null) {ofmt = new OutputFormatter();}
        return this.toFormattedString(ofmt);   
    }

}