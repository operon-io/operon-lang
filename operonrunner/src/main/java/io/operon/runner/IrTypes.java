package io.operon.runner;

// 
// Serialization codes for IR (Intermediate Representation) types.
// These appear in the serialized output
// 
public class IrTypes {
    public static byte NUMBER_TYPE = 0;
    public static byte STRING_TYPE = 1;
    public static byte OBJECT_TYPE = 2;
    public static byte ARRAY_TYPE = 3;
    public static byte EMPTY_TYPE = 4;
    public static byte END_VALUE_TYPE = 5;
    public static byte ERROR_VALUE = 6;
    public static byte FALSE_TYPE = 7;
    public static byte TRUE_TYPE = 8;
    public static byte PAIR_TYPE = 9;
    public static byte PATH_TYPE = 10;
    public static byte RAW_VALUE = 11;
    public static byte STREAM_VALUE = 12;
    public static byte VALUE_CONSTRAINT = 13;
    public static byte NULL_TYPE = 14;
    public static byte OPERON_VALUE = 15;
    
    public static byte UNARY_NODE = 20;
    public static byte BINARY_NODE = 21;
    public static byte MULTI_NODE = 22;
    
    public static byte VALUE_REF = 30;
    public static byte CHOICE = 31;
    public static byte ASSIGN = 32;
    public static byte BREAK_LOOP = 33;
    public static byte AGGREGATE = 34;
    public static byte FUNCTION_CALL = 35;
    public static byte FUNCTION_REF = 36;
    
    public static byte FUNCTION_0 = 100;
    public static byte FUNCTION_1 = 101;
    public static byte FUNCTION_2 = 102;
}