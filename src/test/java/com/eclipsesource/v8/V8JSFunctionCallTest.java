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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class V8JSFunctionCallTest {

    private V8Isolate v8Isolate;
    private V8Context v8Context;
    private Object    result;

    @Before
    public void setup() {
        v8Isolate = V8Isolate.create();
        v8Context = v8Isolate.createContext();
    }

    @After
    public void tearDown() {
        try {
            if (v8Isolate != null) {
                v8Isolate.close();
            }
            if (V8Isolate.getActiveRuntimes() != 0) {
                throw new IllegalStateException("V8Runtimes not properly released");
            }
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
            throw e;
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testHandleReleasedReceiver() {
        V8Object object = v8Context.executeObjectScript("var x = { a: function() { return 10; } }; x;");
        V8Function function = (V8Function) object.get("a");
        object.close();
        V8Array parameters = new V8Array(v8Context);
        try {
            function.call(object, parameters);
        } finally {
            parameters.close();
            function.close();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testHandleReleasedParameters() {
        V8Object object = v8Context.executeObjectScript("var x = { a: function() { return 10; } }; x;");
        V8Function function = (V8Function) object.get("a");
        V8Array parameters = new V8Array(v8Context);
        parameters.close();
        try {
            function.call(object, parameters);
        } finally {
            object.close();
            function.close();
        }
    }

    @Test
    public void testGetFunction() {
        v8Context.executeVoidScript("function add(x, y) {return x+y;}");

        V8Object result = v8Context.getObject("add");

        assertTrue(result instanceof V8Function);
        result.close();
    }

    @Test
    public void testCallFunction() {
        v8Context.executeVoidScript("function add(x, y) {return x+y;}");
        V8Function function = (V8Function) v8Context.getObject("add");
        V8Array parameters = new V8Array(v8Context);
        parameters.push(7).push(8);

        Object result = function.call(v8Context, parameters);

        assertEquals(15, result);
        function.close();
        parameters.close();
    }

    @Test
    public void testCallFunctionNullParameters() {
        v8Context.executeVoidScript("function call() {return true;}");
        V8Function function = (V8Function) v8Context.getObject("call");

        boolean result = (Boolean) function.call(v8Context, null);

        assertTrue(result);
        function.close();
    }

    @Test
    public void testCallFunctionNullReceiver() {
        v8Context.executeVoidScript("function call() {return this;}");
        V8Function function = (V8Function) v8Context.getObject("call");

        Object result = function.call(null, null);

        assertEquals(v8Context, result);
        function.close();
        ((Releasable) result).release();
    }

    @Test
    public void testCallFunctionOnUndefined() {
        v8Context.executeVoidScript("function add(x, y) {return x+y;}");
        V8Function function = (V8Function) v8Context.getObject("add");
        V8Array parameters = new V8Array(v8Context);
        parameters.push(7).push(8);

        Object result = function.call(new V8Object.Undefined(), parameters);

        assertEquals(15, result);
        function.close();
        parameters.close();
    }

    @Test
    public void testFunctionScope() {
        v8Context.executeVoidScript("function say() { return this.name + ' say meow!'} ");
        V8Function function = (V8Function) v8Context.getObject("say");
        V8Object ginger = new V8Object(v8Context);
        ginger.add("name", "ginger");
        V8Object felix = new V8Object(v8Context);
        felix.add("name", "felix");

        Object result1 = function.call(ginger, null);
        Object result2 = function.call(felix, null);

        assertEquals("ginger say meow!", result1);
        assertEquals("felix say meow!", result2);
        function.close();
        ginger.close();
        felix.close();
    }

    @Test
    public void testIntFunction() {
        v8Context.executeVoidScript("function add(x, y) {return x+y;}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push(7);
        parameters.push(8);

        int result = v8Context.executeIntegerFunction("add", parameters);

        assertEquals(15, result);
        parameters.close();
    }

    @Test(expected = V8ResultUndefined.class)
    public void testIntegerFunctionNotInteger() {
        v8Context.executeVoidScript("function add(x, y) {return 'bar';}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push(7);
        parameters.push(8);

        try {
            v8Context.executeIntegerFunction("add", parameters);
        } finally {
            parameters.close();
        }
    }

    @Test(expected = V8ResultUndefined.class)
    public void testIntegerFunctionNoReturn() {
        v8Context.executeVoidScript("function add(x, y) {;}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push(7);
        parameters.push(8);

        try {
            v8Context.executeIntegerFunction("add", parameters);
        } finally {
            parameters.close();
        }
    }

    @Test
    public void testDoubleFunctionCall() {
        v8Context.executeVoidScript("function add(x, y) {return x+y;}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push(1.1);
        parameters.push(2.2);

        double result = v8Context.executeDoubleFunction("add", parameters);

        assertEquals(3.3, result, 0.000001);
        parameters.close();
    }

    @Test(expected = V8ResultUndefined.class)
    public void testDoubleFunctionNotDouble() {
        v8Context.executeVoidScript("function add(x, y) {return 'bar';}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push(7);
        parameters.push(8);

        try {
            v8Context.executeDoubleFunction("add", parameters);
        } finally {
            parameters.close();
        }
    }

    @Test(expected = V8ResultUndefined.class)
    public void testDoubleFunctionNoReturn() {
        v8Context.executeVoidScript("function add(x, y) {;}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push(7);
        parameters.push(8);

        try {
            v8Context.executeDoubleFunction("add", parameters);
        } finally {
            parameters.close();
        }
    }

    @Test
    public void testStringFunctionCall() {
        v8Context.executeVoidScript("function add(x, y) {return x+y;}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push("hello, ");
        parameters.push("world!");

        String result = v8Context.executeStringFunction("add", parameters);

        assertEquals("hello, world!", result);
        parameters.close();
    }

    @Test(expected = V8ResultUndefined.class)
    public void testStringFunctionNotString() {
        v8Context.executeVoidScript("function add(x, y) {return 7;}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push(7);
        parameters.push(8);

        try {
            v8Context.executeStringFunction("add", parameters);
        } finally {
            parameters.close();
        }
    }

    @Test(expected = V8ResultUndefined.class)
    public void testStringFunctionNoReturn() {
        v8Context.executeVoidScript("function add(x, y) {;}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push(7);
        parameters.push(8);

        try {
            v8Context.executeStringFunction("add", parameters);
        } finally {
            parameters.close();
        }
    }

    @Test(expected = V8ResultUndefined.class)
    public void testBooleanFunctionNotBoolean() {
        v8Context.executeVoidScript("function add(x, y) {return 'bar';}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push(7);
        parameters.push(8);

        try {
            v8Context.executeBooleanFunction("add", parameters);
        } finally {
            parameters.close();
        }
    }

    @Test(expected = V8ResultUndefined.class)
    public void testBooleanFunctionNoReturn() {
        v8Context.executeVoidScript("function add(x, y) {;}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push(7);
        parameters.push(8);

        try {
            v8Context.executeBooleanFunction("add", parameters);
        } finally {
            parameters.close();
        }
    }

    @Test
    public void testBooleanFunctionCall() {
        v8Context.executeVoidScript("function add(x, y) {return x&&y;}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push(true);
        parameters.push(true);

        boolean result = v8Context.executeBooleanFunction("add", parameters);

        assertTrue(result);
        parameters.close();
    }

    @Test
    public void testArrayFunctionCall() {
        v8Context.executeVoidScript("function add(a,b,c,d) {return [a,b,c,d];}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push(true);
        parameters.push(false);
        parameters.push(7);
        parameters.push("foo");

        V8Array result = v8Context.executeArrayFunction("add", parameters);

        assertTrue(result.getBoolean(0));
        assertFalse(result.getBoolean(1));
        assertEquals(7, result.getInteger(2));
        assertEquals("foo", result.getString(3));
        parameters.close();
        result.close();
    }

    @Test(expected = V8ResultUndefined.class)
    public void testArrayFunctionNotArray() {
        v8Context.executeVoidScript("function add(x, y) {return 7;}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push(7);
        parameters.push(8);

        try {
            v8Context.executeArrayFunction("add", parameters);
        } finally {
            parameters.close();
        }
    }

    @Test
    public void testObjectFunctionCall() {
        v8Context.executeVoidScript("function getPerson(first, last, age) {return {'first':first, 'last':last, 'age':age};}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push("John");
        parameters.push("Smith");
        parameters.push(7);

        V8Object result = v8Context.executeObjectFunction("getPerson", parameters);

        assertEquals("John", result.getString("first"));
        assertEquals("Smith", result.getString("last"));
        assertEquals(7, result.getInteger("age"));
        parameters.close();
        result.close();
    }

    @Test(expected = V8ResultUndefined.class)
    public void testObjectFunctionNotObject() {
        v8Context.executeVoidScript("function add(x, y) {return 7;}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push(7);
        parameters.push(8);

        try {
            v8Context.executeObjectFunction("add", parameters);
        } finally {
            parameters.close();
        }
    }

    @Test
    public void testFunctionCallWithObjectReturn() {
        v8Context.executeVoidScript("function getPerson(first, last, age) {return {'first':first, 'last':last, 'age':age};}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push("John");
        parameters.push("Smith");
        parameters.push(7);

        Object result = v8Context.executeFunction("getPerson", parameters);

        assertTrue(result instanceof V8Object);
        V8Object v8Object = (V8Object) result;
        assertEquals("John", v8Object.getString("first"));
        assertEquals("Smith", v8Object.getString("last"));
        assertEquals(7, v8Object.getInteger("age"));
        parameters.close();
        v8Object.close();
    }

    @Test
    public void testFunctionCallWithIntegerReturn() {
        v8Context.executeVoidScript("function getAge(first, last, age) {return age;}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push("John");
        parameters.push("Smith");
        parameters.push(7);

        Object result = v8Context.executeFunction("getAge", parameters);

        assertTrue(result instanceof Integer);
        assertEquals(7, result);
        parameters.close();
    }

    @Test
    public void testFunctionCallWithDoubleReturn() {
        v8Context.executeVoidScript("function getFoo() {return 33.3;}");

        Object result = v8Context.executeFunction("getFoo", null);

        assertEquals(33.3, (Double) result, 0.000001);
    }

    @Test
    public void testFunctionCallWithStringReturn() {
        v8Context.executeVoidScript("function getFoo() {return 'bar';}");

        Object result = v8Context.executeFunction("getFoo", null);

        assertEquals("bar", result);
    }

    @Test
    public void testFunctionCallWithBooleanReturn() {
        v8Context.executeVoidScript("function getFoo() {return true;}");

        Object result = v8Context.executeFunction("getFoo", null);

        assertTrue((Boolean) result);
    }

    @Test
    public void testFunctionCallWithNullReturn() {
        v8Context.executeVoidScript("function getFoo() {return null;}");

        Object result = v8Context.executeFunction("getFoo", null);

        assertNull(result);
    }

    @Test
    public void testFunctionCallWithUndefinedReturn() {
        v8Context.executeVoidScript("function getFoo() {return undefined;}");

        Object result = v8Context.executeFunction("getFoo", null);

        assertEquals(V8Isolate.getUndefined(), result);
    }

    @Test
    public void testFunctionCallWithArrayReturn() {
        v8Context.executeVoidScript("function getFoo() {return [1,2,3];}");

        Object result = v8Context.executeFunction("getFoo", null);

        assertTrue(result instanceof V8Array);
        V8Array v8Array = (V8Array) result;
        assertEquals(3, v8Array.length());
        assertEquals(1, v8Array.get(0));
        assertEquals(2, v8Array.get(1));
        assertEquals(3, v8Array.get(2));
        v8Array.close();
    }

    @Test
    public void testFunctionCallWithNoReturn() {
        v8Context.executeVoidScript("function getAge(first, last, age) {}");

        Object result = v8Context.executeFunction("getAge", null);

        assertEquals(V8Isolate.getUndefined(), result);
    }

    @Test
    public void testVoidFunctionCall() {
        v8Context.executeVoidScript("function setPerson(first, last, age) {person = {'first':first, 'last':last, 'age':age};}");
        V8Array parameters = new V8Array(v8Context);
        parameters.push("John");
        parameters.push("Smith");
        parameters.push(7);

        v8Context.executeVoidFunction("setPerson", parameters);
        V8Object result = v8Context.getObject("person");

        assertEquals("John", result.getString("first"));
        assertEquals("Smith", result.getString("last"));
        assertEquals(7, result.getInteger("age"));
        parameters.close();
        result.close();
    }

    @Test
    public void testIntFunctionCallNoParameters() {
        v8Context.executeVoidScript("function foo() {return 7;}");
        V8Array parameters = new V8Array(v8Context);

        int result = v8Context.executeIntegerFunction("foo", parameters);

        assertEquals(7, result);
        parameters.close();
    }

    @Test
    public void testDoubleFunctionCallNoParameters() {
        v8Context.executeVoidScript("function foo() {return 7.2;}");
        V8Array parameters = new V8Array(v8Context);

        double result = v8Context.executeDoubleFunction("foo", parameters);

        assertEquals(7.2, result, 0.0000001);
        parameters.close();
    }

    @Test
    public void testStringFunctionCallNoParameters() {
        v8Context.executeVoidScript("function foo() {return 'hello';}");
        V8Array parameters = new V8Array(v8Context);

        String result = v8Context.executeStringFunction("foo", parameters);

        assertEquals("hello", result);
        parameters.close();
    }

    @Test
    public void testBooleanFunctionCallNoParameters() {
        v8Context.executeVoidScript("function foo() {return true;}");
        V8Array parameters = new V8Array(v8Context);

        boolean result = v8Context.executeBooleanFunction("foo", parameters);

        assertTrue(result);
        parameters.close();
    }

    @Test
    public void testArrayFunctionCallNoParameters() {
        v8Context.executeVoidScript("function foo() {return [];}");
        V8Array parameters = new V8Array(v8Context);

        V8Array result = v8Context.executeArrayFunction("foo", parameters);

        assertEquals(0, result.length());
        parameters.close();
        result.close();
    }

    @Test
    public void testObjectFunctionCallNoParameters() {
        v8Context.executeVoidScript("function foo() {return {bar:8};}");
        V8Array parameters = new V8Array(v8Context);

        V8Object result = v8Context.executeObjectFunction("foo", parameters);

        assertEquals(8, result.getInteger("bar"));
        parameters.close();
        result.close();
    }

    @Test
    public void testVoidFunctionCallNoParameters() {
        v8Context.executeVoidScript("function foo() {x=7;}");
        V8Array parameters = new V8Array(v8Context);

        v8Context.executeVoidFunction("foo", parameters);

        assertEquals(7, v8Context.getInteger("x"));
        parameters.close();
    }

    @Test
    public void testIntFunctionCallNullParameters() {
        v8Context.executeVoidScript("function foo() {return 7;}");

        int result = v8Context.executeIntegerFunction("foo", null);

        assertEquals(7, result);
    }

    @Test
    public void testDoubleFunctionCallNullParameters() {
        v8Context.executeVoidScript("function foo() {return 7.1;}");

        double result = v8Context.executeDoubleFunction("foo", null);

        assertEquals(7.1, result, 0.000001);
    }

    @Test
    public void testStringFunctionCallNullParameters() {
        v8Context.executeVoidScript("function foo() {return 'hello';}");

        String result = v8Context.executeStringFunction("foo", null);

        assertEquals("hello", result);
    }

    @Test
    public void testBooleanFunctionCallNullParameters() {
        v8Context.executeVoidScript("function foo() {return true;}");

        boolean result = v8Context.executeBooleanFunction("foo", null);

        assertTrue(result);
    }

    @Test
    public void testArrayFunctionCallNullParameters() {
        v8Context.executeVoidScript("function foo() {return [1,2];}");

        V8Array result = v8Context.executeArrayFunction("foo", null);

        assertEquals(2, result.length());
        result.close();
    }

    @Test
    public void testObjectFunctionCallNullParameters() {
        v8Context.executeVoidScript("function foo() {return {a:'b'};}");

        V8Object result = v8Context.executeObjectFunction("foo", null);

        assertEquals("b", result.getString("a"));
        result.close();
    }

    @Test
    public void testVoidFunctionCallNullParameters() {
        v8Context.executeVoidScript("function foo() {x=7;}");

        v8Context.executeVoidFunction("foo", null);

        assertEquals(7, v8Context.getInteger("x"));
    }

    @Test
    public void testIntFunctionCallOnObject() {
        v8Context.executeVoidScript("function add(x, y) {return x + y;}");
        v8Context.executeVoidScript("adder = {};");
        v8Context.executeVoidScript("adder.addFuction = add;");
        V8Object object = v8Context.getObject("adder");

        V8Array parameters = new V8Array(v8Context);
        parameters.push(7);
        parameters.push(8);
        int result = object.executeIntegerFunction("addFuction", parameters);
        parameters.close();

        assertEquals(15, result);
        object.close();
    }

    @Test
    public void testDoubleFunctionCallOnObject() {
        v8Context.executeVoidScript("function add(x, y) {return x + y;}");
        v8Context.executeVoidScript("adder = {};");
        v8Context.executeVoidScript("adder.addFuction = add;");
        V8Object object = v8Context.getObject("adder");

        V8Array parameters = new V8Array(v8Context);
        parameters.push(7.1);
        parameters.push(8.1);
        double result = object.executeDoubleFunction("addFuction", parameters);
        parameters.close();

        assertEquals(15.2, result, 0.000001);
        object.close();
    }

    @Test
    public void testStringFunctionCallOnObject() {
        v8Context.executeVoidScript("function add(x, y) {return x + y;}");
        v8Context.executeVoidScript("adder = {};");
        v8Context.executeVoidScript("adder.addFuction = add;");
        V8Object object = v8Context.getObject("adder");

        V8Array parameters = new V8Array(v8Context);
        parameters.push("hello, ");
        parameters.push("world!");
        String result = object.executeStringFunction("addFuction", parameters);
        parameters.close();

        assertEquals("hello, world!", result);
        object.close();
    }

    @Test
    public void testBooleanFunctionCallOnObject() {
        v8Context.executeVoidScript("function add(x, y) {return x && y;}");
        v8Context.executeVoidScript("adder = {};");
        v8Context.executeVoidScript("adder.addFuction = add;");
        V8Object object = v8Context.getObject("adder");

        V8Array parameters = new V8Array(v8Context);
        parameters.push(true);
        parameters.push(false);
        boolean result = object.executeBooleanFunction("addFuction", parameters);
        parameters.close();

        assertFalse(result);
        object.close();
    }

    @Test
    public void testArrayFunctionCallOnObject() {
        v8Context.executeVoidScript("function add(x, y) {return [x,y];}");
        v8Context.executeVoidScript("adder = {};");
        v8Context.executeVoidScript("adder.addFuction = add;");
        V8Object object = v8Context.getObject("adder");

        V8Array parameters = new V8Array(v8Context);
        parameters.push(true);
        parameters.push(false);
        V8Array result = object.executeArrayFunction("addFuction", parameters);
        parameters.close();

        assertFalse(result.getBoolean(1));
        assertTrue(result.getBoolean(0));
        result.close();
        object.close();
    }

    @Test
    public void testObjectFunctionCallOnObject() {
        v8Context.executeVoidScript("function getPoint(x, y) {return {'x':x, 'y':y};}");
        v8Context.executeVoidScript("pointer = {};");
        v8Context.executeVoidScript("pointer.pointGetter = getPoint;");
        V8Object object = v8Context.getObject("pointer");

        V8Array parameters = new V8Array(v8Context);
        parameters.push(8);
        parameters.push(9);
        V8Object result = object.executeObjectFunction("pointGetter", parameters);
        parameters.close();

        assertEquals(8, result.getInteger("x"));
        assertEquals(9, result.getInteger("y"));
        result.close();
        object.close();
    }

    @Test
    public void testVoidFunctionCallOnObject() {
        v8Context.executeVoidScript("pointer = {'x':0,'y':0};");
        v8Context.executeVoidScript("function setPoint(x, y) {pointer.x = x;pointer.y=y;}");
        v8Context.executeVoidScript("pointer.pointSetter = setPoint;");
        V8Object object = v8Context.getObject("pointer");

        V8Array parameters = new V8Array(v8Context);
        parameters.push(8);
        parameters.push(9);
        object.executeVoidFunction("pointSetter", parameters);
        parameters.close();

        assertEquals(8, object.getInteger("x"));
        assertEquals(9, object.getInteger("y"));
        object.close();
    }

    @Test
    public void testStringParameter() {
        v8Context.executeVoidScript("function countLength(str) {return str.length;}");

        V8Array parameters = new V8Array(v8Context);
        parameters.push("abcdefghijklmnopqrstuvwxyz");

        assertEquals(26, v8Context.executeIntegerFunction("countLength", parameters));
        parameters.close();
    }

    @Test
    public void testObjectParameter() {
        V8Object obj1 = new V8Object(v8Context);
        V8Object obj2 = new V8Object(v8Context);
        obj1.add("first", "John");
        obj1.add("last", "Smith");
        obj1.add("age", 7);
        obj2.add("first", "Tim");
        obj2.add("last", "Jones");
        obj2.add("age", 8);
        V8Array parameters = new V8Array(v8Context);
        parameters.push(obj1);
        parameters.push(obj2);

        v8Context.executeVoidScript("function add(p1, p2) {return p1.age + p2['age'];}");
        int result = v8Context.executeIntegerFunction("add", parameters);

        assertEquals(15, result);
        obj1.close();
        obj2.close();
        parameters.close();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExecuteJSFunction_InvalidArg() {
        v8Context.executeVoidScript("function add(p1, p2) {return p1 + p2;}");

        v8Context.executeJSFunction("add", new Object(), 8);
    }

    @Test
    public void testExecuteJSFunction_VarArgs() {
        v8Context.executeVoidScript("function add() {return arguments.length;}");

        int result = (Integer) v8Context.executeJSFunction("add", 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        assertEquals(10, result);
    }

    @Test
    public void testExecuteJSFunction_Integer() {
        v8Context.executeVoidScript("function add(p1, p2) {return p1 + p2;}");

        int result = (Integer) v8Context.executeJSFunction("add", 7, 8);

        assertEquals(15, result);
    }

    @Test
    public void testExecuteJSFunction_Float() {
        v8Context.executeVoidScript("function add(p1, p2) {return p1 + p2;}");

        double result = (Double) v8Context.executeJSFunction("add", 3.1f, 2.2f);

        assertEquals(5.3, result, 0.00001);
    }

    @Test
    public void testExecuteJSFunction_Double() {
        v8Context.executeVoidScript("function add(p1, p2) {return p1 + p2;}");

        double result = (Double) v8Context.executeJSFunction("add", 3.1d, 2.2d);

        assertEquals(5.3, result, 0.00001);
    }

    @Test
    public void testExecuteJSFunction_IntegerSingleParam() {
        v8Context.executeVoidScript("function add(p1) {return p1;}");

        int result = (Integer) v8Context.executeJSFunction("add", 7);

        assertEquals(7, result);
    }

    @Test
    public void testExecuteJSFunction_BooleanSingleParam() {
        v8Context.executeVoidScript("function add(p1) {return p1;}");

        boolean result = (Boolean) v8Context.executeJSFunction("add", false);

        assertFalse(result);
    }

    @Test
    public void testExecuteJSFunction_String() {
        v8Context.executeVoidScript("function add(p1, p2) {return p1 + p2;}");

        String result = (String) v8Context.executeJSFunction("add", "seven", "eight");

        assertEquals("seveneight", result);
    }

    @Test
    public void testExecuteJSFunction_V8Object() {
        V8Object object = new V8Object(v8Context);
        object.add("first", 7).add("second", 8);
        v8Context.executeVoidScript("function add(p1) {return p1.first + p1.second;}");

        int result = (Integer) v8Context.executeJSFunction("add", object);

        assertEquals(15, result);
        object.close();
    }

    @Test
    public void testExecuteJSFunction_V8Array() {
        V8Array array = new V8Array(v8Context);
        array.push(7).push(8);
        v8Context.executeVoidScript("function add(p1) {return p1[0] + p1[1];}");

        int result = (Integer) v8Context.executeJSFunction("add", array);

        assertEquals(15, result);
        array.close();
    }

    @Test
    public void testExecuteJSFunction_Mixed() {
        V8Array array = new V8Array(v8Context);
        array.push(7).push(8);
        V8Object object = new V8Object(v8Context);
        object.add("first", 7).add("second", 8);
        v8Context.executeVoidScript("function add(p1, p2) {return p1[0] + p1[1] + p2.first + p2.second ;}");

        int result = (Integer) v8Context.executeJSFunction("add", array, object);

        assertEquals(30, result);
        object.close();
        array.close();
    }

    @Test
    public void testExecuteJSFunction_Function() {
        v8Context.executeVoidScript("function add(p1, p2) {return p1();}");
        V8Function function = new V8Function(v8Context, new JavaCallback() {

            @Override
            public Object invoke(final V8Object receiver, final V8Array parameters) {
                return 7;
            }
        });

        int result = (Integer) v8Context.executeJSFunction("add", function);

        assertEquals(7, result);
        function.close();
    }

    @Test
    public void testExecuteJSFunction_null() {
        v8Context.executeVoidScript("function test(p1) { if (!p1) { return 'passed';} }");

        String result = (String) v8Context.executeJSFunction("test", new Object[] { null });

        assertEquals("passed", result);
    }

    @Test
    public void testExecuteJSFunction_nullArray() {
        v8Context.executeVoidScript("function test() { return 'passed';}");

        String result = (String) v8Context.executeJSFunction("test", (Object[]) null);

        assertEquals("passed", result);
    }

    @Test
    public void testExecuteJSFunction_NoParameters() {
        v8Context.executeVoidScript("function test() { return 'passed';}");

        String result = (String) v8Context.executeJSFunction("test");

        assertEquals("passed", result);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testExecuteJSFunction_UndefinedReceiver() {
        v8Context.executeVoidScript("function test() { }");
        V8Object undefined = new V8Object.Undefined();

        undefined.executeJSFunction("test", (Object[]) null);
        undefined.close();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testExecuteFunction_UndefinedReceiver() {
        v8Context.executeVoidScript("function test() { }");
        V8Object undefined = new V8Object.Undefined();

        undefined.executeFunction("test", null);
        undefined.close();
    }

    @Test
    public void testExecuteJSFunction_undefined() {
        v8Context.executeVoidScript("function test(p1) { if (!p1) { return 'passed';} }");

        String result = (String) v8Context.executeJSFunction("test", V8Isolate.getUndefined());

        assertEquals("passed", result);
    }

    @Test
    public void testCallFunctionWithEmojiParamter() {
        V8Function v8Function = new V8Function(v8Context, new JavaCallback() {

            @Override
            public Object invoke(final V8Object receiver, final V8Array parameters) {
                return parameters.get(0);
            }
        });

        V8Array paramters = new V8Array(v8Context);
        paramters.push("👄");
        Object result = v8Function.call(null, paramters);

        assertEquals("👄", result);
        paramters.close();
        v8Function.close();
    }

    @Test
    public void testCreateV8Function() {
        V8Function function = new V8Function(v8Context, new JavaCallback() {

            @Override
            public Object invoke(final V8Object receiver, final V8Array parameters) {
                result = "passed";
                return null;
            }
        });
        function.call(null, null);

        assertEquals("passed", result);
        function.close();
    }

    @Test
    public void testCreateV8Function_CalledFromJS() {
        v8Context.executeScript("function doSomething(callback) { callback(); }");
        V8Function function = new V8Function(v8Context, new JavaCallback() {

            @Override
            public Object invoke(final V8Object receiver, final V8Array parameters) {
                result = "passed";
                return null;
            }
        });
        V8Array parameters = new V8Array(v8Context);
        parameters.push(function);
        v8Context.executeVoidFunction("doSomething", parameters);
        function.close();
        parameters.close();

        assertEquals("passed", result);
    }

    @Test
    public void testCreateV8Function_CalledFromJS_AfterFunctionReleased() {
        v8Context.executeScript("function doSomething(callback) { callback(); }");
        V8Function function = new V8Function(v8Context, new JavaCallback() {

            @Override
            public Object invoke(final V8Object receiver, final V8Array parameters) {
                result = "passed";
                return null;
            }
        });
        V8Array parameters = new V8Array(v8Context);
        parameters.push(function);
        function.close();
        v8Context.executeVoidFunction("doSomething", parameters);
        parameters.close();

        assertEquals("passed", result);
    }

    @Test(expected = Error.class)
    public void testSharingObjectsAsFunctionCallParameters_JSFunction() {
        V8Isolate engine = null;
        V8Isolate engine2 = null;
        try {
            engine = V8Isolate.create();
            engine2 = V8Isolate.create();

            V8Context context = engine.createContext();
            V8Context context2 = engine2.createContext();

            V8Function function = new V8Function(context, new JavaCallback() {

                @Override
                public Object invoke(final V8Object receiver, final V8Array parameters) {
                    return parameters.getInteger(0) + parameters.getInteger(1);
                }
            });
            context2.executeScript("a = [3, 4];");
            V8Array a = (V8Array) context2.get("a");

            function.call(null, a);
            function.close();
        } finally {
            engine.release(false);
            engine2.release(false);
        }
    }

    @Test(expected = Error.class)
    public void testSharingObjectsAsFunctionCallThis() {
        V8Isolate engine = null;
        V8Isolate engine2 = null;
        try {
            engine = V8Isolate.create();
            engine2 = V8Isolate.create();

            V8Context context = engine.createContext();
            V8Context context2 = engine2.createContext();

            V8Function function = new V8Function(context, new JavaCallback() {

                @Override
                public Object invoke(final V8Object receiver, final V8Array parameters) {
                    System.out.println(receiver.get("name"));
                    return receiver.get("name");
                }
            });
            context2.executeScript("a = {name: 'joe'};");
            V8Object a = (V8Object) context2.get("a");

            function.call(a, null);
            function.close();
        } finally {
            engine.release(false);
            engine2.release(false);
        }
    }

}
