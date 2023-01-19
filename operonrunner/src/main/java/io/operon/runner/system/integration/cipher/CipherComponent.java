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

package io.operon.runner.system.integration.cipher;

import io.operon.runner.OperonContext;
import io.operon.runner.util.RandomUtil;

import java.util.Date;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.system.IntegrationComponent;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.Context;
import io.operon.runner.processor.function.core.string.StringToRaw;

import io.operon.runner.system.ComponentSystemUtil;
import io.operon.runner.system.integration.BaseComponent;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.exception.OperonComponentException;

import org.apache.logging.log4j.LogManager;


public class CipherComponent extends BaseComponent implements IntegrationComponent {
     // no logger 

    public CipherComponent() {}
    
    public OperonValue produce(OperonValue currentValue) throws OperonComponentException {
        try {
            Info info = this.resolve(currentValue);
            
            byte[] originalBytes = {0};
            
            currentValue = currentValue.evaluate(); // ensure that value is unboxed
            
            if (currentValue instanceof RawValue) {
                //System.out.println("currentValue was RawValue");
                originalBytes = ((RawValue) currentValue).getBytes(); // StandardCharsets.UTF_8
                //System.out.println("  >> " + originalBytes);
            }
            else if (currentValue instanceof StringType) {
                //System.out.println("currentValue was " + currentValue.getClass().getName());
                StringType jsStr = (StringType) currentValue;
                originalBytes = jsStr.getJavaStringValue().getBytes(StandardCharsets.UTF_8);
                // currentValue.toString().getBytes(StandardCharsets.UTF_8);
            }
            else {
                throw new OperonComponentException("Wrong input-type. Expected Raw or String.");
            }
            try {
                byte[] resultBytes = {0};
                if (info.algorithm == Algorithm.AES256) {
                    if (info.mode == Mode.ENCRYPT) {
                        resultBytes = this.doAes256Encrypt(info, originalBytes);
                    }
                    
                    else if (info.mode == Mode.DECRYPT) {
                        //System.out.println("decode bytes: " + new String(originalBytes, StandardCharsets.UTF_8));
                        if (info.decode) {
                            byte [] decodedBytes = {0};
                            if (info.decodeFrom == DecodeFrom.BASE64) {
                                decodedBytes = RawValue.base64ToBytes(originalBytes);
                            }
                            else if (info.decodeFrom == DecodeFrom.BASE64URLSAFE) {
                                decodedBytes = RawValue.base64UrlSafeToBytes(originalBytes);
                            }
                            else if (info.decodeFrom == DecodeFrom.HEX) {
                                decodedBytes = RawValue.hexToBytes(originalBytes);
                            }
                            //System.out.println("decoded bytes: " + new String(decodedBytes, StandardCharsets.UTF_8));
                            resultBytes = this.doAes256Decrypt(info, decodedBytes);
                        }
                        else {
                            resultBytes = this.doAes256Decrypt(info, originalBytes);
                        }
                    }
                }
                
                if (info.mode == Mode.ENCRYPT && info.writeAs == WriteAs.RAW == false) {
                    StringType result = new StringType(currentValue.getStatement());
                    
                    String resultFormatted = null;
                    
                    if (info.writeAs == WriteAs.HEX) {
                        resultFormatted = RawValue.bytesToHex(resultBytes);
                    }
                    else if (info.writeAs == WriteAs.BASE64) {
                        resultFormatted = RawValue.bytesToBase64(resultBytes, true);
                    }
                    else if (info.writeAs == WriteAs.BASE64NOPADDING) {
                        resultFormatted = RawValue.bytesToBase64(resultBytes, false);
                    }
                    else if (info.writeAs == WriteAs.BASE64URLSAFE) {
                        resultFormatted = RawValue.bytesToBase64UrlSafe(resultBytes, true);
                    }
                    else if (info.writeAs == WriteAs.BASE64URLSAFENOPADDING) {
                        resultFormatted = RawValue.bytesToBase64UrlSafe(resultBytes, false);
                    }
                    
                    result.setFromJavaString(resultFormatted);
                    return result;
                }
                
                else {
                   RawValue result = new RawValue(currentValue.getStatement());
                   result.setValue(resultBytes);
                   return result;
                }
            } catch (Exception e) {
                throw new OperonComponentException(e.getMessage());
            }
        } catch (OperonGenericException e) {
            throw new OperonComponentException(e.getErrorJson());
        }
    }
    
