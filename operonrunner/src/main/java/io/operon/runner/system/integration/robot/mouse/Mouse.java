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

package io.operon.runner.system.integration.robot.mouse;

import io.operon.runner.OperonContext;
import io.operon.runner.util.RandomUtil;

import java.util.Date;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
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
import java.time.Duration;

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

import java.awt.Robot;
import java.awt.Color;
import java.awt.event.InputEvent;

import org.apache.logging.log4j.LogManager;


public class Mouse {
    private static Logger log = LogManager.getLogger(Mouse.class);

    private Robot r;

    public Mouse() {}
    
    public static void click(Robot r) {
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(20);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    public static void doubleClick(Robot r) {
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(20);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(20);
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(20);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    public static void rightClick(Robot r) {
        r.mousePress(InputEvent.BUTTON2_DOWN_MASK);
        r.delay(20);
        r.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);
    }

    public static void middleClick(Robot r) {
        r.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        r.delay(20);
        r.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
    }

    public static void move(Robot r, int x, int y) {
        r.mouseMove(x, y);
    }

    public static Color color(Robot r, int x, int y) {
        return r.getPixelColor(x, y);
    }

    public static void mousePress(Robot r, int button) {
        r.mousePress(button);
    }

    public static void mouseRelease(Robot r, int button) {
        r.mouseRelease(button);
    }

}