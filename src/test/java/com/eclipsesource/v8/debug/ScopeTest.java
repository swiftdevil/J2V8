/*******************************************************************************
 * Copyright (c) 2016 EclipseSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    EclipseSource - initial API and implementation
 ******************************************************************************/
package com.eclipsesource.v8.debug;

import com.eclipsesource.v8.V8Context;
import com.eclipsesource.v8.V8Isolate;
import com.eclipsesource.v8.V8Object;
import com.eclipsesource.v8.debug.DebugHandler.DebugEvent;
import com.eclipsesource.v8.debug.mirror.Frame;
import com.eclipsesource.v8.debug.mirror.ObjectMirror;
import com.eclipsesource.v8.debug.mirror.ObjectMirror.PropertyKind;
import com.eclipsesource.v8.debug.mirror.Scope;
import com.eclipsesource.v8.debug.mirror.Scope.ScopeType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class ScopeTest {

    private static String script = "    // 1  \n"
            + "function foo(a, b, c)  { // 2  \n"
            + "  var x = 7;             // 3  \n"
            + "  var y = x + 1;         // 4  \n"
            + "  return function() {    // 5  \n"
            + "    var z = x;           // 6  \n"
            + "    var k = 8;           // 7  \n"
            + "    return z;            // 8  \n"
            + "  }                      // 9  \n"
            + "}                        // 10 \n"
            + "foo(1,2,3)();            // 11 \n";
    private Object        result = false;
    private V8Isolate     v8Isolate;
    private V8Context     v8Context;
    private DebugHandler  debugHandler;
    private BreakHandler  breakHandler;

    @Before
    public void setup() {
        V8Isolate.setFlags("--expose-debug-as=" + DebugHandler.DEBUG_OBJECT_NAME);
        v8Isolate = V8Isolate.create();
        v8Context = v8Isolate.createContext();
        debugHandler = new DebugHandler(v8Context);
        debugHandler.setScriptBreakpoint("script", 7);
        breakHandler = mock(BreakHandler.class);
        debugHandler.addBreakHandler(breakHandler);
    }

    @After
    public void tearDown() {
        try {
            debugHandler.close();
            v8Isolate.close();
            if (V8Isolate.getActiveRuntimes() != 0) {
                throw new IllegalStateException("V8Runtimes not properly released");
            }
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
        }
    }

    @Test
    public void testGetLocalScopeType() {
        handleBreak(new BreakHandler() {

            @Override
            public void onBreak(final DebugEvent event, final ExecutionState state, final EventData eventData, final V8Object data) {
                Frame frame = state.getFrame(0);
                Scope scope = frame.getScope(0);
                result = scope.getType();
                scope.close();
                frame.close();
            }
        });

        v8Context.executeScript(script, "script", 0);

        assertEquals(ScopeType.Local, result);
    }

    @Test
    public void testGetScriptScopeType() {
        handleBreak(new BreakHandler() {

            @Override
            public void onBreak(final DebugEvent event, final ExecutionState state, final EventData eventData, final V8Object data) {
                Frame frame = state.getFrame(0);
                Scope scope = frame.getScope(2);
                result = scope.getType();
                scope.close();
                frame.close();
            }
        });

        v8Context.executeScript(script, "script", 0);

        assertEquals(ScopeType.Script, result);
    }

    @Test
    public void testGetGlobalScopeType() {
        handleBreak(new BreakHandler() {

            @Override
            public void onBreak(final DebugEvent event, final ExecutionState state, final EventData eventData, final V8Object data) {
                Frame frame = state.getFrame(0);
                Scope scope = frame.getScope(3);
                result = scope.getType();
                scope.close();
                frame.close();
            }
        });

        v8Context.executeScript(script, "script", 0);

        assertEquals(ScopeType.Global, result);
    }

    @Test
    public void testGetClosureScope() {
        handleBreak(new BreakHandler() {

            @Override
            public void onBreak(final DebugEvent event, final ExecutionState state, final EventData eventData, final V8Object data) {
                Frame frame = state.getFrame(0);
                Scope scope = frame.getScope(1);
                result = scope.getType();
                scope.close();
                frame.close();
            }
        });

        v8Context.executeScript(script, "script", 0);

        assertEquals(ScopeType.Closure, result);
    }

    @Test
    public void testSetVariableValue() {
        handleBreak(new BreakHandler() {

            @Override
            public void onBreak(final DebugEvent event, final ExecutionState state, final EventData eventData, final V8Object data) {
                Frame frame = state.getFrame(0);
                Scope scope = frame.getScope(0);
                scope.setVariableValue("z", 0);
                scope.close();
                frame.close();
            }
        });

        int result = v8Context.executeIntegerScript(script, "script", 0);

        assertEquals(0, result);
    }

    @Test
    public void testChangeVariableTypeV8Object() {
        handleBreak(new BreakHandler() {

            @Override
            public void onBreak(final DebugEvent event, final ExecutionState state, final EventData eventData, final V8Object data) {
                Frame frame = state.getFrame(0);
                Scope scope = frame.getScope(0);
                V8Object newValue = new V8Object(v8Context);
                newValue.add("foo", "bar");
                scope.setVariableValue("z", newValue);
                newValue.close();
                scope.close();
                frame.close();
            }
        });

        V8Object result = v8Context.executeObjectScript(script, "script", 0);

        assertEquals("bar", result.getString("foo"));
        result.close();
    }

    @Test
    public void testChangeVariableTypeBoolean() {
        handleBreak(new BreakHandler() {

            @Override
            public void onBreak(final DebugEvent event, final ExecutionState state, final EventData eventData, final V8Object data) {
                Frame frame = state.getFrame(0);
                Scope scope = frame.getScope(0);
                scope.setVariableValue("z", false);
                scope.close();
                frame.close();
            }
        });

        boolean result = (Boolean) v8Context.executeScript(script, "script", 0);

        assertFalse(result);
    }

    @Test
    public void testChangeVariableTypeDouble() {
        handleBreak(new BreakHandler() {

            @Override
            public void onBreak(final DebugEvent event, final ExecutionState state, final EventData eventData, final V8Object data) {
                Frame frame = state.getFrame(0);
                Scope scope = frame.getScope(0);
                scope.setVariableValue("z", 3.14);
                scope.close();
                frame.close();
            }
        });

        double result = (Double) v8Context.executeScript(script, "script", 0);

        assertEquals(3.14, result, 0.0001);
    }

    @Test
    public void testChangeVariableTypeString() {
        handleBreak(new BreakHandler() {

            @Override
            public void onBreak(final DebugEvent event, final ExecutionState state, final EventData eventData, final V8Object data) {
                Frame frame = state.getFrame(0);
                Scope scope = frame.getScope(0);
                scope.setVariableValue("z", "someString");
                scope.close();
                frame.close();
            }
        });

        String result = (String) v8Context.executeScript(script, "script", 0);

        assertEquals("someString", result);
    }

    @Test
    public void testGetScopeObject() {
        handleBreak(new BreakHandler() {

            @Override
            public void onBreak(final DebugEvent event, final ExecutionState state, final EventData eventData, final V8Object data) {
                Frame frame = state.getFrame(0);
                Scope scope = frame.getScope(0);
                ObjectMirror scopeObject = scope.getScopeObject();
                String[] propertyNames = scopeObject.getPropertyNames(PropertyKind.Named, 0);
                result = propertyNames.length == 2;
                result = (Boolean) result && propertyNames[0].equals("z");
                result = (Boolean) result && propertyNames[1].equals("k");
                scopeObject.close();
                scope.close();
                frame.close();
            }
        });

        v8Context.executeScript(script, "script", 0);

        assertTrue((Boolean) result);
    }

    private void handleBreak(final BreakHandler handler) {
        doAnswer(new Answer<Object>() {

            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                DebugEvent arg1 = (DebugEvent) invocation.getArguments()[0];
                ExecutionState arg2 = (ExecutionState) invocation.getArguments()[1];
                EventData arg3 = (EventData) invocation.getArguments()[2];
                V8Object arg4 = (V8Object) invocation.getArguments()[3];
                handler.onBreak(arg1, arg2, arg3, arg4);
                return null;
            }

        }).when(breakHandler).onBreak(eq(DebugEvent.Break), any(ExecutionState.class), any(EventData.class), any(V8Object.class));
    }

}
