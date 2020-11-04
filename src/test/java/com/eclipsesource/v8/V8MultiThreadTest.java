/*******************************************************************************
 * Copyright (c) 2015 EclipseSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    EclipseSource - initial API and implementation
 ******************************************************************************/
package com.eclipsesource.v8;

import com.eclipsesource.v8.utils.V8ContextRunnable;
import com.eclipsesource.v8.utils.V8ContextThread;
import com.eclipsesource.v8.utils.V8ObjectUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class V8MultiThreadTest {

    private List<Object> mergeSortResults = new ArrayList<Object>();

    private static final String sortAlgorithm = ""
            + "function merge(left, right){\n"
            + "  var result  = [],\n"
            + "  il      = 0,\n"
            + "  ir      = 0;\n"
            + "  while (il < left.length && ir < right.length){\n"
            + "    if (left[il] < right[ir]){\n"
            + "      result.push(left[il++]);\n"
            + "    } else {\n"
            + "      result.push(right[ir++]);\n"
            + "    }\n"
            + "  }\n"
            + "  return result.concat(left.slice(il)).concat(right.slice(ir));\n"
            + "};\n"
            + "\n"
            + "function sort(data) {\n"
            + "  if ( data.length === 1 ) {\n"
            + "    return [data[0]];\n"
            + "  } else if (data.length === 2 ) {\n"
            + "    if ( data[1] < data[0] ) {\n"
            + "      return [data[1],data[0]];\n"
            + "    } else {\n"
            + "      return data;\n"
            + "    }\n"
            + "  }\n"
            + "  var mid = Math.floor(data.length / 2);\n"
            + "  var first = data.slice(0, mid);\n"
            + "  var second = data.slice(mid);\n"
            + "  return merge(_sort( first ), _sort( second ) );\n"
            + "}\n";

    public class Sort implements JavaCallback {
        List<Object> result = null;

        @Override
        public Object invoke(final V8Object receiver, final V8Array parameters) {
            final List<Object> data = V8ObjectUtils.toList(parameters);

            V8ContextThread t = new V8ContextThread(new V8ContextRunnable() {

                @Override
                public void run(final V8Context v8Context) {
                    v8Context.registerJavaMethod(new Sort(), "_sort");
                    v8Context.executeVoidScript(sortAlgorithm);
                    V8Array parameters = V8ObjectUtils.toV8Array(v8Context, data);
                    V8Array _result = v8Context.executeArrayFunction("sort", parameters);
                    result = V8ObjectUtils.toList(_result);
                    _result.close();
                    parameters.close();
                }
            });
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return V8ObjectUtils.toV8Array(parameters.getContext(), result);
        }
    }

    V8Isolate v8IsolateTempRuntime = null;

    @Test
    public void testLosesCurrentIsolate() {
        final V8Isolate v8Isolate = V8Isolate.create();
        final V8Context v8Context = v8Isolate.createContext();
        v8Context.registerJavaMethod(new JavaCallback() {

            @Override
            public Object invoke(final V8Object receiver, final V8Array parameters) {
                v8IsolateTempRuntime = V8Isolate.create();
                v8IsolateTempRuntime.getLocker().release();
                throw new RuntimeException();
            }
        }, "foo");
        try {
            v8Context.executeFunction("foo", null);
        } catch (RuntimeException e) {
            // doNothing
        }
        v8Isolate.release(false);
        v8IsolateTempRuntime.getLocker().acquire();
        v8IsolateTempRuntime.close();
    }

    @Test(expected = Exception.class)
    public void testReleaseLockInCallback() {
        final V8Isolate v8Isolate = V8Isolate.create();
        final V8Context v8Context = v8Isolate.createContext();
        try {
            v8Context.registerJavaMethod(new JavaCallback() {

                @Override
                public Object invoke(final V8Object receiver, final V8Array parameters) {
                    v8Isolate.getLocker().release();
                    v8Isolate.getLocker().acquire();
                    return null;
                }
            }, "foo");
            v8Context.executeFunction("foo", null);
        } finally {
            v8Isolate.close();
        }
    }

    @SuppressWarnings("unchecked")
    public void testMultiV8Threads() throws InterruptedException {

        final List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < 10; i++) {
            V8ContextThread t = new V8ContextThread(new V8ContextRunnable() {

                @Override
                public void run(final V8Context v8Context) {
                    v8Context.registerJavaMethod(new Sort(), "_sort");
                    v8Context.executeVoidScript(sortAlgorithm);
                    V8Array data = new V8Array(v8Context);
                    int max = 100;
                    for (int i = 0; i < max; i++) {
                        data.push(max - i);
                    }
                    V8Array parameters = new V8Array(v8Context);
                    parameters.push(data);
                    V8Array result = v8Context.executeArrayFunction("sort", parameters);
                    synchronized (threads) {
                        mergeSortResults.add(V8ObjectUtils.toList(result));
                    }
                    result.close();
                    parameters.close();
                    data.close();
                }

            });
            threads.add(t);
        }
        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(10, mergeSortResults.size());
        for (int i = 0; i < 10; i++) {
            assertSorted((List<Integer>) mergeSortResults.get(i));
        }
    }

    private void assertSorted(final List<Integer> result) {
        for (int i = 0; i < (result.size() - 1); i++) {
            assertTrue(result.get(i) < result.get(i + 1));
        }
    }
}
