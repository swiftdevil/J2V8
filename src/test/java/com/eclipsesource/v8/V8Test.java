/*******************************************************************************
 * Copyright (c) 2014 EclipseSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    EclipseSource - initial API and implementation
 ******************************************************************************/
package com.eclipsesource.v8;

import com.eclipsesource.v8.utils.V8Map;
import com.eclipsesource.v8.utils.V8Runnable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class V8Test {

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

    @Test
    public void testGetVersion() {
        String v8version = V8.getV8Version();

        assertNotNull(v8version);
    }

    @Test
    public void testLowMemoryNotification() {
        v8.lowMemoryNotification();
    }

    @Test
    public void testGetVersion_StartsWith5() {
        String v8version = V8.getV8Version();

        assertTrue(v8version.startsWith("5"));
    }

    @Test
    public void testV8Setup() {
        assertNotNull(v8);
        assertNotNull(v8Context);
    }

    @SuppressWarnings("resource")
    @Test
    public void testReleaseRuntimeReportsMemoryLeaks() {
        V8 localV8 = V8.createV8Runtime();
        new V8Object(localV8.getDefaultContext());
        try {
            localV8.release(true);
        } catch (IllegalStateException ise) {
            String message = ise.getMessage();
            assertEquals("1 Object(s) still exist in runtime", message);
            return;
        }
        fail("Exception should have been thrown");
    }

    @SuppressWarnings("resource")
    @Test
    public void testReleaseRuntimeWithWeakReferencesReportsCorrectMemoryLeaks() {
        V8 localV8 = V8.createV8Runtime();
        new V8Object(localV8.getDefaultContext());
        new V8Object(localV8.getDefaultContext()).setWeak();
        try {
            localV8.release(true);
        } catch (IllegalStateException ise) {
            String message = ise.getMessage();
            assertEquals("1 Object(s) still exist in runtime", message);
            return;
        }
        fail("Exception should have been thrown");
    }

    @Test
    public void testObjectReferenceZero() {
        long objectReferenceCount = v8.getObjectReferenceCount();
        assertEquals(0, objectReferenceCount);
    }

    @Test
    public void testObjectReferenceCountOne() {
        V8Object object = new V8Object(v8Context);

        long objectReferenceCount = v8.getObjectReferenceCount();

        assertEquals(1, objectReferenceCount);
        object.close();
    }

    @Test
    public void testObjectReferenceCountReleased() {
        V8Object object = new V8Object(v8Context);
        object.close();

        long objectReferenceCount = v8.getObjectReferenceCount();

        assertEquals(0, objectReferenceCount);
    }

    @Test(expected = Error.class)
    public void testCannotAccessDisposedIsolateVoid() {
        v8.close();
        v8Context.executeVoidScript("");
    }

    @Test(expected = Error.class)
    public void testCannotAccessDisposedIsolateInt() {
        v8.close();
        v8Context.executeIntegerScript("7");
    }

    @Test(expected = Error.class)
    public void testCannotAccessDisposedIsolateString() {
        v8.close();
        v8Context.executeStringScript("'foo'");
    }

    @Test(expected = Error.class)
    public void testCannotAccessDisposedIsolateBoolean() {
        v8.close();
        v8Context.executeBooleanScript("true");
    }

    @Test
    public void testSingleThreadAccess() throws InterruptedException {
        final boolean[] result = new boolean[] { false };
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    v8Context.executeVoidScript("");
                } catch (Error e) {
                    result[0] = e.getMessage().contains("Invalid V8 thread access");
                }
            }
        });
        t.start();
        t.join();

        assertTrue(result[0]);
    }

    @Test
    public void testMultiThreadAccess() throws InterruptedException {
        v8Context.add("foo", "bar");
        v8.getLocker().release();
        Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                v8.getLocker().acquire();
                v8.add("foo", "baz");
                v8.getLocker().release();
            }
        });
        t.start();
        t.join();
        v8.getLocker().acquire();

        assertEquals("baz", v8Context.getString("foo"));
    }

    @SuppressWarnings("resource")
    @Test
    public void testISENotThrownOnShutdown() {
        V8 v8_ = V8.createV8Runtime();

        new V8Object(v8_.getDefaultContext());
        v8_.release(false);
    }

    @SuppressWarnings("resource")
    @Test(expected = IllegalStateException.class)
    public void testISEThrownOnShutdown() {
        V8 v8_ = V8.createV8Runtime();

        new V8Object(v8_.getDefaultContext());
        v8_.release(true);
    }

    @Test
    public void testReleaseAttachedObjects() {
        V8 runtime = V8.createV8Runtime();
        V8Object v8Object = new V8Object(v8Context);
        runtime.registerResource(v8Object);

        runtime.release(true);
    }

    @Test
    public void testReleaseSeveralAttachedObjects() {
        V8 runtime = V8.createV8Runtime();
        runtime.registerResource(new V8Object(runtime.getDefaultContext()));
        runtime.registerResource(new V8Object(runtime.getDefaultContext()));
        runtime.registerResource(new V8Object(runtime.getDefaultContext()));

        runtime.release(true);
    }

    @Test
    public void testReleaseAttachedMap() {
        V8 runtime = V8.createV8Runtime();
        V8Map<String> v8Map = new V8Map<String>();
        V8Object v8Object = new V8Object(runtime.getDefaultContext());
        v8Map.put(v8Object, "foo");
        v8Object.close();
        runtime.registerResource(v8Map);

        runtime.release(true);
    }

    /*** Void Script ***/
    @Test
    public void testSimpleVoidScript() {
        v8Context.executeVoidScript("function foo() {return 1+1}");

        int result = v8Context.executeIntegerFunction("foo", null);

        assertEquals(2, result);
    }

    @Test
    public void testMultipleScriptCallsPermitted() {
        v8Context.executeVoidScript("function foo() {return 1+1}");
        v8Context.executeVoidScript("function bar() {return foo() + 1}");

        int foo = v8Context.executeIntegerFunction("foo", null);
        int bar = v8Context.executeIntegerFunction("bar", null);

        assertEquals(2, foo);
        assertEquals(3, bar);
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testSyntaxErrorInVoidScript() {
        v8Context.executeVoidScript("'a");
    }

    @Test
    public void testSyntaxErrorMissingParam() {
        try {
            v8Context.executeScript("foo());");
        } catch (V8ScriptCompilationException e) {
            String string = e.toString();
            assertNotNull(string);
            return;
        }
        fail("Exception expected.");
    }

    @Test
    public void testVoidScriptWithName() {
        v8Context.executeVoidScript("function foo() {return 1+1}", "name", 1);

        int result = v8Context.executeIntegerFunction("foo", null);

        assertEquals(2, result);
    }

    /*** Int Script ***/
    @Test
    public void testSimpleIntScript() {
        int result = v8Context.executeIntegerScript("1+2;");

        assertEquals(3, result);
    }

    @Test
    public void testIntScriptWithDouble() {
        int result = v8Context.executeIntegerScript("1.9+2.9;");

        assertEquals(4, result);
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testSimpleSyntaxError() {
        v8Context.executeIntegerScript("return 1+2");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionIntScript() {
        v8Context.executeIntegerScript("");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionForWrongReturnTypeIntScript() {
        v8Context.executeIntegerScript("'test'");
    }

    @Test
    public void testIntScriptWithName() {
        int result = v8Context.executeIntegerScript("1+2;", "name", 2);

        assertEquals(3, result);
    }

    /*** Double Script ***/
    @Test
    public void testSimpleDoubleScript() {
        double result = v8Context.executeDoubleScript("3.14159;");

        assertEquals(3.14159, result, 0.00001);
    }

    @Test
    public void testDoubleScriptWithInt() {
        double result = v8Context.executeDoubleScript("1");

        assertEquals(1.0, result, 0.00001);
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testSimpleSyntaxErrorInDoubleScript() {
        v8Context.executeDoubleScript("return 1+2");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionDoubleScript() {
        v8Context.executeDoubleScript("");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionForWrongReturnTypeDoubleScript() {
        v8Context.executeDoubleScript("'test'");
    }

    @Test
    public void testDoubleScriptHandlesInts() {
        int result = (int) v8Context.executeDoubleScript("1");

        assertEquals(1, result);
    }

    @Test
    public void testDoubleScriptWithName() {
        double result = v8Context.executeDoubleScript("3.14159;", "name", 3);

        assertEquals(3.14159, result, 0.00001);
    }

    /*** Boolean Script ***/
    @Test
    public void testSimpleBooleanScript() {
        boolean result = v8Context.executeBooleanScript("true");

        assertTrue(result);
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testSimpleSyntaxErrorInBooleanScript() {
        v8Context.executeBooleanScript("return 1+2");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionBooleanScript() {
        v8Context.executeBooleanScript("");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionForWrongReturnTypeBooleanScript() {
        v8Context.executeBooleanScript("'test'");
    }

    @Test
    public void testBooleanScriptWithName() {
        boolean result = v8Context.executeBooleanScript("true", "name", 4);

        assertTrue(result);
    }

    /*** String Script ***/
    @Test
    public void testSimpleStringScript() {
        String result = v8Context.executeStringScript("'hello, world'");

        assertEquals("hello, world", result);
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testSimpleSyntaxErrorStringScript() {
        v8Context.executeStringScript("'a");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionStringScript() {
        v8Context.executeIntegerScript("");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionForWrongReturnTypeStringScript() {
        v8Context.executeStringScript("42");
    }

    @Test
    public void testStringScriptWithName() {
        String result = v8Context.executeStringScript("'hello, world'", "name", 5);

        assertEquals("hello, world", result);
    }

    /*** Unknown Script ***/
    @Test
    public void testAnyScriptReturnedNothing() {
        V8Value result = (V8Value) v8Context.executeScript("");

        assertTrue(result.isUndefined());
    }

    @Test
    public void testAnyScriptReturnedNull() {
        Object result = v8Context.executeScript("null;");

        assertNull(result);
    }

    @Test
    public void testAnyScriptReturnedUndefined() {
        V8Value result = (V8Value) v8Context.executeScript("undefined;");

        assertTrue(result.isUndefined());
    }

    @Test
    public void testAnyScriptReturnInt() {
        Object result = v8Context.executeScript("1;");

        assertEquals(1, result);
    }

    @Test
    public void testAnyScriptReturnDouble() {
        Object result = v8Context.executeScript("1.1;");

        assertEquals(1.1, (Double) result, 0.000001);
    }

    @Test
    public void testAnyScriptReturnString() {
        Object result = v8Context.executeScript("'foo';");

        assertEquals("foo", result);
    }

    @Test
    public void testAnyScriptReturnBoolean() {
        Object result = v8Context.executeScript("false;");

        assertFalse((Boolean) result);
    }

    @Test
    public void testAnyScriptReturnsV8Object() {
        V8Object result = (V8Object) v8Context.executeScript("foo = {hello:'world'}; foo;");

        assertEquals("world", result.getString("hello"));
        result.close();
    }

    @Test
    public void testAnyScriptReturnsV8Array() {
        V8Array result = (V8Array) v8Context.executeScript("[1,2,3];");

        assertEquals(3, result.length());
        assertEquals(1, result.get(0));
        assertEquals(2, result.get(1));
        assertEquals(3, result.get(2));
        result.close();
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testSimpleSyntaxErrorAnytScript() {
        v8Context.executeScript("'a");
    }

    @Test
    public void testAnyScriptWithName() {
        V8Object result = (V8Object) v8Context.executeScript("foo = {hello:'world'}; foo;", "name", 6);

        assertEquals("world", result.getString("hello"));
        result.close();
    }

    /*** Object Script ***/
    @Test
    public void testSimpleObjectScript() {
        V8Object result = v8Context.executeObjectScript("foo = {hello:'world'}; foo;");

        assertEquals("world", result.getString("hello"));
        result.close();
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testSimpleSyntaxErrorObjectScript() {
        v8Context.executeObjectScript("'a");
    }

    @Test
    public void testResultUndefinedExceptionObjectScript() {
        V8Object result = v8Context.executeObjectScript("");

        assertTrue(result.isUndefined());
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionForWrongReturnTypeObjectScript() {
        v8Context.executeObjectScript("42");
    }

    @Test
    public void testNestedObjectScript() {
        V8Object result = v8Context.executeObjectScript("person = {name : {first : 'john', last:'smith'} }; person;");

        V8Object name = result.getObject("name");
        assertEquals("john", name.getString("first"));
        assertEquals("smith", name.getString("last"));
        result.close();
        name.close();
    }

    @Test
    public void testObjectScriptWithName() {
        V8Object result = v8Context.executeObjectScript("foo = {hello:'world'}; foo;", "name", 6);

        assertEquals("world", result.getString("hello"));
        result.close();
    }

    /*** Array Script ***/
    @Test
    public void testSimpleArrayScript() {
        V8Array result = v8Context.executeArrayScript("foo = [1,2,3]; foo;");

        assertNotNull(result);
        result.close();
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testSimpleSyntaxErrorArrayScript() {
        v8Context.executeArrayScript("'a");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionArrayScript() {
        v8Context.executeArrayScript("");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionForWrongReturnTypeArrayScript() {
        v8Context.executeArrayScript("42");
    }

    @Test
    public void testArrayScriptWithName() {
        V8Array result = v8Context.executeArrayScript("foo = [1,2,3]; foo;", "name", 7);

        assertNotNull(result);
        result.close();
    }

    /*** Int Function ***/
    @Test
    public void testSimpleIntFunction() {
        v8Context.executeIntegerScript("function foo() {return 1+2;}; 42");

        int result = v8Context.executeIntegerFunction("foo", null);

        assertEquals(3, result);
    }

    @Test
    public void testSimpleIntFunctionWithDouble() {
        v8Context.executeVoidScript("function foo() {return 1.2+2.9;};");

        int result = v8Context.executeIntegerFunction("foo", null);

        assertEquals(4, result);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForWrongReturnTypeOfIntFunction() {
        v8Context.executeIntegerScript("function foo() {return 'test';}; 42");

        int result = v8Context.executeIntegerFunction("foo", null);

        assertEquals(3, result);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForNoReturnInIntFunction() {
        v8Context.executeIntegerScript("function foo() {}; 42");

        int result = v8Context.executeIntegerFunction("foo", null);

        assertEquals(3, result);
    }

    /*** String Function ***/
    @Test
    public void testSimpleStringFunction() {
        v8Context.executeVoidScript("function foo() {return 'hello';}");

        String result = v8Context.executeStringFunction("foo", null);

        assertEquals("hello", result);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForWrongReturnTypeOfStringFunction() {
        v8Context.executeVoidScript("function foo() {return 42;}");

        v8Context.executeStringFunction("foo", null);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForNoReturnInStringFunction() {
        v8Context.executeVoidScript("function foo() {};");

        v8Context.executeStringFunction("foo", null);
    }

    /*** Double Function ***/
    @Test
    public void testSimpleDoubleFunction() {
        v8Context.executeVoidScript("function foo() {return 3.14 + 1;}");

        double result = v8Context.executeDoubleFunction("foo", null);

        assertEquals(4.14, result, 0.000001);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForWrongReturnTypeOfDoubleFunction() {
        v8Context.executeVoidScript("function foo() {return 'foo';}");

        v8Context.executeDoubleFunction("foo", null);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForNoReturnInDoubleFunction() {
        v8Context.executeVoidScript("function foo() {};");

        v8Context.executeDoubleFunction("foo", null);
    }

    /*** Boolean Function ***/
    @Test
    public void testSimpleBooleanFunction() {
        v8Context.executeVoidScript("function foo() {return true;}");

        boolean result = v8Context.executeBooleanFunction("foo", null);

        assertTrue(result);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForWrongReturnTypeOfBooleanFunction() {
        v8Context.executeVoidScript("function foo() {return 'foo';}");

        v8Context.executeBooleanFunction("foo", null);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForNoReturnInBooleanFunction() {
        v8Context.executeVoidScript("function foo() {};");

        v8Context.executeBooleanFunction("foo", null);
    }

    /*** Object Function ***/
    @Test
    public void testSimpleObjectFunction() {
        v8Context.executeVoidScript("function foo() {return {foo:true};}");

        V8Object result = v8Context.executeObjectFunction("foo", null);

        assertTrue(result.getBoolean("foo"));
        result.close();
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForWrongReturnTypeOfObjectFunction() {
        v8Context.executeVoidScript("function foo() {return 'foo';}");

        v8Context.executeObjectFunction("foo", null);
    }

    @Test
    public void testResultUndefinedForNoReturnInobjectFunction() {
        v8Context.executeVoidScript("function foo() {};");

        V8Object result = v8Context.executeObjectFunction("foo", null);

        assertTrue(result.isUndefined());
    }

    /*** Array Function ***/
    @Test
    public void testSimpleArrayFunction() {
        v8Context.executeVoidScript("function foo() {return [1,2,3];}");

        V8Array result = v8Context.executeArrayFunction("foo", null);

        assertEquals(3, result.length());
        result.close();
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForWrongReturnTypeOfArrayFunction() {
        v8Context.executeVoidScript("function foo() {return 'foo';}");

        v8Context.executeArrayFunction("foo", null);
    }

    @Test
    public void testResultUndefinedForNoReturnInArrayFunction() {
        v8Context.executeVoidScript("function foo() {};");

        V8Array result = v8Context.executeArrayFunction("foo", null);

        assertTrue(result.isUndefined());
    }

    /*** Void Function ***/
    @Test
    public void testSimpleVoidFunction() {
        v8Context.executeVoidScript("function foo() {x=1}");

        v8Context.executeVoidFunction("foo", null);

        assertEquals(1, v8Context.getInteger("x"));
    }

    /*** Add Int ***/
    @Test
    public void testAddInt() {
        v8Context.add("foo", 42);

        int result = v8Context.executeIntegerScript("foo");

        assertEquals(42, result);
    }

    @Test
    public void testAddIntReplaceValue() {
        v8Context.add("foo", 42);
        v8Context.add("foo", 43);

        int result = v8Context.executeIntegerScript("foo");

        assertEquals(43, result);
    }

    /*** Add Double ***/
    @Test
    public void testAddDouble() {
        v8Context.add("foo", 3.14159);

        double result = v8Context.executeDoubleScript("foo");

        assertEquals(3.14159, result, 0.000001);
    }

    @Test
    public void testAddDoubleReplaceValue() {
        v8Context.add("foo", 42.1);
        v8Context.add("foo", 43.1);

        double result = v8Context.executeDoubleScript("foo");

        assertEquals(43.1, result, 0.000001);
    }

    /*** Add String ***/
    @Test
    public void testAddString() {
        v8Context.add("foo", "hello, world!");

        String result = v8Context.executeStringScript("foo");

        assertEquals("hello, world!", result);
    }

    @Test
    public void testAddStringReplaceValue() {
        v8Context.add("foo", "hello");
        v8Context.add("foo", "world");

        String result = v8Context.executeStringScript("foo");

        assertEquals("world", result);
    }

    /*** Add Boolean ***/
    @Test
    public void testAddBoolean() {
        v8Context.add("foo", true);

        boolean result = v8Context.executeBooleanScript("foo");

        assertTrue(result);
    }

    @Test
    public void testAddBooleanReplaceValue() {
        v8Context.add("foo", true);
        v8Context.add("foo", false);

        boolean result = v8Context.executeBooleanScript("foo");

        assertFalse(result);
    }

    @Test
    public void testAddReplaceValue() {
        v8Context.add("foo", true);
        v8Context.add("foo", "test");

        String result = v8Context.executeStringScript("foo");

        assertEquals("test", result);
    }

    /*** Add Object ***/
    @Test
    public void testAddObject() {
        V8Object v8Object = new V8Object(v8Context);
        v8Context.add("foo", v8Object);

        V8Object result = v8Context.executeObjectScript("foo");

        assertNotNull(result);
        result.close();
        v8Object.close();
    }

    @Test
    public void testAddObjectReplaceValue() {
        V8Object v8ObjectFoo1 = new V8Object(v8Context);
        v8ObjectFoo1.add("test", true);
        V8Object v8ObjectFoo2 = new V8Object(v8Context);
        v8ObjectFoo2.add("test", false);

        v8Context.add("foo", v8ObjectFoo1);
        v8Context.add("foo", v8ObjectFoo2);

        boolean result = v8Context.executeBooleanScript("foo.test");

        assertFalse(result);
        v8ObjectFoo1.close();
        v8ObjectFoo2.close();
    }

    /*** Add Array ***/
    @Test
    public void testAddArray() {
        V8Array array = new V8Array(v8Context);
        v8Context.add("foo", array);

        V8Array result = v8Context.executeArrayScript("foo");

        assertNotNull(result);
        array.close();
        result.close();
    }

    /*** Get Int ***/
    @Test
    public void testGetInt() {
        v8Context.executeVoidScript("x = 7");

        int result = v8Context.getInteger("x");

        assertEquals(7, result);
    }

    @Test
    public void testGetIntFromDouble() {
        v8Context.executeVoidScript("x = 7.7");

        int result = v8Context.getInteger("x");

        assertEquals(7, result);
    }

    @Test
    public void testGetIntReplaceValue() {
        v8Context.executeVoidScript("x = 7; x = 8");

        int result = v8Context.getInteger("x");

        assertEquals(8, result);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetIntWrongType() {
        v8Context.executeVoidScript("x = 'foo'");

        v8Context.getInteger("x");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetIntDoesNotExist() {
        v8Context.executeVoidScript("");

        v8Context.getInteger("x");
    }

    /*** Get Double ***/
    @Test
    public void testGetDouble() {
        v8Context.executeVoidScript("x = 3.14159");

        double result = v8Context.getDouble("x");

        assertEquals(3.14159, result, 0.00001);
    }

    @Test
    public void testGetDoubleReplaceValue() {
        v8Context.executeVoidScript("x = 7.1; x = 8.1");

        double result = v8Context.getDouble("x");

        assertEquals(8.1, result, 0.00001);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetDoubleWrongType() {
        v8Context.executeVoidScript("x = 'foo'");

        v8Context.getDouble("x");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetDoubleDoesNotExist() {
        v8Context.executeVoidScript("");

        v8Context.getDouble("x");
    }

    /*** Get String ***/
    @Test
    public void testGetString() {
        v8Context.executeVoidScript("x = 'hello'");

        String result = v8Context.getString("x");

        assertEquals("hello", result);
    }

    @Test
    public void testGetStringReplaceValue() {
        v8Context.executeVoidScript("x = 'hello'; x = 'world'");

        String result = v8Context.getString("x");

        assertEquals("world", result);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetStringeWrongType() {
        v8Context.executeVoidScript("x = 42");

        v8Context.getString("x");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetStringDoesNotExist() {
        v8Context.executeVoidScript("");

        v8Context.getString("x");
    }

    /*** Get Boolean ***/
    @Test
    public void testGetBoolean() {
        v8Context.executeVoidScript("x = true");

        boolean result = v8Context.getBoolean("x");

        assertTrue(result);
    }

    @Test
    public void testGetBooleanReplaceValue() {
        v8Context.executeVoidScript("x = true; x = false");

        boolean result = v8Context.getBoolean("x");

        assertFalse(result);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetBooleanWrongType() {
        v8Context.executeVoidScript("x = 42");

        v8Context.getBoolean("x");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetBooleanDoesNotExist() {
        v8Context.executeVoidScript("");

        v8Context.getBoolean("x");
    }

    @Test
    public void testAddGet() {
        v8Context.add("string", "string");
        v8Context.add("int", 7);
        v8Context.add("double", 3.1);
        v8Context.add("boolean", true);

        assertEquals("string", v8Context.getString("string"));
        assertEquals(7, v8Context.getInteger("int"));
        assertEquals(3.1, v8Context.getDouble("double"), 0.00001);
        assertTrue(v8Context.getBoolean("boolean"));
    }

    /*** Get Array ***/
    @Test
    public void testGetV8Array() {
        v8Context.executeVoidScript("foo = [1,2,3]");

        V8Array array = v8Context.getArray("foo");

        assertEquals(3, array.length());
        assertEquals(1, array.getInteger(0));
        assertEquals(2, array.getInteger(1));
        assertEquals(3, array.getInteger(2));
        array.close();
    }

    @Test
    public void testGetMultipleV8Arrays() {
        v8Context.executeVoidScript("foo = [1,2,3]; " + "bar=['first', 'second']");

        V8Array fooArray = v8Context.getArray("foo");
        V8Array barArray = v8Context.getArray("bar");

        assertEquals(3, fooArray.length());
        assertEquals(2, barArray.length());

        fooArray.close();
        barArray.close();
    }

    @Test
    public void testGetNestedV8Array() {
        v8Context.executeVoidScript("foo = [[1,2]]");

        for (int i = 0; i < 1000; i++) {
            V8Array fooArray = v8Context.getArray("foo");
            V8Array nested = fooArray.getArray(0);

            assertEquals(1, fooArray.length());
            assertEquals(2, nested.length());

            fooArray.close();
            nested.close();
        }
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetArrayWrongType() {
        v8Context.executeVoidScript("foo = 42");

        v8Context.getArray("foo");
    }

    @Test()
    public void testGetArrayDoesNotExist() {
        v8Context.executeVoidScript("foo = 42");

        V8Array result = v8Context.getArray("bar");

        assertTrue(result.isUndefined());
    }

    /*** Contains ***/
    @Test
    public void testContainsKey() {
        v8Context.add("foo", true);

        boolean result = v8Context.contains("foo");

        assertTrue(result);
    }

    @Test
    public void testContainsKeyFromScript() {
        v8Context.executeVoidScript("bar = 3");

        assertTrue(v8Context.contains("bar"));
    }

    @Test
    public void testContainsMultipleKeys() {
        v8Context.add("true", true);
        v8Context.add("test", "test");
        v8Context.add("one", 1);
        v8Context.add("pi", 3.14);

        assertTrue(v8Context.contains("true"));
        assertTrue(v8Context.contains("test"));
        assertTrue(v8Context.contains("one"));
        assertTrue(v8Context.contains("pi"));
        assertFalse(v8Context.contains("bar"));
    }

    @Test
    public void testDoesNotContainsKey() {
        v8Context.add("foo", true);

        boolean result = v8Context.contains("bar");

        assertFalse(result);
    }

    /*** GetKeys ***/
    @Test
    public void testZeroKeys() {
        assertEquals(0, v8Context.getKeys().length);
    }

    @Test
    public void testGetKeys() {
        v8Context.add("true", true);
        v8Context.add("test", "test");
        v8Context.add("one", 1);
        v8Context.add("pi", 3.14);

        assertEquals(4, v8Context.getKeys().length);
        assertTrue(arrayContains(v8Context.getKeys(), "true", "test", "one", "pi"));
    }

    static boolean arrayContains(final String[] keys, final String... strings) {
        List<String> keyList = Arrays.asList(keys);
        for (String s : strings) {
            if (!keyList.contains(s)) {
                return false;
            }
        }
        return true;
    }

    @Test
    public void testReplacedKey() {
        v8Context.add("test", true);
        v8Context.add("test", "test");
        v8Context.add("test", 1);
        v8Context.add("test", 3.14);

        assertEquals(1, v8Context.getKeys().length);
        assertEquals("test", v8Context.getKeys()[0]);
    }

    @Test
    public void testGetKeysSetFromScript() {
        v8Context.executeVoidScript("var foo=37");

        assertEquals(1, v8Context.getKeys().length);
        assertEquals("foo", v8Context.getKeys()[0]);
    }

    /*** Global Object Prototype Manipulation ***/
    private void setupWindowAlias() {
        v8.close();
        v8 = V8.createV8Runtime("window");
        v8Context = v8.getDefaultContext();
        v8Context.executeVoidScript("function Window(){};");
        V8Object prototype = v8Context.executeObjectScript("Window.prototype");
        v8Context.setPrototype(prototype);
        prototype.close();
    }

    @Test
    public void testAccessWindowObjectInStrictMode() {
        setupWindowAlias();
        String script = "'use strict';\n"
                + "window.foo = 7;\n"
                + "true\n";

        boolean result = v8Context.executeBooleanScript(script);

        assertTrue(result);
        assertEquals(7, v8Context.executeIntegerScript("window.foo"));
    }

    @Test
    public void testWindowWindowWindowWindow() {
        setupWindowAlias();

        assertTrue(v8Context.executeBooleanScript("window.window.window === window"));
    }

    @Test
    public void testGlobalIsWindow() {
        setupWindowAlias();
        v8Context.executeVoidScript("var global = Function('return this')();");

        assertTrue(v8Context.executeBooleanScript("global === window"));
    }

    @Test
    public void testWindowIsGlobal() {
        setupWindowAlias();
        v8Context.executeVoidScript("var global = Function('return this')();");

        assertTrue(v8Context.executeBooleanScript("window === global"));
    }

    @Test
    public void testV8IsGlobalStrictEquals() {
        setupWindowAlias();
        v8Context.executeVoidScript("var global = Function('return this')();");

        V8Object global = v8Context.executeObjectScript("global");

        assertTrue(v8Context.strictEquals(global));
        assertTrue(global.strictEquals(v8));
        global.close();
    }

    @Test
    public void testV8IsGlobalEquals() {
        setupWindowAlias();
        v8Context.executeVoidScript("var global = Function('return this')();");

        V8Object global = v8Context.executeObjectScript("global");

        assertTrue(v8Context.equals(global));
        assertTrue(global.equals(v8Context));
        global.close();
    }

    @Test
    public void testV8EqualsGlobalHash() {
        setupWindowAlias();
        v8Context.executeVoidScript("var global = Function('return this')();");

        V8Object global = v8Context.executeObjectScript("global");

        assertEquals(v8Context.hashCode(), global.hashCode());
        global.close();
    }

    @Test
    public void testV8IsThis() {
        setupWindowAlias();
        v8Context.executeVoidScript("var global = Function('return this')();");

        V8Object _this = v8Context.executeObjectScript("this;");

        assertEquals(v8, _this);
        assertEquals(_this, v8);
        _this.close();
    }

    @Test
    public void testWindowIsGlobal2() {
        setupWindowAlias();
        v8Context.executeVoidScript("var global = Function('return this')();");

        assertTrue(v8Context.executeBooleanScript("window === global"));
    }

    @Test
    public void testAlternateGlobalAlias() {
        v8Context.close();
        v8 = V8.createV8Runtime("document");
        v8Context.executeVoidScript("var global = Function('return this')();");

        assertTrue(v8Context.executeBooleanScript("global === document"));
    }

    @Test
    public void testAccessGlobalViaWindow() {
        setupWindowAlias();
        String script = "var global = {data: 0};\n" + "global === window.global";

        assertTrue(v8Context.executeBooleanScript(script));
    }

    @Test
    public void testwindowIsInstanceOfWindow() {
        setupWindowAlias();

        assertTrue(v8Context.executeBooleanScript("window instanceof Window"));
    }

    @Test
    public void testChangeToWindowPrototypeAppearsInGlobalScope() {
        setupWindowAlias();
        V8Object prototype = v8Context.executeObjectScript("Window.prototype");

        prototype.add("foo", "bar");
        v8Context.executeVoidScript("delete window.foo");

        assertEquals("bar", v8Context.getString("foo"));
        assertEquals("bar", v8Context.executeStringScript("window.foo;"));
        prototype.close();
    }

    @Test
    public void testWindowAliasForGlobalScope() {
        setupWindowAlias();

        v8Context.executeVoidScript("a = 1; window.b = 2;");

        assertEquals(1, v8Context.executeIntegerScript("window.a;"));
        assertEquals(2, v8Context.executeIntegerScript("b;"));
        assertTrue(v8Context.executeBooleanScript("window.hasOwnProperty( \"Object\" )"));
    }

    @Test
    public void testExecuteUnicodeScript() {
        String result = v8Context.executeStringScript("var ಠ_ಠ = function() { return '🌞' + '💐'; }; ಠ_ಠ();");

        assertEquals("🌞💐", result);
    }

    @Test
    public void testExecuteUnicodeFunction() {
        v8Context.executeVoidScript("var ಠ_ಠ = function() { return '🌞' + '💐'; }; ");

        assertEquals("🌞💐", v8Context.executeStringFunction("ಠ_ಠ", null));
    }

    @Test
    public void testCompileErrowWithUnicode() {
        try {
            v8Context.executeVoidScript("🌞");
        } catch (V8ScriptCompilationException e) {
            assertTrue(e.toString().contains("🌞"));
            return;
        }

        fail("Exception should have been thrown.");
    }

    @Test
    public void testExecutionExceptionWithUnicode() {
        try {
            v8Context.executeVoidScript("throw('🌞')");
        } catch (V8RuntimeException e) {
            assertTrue(e.toString().contains("throw('🌞"));
        }
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testInvalidJSScript() {
        String script = "x = [1,2,3];\n"
                + "y = 0;\n"
                + "\n"
                + "//A JS Script that has a compile error, int should be var\n"
                + "for (int i = 0; i < x.length; i++) {\n"
                + "  y = y + x[i];\n"
                + "}";

        v8Context.executeVoidScript(script, "example.js", 0);
    }

    @Test
    public void testV8HandleCreated_V8Object() {
        ReferenceHandler referenceHandler = mock(ReferenceHandler.class);
        v8.addReferenceHandler(referenceHandler);

        V8Object object = new V8Object(v8Context);

        verify(referenceHandler, times(1)).v8HandleCreated(object);
        object.close();
    }

    @Test
    public void testV8HandleCreated_AccessedObject() {
        ReferenceHandler referenceHandler = mock(ReferenceHandler.class);
        v8.addReferenceHandler(referenceHandler);

        V8Object object = v8Context.executeObjectScript("foo = {}; foo;");

        verify(referenceHandler, times(1)).v8HandleCreated(object);
        object.close();
    }

    @Test
    public void testV8HandleCreated_AccessedArray() {
        ReferenceHandler referenceHandler = mock(ReferenceHandler.class);
        v8.addReferenceHandler(referenceHandler);

        V8Array object = (V8Array) v8Context.executeScript("[1,2,3];");

        verify(referenceHandler, times(1)).v8HandleCreated(object);
        object.close();
    }

    @Test
    public void testV8ReferenceHandleRemoved() {
        ReferenceHandler referenceHandler = mock(ReferenceHandler.class);
        v8.addReferenceHandler(referenceHandler);
        v8.removeReferenceHandler(referenceHandler);

        V8Object object = new V8Object(v8Context);

        verify(referenceHandler, never()).v8HandleCreated(object);
        object.close();
    }

    @Test
    public void testV8UnknownReferenceHandleRemoved() {
        ReferenceHandler referenceHandler1 = mock(ReferenceHandler.class);
        ReferenceHandler referenceHandler2 = mock(ReferenceHandler.class);
        v8.addReferenceHandler(referenceHandler1);
        v8.removeReferenceHandler(referenceHandler2);

        V8Object object = new V8Object(v8Context);

        verify(referenceHandler1, times(1)).v8HandleCreated(object);
        object.close();
    }

    @Test
    public void testV8MultipleReferenceHandlers() {
        ReferenceHandler referenceHandler1 = mock(ReferenceHandler.class);
        ReferenceHandler referenceHandler2 = mock(ReferenceHandler.class);
        v8.addReferenceHandler(referenceHandler1);
        v8.addReferenceHandler(referenceHandler2);

        V8Object object = new V8Object(v8Context);

        verify(referenceHandler1, times(1)).v8HandleCreated(object);
        verify(referenceHandler2, times(1)).v8HandleCreated(object);
        object.close();
    }

    @Test
    public void testV8ReleaseHandleRemoved() {
        V8 testV8 = V8.createV8Runtime();
        V8Runnable releaseHandler = mock(V8Runnable.class);
        testV8.addReleaseHandler(releaseHandler);
        testV8.removeReleaseHandler(releaseHandler);

        testV8.close();

        verify(releaseHandler, never()).run(testV8.getDefaultContext());
    }

    @Test
    public void testV8UnknownReleaseHandleRemoved() {
        V8 testV8 = V8.createV8Runtime();
        V8Runnable releaseHandler1 = mock(V8Runnable.class);
        V8Runnable releaseHandler2 = mock(V8Runnable.class);
        testV8.addReleaseHandler(releaseHandler1);
        testV8.removeReleaseHandler(releaseHandler2);

        testV8.close();

        verify(releaseHandler1, times(1)).run(any(V8Context.class)); // cannot check against the real v8 because it's released.
    }

    @Test
    public void testV8MultipleReleaseHandlers() {
        V8 testV8 = V8.createV8Runtime();
        V8Runnable releaseHandler1 = mock(V8Runnable.class);
        V8Runnable releaseHandler2 = mock(V8Runnable.class);
        testV8.addReleaseHandler(releaseHandler1);
        testV8.addReleaseHandler(releaseHandler2);

        testV8.close();

        verify(releaseHandler1, times(1)).run(any(V8Context.class)); // cannot check against the real v8 because it's released.
        verify(releaseHandler2, times(1)).run(any(V8Context.class)); // cannot check against the real v8 because it's released.
    }

    @Test
    public void testExceptionInReleaseHandlerStillReleasesV8() {
        V8 testV8 = V8.createV8Runtime();
        V8Runnable releaseHandler = mock(V8Runnable.class);
        doThrow(new RuntimeException()).when(releaseHandler).run(any(V8Context.class));
        testV8.addReleaseHandler(releaseHandler);

        try {
            testV8.close();
        } catch (Exception e) {
            assertTrue(testV8.isReleased());
            return;
        }

        fail("Exception should have been caught.");
    }

    @Test
    public void testV8HandleCreated_V8Array() {
        ReferenceHandler referenceHandler = mock(ReferenceHandler.class);
        v8.addReferenceHandler(referenceHandler);

        V8Array object = new V8Array(v8Context);

        verify(referenceHandler, times(1)).v8HandleCreated(object);
        object.close();
    }

    @Test
    public void testV8HandleCreated_V8Function() {
        ReferenceHandler referenceHandler = mock(ReferenceHandler.class);
        v8.addReferenceHandler(referenceHandler);

        V8Function object = new V8Function(v8Context);

        verify(referenceHandler, times(1)).v8HandleCreated(object);
        object.close();
    }

    @Test
    public void testV8HandleCreated_V8ArrayBuffer() {
        ReferenceHandler referenceHandler = mock(ReferenceHandler.class);
        v8.addReferenceHandler(referenceHandler);

        V8ArrayBuffer object = new V8ArrayBuffer(v8Context, 100);

        verify(referenceHandler, times(1)).v8HandleCreated(object);
        object.close();
    }

    @Test
    public void testV8HandleCreated_V8TypedArray() {
        ReferenceHandler referenceHandler = mock(ReferenceHandler.class);
        v8.addReferenceHandler(referenceHandler);

        V8ArrayBuffer buffer = new V8ArrayBuffer(v8Context, 100);
        V8TypedArray object = new V8TypedArray(v8Context, buffer, V8API.INT_16_ARRAY, 0, 50);

        verify(referenceHandler, times(1)).v8HandleCreated(buffer);
        verify(referenceHandler, times(1)).v8HandleCreated(object);
        buffer.close();
        object.close();
    }

    @Test
    public void testV8HandleDisposed() {
        ReferenceHandler referenceHandler = mock(ReferenceHandler.class);
        v8.addReferenceHandler(referenceHandler);

        V8Object object = new V8Object(v8Context);
        object.close();

        verify(referenceHandler, times(1)).v8HandleDisposed(any(V8Object.class)); // Can't test the actual one because it's disposed
    }

    @SuppressWarnings("resource")
    @Test
    public void testV8ObjectHandlerExceptionDuringCreation() {
        ReferenceHandler referenceHandler = mock(ReferenceHandler.class);
        doThrow(new RuntimeException()).when(referenceHandler).v8HandleCreated(any(V8Object.class));
        v8.addReferenceHandler(referenceHandler);

        try {
            new V8Object(v8Context);
        } catch (Exception e) {
            assertEquals(0, v8.getObjectReferenceCount());
            return;
        }

        fail("Exception should have been caught.");
    }

    @SuppressWarnings("resource")
    @Test
    public void testV8ArrayHandlerExceptionDuringCreation() {
        ReferenceHandler referenceHandler = mock(ReferenceHandler.class);
        doThrow(new RuntimeException()).when(referenceHandler).v8HandleCreated(any(V8Object.class));
        v8.addReferenceHandler(referenceHandler);

        try {
            new V8Array(v8Context);
        } catch (Exception e) {
            assertEquals(0, v8.getObjectReferenceCount());
            return;
        }

        fail("Exception should have been caught.");
    }

    @SuppressWarnings("resource")
    @Test
    public void testV8ArrayBufferHandlerExceptionDuringCreation() {
        ReferenceHandler referenceHandler = mock(ReferenceHandler.class);
        doThrow(new RuntimeException()).when(referenceHandler).v8HandleCreated(any(V8Value.class));
        v8.addReferenceHandler(referenceHandler);

        try {
            new V8ArrayBuffer(v8Context, 100);
        } catch (Exception e) {
            assertEquals(0, v8.getObjectReferenceCount());
            return;
        }

        fail("Exception should have been caught.");
    }

    @Test(expected = Error.class)
    public void testSharingObjectsShouldNotCrashVM() {
        V8 engine = null;
        V8 engine2 = null;
        try {
            engine = V8.createV8Runtime();
            engine2 = V8.createV8Runtime();

            engine.getDefaultContext().executeScript("b = { 'c': 'c' }");
            engine2.getDefaultContext().executeScript("a = { 'd': 'd' };");

            V8Object a = (V8Object) engine2.getDefaultContext().get("a");
            V8Object b = (V8Object) engine.get("b");
            b.add("data", a);
        } finally {
            engine.release(false);
            engine2.release(false);
        }
    }

    @Test(expected = Error.class)
    public void testSharingObjectsInArrayShouldNotCrashVM() {
        V8 engine = null;
        V8 engine2 = null;
        try {
            engine = V8.createV8Runtime();
            engine2 = V8.createV8Runtime();

            engine.getDefaultContext().executeScript("b = [];");
            engine2.getDefaultContext().executeScript("a = [];");

            V8Array a = (V8Array) engine2.getDefaultContext().get("a");
            V8Array b = (V8Array) engine.get("b");
            b.push(a);
        } finally {
            engine.release(false);
            engine2.release(false);
        }
    }

    @Test(expected = Error.class)
    public void testSharingObjectsAsFunctionCallParameters_ArrayFunction() {
        V8 engine = null;
        V8 engine2 = null;
        try {
            engine = V8.createV8Runtime();
            engine2 = V8.createV8Runtime();

            engine.getDefaultContext().executeScript("b = function(param){return param;}");
            engine2.getDefaultContext().executeScript("a = [[1,2,3]];");

            V8Array a = (V8Array) engine2.getDefaultContext().get("a");
            engine.getDefaultContext().executeArrayFunction("b", a);
        } finally {
            engine.release(false);
            engine2.release(false);
        }
    }

    @Test(expected = Error.class)
    public void testSharingObjectsAsFunctionCallParameters_ObjectFunction() {
        V8 engine = null;
        V8 engine2 = null;
        try {
            engine = V8.createV8Runtime();
            engine2 = V8.createV8Runtime();

            engine.getDefaultContext().executeScript("b = function(param){return param;}");
            engine2.getDefaultContext().executeScript("a = [{name: 'joe'}];");

            V8Array a = (V8Array) engine2.getDefaultContext().get("a");
            engine.getDefaultContext().executeObjectFunction("b", a);
        } finally {
            engine.release(false);
            engine2.release(false);
        }
    }

    @Test(expected = Error.class)
    public void testSharingObjectsAsFunctionCallParameters_ExecuteFunction() {
        V8 engine = null;
        V8 engine2 = null;
        try {
            engine = V8.createV8Runtime();
            engine2 = V8.createV8Runtime();

            engine.getDefaultContext().executeScript("b = function(param){return param;}");
            engine2.getDefaultContext().executeScript("a = [{name: 'joe'}];");

            V8Array a = (V8Array) engine2.getDefaultContext().get("a");
            engine.getDefaultContext().executeFunction("b", a);
        } finally {
            engine.release(false);
            engine2.release(false);
        }
    }

    @Test(expected = Error.class)
    public void testSharingObjectsAsFunctionCallParameters_BooleanFunction() {
        V8 engine = null;
        V8 engine2 = null;
        try {
            engine = V8.createV8Runtime();
            engine2 = V8.createV8Runtime();

            engine.getDefaultContext().executeScript("b = function(param){return param;}");
            engine2.getDefaultContext().executeScript("a = [false];");

            V8Array a = (V8Array) engine2.getDefaultContext().get("a");
            engine.getDefaultContext().executeBooleanFunction("b", a);
        } finally {
            engine.release(false);
            engine2.release(false);
        }
    }

    @Test(expected = Error.class)
    public void testSharingObjectsAsFunctionCallParameters_StringFunction() {
        V8 engine = null;
        V8 engine2 = null;
        try {
            engine = V8.createV8Runtime();
            engine2 = V8.createV8Runtime();

            engine.getDefaultContext().executeScript("b = function(param){return param;}");
            engine2.getDefaultContext().executeScript("a = ['foo'];");

            V8Array a = (V8Array) engine2.getDefaultContext().get("a");
            engine.getDefaultContext().executeStringFunction("b", a);
        } finally {
            engine.release(false);
            engine2.release(false);
        }
    }

    @Test(expected = Error.class)
    public void testSharingObjectsAsFunctionCallParameters_IntegerFunction() {
        V8 engine = null;
        V8 engine2 = null;
        try {
            engine = V8.createV8Runtime();
            engine2 = V8.createV8Runtime();

            engine.getDefaultContext().executeScript("b = function(param){return param;}");
            engine2.getDefaultContext().executeScript("a = [7];");

            V8Array a = (V8Array) engine2.getDefaultContext().get("a");
            engine.getDefaultContext().executeIntegerFunction("b", a);
        } finally {
            engine.release(false);
            engine2.release(false);
        }
    }

    @Test(expected = Error.class)
    public void testSharingObjectsAsFunctionCallParameters_DoubleFunction() {
        V8 engine = null;
        V8 engine2 = null;
        try {
            engine = V8.createV8Runtime();
            engine2 = V8.createV8Runtime();

            engine.getDefaultContext().executeScript("b = function(param){return param;}");
            engine2.getDefaultContext().executeScript("a = [3.14];");

            V8Array a = (V8Array) engine2.getDefaultContext().get("a");
            engine.getDefaultContext().executeDoubleFunction("b", a);
        } finally {
            engine.release(false);
            engine2.release(false);
        }
    }

    @Test(expected = Error.class)
    public void testSharingObjectsAsFunctionCallParameters_VoidFunction() {
        V8 engine = null;
        V8 engine2 = null;
        try {
            engine = V8.createV8Runtime();
            engine2 = V8.createV8Runtime();

            engine.getDefaultContext().executeScript("b = function(param1, param2){ param1 + param2;}");
            engine2.getDefaultContext().executeScript("a = [3, 4];");

            V8Array a = (V8Array) engine2.getDefaultContext().get("a");
            engine.getDefaultContext().executeVoidFunction("b", a);
        } finally {
            engine.release(false);
            engine2.release(false);
        }
    }

    @Test(expected = Error.class)
    public void testSharingObjectsAsFunctionCallParameters_JSFunction() {
        V8 engine = null;
        V8 engine2 = null;
        try {
            engine = V8.createV8Runtime();
            engine2 = V8.createV8Runtime();

            engine.getDefaultContext().executeScript("b = function(param){ param[0] + param[1];}");
            engine2.getDefaultContext().executeScript("a = [3, 4];");

            V8Array a = (V8Array) engine2.getDefaultContext().get("a");
            engine.getDefaultContext().executeJSFunction("b", a);
        } finally {
            engine.release(false);
            engine2.release(false);
        }
    }

    @Test
    public void testGetData() {
        Object value = new Object();
        v8Context.setData("foo", value);

        Object result = v8Context.getData("foo");

        assertSame(value, result);
    }

    @Test
    public void testReplaceValue() {
        Object value = new Object();
        v8Context.setData("foo", value);
        v8Context.setData("foo", "new value");

        Object result = v8Context.getData("foo");

        assertEquals("new value", result);
    }

    @Test
    public void testReplaceWithNull() {
        Object value = new Object();
        v8Context.setData("foo", value);
        v8Context.setData("foo", null);

        assertNull(v8Context.getData("foo"));
    }

    @Test
    public void testGetDataNothingSet() {
        assertNull(v8Context.getData("foo"));
    }

    @Test
    public void testGetNotSet() {
        Object value = new Object();
        v8Context.setData("foo", value);

        assertNull(v8Context.getData("bar"));
    }

    @Test
    public void testInitEmptyContainerNonNull() {
        long initEmptyContainer = v8Context.initEmptyContainer();

        assertNotEquals(0l, initEmptyContainer);
    }

    @Test
    public void testSetStackTraceLimit() {
        v8Context.executeVoidScript("Error.stackTraceLimit = Infinity");
        String script = "function a() { dieInHell(); }\n" +
                "function b() { a(); }\n" +
                "function c() { b(); }\n" +
                "function d() { c(); }\n" +
                "function e() { d(); }\n" +
                "function f() { e(); }\n" +
                "function g() { f(); }\n" +
                "function h() { g(); }\n" +
                "function i() { h(); }\n" +
                "function j() { i(); }\n" +
                "function k() { j(); }\n" +
                "function l() { k(); }\n" +
                "function m() { l(); }\n" +
                "function n() { m(); }\n" +
                "function o() { n(); }\n" +
                "function p() { o(); }\n" +
                "function q() { p(); }\n" +
                "\n" +
                "q();";
        try {
            v8Context.executeScript(script);
        } catch (V8ScriptException e) {
            int jsStackLength = e.getJSStackTrace().split("\n").length;
            assertEquals(19, jsStackLength);
            return;
        }
        fail("Exception not thrown");
    }

}
