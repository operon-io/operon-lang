/** OPERON-LICENSE **/
package io.operon.runner.system.integration.robot.keyboard;

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
import static java.awt.event.KeyEvent.*;
import io.operon.runner.system.integration.robot.keyboard.layout.*;

import org.apache.logging.log4j.LogManager;


public class Keyboard {
    private static Logger log = LogManager.getLogger(Keyboard.class);

    private Robot r;
    private Locale locale;

    public Keyboard(Locale locale) {
        this.locale = locale;
    }
    
    public static void typeString(Robot r, Locale loc, CharSequence characters) {
        int length = characters.length();
        for (int i = 0; i < length; i++) {
            char character = characters.charAt(i);
            robotType(r, character, loc);
        }
    }

	//
	// FI-layout is the default
	//
    public static void robotType(Robot r, char character, Locale locale) {
        //System.out.println("TYPE: " + character);
        KeyboardLayout layout = null;
        if (locale.getLanguage().toLowerCase().equals("fi")) {
            layout = new KeyboardLayoutFI();
            int[] keyCodes = layout.getKeyCodes(character);
            doType(r, keyCodes);
        }
        else {
            // TODO: use the default-locale (US)
            //System.out.println("Using default-locale");
            layout = new KeyboardLayoutFI();
            int[] keyCodes = layout.getKeyCodes(character);
            doType(r, keyCodes);
        }
    }

    public static int[] emit(int... keyCodes) {
        return keyCodes;
    }

    private static void doType(Robot r, int... keyCodes) {
        doType(r, keyCodes, 0, keyCodes.length);
    }

	//
	// Recursive function: press key 1 --> release key 1
	//
    private static void doType(Robot r, int[] keyCodes, int offset, int length) {
        if (length == 0) {
            return;
        }

        r.keyPress(keyCodes[offset]);
        doType(r, keyCodes, offset + 1, length - 1);
        r.keyRelease(keyCodes[offset]);
    }

}