    public byte[] doAes256Encrypt(Info info, byte[] originalBytes) throws Exception {
      byte[] iv = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
      IvParameterSpec ivspec = new IvParameterSpec(iv);
 
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      KeySpec spec = new PBEKeySpec(info.secretKey.toCharArray(), info.salt.getBytes(), 65536, 256);
      SecretKey tmp = factory.generateSecret(spec);
      SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");
 
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivspec);
      return cipher.doFinal(originalBytes);
    }
    
    public byte[] doAes256Decrypt(Info info, byte[] originalBytes) throws Exception {
      byte[] iv = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
      IvParameterSpec ivspec = new IvParameterSpec(iv);
 
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      KeySpec spec = new PBEKeySpec(info.secretKey.toCharArray(), info.salt.getBytes(), 65536, 256);
      SecretKey tmp = factory.generateSecret(spec);
      SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");
 
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
      cipher.init(Cipher.DECRYPT_MODE, secretKey, ivspec);
      return cipher.doFinal(originalBytes);
    }
    
    public Info resolve(OperonValue currentValue) throws OperonGenericException {
        OperonValue currentValueCopy = currentValue;

        ObjectType jsonConfiguration = this.getJsonConfiguration();
        jsonConfiguration.getStatement().setCurrentValue(currentValueCopy);
        List<PairType> jsonPairs = jsonConfiguration.getPairs();

        Info info = new Info();
        
        for (PairType pair : jsonPairs) {
            String key = pair.getKey();
            OperonValue currentValueCopy2 = currentValue;
            pair.getStatement().setCurrentValue(currentValueCopy);
            switch (key.toLowerCase()) {
                case "\"algorithm\"":
                    Node algorithmNode = pair.getEvaluatedValue();
                    String algorithmStr = ((StringType) algorithmNode).getJavaStringValue();
                    info.algorithm = Algorithm.valueOf(algorithmStr.toUpperCase());
                    break;
                case "\"mode\"":
                    Node modeNode = pair.getEvaluatedValue();
                    String modeStr = ((StringType) modeNode).getJavaStringValue();
                    Mode m = Mode.valueOf(modeStr.toUpperCase());
                    if (m == Mode.ENCRYPT && info.mode == Mode.DECRYPT) {
                        System.err.println("Warning: invalid settings detected: when decode or decodeFrom is explicitly set, then mode must be decrypt. Forcing mode now to encrypt.");
                    }
                    info.mode = m;
                    break;
                case "\"writeas\"":
                    Node writeAsNode = pair.getEvaluatedValue();
                    String writeAsStr = ((StringType) writeAsNode).getJavaStringValue();
                    info.writeAs = WriteAs.valueOf(writeAsStr.toUpperCase());
                    break;
                case "\"decodefrom\"":
                    Node decodeFromNode = pair.getEvaluatedValue();
                    String decodeFromStr = ((StringType) decodeFromNode).getJavaStringValue();
                    info.decodeFrom = DecodeFrom.valueOf(decodeFromStr.toUpperCase());
                    info.mode = Mode.DECRYPT;
                    break;
                case "\"secretkey\"":
                    Node secretKeyNode = pair.getEvaluatedValue();
                    String secretKeyStr = ((StringType) secretKeyNode).getJavaStringValue();
                    info.secretKey = secretKeyStr;
                    break;
                case "\"salt\"":
                    Node saltNode = pair.getEvaluatedValue();
                    String saltStr = ((StringType) saltNode).getJavaStringValue();
                    info.salt = saltStr;
                    break;
                case "\"decode\"":
                    Node decode_Node = pair.getEvaluatedValue();
                    if (decode_Node instanceof TrueType) {
                        info.decode = true;
                    }
                    else {
                        info.decode = false;
                    }
                    info.mode = Mode.DECRYPT;
                    break;
                default:
                    //:OFF:log.debug("digest -producer: no mapping for configuration key: " + key);
                    System.err.println("digest -producer: no mapping for configuration key: " + key);
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "DIGEST", "ERROR", "digest -producer: no mapping for configuration key: " + key);
            }
        }
        
        currentValue.getStatement().setCurrentValue(currentValueCopy);
        return info;
    }

    private class Info {
        private Algorithm algorithm = Algorithm.AES256;
        private Mode mode = Mode.ENCRYPT;
        private WriteAs writeAs = WriteAs.BASE64;
        private boolean decode = true; // when mode=DECRYPT, this tells if to decode the input first (e.g. from Base64). NOTE: setting this sets also the mode to decrypt.
        private DecodeFrom decodeFrom = DecodeFrom.BASE64; // NOTE: setting this sets also the mode to decrypt.
        private String secretKey = "";
        private String salt = "0";
        private String binaryEncoding = "utf-8"; // null = none, print as raw. UTF-8
    }

    enum Mode {
        ENCRYPT, DECRYPT;
    }

    enum WriteAs {
        BASE64, BASE64NOPADDING, BASE64URLSAFE, BASE64URLSAFENOPADDING, RAW, HEX;
    }
    
    enum DecodeFrom {
        BASE64, BASE64URLSAFE, HEX;
    }
    
    enum Algorithm {
        AES256("AES-256");
        
        private final String alg;
        
        Algorithm(final String alg) {
            this.alg = alg;
        }
        
        @Override
        public String toString() {
            return this.alg;
        }
    }
}