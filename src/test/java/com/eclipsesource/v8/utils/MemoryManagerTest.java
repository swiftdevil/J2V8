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
package com.eclipsesource.v8.utils;

import com.eclipsesource.v8.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ConcurrentModificationException;

import static org.junit.Assert.*;

public class MemoryManagerTest {

    private V8Isolate v8Isolate;
    private V8Context v8Context;

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
        }
    }

    @SuppressWarnings("resource")
    @Test
    public void testMemoryManagerReleasesObjects() {
        MemoryManager memoryManager = new MemoryManager(v8Context);

        new V8Object(v8Context);
        memoryManager.release();

        assertEquals(0, v8Isolate.getObjectReferenceCount());
    }

    @SuppressWarnings("resource")
    @Test
    public void testObjectIsReleased() {
        MemoryManager memoryManager = new MemoryManager(v8Context);

        V8Object object = new V8Object(v8Context);
        memoryManager.release();

        assertTrue(object.isReleased());
    }

    @Test
    public void testMemoryManagerReleasesFunctions() {
        MemoryManager memoryManager = new MemoryManager(v8Context);

        v8Context.executeScript("(function() {})");
        memoryManager.release();

        assertEquals(0, v8Isolate.getObjectReferenceCount());
    }

    @Test
    public void testMemoryReferenceCount0() {
        MemoryManager memoryManager = new MemoryManager(v8Context);

        assertEquals(0, memoryManager.getObjectReferenceCount());
    }

    @Test
    public void testMemoryReferenceCount0_AfterRemove() {
        MemoryManager memoryManager = new MemoryManager(v8Context);

        new V8Object(v8Context).close();

        assertEquals(0, memoryManager.getObjectReferenceCount());
    }

    @Test
    public void testMemoryReferenceCount() {
        MemoryManager memoryManager = new MemoryManager(v8Context);

        v8Context.executeScript("(function() {})");
        assertEquals(1, memoryManager.getObjectReferenceCount());
        memoryManager.release();

        assertEquals(0, v8Isolate.getObjectReferenceCount());
    }

    @Test
    public void testMemoryManagerReleasesReturnedObjects() {
        MemoryManager memoryManager = new MemoryManager(v8Context);

        v8Context.executeScript("foo = {}; foo");

        assertEquals(1, v8Isolate.getObjectReferenceCount());
        memoryManager.release();
        assertEquals(0, v8Isolate.getObjectReferenceCount());
    }

    @Test
    public void testReleasedMemoryManagerDoesTrackObjects() {
        MemoryManager memoryManager = new MemoryManager(v8Context);

        memoryManager.release();
        V8Object object = new V8Object(v8Context);

        assertEquals(1, v8Isolate.getObjectReferenceCount());
        object.close();
    }

    @SuppressWarnings("resource")
    @Test
    public void testNestedMemoryManagers() {
        MemoryManager memoryManager1 = new MemoryManager(v8Context);
        MemoryManager memoryManager2 = new MemoryManager(v8Context);

        new V8Object(v8Context);
        memoryManager2.release();
        new V8Object(v8Context);

        assertEquals(1, v8Isolate.getObjectReferenceCount());
        memoryManager1.release();
        assertEquals(0, v8Isolate.getObjectReferenceCount());
    }

    @SuppressWarnings("resource")
    @Test
    public void testNestedMemoryManagerHasProperObjectCount() {
        MemoryManager memoryManager1 = new MemoryManager(v8Context);

        new V8Object(v8Context);
        MemoryManager memoryManager2 = new MemoryManager(v8Context);
        new V8Object(v8Context);

        assertEquals(2, memoryManager1.getObjectReferenceCount());
        assertEquals(1, memoryManager2.getObjectReferenceCount());
        memoryManager2.release();

        assertEquals(1, memoryManager1.getObjectReferenceCount());
        memoryManager1.release();
    }

    @SuppressWarnings("resource")
    @Test
    public void testNestedMemoryManager_ReverseReleaseOrder() {
        MemoryManager memoryManager1 = new MemoryManager(v8Context);

        new V8Object(v8Context);
        MemoryManager memoryManager2 = new MemoryManager(v8Context);
        new V8Object(v8Context);

        assertEquals(2, memoryManager1.getObjectReferenceCount());
        assertEquals(1, memoryManager2.getObjectReferenceCount());
        memoryManager1.release();

        assertEquals(0, memoryManager2.getObjectReferenceCount());
        memoryManager2.release();
    }

    @Test(expected = IllegalStateException.class)
    public void testMemoryManagerReleased_CannotCallGetObjectReferenceCount() {
        MemoryManager memoryManager = new MemoryManager(v8Context);

        memoryManager.release();

        memoryManager.getObjectReferenceCount();
    }

    @Test
    public void testCanReleaseTwice() {
        MemoryManager memoryManager = new MemoryManager(v8Context);

        memoryManager.release();
        memoryManager.release();
    }

    @Test
    public void testIsReleasedTrue() {
        MemoryManager memoryManager = new MemoryManager(v8Context);

        memoryManager.release();

        assertTrue(memoryManager.isReleased());
    }

    @Test
    public void testIsReleasedFalse() {
        MemoryManager memoryManager = new MemoryManager(v8Context);

        assertFalse(memoryManager.isReleased());
    }

    @Test
    public void testPersistObject() {
        MemoryManager memoryManager = new MemoryManager(v8Context);

        V8Object object = new V8Object(v8Context);
        memoryManager.persist(object);
        memoryManager.release();

        assertFalse(object.isReleased());
        object.close();
    }

    @Test
    public void testPersistNonManagedObject() {
        V8Object object = new V8Object(v8Context);
        MemoryManager memoryManager = new MemoryManager(v8Context);

        memoryManager.persist(object);
        memoryManager.release();

        assertFalse(object.isReleased());
        object.close();
    }

    @SuppressWarnings("resource")
    @Test
    public void testTwins() {
        MemoryManager memoryManager = new MemoryManager(v8Context);

        V8Object object = new V8Object(v8Context);
        object.twin();

        assertEquals(2, memoryManager.getObjectReferenceCount());
        memoryManager.release();
    }

    @Test
    public void testTwinsReleaseOne() {
        MemoryManager memoryManager = new MemoryManager(v8Context);

        V8Object object = new V8Object(v8Context);
        object.twin();
        object.close();

        assertEquals(1, memoryManager.getObjectReferenceCount());
        memoryManager.release();
    }

    @Test
    public void testGetObjectTwice() {
        v8Context.executeVoidScript("foo = {}");
        MemoryManager memoryManager = new MemoryManager(v8Context);

        V8Object foo1 = v8Context.getObject("foo");
        v8Context.getObject("foo").close();

        assertEquals(1, memoryManager.getObjectReferenceCount());
        memoryManager.release();
        assertTrue(foo1.isReleased());
    }

    @Test(expected = IllegalStateException.class)
    public void testCannotCallPersistOnReleasedManager() {
        MemoryManager memoryManager = new MemoryManager(v8Context);

        V8Object object = new V8Object(v8Context);
        memoryManager.release();
        memoryManager.persist(object);
    }

    MemoryManager memoryManager;

    //@Test
    @SuppressWarnings("resource")
    public void testExceptionDuringReleaseDoesNotReleaseMemoryManager() {
        memoryManager = new MemoryManager(v8Context);
        ReferenceHandler handler = new ReferenceHandler() {

            @Override
            public void v8HandleDisposed(final V8Value object) {
                // Throws CME
                memoryManager.persist(object);
            }

            @Override
            public void v8HandleCreated(final V8Value object) {
            }
        };
        v8Context.addReferenceHandler(handler);

        new V8Object(v8Context);
        try {
            memoryManager.release();
        } catch (ConcurrentModificationException e) {

        }

        assertFalse(memoryManager.isReleased());

        v8Context.removeReferenceHandler(handler);
        memoryManager.release();
        assertTrue(memoryManager.isReleased());
    }

}
