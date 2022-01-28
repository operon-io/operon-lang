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

package io.operon.runner.system.integration.robot;

import io.operon.runner.OperonContext;
import io.operon.runner.util.RandomUtil;

import java.util.Date;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

import java.io.File;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

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

import java.awt.Robot;
import java.awt.Color;
import static java.awt.event.KeyEvent.*;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import io.operon.runner.system.integration.robot.keyboard.Keyboard;
import io.operon.runner.system.integration.robot.mouse.Mouse;

import org.apache.logging.log4j.LogManager;


public class RobotComponent extends BaseComponent implements IntegrationComponent {
    private static Logger log = LogManager.getLogger(RobotComponent.class);

    private Robot r;

    public RobotComponent() {}
    
    public OperonValue produce(OperonValue currentValue) throws OperonComponentException {
        try {
            Info info = this.resolve(currentValue);
            
            try {
                if (r == null) {
                    r = new Robot();
                }
                
				//System.out.println("cid=" + this.getComponentId());
				
				if (this.getComponentId() != null && this.getComponentId().toLowerCase().equals("type") && info.text != null) {
					Keyboard.typeString(r, info.locale, info.text);
				}
				
				else if (this.getComponentId() != null && this.getComponentId().toLowerCase().equals("click")) {
					Mouse.click(r);
				}
				
				else if (this.getComponentId() != null && this.getComponentId().toLowerCase().equals("rightclick")) {
					Mouse.rightClick(r);
				}
				
				else if (this.getComponentId() != null && this.getComponentId().toLowerCase().equals("doubleclick")) {
					Mouse.doubleClick(r);
				}
				
				else if (this.getComponentId() != null && this.getComponentId().toLowerCase().equals("middleclick")) {
					Mouse.middleClick(r);
				}
				
				else if (this.getComponentId() != null && this.getComponentId().toLowerCase().equals("move")) {
					Mouse.move(r, info.x, info.y);
				}
                
				else if (this.getComponentId() != null && this.getComponentId().toLowerCase().equals("color")) {
					Color c = Mouse.color(r, info.x, info.y);
					int red = c.getRed();
					int green = c.getGreen();
					int blue = c.getBlue();
					ArrayType result = new ArrayType(currentValue.getStatement());
					NumberType redNum = NumberType.create(currentValue.getStatement(), (double) red, (byte) 0);
					NumberType greenNum = NumberType.create(currentValue.getStatement(), (double) green, (byte) 0);
					NumberType blueNum = NumberType.create(currentValue.getStatement(), (double) blue, (byte) 0);
					result.addValue(redNum);
					result.addValue(greenNum);
					result.addValue(blueNum);
					return result;
				}
                
				else if (this.getComponentId() != null && this.getComponentId().toLowerCase().equals("screencapture")) {
					BufferedImage bi = RobotComponent.screenCapture(r, info.x, info.y, info.width, info.height);
					byte[] ba = RobotComponent.toByteArray(bi, "png");
					RawValue raw = new RawValue(currentValue.getStatement());
					raw.setValue(ba);
					return raw;
				}
                
				else if (this.getComponentId() != null && this.getComponentId().toLowerCase().equals("mousepress")) {
					Mouse.mousePress(r, info.mousebutton);
				}
                
				else if (this.getComponentId() != null && this.getComponentId().toLowerCase().equals("mouserelease")) {
					Mouse.mouseRelease(r, info.mousebutton);
				}
                
            } catch (Exception e) {
                throw new OperonComponentException(e.getMessage());
            }
            return currentValue;
        } catch (OperonGenericException e) {
            throw new OperonComponentException(e.getErrorJson());
        }
    }
    
    public static BufferedImage screenCapture(Robot r, int x, int y, int width, int height) {
        Rectangle rect = new Rectangle(x, y, width, height);
        return r.createScreenCapture(rect);
    }
    
    // convert BufferedImage to byte[]
    public static byte[] toByteArray(BufferedImage bi, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bi, format, baos);
        byte[] bytes = baos.toByteArray();
        return bytes;

    }

    // convert byte[] to BufferedImage
    public static BufferedImage toBufferedImage(byte[] bytes) throws IOException {
        InputStream is = new ByteArrayInputStream(bytes);
        BufferedImage bi = ImageIO.read(is);
        return bi;

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
                case "\"text\"":
                    String text = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.text = text;
                    break;
                case "\"locale\"":
                    String localeStr = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.locale = new Locale(localeStr.toLowerCase(), localeStr.toUpperCase());
                    break;
                case "\"debug\"":
                    OperonValue debugValue = pair.getEvaluatedValue();
                    if (debugValue instanceof FalseType) {
                        info.debug = false;
                    }
                    else {
                        info.debug = true;
                    }
                    break;
                case "\"x\"":
                    double xPoint = ((NumberType) pair.getEvaluatedValue()).getDoubleValue();
                    info.x = (int) xPoint;
                    break;
                case "\"y\"":
                    double yPoint = ((NumberType) pair.getEvaluatedValue()).getDoubleValue();
                    info.y = (int) yPoint;
                    break;
                case "\"width\"":
                    double xw = ((NumberType) pair.getEvaluatedValue()).getDoubleValue();
                    info.width = (int) xw;
                    break;
                case "\"height\"":
                    double yh = ((NumberType) pair.getEvaluatedValue()).getDoubleValue();
                    info.height = (int) yh;
                    break;
                case "\"mousebutton\"":
                    double mouseButton = ((NumberType) pair.getEvaluatedValue()).getDoubleValue();
                    info.mousebutton = (int) mouseButton;
                    break;
                default:
                    log.debug("robot -producer: no mapping for configuration key: " + key);
                    System.err.println("robot -producer: no mapping for configuration key: " + key);
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "ROBOT", "ERROR", "robot -producer: no mapping for configuration key: " + key);
            }
        }
        
        currentValue.getStatement().setCurrentValue(currentValueCopy);
        return info;
    }

    private class Info {
        private String text = null;
        private boolean debug = false;
        private Locale locale = Locale.US;
        private int x = 0;
        private int y = 0;
        private int width = 0;
        private int height = 0;
        private int mousebutton;
    }

}