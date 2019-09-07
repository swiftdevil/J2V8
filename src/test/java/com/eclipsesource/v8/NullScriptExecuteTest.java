/*******************************************************************************
 * Copyright (c) 2015 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * 	Contributors:
 * 		 Red Hat Inc. - initial API and implementation and/or initial documentation
 *******************************************************************************/
package com.eclipsesource.v8;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NullScriptExecuteTest {

    private V8 v8;
    private V8Context v8Context;

    @Before
    public void setup() {
        v8 = V8.createV8Runtime();
        v8Context = v8.getDefaultContext();
    }

    @After
    public void tearDown() {
        try {
            if (v8 != null) {
                v8.close();
            }
            if (V8.getActiveRuntimes() != 0) {
                throw new IllegalStateException("V8Runtimes not properly released");
            }
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
        }
    }

    @Test(expected = NullPointerException.class)
    public void testStringScript() {
        v8Context.executeStringScript(null);
    }

    @Test(expected = NullPointerException.class)
    public void testArrayScript() {
        v8Context.executeArrayScript(null);
    }

    @Test(expected = NullPointerException.class)
    public void testBooleancript() {
        v8Context.executeBooleanScript(null);
    }

    @Test(expected = NullPointerException.class)
    public void testDoubleScript() {
        v8Context.executeDoubleScript(null);
    }

    @Test(expected = NullPointerException.class)
    public void testIntScript() {
        v8Context.executeIntegerScript(null);
    }

    @Test(expected = NullPointerException.class)
    public void testObjectScript() {
        v8Context.executeObjectScript(null);
    }

    @Test(expected = NullPointerException.class)
    public void testScript() {
        v8Context.executeScript(null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullStringScript() {
        v8Context.executeVoidScript(null);
    }

}
