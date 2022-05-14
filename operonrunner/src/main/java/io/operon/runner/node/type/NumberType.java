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

package io.operon.runner.node.type;

import java.text.NumberFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;

public class NumberType extends OperonValue implements Node, AtomicOperonValue, Comparable {

	public static Locale defaultLocale = Locale.US;
	private static DecimalFormatSymbols dfs = new DecimalFormatSymbols(defaultLocale);
	private static NumberFormat DF_1 =       new DecimalFormat("0", dfs);
	private static NumberFormat DF_PREC_1 =  new DecimalFormat("0.0", dfs);
	private static NumberFormat DF_PREC_2 =  new DecimalFormat("0.00", dfs);
	private static NumberFormat DF_PREC_3 =  new DecimalFormat("0.000", dfs);
	private static NumberFormat DF_PREC_4 =  new DecimalFormat("0.0000", dfs);
	private static NumberFormat DF_PREC_5 =  new DecimalFormat("0.00000", dfs);
	private static NumberFormat DF_PREC_6 =  new DecimalFormat("0.000000", dfs);
	private static NumberFormat DF_PREC_7 =  new DecimalFormat("0.0000000", dfs);
	private static NumberFormat DF_PREC_8 =  new DecimalFormat("0.00000000", dfs);
	private static NumberFormat DF_PREC_9 =  new DecimalFormat("0.000000000", dfs);
	private static NumberFormat DF_PREC_10 = new DecimalFormat("0.0000000000", dfs);
	private static NumberFormat DF_PREC_11 = new DecimalFormat("0.00000000000", dfs);
	private static NumberFormat DF_PREC_12 = new DecimalFormat("0.000000000000", dfs);
	private static NumberFormat DF_PREC_13 = new DecimalFormat("0.0000000000000", dfs);
	private static NumberFormat DF_PREC_14 = new DecimalFormat("0.00000000000000", dfs);
	private static NumberFormat DF_PREC_15 = new DecimalFormat("0.000000000000000", dfs);
	private static NumberFormat DF_PREC_16 = new DecimalFormat("0.0000000000000000", dfs);
	private static NumberFormat DF_PREC_17 = new DecimalFormat("0.00000000000000000", dfs);
	private static NumberFormat DF_PREC_18 = new DecimalFormat("0.000000000000000000", dfs);

    private byte precision; // -1 = undefined, 0 = 0 decimals, 1 = 1 decimal, etc.
    
    private double value;
    
    public NumberType(Statement stmnt) {
        super(stmnt);
        this.precision = -1;
    }

    public void setDoubleValue(double value) {
        this.value = value;
    }

    public NumberType evaluate() throws OperonGenericException {
        this.setUnboxed(true);
        return this;
    }
    
    //
    // precision -1 means automatic precision resolution is applied.
    //
    public static NumberType create(Statement stmt, double value, byte precision) {
        NumberType result = new NumberType(stmt);
        result.setDoubleValue(value);
        if (precision > -1) {
            result.setPrecision(precision);
        }
        return result;
    }
    
    public OperonValue getValue() {
        return this;
    }
    
    public double getDoubleValue() {
        return (double) this.value;
    }

    public void setPrecision(byte p) {
        this.precision = p;
    }
    
    public byte getPrecision() {
        return this.precision;
    }
    
    public void resolvePrecision() {
        // Resolve missing precision:
        if (this.getPrecision() == -1) {
            if (String.valueOf(this.getDoubleValue()).split("\\.")[1].equals("0")) {
                this.setPrecision((byte) 0);
            }
            else {
                this.setPrecision(NumberType.getPrecisionFromStr(String.valueOf(this.getDoubleValue())));
            }
        }
    }
    
    //
    // The precision is e.g. "100.00" --> 2
    //
    public static byte getPrecisionFromStr(String value) {
        byte result = 0; // No decimal part.
        value = value.toLowerCase();
        int ePos = value.indexOf("e");
        if (ePos >= 0) {
            if (value.charAt(ePos + 1) == '+') {
                value = value.substring(0, ePos);
            }
            
            else if (value.charAt(ePos + 1) == '-') {
               // Resolve precision from mantissa:
               result = Byte.parseByte( value.substring(ePos+2, value.length()) );
               return result;
            }
            
            else {
                // e.g. '1.234E7', i.e. missing '+'
                value = value.substring(0, ePos);
            }
        }
        
        String [] valueStrParts = value.split("\\.");
        if (valueStrParts.length == 2) {
            result = (byte) valueStrParts[1].length(); // Number of decimals.
        }
        return result;
    }
    
