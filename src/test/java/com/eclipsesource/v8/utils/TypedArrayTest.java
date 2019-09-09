package com.eclipsesource.v8.utils;

import com.eclipsesource.v8.V8API;
import com.eclipsesource.v8.V8Context;
import com.eclipsesource.v8.V8Isolate;
import com.eclipsesource.v8.V8TypedArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TypedArrayTest {

    private V8Isolate v8Isolate;
    private V8Context v8Context;

    @Before
    public void setup() {
        v8Isolate = V8Isolate.create();
        v8Context = v8Isolate.createContext();
    }

    @After
    public void tearDown() {
        if (v8Isolate != null) {
            v8Isolate.close();
        }
        if (V8Isolate.getActiveRuntimes() != 0) {
            throw new IllegalStateException("V8Runtimes not properly released");
        }
    }

    @Test
    public void testGetV8TypedArray() {
        TypedArray typedArray = new TypedArray(v8Context, new ArrayBuffer(v8Context, ByteBuffer.allocateDirect(8)), V8API.INT_8_ARRAY, 0, 8);

        V8TypedArray v8TypedArray = typedArray.getV8TypedArray();

        assertNotNull(v8TypedArray);
        v8TypedArray.close();
    }

    @Test
    public void testV8TypedArrayAvailable() {
        TypedArray typedArray = new TypedArray(v8Context, new ArrayBuffer(v8Context, ByteBuffer.allocateDirect(8)), V8API.INT_8_ARRAY, 0, 8);

        assertTrue(typedArray.isAvailable());
    }

}
