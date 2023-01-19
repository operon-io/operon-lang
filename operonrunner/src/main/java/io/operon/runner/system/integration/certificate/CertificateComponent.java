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

package io.operon.runner.system.integration.certificate;

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
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.InvocationTargetException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.InvalidKeyException;
import java.security.spec.InvalidKeySpecException;

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

import java.io.ByteArrayOutputStream;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;


public class CertificateComponent extends BaseComponent implements IntegrationComponent {
     // no logger 

    public CertificateComponent() {}
    
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
                if (info.method.toLowerCase().equals("verify")) {
                    boolean verifyResult = this.doVerify(info, originalBytes);
                    if (verifyResult == true) {
                        return new TrueType(currentValue.getStatement());
                    }
                    else {
                        return new FalseType(currentValue.getStatement());
                    }
                }
                else if (info.method.toLowerCase().equals("sign")) {
                    byte[] signature = this.doSign(info, originalBytes);
                    RawValue result = new RawValue(currentValue.getStatement());
                    result.setValue(signature);
                    return result;
                }
                else {
                    return new EmptyType(currentValue.getStatement());
                }
            } catch (NoSuchAlgorithmException nsae) {
                throw new OperonComponentException(nsae.getMessage());
            } catch (SignatureException se) {
                throw new OperonComponentException(se.getMessage());
            } catch (InvalidKeyException ike) {
                throw new OperonComponentException(ike.getMessage());
            } catch (InvalidKeySpecException ikse) {
                throw new OperonComponentException(ikse.getMessage());
            }
        } catch (OperonGenericException e) {
            throw new OperonComponentException(e.getErrorJson());
        }
    }
    
    public boolean doVerify(Info info, byte[] originalBytes)
        throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, InvalidKeySpecException {
        
        if (info.publicKey == null || info.publicKey.length == 0) {
            throw new InvalidKeyException("Missing public key");
        }
        
        else if (info.signature == null || info.signature.length == 0) {
            throw new InvalidKeyException("Missing signature");
        }
        
        //System.out.println("PublicKey bytes: " + new String(info.publicKey));
        
        //System.out.println("Public key base64: " + RawValue.bytesToBase64(info.publicKey));
        
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(info.publicKey);
        //RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(info.publicKey);
        KeyFactory keyFactory = KeyFactory.getInstance(info.keyAlgorithm.toString());
        PublicKey pubKey = keyFactory.generatePublic(publicKeySpec);
        
        //System.out.println("PublicKey loaded.");
        
        Signature sig = Signature.getInstance(info.signatureAlgorithm.toString());
        sig.initVerify(pubKey);
        //System.out.println("originalBytes length: " + originalBytes.length);
        sig.update(originalBytes);
        
        Boolean verifyResult = null;
        //System.out.println("Signature base64: \"" + RawValue.bytesToBase64(info.signature) + "\"");
        verifyResult = sig.verify(info.signature);
        
        
        //if (verifyResult == true) {
        //    System.out.println("Signature verified");   
        //} else {
        //    System.out.println("Signature verify failed");
        //}
        return verifyResult;
    }
    
    public byte[] doSign(Info info, byte[] originalBytes)
        throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, InvalidKeySpecException {
        
        //System.out.println("Signing value:");
        //System.out.println(new String(originalBytes));
        
        if (info.privateKey == null || info.privateKey.length == 0) {
            throw new InvalidKeyException("Missing private key");
        }
        
        KeyFactory kf = KeyFactory.getInstance(info.keyAlgorithm.toString());

        //System.out.println("Private key:" + new String(info.privateKey));

        PKCS8EncodedKeySpec privateKeySpecPKCS8 = new PKCS8EncodedKeySpec(info.privateKey);
        PrivateKey privKey = kf.generatePrivate(privateKeySpecPKCS8);
        //System.out.println("PrivateKey loaded successfully: " + info.privateKey);
        
        //System.out.println("Start signing.");
        //Creating a Signature object
        Signature sign = Signature.getInstance(info.signatureAlgorithm.toString());
        
        //Initializing the signature
        sign.initSign(privKey);
        
        //Adding data to the signature
        sign.update(originalBytes);
        
        //Calculating the signature
        byte[] signature = sign.sign();
        
        //System.out.println("Signature created.");
        return signature;
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
            pair.getStatement().setCurrentValue(currentValueCopy2);
            switch (key.toLowerCase()) {
                case "\"method\"":
                    Node methodNode = pair.getEvaluatedValue();
                    String methodStr = ((StringType) methodNode).getJavaStringValue();
                    info.method = methodStr;
                    break;
                case "\"keyalgorithm\"":
                    Node keyAlgorithmNode = pair.getEvaluatedValue();
                    String keyAlgorithmStr = ((StringType) keyAlgorithmNode).getJavaStringValue();
                    info.keyAlgorithm = KeyAlgorithm.valueOf(keyAlgorithmStr.toUpperCase());
                    break;
                case "\"signaturealgorithm\"":
                    Node signatureAlgorithmNode = pair.getEvaluatedValue();
                    String signatureAlgorithmStr = ((StringType) signatureAlgorithmNode).getJavaStringValue();
                    info.signatureAlgorithm = SignatureAlgorithm.valueOf(signatureAlgorithmStr.toUpperCase());
                    break;
                case "\"privatekey\"":
                    Node privateKeyNode = pair.getEvaluatedValue();
                    byte[] privateKeyBytes = ((RawValue) privateKeyNode).getBytes();
                    info.privateKey = privateKeyBytes;
                    break;
                case "\"publickey\"":
                    Node publicKeyNode = pair.getEvaluatedValue();
                    byte[] publicKeyBytes = ((RawValue) publicKeyNode).getBytes();
                    info.publicKey = publicKeyBytes;
                    break;
                case "\"signature\"":
                    Node signatureNode = pair.getEvaluatedValue();
                    byte[] signatureBytes = ((RawValue) signatureNode).getBytes();
                    info.signature = signatureBytes;
                    break;
                default:
                    //:OFF:log.debug("certificate -producer: no mapping for configuration key: " + key);
                    System.err.println("certificate -producer: no mapping for configuration key: " + key);
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "CERTIFICATE", "ERROR", "certificate -producer: no mapping for configuration key: " + key);
            }
        }
        
        currentValue.getStatement().setCurrentValue(currentValueCopy);
        return info;
    }

    private class Info {
        private String method = "verify"; // sign, verify
        private KeyAlgorithm keyAlgorithm = KeyAlgorithm.RSA;
        private SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.SHA256WITHRSA;
        private byte[] privateKey = null;
        private byte[] publicKey = null;
        private byte[] signature = null;
        private String binaryEncoding = "utf-8"; // null = none, print as raw. UTF-8
    }

    enum KeyAlgorithm {
        DSA("DSA"),
        RSA("RSA"),
        SHA256WITHDSA("SHA256WithDSA");
        
        private final String alg;
        
        KeyAlgorithm(final String alg) {
            this.alg = alg;
        }
        
        @Override
        public String toString() {
            return this.alg;
        }
    }

    enum SignatureAlgorithm {
        DSA("DSA"),
        SHA256WITHRSA("SHA256withRSA"),
        SHA256WITHDSA("SHA256WithDSA");
        
        private final String alg;
        
        SignatureAlgorithm(final String alg) {
            this.alg = alg;
        }
        
        @Override
        public String toString() {
            return this.alg;
        }
    }

}