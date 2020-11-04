/*******************************************************************************
 * Copyright (c) 2016 Brandon Sanders
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Brandon Sanders - initial API and implementation and/or initial documentation
 *    R. Ian Bull - additional test cases
 ******************************************************************************/
package com.eclipsesource.v8.utils;

import com.eclipsesource.v8.V8Context;
import com.eclipsesource.v8.V8Isolate;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class ConcurrentV8Test {

    public static class ExceptionThrower {
        public void whine() throws Exception {
            throw new Exception("Whaaa!");
        }
    }

    @After
    public void tearDown() {
        try {
            if (V8Isolate.getActiveRuntimes() != 0) {
                throw new IllegalStateException("V8Runtimes not properly released");
            }
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
        }
    }

    @Test
    public void testLockNotAquiredAfterCreation() {
        ConcurrentV8 concurrentV8 = new ConcurrentV8();

        assertFalse(concurrentV8.getV8().getLocker().hasLock());
        concurrentV8.release();
    }

    @Test
    public void testLockNotAcquiredAfterExecution() {
        ConcurrentV8 concurrentV8 = new ConcurrentV8();

        concurrentV8.run(new V8ContextRunnable() {

            @Override
            public void run(final V8Context v8Context) {
                v8Context.executeScript("foo = 7;");
            }
        });

        assertFalse(concurrentV8.getV8().getLocker().hasLock());
        concurrentV8.release();
    }

    @Test
    public void testLockNotAquiredAfterException() {
        ConcurrentV8 concurrentV8 = new ConcurrentV8();

        try {
            concurrentV8.run(new V8ContextRunnable() {

                @Override
                public void run(final V8Context v8Context) {
                    v8Context.executeScript("throw 'my exception';");
                }
            });
        } catch (Exception e) {
            // do nothing
        }

        assertFalse(concurrentV8.getV8().getLocker().hasLock());
        concurrentV8.release();
    }

    @Test
    public void testLockAquiredDuringExecution() {
        ConcurrentV8 concurrentV8 = new ConcurrentV8();

        try {
            concurrentV8.run(new V8ContextRunnable() {

                @Override
                public void run(final V8Context v8Context) {
                    assertTrue(v8Context.getIsolate().getLocker().hasLock());
                }
            });
        } catch (Exception e) {
            // do nothing
        }
        concurrentV8.release();
    }

    @Test
    public void testConstructorWithoutV8() {
        ConcurrentV8 concurrentV8 = new ConcurrentV8();

        assertNotNull(concurrentV8.getV8());
        concurrentV8.release();
    }

    @Test
    public void shouldShareV8AcrossThreads() {
        final ConcurrentV8 v8 = new ConcurrentV8();

        Thread thread1 = new Thread(new Runnable() {
            @Override
            public void run() {
                v8.run(new V8ContextRunnable() {
                    @Override
                    public void run(final V8Context v8Context) {
                        v8Context.executeVoidScript("var i = 3000;");
                    }
                });
            }
        });

        thread1.start();
        try {
            thread1.join();
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        Thread thread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                v8.run(new V8ContextRunnable() {
                    @Override
                    public void run(final V8Context v8Context) {
                        v8Context.executeVoidScript("i += 344;");
                    }
                });
            }
        });

        thread2.start();
        try {
            thread2.join();
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        v8.run(new V8ContextRunnable() {
            @Override
            public void run(final V8Context v8Context) {
                Assert.assertEquals(3344, v8Context.executeIntegerScript("i"));
            }
        });

        v8.release();
    }

    @Test
    public void exceptionsShouldBeRaised() {

        ConcurrentV8 v8 = new ConcurrentV8();
        final ExceptionThrower exceptionThrower = new ExceptionThrower();

        v8.run(new V8ContextRunnable() {
            @Override
            public void run(final V8Context v8Context) {
                v8Context.registerJavaMethod(exceptionThrower, "whine", "whine", new Class[0]);
            }
        });

        try {
            v8.run(new V8ContextRunnable() {
                @Override
                public void run(final V8Context v8Context) {
                    v8Context.executeScript("whine();");
                }
            });

            Assert.fail("Regular concurrent V8 invocations should pass on exceptions.");
        } catch (Exception e) {
            assertEquals("Whaaa!", e.getCause().getMessage());
        }

        v8.release();
    }
}