    public int compareTo(Object n) {
        if (n == null) {
            throw new NullPointerException();
        }
        if (n instanceof NumberType == false) {
            throw new ClassCastException();
        }
        if (this.getDoubleValue() < ((NumberType) n).getDoubleValue()) {
            return -1;
        }
        else if (this.getDoubleValue() == ((NumberType) n).getDoubleValue()) {
            return 0;
        }
        else {
            return 1;
        }
    }
    
    public static int getPrecision(double value) {
        return getPrecisionFromStr(String.valueOf(value));
    }
    
    @Override
    public String toString() {
		//System.out.println("Default locale=" + defaultLocale);
		//System.out.println("Default locale minus=" + dfs.getMinusSign());
		//System.out.println("Precision=" + this.getPrecision());
		if (this.getPrecision() != -1 && this.getPrecision() == 0) {
            return DF_1.format(this.getDoubleValue());
        }
        
        else if (this.getPrecision() != -1 && this.getPrecision() > 0) {
			NumberFormat format = null;
			switch (this.getPrecision()) {
				case 1: format = DF_PREC_1;
					break;
				case 2: format = DF_PREC_2;
					break;
				case 3: format = DF_PREC_3;
					break;
				case 4: format = DF_PREC_4;
					break;
				case 5: format = DF_PREC_5;
					break;
				case 6: format = DF_PREC_6;
					break;
				case 7: format = DF_PREC_7;
					break;
				case 8: format = DF_PREC_8;
					break;
				case 9: format = DF_PREC_9;
					break;
				case 10: format = DF_PREC_10;
					break;
				case 11: format = DF_PREC_11;
					break;
				case 12: format = DF_PREC_12;
					break;
				case 13: format = DF_PREC_13;
					break;
				case 14: format = DF_PREC_14;
					break;
				case 15: format = DF_PREC_15;
					break;
				case 16: format = DF_PREC_16;
					break;
				case 17: format = DF_PREC_17;
					break;
				case 18: format = DF_PREC_18;
					break;
				default: format = DF_PREC_18;
			}
            return format.format(this.getDoubleValue());
        }
        
        else {
            String result = String.valueOf(this.getDoubleValue());
            return DF_1.format(this.getDoubleValue());
        }
    }

    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        if (ofmt == null) {ofmt = new OutputFormatter();}
		return this.toString();
    }
    
    @Override
    public String toYamlString(YamlFormatter yf) {
        if (yf == null) {yf = new YamlFormatter();}
		//System.out.println("Default locale=" + defaultLocale);
		//System.out.println("Default locale minus=" + dfs.getMinusSign());
		//System.out.println("Precision=" + this.getPrecision());
		if (this.getPrecision() != -1 && this.getPrecision() == 0) {
            return DF_1.format(this.getDoubleValue());
        }
        
        else if (this.getPrecision() != -1 && this.getPrecision() > 0) {
			NumberFormat format = null;
			switch (this.getPrecision()) {
				case 1: format = DF_PREC_1;
					break;
				case 2: format = DF_PREC_2;
					break;
				case 3: format = DF_PREC_3;
					break;
				case 4: format = DF_PREC_4;
					break;
				case 5: format = DF_PREC_5;
					break;
				case 6: format = DF_PREC_6;
					break;
				case 7: format = DF_PREC_7;
					break;
				case 8: format = DF_PREC_8;
					break;
				case 9: format = DF_PREC_9;
					break;
				case 10: format = DF_PREC_10;
					break;
				case 11: format = DF_PREC_11;
					break;
				case 12: format = DF_PREC_12;
					break;
				case 13: format = DF_PREC_13;
					break;
				case 14: format = DF_PREC_14;
					break;
				case 15: format = DF_PREC_15;
					break;
				case 16: format = DF_PREC_16;
					break;
				case 17: format = DF_PREC_17;
					break;
				case 18: format = DF_PREC_18;
					break;
				default: format = DF_PREC_18;
			}
            return format.format(this.getDoubleValue());
        }
        
        else {
            String result = String.valueOf(this.getDoubleValue());
            return DF_1.format(this.getDoubleValue());
        }
    }
    
    @Override
    public String toTomlString(OutputFormatter ofmt) {
        if (ofmt == null) {ofmt = new OutputFormatter();}
		return this.toString();
    }
}