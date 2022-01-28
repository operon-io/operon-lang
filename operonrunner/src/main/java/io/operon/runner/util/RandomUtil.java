/** OPERON-LICENSE **/
package io.operon.runner.util;

import java.util.Random;

public class RandomUtil {
    // Used to generate the correlationId and transactionId.
    private static final String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    
    public static String randomAlphaNumeric(int count) {
        StringBuilder builder = new StringBuilder();
        while (count-- != 0) {
            int character = (int) (Math.random() * ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        }
        return builder.toString();
    }
    
    // Used to generate the correlationId, and transactionId.
    public static String randomAlphaNumericSeeded(int count, long seed) {
        StringBuilder builder = new StringBuilder();
        Random rand = new Random(seed);
        while (count-- != 0) {
            int character = (int) (rand.nextInt() * ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        }
        return builder.toString();
    }
}