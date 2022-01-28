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

package io.operon.runner.processor.function.core.date;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.text.ParseException;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.Arity3;
import io.operon.runner.util.StringUtil;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class DateAdd extends AbstractNode implements Node, Arity3 {
    
    private Node param1; // amount to add
    private Node param2; // field to add
    private Node param3; // date-obj
    
    private String functionName = "add";
    
    private ObjectType dateObj = null;
    private int amountToAdd = 0;
    private String fieldToUpdate = "Day"; // Possible fields: "Year", "Month", "Day", "Hour", "Minute", "Second", "Millisecond"
    
    public DateAdd(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        
        //
        // TODO: support three params, where third params means which field to update ("Year", "Month", etc.)
        //
        
        // param 1 ; amount to add
        // param 2 : field to add
        // param 3 : date obj (note, actually in reverse order!)
        
        //System.out.println("Params size :: " + params.size());
        
        if (params.size() == 1) {
            //System.out.println("Params :: " + params);
            try {
                if (params.get(0) instanceof FunctionRegularArgument) {
                    this.setParam1( ((FunctionRegularArgument) (params.get(0))) );
                }
                // named arg not supported yet
            } catch (Exception e) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "date:" + this.getFunctionName() + ":001", e.getMessage());
            }
        }
        
        else if (params.size() == 2) {
            //System.out.println("Params 2 :: " + params);
            try {
                if (params.get(0) instanceof FunctionRegularArgument && params.get(1) instanceof FunctionRegularArgument) {
                    // TODO: parameters in reverse order??? --> check from other functions!
                    this.setParam1( ((FunctionRegularArgument) params.get(0)) );
                    this.setParam2( ((FunctionRegularArgument) params.get(1)) );
                }
                // named arg not supported yet
            } catch (Exception e) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "date:" + this.getFunctionName() + ":002", e.getMessage());
            }
        }
        
        else if (params.size() == 3) {
            //System.out.println("Params 3 :: " + params);
            try {
                if (params.get(0) instanceof FunctionRegularArgument && params.get(1) instanceof FunctionRegularArgument) {
                    // TODO: parameters in reverse order??? --> check from other functions!
                    
                    this.setParam1( ((FunctionRegularArgument) params.get(0)) );
                    this.setParam2( ((FunctionRegularArgument) params.get(1)) );
                    this.setParam3( ((FunctionRegularArgument) params.get(2)) );
                }
                // named arg not supported yet
            } catch (Exception e) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "date:" + this.getFunctionName() + ":003", e.getMessage());
            }
        }
        
    }

    public ObjectType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            
            if (this.getParam1() != null && this.getParam2() == null) {
                double amountToAddDouble = ((NumberType) ((FunctionRegularArgument) param1).getArgument().evaluate().evaluate()).getDoubleValue();
                amountToAdd = (int) amountToAddDouble;
            }
            
            else if (this.getParam1() != null && this.getParam2() != null && this.getParam3() == null) {
                fieldToUpdate = ((StringType) ((FunctionRegularArgument) this.getParam1()).getArgument().evaluate()).getJavaStringValue();
                this.getStatement().setCurrentValue(currentValue);
                
                //System.out.println("Params 1 :: evaluate to NumberType");
                double amountToAddDouble = ((NumberType) ((FunctionRegularArgument) this.getParam2()).getArgument().evaluate().evaluate()).getDoubleValue();
                this.getStatement().setCurrentValue(currentValue);
                amountToAdd = (int) amountToAddDouble;
                //System.out.println("Params 2 :: evaluate to NumberType to int :: " + amountToAdd);
            }
            
            else if (this.getParam1() != null && this.getParam2() != null && this.getParam3() != null) {
                Node dateNode = ((FunctionRegularArgument) this.getParam1()).getArgument(); // get from reverse order
                
                if (dateNode instanceof ObjectType == false) {
                    //System.out.println(">> 3.1 :: " + dateNode);
                    //dateObj = (ObjectType) dateNode.evaluate();
                    OperonValue evaluatedDateObj = dateNode.evaluate();
                    this.dateObj = (ObjectType) evaluatedDateObj.evaluate();
                    this.getStatement().setCurrentValue(currentValue);
                    //System.out.println("date :: " + date);
                }
                
                fieldToUpdate = ((StringType) ((FunctionRegularArgument) this.getParam2()).getArgument().evaluate()).getJavaStringValue();
                this.getStatement().setCurrentValue(currentValue);
                // evaluate twice because might still be OperonValue when referenced from ValueRef (or fr/lfr...)
                double amountToAddDouble = ((NumberType) ((FunctionRegularArgument) this.getParam3()).getArgument().evaluate().evaluate()).getDoubleValue();
                this.getStatement().setCurrentValue(currentValue);
                
                amountToAdd = (int) amountToAddDouble;
                //System.out.println("Params 2 :: evaluate to NumberType to int :: " + amountToAdd);
            }
            
            if (this.dateObj == null) {
                //System.out.println("CurrentValue :: " + currentValue);
                ObjectType currentValueObj = (ObjectType) currentValue.evaluate();
                //System.out.println("CurrentValue :: evaluated");
                
                ObjectType result = DateAdd.getDateAddObjectType(this.getStatement(), currentValueObj, this.amountToAdd, this.fieldToUpdate);
                return result;
            }
            // TODO: fix up a little bit, redundant with the above code!
            else {
                ObjectType result = DateAdd.getDateAddObjectType(this.getStatement(), this.dateObj, this.amountToAdd, this.fieldToUpdate);
                return result;
            }
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "date:" + this.getFunctionName() + ":004", e.getMessage());
            return null;
        }
    }

    //
    // Example result: {"Year": 2021, "Month": 8, "Day": 13, "Hour": 10, "Minute": 8, "Second": 0, "Millisecond": 1}
    //
    public static ObjectType getDateAddObjectType(Statement stmt, ObjectType date, int amountToAdd, String fieldToUpdate) throws OperonGenericException {
            ObjectType result = new ObjectType(stmt);

            List<PairType> jsonPairs = date.getPairs();
            
            String yearStr = "";
            String monthStr = "";
            String dayOfMonthStr = "";
            String hoursStr = "";
            String minutesStr = "";
            String secondsStr = "";
            String millisStr = "";

            for (int i = 0; i < jsonPairs.size(); i ++) {
                if (jsonPairs.get(i).getKey().equals("\"Year\"")) {
                    yearStr = String.valueOf( (int) ((NumberType) (jsonPairs.get(i).getValue().evaluate())).getDoubleValue() );
                }
                else if (jsonPairs.get(i).getKey().equals("\"Month\"")) {
                    monthStr = String.valueOf( (int) ((NumberType) (jsonPairs.get(i).getValue().evaluate())).getDoubleValue() );
                }
                else if (jsonPairs.get(i).getKey().equals("\"Day\"")) {
                    dayOfMonthStr = String.valueOf( (int) ((NumberType) (jsonPairs.get(i).getValue().evaluate())).getDoubleValue() );
                }
                else if (jsonPairs.get(i).getKey().equals("\"Hour\"")) {
                    hoursStr = String.valueOf( (int) ((NumberType) (jsonPairs.get(i).getValue().evaluate())).getDoubleValue() );
                }
                else if (jsonPairs.get(i).getKey().equals("\"Minute\"")) {
                    minutesStr = String.valueOf( (int) ((NumberType) (jsonPairs.get(i).getValue().evaluate())).getDoubleValue() );
                }
                else if (jsonPairs.get(i).getKey().equals("\"Second\"")) {
                    secondsStr = String.valueOf( (int) ((NumberType) (jsonPairs.get(i).getValue().evaluate())).getDoubleValue() );
                }
                else if (jsonPairs.get(i).getKey().equals("\"Millisecond\"")) {
                    millisStr = String.valueOf( (int) ((NumberType) (jsonPairs.get(i).getValue().evaluate())).getDoubleValue() );
                }
            }
            
            String dt = yearStr + "-" +  
                        StringUtil.prefix(monthStr, "0", 2) + "-" +
                        StringUtil.prefix(dayOfMonthStr, "0", 2) + " " +
                        StringUtil.prefix(hoursStr, "0", 2) + ":" +
                        StringUtil.prefix(minutesStr, "0", 2) + ":" +
                        StringUtil.prefix(secondsStr, "0", 2) + "." +
                        StringUtil.prefix(millisStr, "0", 3); // Start date
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            Calendar c = Calendar.getInstance();
            
            try {
                c.setTime(sdf.parse(dt));
                if (fieldToUpdate.toLowerCase().equals("day")) {
                    c.add(Calendar.DAY_OF_MONTH, amountToAdd);  // number of days to add
                }
                else if (fieldToUpdate.toLowerCase().equals("month")) {
                    c.add(Calendar.MONTH, amountToAdd);
                }
                else if (fieldToUpdate.toLowerCase().equals("year")) {
                    c.add(Calendar.YEAR, amountToAdd);
                }
                else if (fieldToUpdate.toLowerCase().equals("hour")) {
                    c.add(Calendar.HOUR_OF_DAY, amountToAdd);
                }
                else if (fieldToUpdate.toLowerCase().equals("minute")) {
                    c.add(Calendar.MINUTE, amountToAdd);
                }
                else if (fieldToUpdate.toLowerCase().equals("second")) {
                    c.add(Calendar.SECOND, amountToAdd);
                }
                else if (fieldToUpdate.toLowerCase().equals("millisecond")) {
                    c.add(Calendar.MILLISECOND, amountToAdd);
                }
                else {
                    ErrorUtil.createErrorValueAndThrow(stmt, "FUNCTION", "date:add", "Unknown field: " + fieldToUpdate);
                }
            } catch (ParseException pe) {
                ErrorUtil.createErrorValueAndThrow(stmt, "FUNCTION", "date:add", pe.getMessage());
            }
            //dt = sdf.format(c.getTime());  // dt is now the new date
            //Calendar c = Calendar.getInstance();

            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH) + 1;
            int dayOfMonth = c.get(Calendar.DAY_OF_MONTH);
            int hours = c.get(Calendar.HOUR_OF_DAY);
            int minutes = c.get(Calendar.MINUTE);
            int seconds = c.get(Calendar.SECOND);
            int millis = c.get(Calendar.MILLISECOND);

            NumberType y = new NumberType(stmt);
            NumberType m = new NumberType(stmt);
            NumberType d = new NumberType(stmt);
            NumberType h = new NumberType(stmt);
            NumberType min = new NumberType(stmt);
            NumberType s = new NumberType(stmt);
            NumberType ms = new NumberType(stmt);

            y.setDoubleValue((double) year);
            y.setPrecision((byte) 0);
            m.setDoubleValue((double) month);
            m.setPrecision((byte) 0);
            d.setDoubleValue((double) dayOfMonth);
            d.setPrecision((byte) 0);
            h.setDoubleValue((double) hours);
            h.setPrecision((byte) 0);
            min.setDoubleValue((double) minutes);
            min.setPrecision((byte) 0);
            s.setDoubleValue((double) seconds);
            s.setPrecision((byte) 0);
            ms.setDoubleValue((double) millis);
            ms.setPrecision((byte) 0);

            PairType yP = new PairType(stmt);
            PairType mP = new PairType(stmt);
            PairType dP = new PairType(stmt);
            PairType hP = new PairType(stmt);
            PairType minP = new PairType(stmt);
            PairType sP = new PairType(stmt);
            PairType msP = new PairType(stmt);

            yP.setPair("\"Year\"", y);
            mP.setPair("\"Month\"", m);
            dP.setPair("\"Day\"", d);
            hP.setPair("\"Hour\"", h);
            minP.setPair("\"Minute\"", min);
            sP.setPair("\"Second\"", s);
            msP.setPair("\"Millisecond\"", ms);

            result.addPair(yP);
            result.addPair(mP);
            result.addPair(dP);
            result.addPair(hP);
            result.addPair(minP);
            result.addPair(sP);
            result.addPair(msP);
            
            return result;
    }
    
    public void setParam1(Node param1) {this.param1 = param1;}
    public void setParam2(Node param2) {this.param2 = param2;}
    public void setParam3(Node param3) {this.param3 = param3;}
    public Node getParam1() {return this.param1;}
    public Node getParam2() {return this.param2;}
    public Node getParam3() {return this.param3;}
    public String getFunctionName() {return this.functionName;}
    public String getParam1Name() {return "amount";}
    public String getParam2Name() {return "field";}
    public String getParam3Name() {return "date";}
    public boolean ensuredCurrentValueSetBetweenParamEvaluations() {
        return true;
    }

}