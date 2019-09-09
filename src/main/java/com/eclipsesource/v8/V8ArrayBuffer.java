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
package com.eclipsesource.v8;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * V8ArrayBuffers represent ArrayBuffers from V8, but are backed by a
 * java.nio.ByteBuffer. This means that any data stored in a TypedArray
 * can be accessed by the java.nio.ByteBuffer. This significantly improves
 * performance of data access from Java to JavaScript.
 *
 * V8ArrayBuffers can either be constructed in Java, or returned from
 * JavaScript.
 *
 */
public class V8ArrayBuffer extends V8Value {

    ByteBuffer byteBuffer;

    /**
     * Creates a new V8ArrayBuffer on a given V8Context with a
     * given capacity.
     *
     * @param v8Context The runtime context on which to create the ArrayBuffer
     * @param capacity The capacity of the buffer
     */
    public V8ArrayBuffer(final V8Context v8Context, final int capacity) {
        super(v8Context);
        initialize(capacity);
        byteBuffer = getContext().createV8ArrayBufferBackingStore(objectHandle, capacity);
        byteBuffer.order(ByteOrder.nativeOrder());
    }

    public V8ArrayBuffer(final V8Context v8Context, ByteBuffer byteBuffer) {
        super(v8Context);
        if (byteBuffer == null) {
            byteBuffer = ByteBuffer.allocateDirect(0);
        }
        if (!byteBuffer.isDirect()) {
            throw new IllegalArgumentException("ByteBuffer must be a allocated as a direct ByteBuffer");
        }
        initialize(byteBuffer);
        this.byteBuffer = byteBuffer;
        byteBuffer.order(ByteOrder.nativeOrder());
    }

    @Override
    protected void initialize(final Object data) {
        getIsolate().checkThread();
        if (data instanceof ByteBuffer) {
            ByteBuffer buffer = (ByteBuffer) data;
            int capacity = buffer.limit();
            objectHandle = getContext().initNewV8ArrayBuffer(buffer, capacity);
        } else {
            int capacity = (Integer) data;
            objectHandle = getContext().initNewV8ArrayBuffer(capacity);
        }
        released = false;
        addObjectReference(objectHandle);
    }

    @Override
    protected V8Value createTwin() {
        return new V8ArrayBuffer(getContext(), byteBuffer);
    }

    /*
     * (non-Javadoc)
     * @see com.eclipsesource.v8.V8Object#twin()
     */
    @Override
    public V8ArrayBuffer twin() {
        getIsolate().checkThread();
        checkReleased();
        return (V8ArrayBuffer) super.twin();
    }

    /**
     * Returns the buffers limit
     *
     * @return the buffers limit
     */
    public int limit() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.limit();
    }

    /**
     * Returns the buffers capacity
     *
     * @return the buffers capacity
     */
    public final int capacity() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.capacity();
    }

    /**
     *
     * @return
     */
    public final int position() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.position();
    }

    public final V8ArrayBuffer position(final int newPosition) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.position(newPosition);
        return this;
    }

    public final V8ArrayBuffer limit(final int newLimit) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.limit(newLimit);
        return this;
    }

    public final V8ArrayBuffer mark() {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.mark();
        return this;
    }

    public final V8ArrayBuffer reset() {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.reset();
        return this;
    }

    public final V8ArrayBuffer clear() {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.clear();
        return this;
    }

    public final V8ArrayBuffer flip() {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.flip();
        return this;
    }

    public final V8ArrayBuffer rewind() {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.rewind();
        return this;
    }

    public final int remaining() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.remaining();
    }

    public final boolean hasRemaining() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.hasRemaining();
    }

    public boolean isReadOnly() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.isReadOnly();
    }

    public byte get() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.get();
    }

    public V8ArrayBuffer put(final byte b) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.put(b);
        return this;
    }

    public byte get(final int index) {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.get(index);
    }

    public V8ArrayBuffer put(final int index, final byte b) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.put(index, b);
        return this;
    }

    public V8ArrayBuffer get(final byte[] dst, final int offset, final int length) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.get(dst, offset, length);
        return this;
    }

    public V8ArrayBuffer get(final byte[] dst) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.get(dst);
        return this;
    }

    public V8ArrayBuffer put(final ByteBuffer src) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.put(src);
        return this;
    }

    public V8ArrayBuffer put(final byte[] src, final int offset, final int length) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.put(src, offset, length);
        return this;
    }

    public final V8ArrayBuffer put(final byte[] src) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.put(src);
        return this;
    }

    public final boolean hasArray() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.hasArray();
    }

    public final byte[] array() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.array();
    }

    public final int arrayOffset() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.arrayOffset();
    }

    public V8ArrayBuffer compact() {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.compact();
        return this;
    }

    public boolean isDirect() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.isDirect();
    }

    public final ByteOrder order() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.order();
    }

    public final V8ArrayBuffer order(final ByteOrder bo) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.order(bo);
        return this;
    }

    public char getChar() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.getChar();
    }

    public V8ArrayBuffer putChar(final char value) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.putChar(value);
        return this;
    }

    public char getChar(final int index) {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.getChar(index);
    }

    public V8ArrayBuffer putChar(final int index, final char value) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.putChar(index, value);
        return this;
    }

    public short getShort() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.getShort();
    }

    public V8ArrayBuffer putShort(final short value) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.putShort(value);
        return this;
    }

    public short getShort(final int index) {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.getShort(index);
    }

    public V8ArrayBuffer putShort(final int index, final short value) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.putShort(index, value);
        return this;
    }

    public int getInt() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.getInt();
    }

    public V8ArrayBuffer putInt(final int value) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.putInt(value);
        return this;
    }

    public int getInt(final int index) {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.getInt(index);
    }

    public V8ArrayBuffer putInt(final int index, final int value) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.asIntBuffer().put(index, value);
        return this;
    }

    public long getLong() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.getLong();
    }

    public V8ArrayBuffer putLong(final long value) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.putLong(value);
        return this;
    }

    public long getLong(final int index) {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.getLong(index);
    }

    public V8ArrayBuffer putLong(final int index, final long value) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.putLong(index, value);
        return this;
    }

    public float getFloat() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.getFloat();
    }

    public V8ArrayBuffer putFloat(final float value) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.putFloat(value);
        return this;
    }

    public float getFloat(final int index) {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.getFloat(index);
    }

    public V8ArrayBuffer putFloat(final int index, final float value) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.putFloat(index, value);
        return this;
    }

    public double getDouble() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.getDouble();
    }

    public V8ArrayBuffer putDouble(final double value) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.putDouble(value);
        return this;
    }

    public double getDouble(final int index) {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.getDouble(index);
    }

    public V8ArrayBuffer putDouble(final int index, final double value) {
        getIsolate().checkThread();
        checkReleased();
        byteBuffer.putDouble(index, value);
        return this;
    }

    public int floatLimit() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.asFloatBuffer().limit();
    }

    public int intLimit() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.asIntBuffer().limit();
    }

    public int shortLimit() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.asShortBuffer().limit();
    }

    public int doubleLimit() {
        getIsolate().checkThread();
        checkReleased();
        return byteBuffer.asDoubleBuffer().limit();
    }

}
