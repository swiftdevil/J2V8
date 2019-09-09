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

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static org.junit.Assert.*;

public class V8LockerTest {

    private boolean passed = false;
    private V8Isolate v8Isolate = null;

    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    @Before
    public void setup() {
        v8Isolate = V8Isolate.create();
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
        }
    }

    @Test
    public void testAcquireOnCreation() {
        V8Locker v8Locker = new V8Locker(v8Isolate);

        v8Locker.checkThread();
    }

    @Test
    public void testGetThread() {
        V8Locker v8Locker = new V8Locker(v8Isolate);

        assertEquals(Thread.currentThread(), v8Locker.getThread());
    }

    @Test
    public void testGetThreadNullAfterRelease() {
        V8Locker v8Locker = new V8Locker(v8Isolate);
        v8Locker.release();

        assertNull(v8Locker.getThread());
    }

    @Test
    public void testAcquireLocker() {
        V8Locker v8Locker = new V8Locker(v8Isolate);
        v8Locker.release();
        v8Locker.acquire();

        v8Locker.checkThread();
    }

    @Test
    public void testMultipleRelease() {
        V8Locker v8Locker = new V8Locker(v8Isolate);

        v8Locker.release();
        v8Locker.release();
        v8Locker.release();
    }

    @Test
    public void testReleaseAfterV8Released() {
        V8Locker v8Locker = new V8Locker(v8Isolate);
        v8Isolate.close();

        v8Locker.release();
        v8Isolate = V8Isolate.create(); // Create a new runtime so the teardown doesn't fail
    }

    @Test
    public void testTryAcquireLocker_True() {
        V8Locker v8Locker = new V8Locker(v8Isolate);
        v8Locker.release();
        boolean result = v8Locker.tryAcquire();

        assertTrue(result);
        v8Locker.checkThread();
    }

    @Test
    public void testHasLock() {
        V8Locker v8Locker = new V8Locker(v8Isolate);

        assertTrue(v8Locker.hasLock());
    }

    @Test
    public void testDoesNotHasLock() {
        V8Locker v8Locker = new V8Locker(v8Isolate);
        v8Locker.release();

        assertFalse(v8Locker.hasLock());
    }

    @Test
    public void testThreadLocked_tryAcquire() throws InterruptedException {
        final V8Locker v8Locker = new V8Locker(v8Isolate);
        final boolean result[] = new boolean[1];
        Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                result[0] = v8Locker.tryAcquire();
            }
        });
        t.start();
        t.join();

        assertFalse(result[0]);
    }

    @Test
    public void testThreadLocked() throws InterruptedException {
        final V8Locker v8Locker = new V8Locker(v8Isolate);
        passed = false;
        Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    v8Locker.checkThread();
                } catch (Error e) {
                    assertTrue(e.getMessage().startsWith("Invalid V8 thread access"));
                    passed = true;
                }
            }
        });
        t.start();
        t.join();

        assertTrue(passed);
    }

    @Test
    public void testCannotUseReleasedLocker() {
        V8Locker v8Locker = new V8Locker(v8Isolate);
        v8Locker.release();

        try {
            v8Locker.checkThread();
        } catch (Error e) {
            assertTrue(e.getMessage().startsWith("Invalid V8 thread access"));
            return;
        }
        fail("Expected exception");
    }

    // TODO: frozen/deadlock on android
    @Test
    public void testBinarySemaphore() throws InterruptedException {
        v8Isolate.getLocker().acquire(); // Lock has been acquired twice
        v8Isolate.getLocker().release(); // Lock should be released, second acquire shouldn't count
        Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                v8Isolate.getLocker().acquire();
                v8Isolate.getLocker().release();
            }
        });
        t.start();
        t.join();
        v8Isolate.getLocker().acquire();
    }

}
