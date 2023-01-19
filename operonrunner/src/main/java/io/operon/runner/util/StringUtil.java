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

package io.operon.runner.util;

import java.security.MessageDigest;
import java.io.IOException;
import java.util.Random;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import io.operon.runner.OperonContext;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.Main;

public class StringUtil {

    public static String prefix(String value, String c, int count) {
        if (count - value.length() > 0) {
            String result = "";
            for (int i = 0; i < (count - value.length()); i ++) {
                result += c;
            }
            return result + value;
        }
        else {
            return value;
        }
    }

    public static String sha256Hex(byte[] encoded) throws OperonGenericException {
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(encoded);
            StringBuffer hexString = new StringBuffer();
    
            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if(hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
    
            return hexString.toString();
        } catch (Exception ex){
           try {
               OperonContext ctx = new OperonContext();
               DefaultStatement stmt = new DefaultStatement(ctx);
               String type = "STRING";
               String code = "SHA-256-HEX";
               String message = "Could not format sha-256 into hex.";
               ErrorUtil.createErrorValueAndThrow(stmt, type, code, message);
               return null;
           } catch (IOException ioe) {
               return null;
           }
        }
    }

    public static String loadFileAsString(String pathToFile) throws Exception {
        Path path = Paths.get(pathToFile.toString());
        String fileContent = null;
        fileContent = new String(Files.readAllBytes(path), Main.defaultCharset);
        return fileContent;
    }

    //
    // Returns standard deviation of the data
    //
    public static double standardDeviation(byte[] data) {
        int[] freqMap = new int[256];
        for (int i = 0; i < 256; i ++) {
            freqMap[i] = 0;
        }
        
        for (int i = 0; i < data.length; i ++) {
            int int_byte = ((int) data[i]) + 128;
            int freq = freqMap[int_byte] + 1;
            freqMap[int_byte] = freq;
        }
        
        int sum = 0;
        for (int i = 0; i < 256; i ++) {
            int f = freqMap[i];
            sum += f;
        }
        double avg = sum / 256;
        double sum_avg = 0;
        for (int i = 0; i < 256; i ++) {
            int f = freqMap[i];
            double n = (f - avg) * (f - avg);
            sum_avg += n;
        }
        double standard_dev = Math.sqrt(sum_avg / 256);
        return standard_dev;
    }

    public static class MatchResult {
        static int max_xor_matching_bits = 0;
        static int max_1_bits = 0;
        static byte[] mutated = {0};
        static int result_i = 0;
        static int result_seed = 0;
    }

    public static void mutate_and_find_max_xor_matching_bits(byte[] data, int mask_len, int seed) {
        int iterations = 100000;
        
        for (int i = 0; i < iterations; i ++) {
            byte[] mutated = xor_mutate(data, seed + i, mask_len);
            int match_count = xor_matching_bits(data, mutated);
            if (match_count > MatchResult.max_xor_matching_bits) {
                MatchResult.max_xor_matching_bits = match_count;
                MatchResult.mutated = mutated;
                MatchResult.result_i = i;
                MatchResult.result_seed = seed;
                System.out.println("  ++ max_xor_matching_bits=" + MatchResult.max_xor_matching_bits);
            }
            if (i % 10000 == 0) {
                System.out.println("  - mutate_and_find_max_xor_matching_bits: iteration = " + i);
            }
        }
        System.out.println("REPORT: max_xor_matching_bits=" + MatchResult.max_xor_matching_bits + ", i=" + MatchResult.result_i);
    }

    public static boolean byte_array_equals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i ++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    public static void mutate_and_find_max_1_bits(byte[] data, int mask_len, int seed) {
        int iterations = 100000;
        
        for (int i = 0; i < iterations; i ++) {
            byte[] mutated = xor_mutate(data, seed + i, mask_len);
            int match_count = count_1_bits(mutated);
            if (match_count > MatchResult.max_1_bits) {
                MatchResult.max_1_bits = match_count;
                MatchResult.mutated = mutated;
                MatchResult.result_i = i;
                MatchResult.result_seed = seed;
                System.out.println("  ++ max_1_bits=" + MatchResult.max_1_bits);
            }
            if (i % 10000 == 0) {
                System.out.println("  - mutate_and_find_max_1_bits: iteration = " + i);
            }
        }
        System.out.println("REPORT: max_1_bits=" + MatchResult.max_1_bits + ", i=" + MatchResult.result_i);
    }

    public static byte[] xor_mutate(byte[] data, int seed, int mask_len) {
        Random r = new Random(seed);
        int len = data.length;
        byte[] mask = new byte[mask_len];
        r.nextBytes(mask);
        byte[] mutated = new byte[len];
        int mask_i = 0;
        for (int i = 0; i < data.length; i ++) {
            mutated[i] = (byte) (data[i] ^ mask[mask_i]);
            mask_i += 1;
            if (mask_i >= mask_len) {
                mask_i = 0;
            }
        }
        return mutated;
    }

    public static byte[] xor_bytes(byte[] data, byte[] mask) {
        byte[] result = new byte[data.length];
        int mask_i = 0;
        for (int i = 0; i < data.length; i ++) {
            result[i] = (byte) (data[i] ^ mask[mask_i]);
            mask_i += 1;
            if (mask_i >= mask.length) {
                mask_i = 0;
            }
        }
        return result;
    }

    public static int xor_matching_bits(byte[] a, byte[] b) {
        int xor_matching_bits = 0;
        for (int i = 0; i < a.length; i ++) {
            String bits_a_byte = toBits(a[i]);
            String bits_b_byte = toBits(b[i]);
            for (int j = 0; j < 8; j ++) {
                if (bits_a_byte.charAt(j) != bits_b_byte.charAt(j)) {
                    xor_matching_bits += 1;
                }
            }
        }
        return xor_matching_bits;
    }
 
    public static String toBits(final byte val) {
    	final StringBuilder result = new StringBuilder();
    	for (int i = 0; i < 8; i ++) {
    		result.append((int)(val >> (8 - (i + 1)) & 0x0001));
    	}
    	return result.toString();
    }

    public static int count_1_bits(byte[] data) {
    	int count = 0;
    	for (int i = 0; i < data.length; i ++) {
    	    String bits = toBits(data[i]);
    	    for (int j = 0; j < 8; j ++) {
    	        if (bits.charAt(j) == '1') {
    	            count += 1;
    	        }
    	    }
    	}
    	return count;
    }

}