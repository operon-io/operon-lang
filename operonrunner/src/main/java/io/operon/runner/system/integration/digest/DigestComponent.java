/** OPERON-LICENSE **/
package io.operon.runner.system.integration.digest;

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


public class DigestComponent extends BaseComponent implements IntegrationComponent {
    private static Logger log = LogManager.getLogger(DigestComponent.class);

    public DigestComponent() {}
    
    public OperonValue produce(OperonValue currentValue) throws OperonComponentException {
        try {
            Info info = this.resolve(currentValue);
            
            byte[] originalBytes = {0};
            
            if (currentValue instanceof RawValue) {
                //System.out.println("currentValue was RawValue");
                originalBytes = ((RawValue) currentValue).getBytes(); // StandardCharsets.UTF_8
                //System.out.println("  >> " + originalBytes);
            }
            else {
                //System.out.println("currentValue was " + currentValue.getClass().getName());
                originalBytes = currentValue.toString().getBytes();
                // currentValue.toString().getBytes(StandardCharsets.UTF_8);
            }
            try {
                byte[] resultBytes = {0};
                
                if (info.algorithm == Algorithm.SHA256 || 
                    info.algorithm == Algorithm.SHA1 ||
                    info.algorithm == Algorithm.MD5) {
                    resultBytes = this.doDigest(info, originalBytes);
                }
                
                else if (info.algorithm == Algorithm.HMACSHA256 ||
                         info.algorithm == Algorithm.HS256 ||
                         info.algorithm == Algorithm.HMACSHA512 ||
                         info.algorithm == Algorithm.HS512 ||
                         info.algorithm == Algorithm.HMACSHA1 ||
                         info.algorithm == Algorithm.HS1 ||
                         info.algorithm == Algorithm.HMACMD5 ||
                         info.algorithm == Algorithm.HMD5) {
                    resultBytes = this.doMacDigest(info, originalBytes);
                }
                
                if (info.writeAs == WriteAs.RAW == false) {
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
            } catch (NoSuchAlgorithmException nsae) {
                throw new OperonComponentException(nsae.getMessage());
            } catch (UnsupportedEncodingException uee) {
                throw new OperonComponentException(uee.getMessage());
            } catch (InvalidKeyException ike) {
                throw new OperonComponentException(ike.getMessage());
            }
        } catch (OperonGenericException e) {
            throw new OperonComponentException(e.getErrorJson());
        }
    }
    
    public byte[] doDigest(Info info, byte[] originalBytes) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(info.algorithm.toString());
        byte[] encodedhash = digest.digest(originalBytes);
        return encodedhash;
    }
    
    public byte[] doMacDigest(Info info, byte[] originalBytes) throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeyException {
        // 1. Get an algorithm instance.
        Algorithm algorithm = info.algorithm;
        switch (info.algorithm) {
            case HS256:
                algorithm = Algorithm.HMACSHA256;
                break;
            case HS512:
                algorithm = Algorithm.HMACSHA512;
                break;
            case HS1:
                algorithm = Algorithm.HMACSHA1;
                break;
            case HMD5:
                algorithm = Algorithm.HMACMD5;
                break;
        }
        Mac sha256_hmac = Mac.getInstance(algorithm.toString());
        SecretKeySpec secret_key = new SecretKeySpec(info.secretKey.getBytes(), info.algorithm.toString());
        sha256_hmac.init(secret_key);
        byte[] hash = sha256_hmac.doFinal(originalBytes);
        return hash;

        /**
         * Here are the outputs for given algorithms:-
         * 
         * HmacMD5 = hpytHW6XebJ/hNyJeX/A2w==
         * HmacSHA1 = CZbtauhnzKs+UkBmdC1ssoEqdOw=
         * HmacSHA256 =gCZJBUrp45o+Z5REzMwyJrdbRj8Rvfoy33ULZ1bySXM=
         * HmacSHA512 = OAqi5yEbt2lkwDuFlO6/4UU6XmU2JEDuZn6+1pY4xLAq/JJGSNfSy1if499coG1K2Nqz/yyAMKPIx9C91uLj+w==
         */

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
                case "\"writeas\"":
                    Node writeAsNode = pair.getEvaluatedValue();
                    String writeAsStr = ((StringType) writeAsNode).getJavaStringValue();
                    info.writeAs = WriteAs.valueOf(writeAsStr.toUpperCase());
                    break;
                case "\"secretkey\"":
                    Node secretKeyNode = pair.getEvaluatedValue();
                    String secretKeyStr = ((StringType) secretKeyNode).getJavaStringValue();
                    info.secretKey = secretKeyStr;
                    break;
                default:
                    log.debug("digest -producer: no mapping for configuration key: " + key);
                    System.err.println("digest -producer: no mapping for configuration key: " + key);
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "DIGEST", "ERROR", "digest -producer: no mapping for configuration key: " + key);
            }
        }
        
        currentValue.getStatement().setCurrentValue(currentValueCopy);
        return info;
    }

    private class Info {
        private Algorithm algorithm = Algorithm.SHA256;
        private WriteAs writeAs = WriteAs.HEX;
        private String secretKey = "";
        private String binaryEncoding = "utf-8"; // null = none, print as raw. UTF-8
    }

    enum WriteAs {
        BASE64, BASE64NOPADDING, BASE64URLSAFE, BASE64URLSAFENOPADDING, RAW, HEX;
    }
    
    enum Algorithm {
        SHA1("SHA-1"),
        SHA256("SHA-256"),
        MD5("MD5"),
        HMACSHA512("HMACSHA512"),
        HS512("HS512"),
        HMACSHA256("HMACSHA256"),
        HS256("HS256"),
        HMACSHA1("HMACSHA1"),
        HS1("HS1"),
        HMACMD5("HMACMD5"),
        HMD5("HMD5");
        
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