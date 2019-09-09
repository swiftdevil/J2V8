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

/**
 * A base class for all V8 resources. V8 resources must
 * be closed/released. The rules for releasing resources is as
 * follows:
 *
 * 1. If you created it, you must close it, with one exception;
 *    if the object is being passed pack via a return statement,
 *    the system will release it for you.
 *
 * 2. If the system created it, you don’t need to worry about it,
 *    with one caveat; if the object was returned to you as a
 *    result of a method call, you must close it.
 */
abstract public class V8Value implements Releasable {

    private   V8Context v8Context;
    protected long      objectHandle;
    protected boolean   released = true;

    protected V8Value() {
        super();
    }

    protected V8Value(final V8Context v8Context) {
        if (v8Context == null) {
            this.v8Context = (V8Context) this;
        } else {
            this.v8Context = v8Context;
        }
    }

    protected void initialize(final Object data) {
        long objectHandle = getContext().initNewV8Object();
        released = false;
        addObjectReference(objectHandle);
    }

    void addObjectReference(final long objectHandle) throws Error {
        this.objectHandle = objectHandle;
        try {
            getContext().addObjRef(this);
        } catch (Error | RuntimeException e) {
            release();
            throw e;
        }
    }


    /**
     * Returns a string representation of the V8 Type.
     * @param type Type to return as a string. See constants in V8Value.
     * @return The V8Value type as a string.
     * @deprecated Use
     */
    @Deprecated
    public static String getStringRepresentaion(final int type) {
        return getStringRepresentation(type);
    }

    /**
     * Returns a string representation of the V8 Type.
     * @param type Type to return as a string. See constants in V8Value.
     * @return The V8Value type as a string.
     */
    public static String getStringRepresentation(final int type) {
        switch (type) {
            case V8API.NULL:
                return "Null";
            case V8API.INTEGER:
                return "Integer";
            case V8API.DOUBLE:
                return "Double";
            case V8API.BOOLEAN:
                return "Boolean";
            case V8API.STRING:
                return "String";
            case V8API.V8_ARRAY:
                return "V8Array";
            case V8API.V8_OBJECT:
                return "V8Object";
            case V8API.V8_FUNCTION:
                return "V8Function";
            case V8API.V8_TYPED_ARRAY:
                return "V8TypedArray";
            case V8API.BYTE:
                return "Byte";
            case V8API.V8_ARRAY_BUFFER:
                return "V8ArrayBuffer";
            case V8API.UNSIGNED_INT_8_ARRAY:
                return "UInt8Array";
            case V8API.UNSIGNED_INT_8_CLAMPED_ARRAY:
                return "UInt8ClampedArray";
            case V8API.INT_16_ARRAY:
                return "Int16Array";
            case V8API.UNSIGNED_INT_16_ARRAY:
                return "UInt16Array";
            case V8API.UNSIGNED_INT_32_ARRAY:
                return "UInt32Array";
            case V8API.FLOAT_32_ARRAY:
                return "Float32Array";
            case V8API.UNDEFINED:
                return "Undefined";
            default:
                throw new IllegalArgumentException("Invalid V8 type: " + type);
        }
    }

    /**
     * Returns a constructor name of the V8 Value.
     *
     * @return The V8Value constructor name as a string.
     */
    public String getConstructorName() {
        getIsolate().checkThread();
        getIsolate().checkReleased();
        return getContext().getConstructorName(objectHandle);
    }

    /**
     * Determines if this value is undefined.
     *
     * @return Returns true if the value is undefined, false otherwise
     */
    public boolean isUndefined() {
        return false;
    }

    /**
     * Gets the runtime this Value was created on.
     *
     * @return Returns the V8 runtime this value is associated with.
     */
    public V8Isolate getIsolate() {
        return getContext().getIsolate();
    }
    
    public V8Context getContext() {
        return v8Context;
    }

    /**
     * Returns the 'type' of this V8Value. The available types are defined
     * as constants in {@link V8Value}. Only types that inherit from
     * {@link V8Value} can be returned here.
     *
     * @return The 'type of this V8Value.
     */
    public int getV8Type() {
        if (isUndefined()) {
            return V8API.UNDEFINED;
        }
        getIsolate().checkThread();
        getContext().checkReleased();
        return getContext().getType(objectHandle);
    }

    /**
     * Creates a new Java object pointing at the same V8 Value
     * as this. If the value is mutated (by adding new members or
     * changing existing ones) then both the original and twin
     * will be updated. Twins are .equal and .strict equals, but
     * not == in Java.
     *
     * Twins must be closed separately since they have their own
     * native resources.
     *
     * @return A new Java object pointing at the same V8 Value
     * as this.
     */
    public V8Value twin() {
        if (isUndefined()) {
            return this;
        }
        getIsolate().checkThread();
        getContext().checkReleased();
        V8Value twin = createTwin();
        getContext().createTwin(this, twin);
        return twin;
    }

    /**
     * Sets the V8Value as weak reference. A weak reference will eventually
     * be closed when no more references exist to this object. Once setWeak
     * is called, you should check if {@link V8Value#isReleased()} is true
     * before invoking any methods on this object.
     *
     * If any other references exist to this object, the object will not be
     * reclaimed. Even if no reference exist, V8 does not give any guarantee
     * the object will be closed, so this should only be used if there is no
     * other way to track object usage.
     *
     * @return The receiver.
     */
    public V8Value setWeak() {
        getIsolate().checkThread();
        getContext().checkReleased();
        getContext().weakReferenceAdded(getHandle(), this);
        getContext().setWeak(getHandle());
        return this;
    }

    /**
     * Clears any weak reference set on this V8Value and makes this a strong
     * reference. Strong references will not be garbage collected and this
     * Object must be explicitly released.
     *
     * Calling clearWeak does nothing if the object is not currently set
     * to weak.
     *
     * @return The receiver.
     */
    public V8Value clearWeak() {
        getIsolate().checkThread();
        getContext().checkReleased();
        getContext().weakReferenceRemoved(getHandle());
        getContext().clearWeak(getHandle());
        return this;
    }

    /**
     * If {@link V8Value#setWeak()} has been called on this Object, this method
     * will return true. Otherwise it will return false.
     *
     * @return Returns true if this object has been set 'Weak', return false otherwise.
     */
    public boolean isWeak() {
        getIsolate().checkThread();
        getIsolate().checkReleased();
        return getContext().isWeak(getHandle());
    }

    /*
     * (non-Javadoc)
     * @see java.io.Closeable#close()
     */
    @Override
    public void close() {
        getIsolate().checkThread();
        if (!released) {
            try {
                getContext().releaseObjRef(this);
            } finally {
                released = true;
                getContext().release(objectHandle);
            }
        }
    }

    /**
     * Releases the native resources associated with this V8Value.
     *
     * @deprecated use close() instead.
     */
    @Override
    @Deprecated
    public void release() {
        close();
    }

    /**
     * Determine if the native resources have been released. Once released
     * a V8 Value can no longer be used.
     *
     * @return Returns true if this object has been released, false otherwise.
     */
    public boolean isReleased() {
        return released;
    }

    /**
     * Performs a JS === on the parameter and the receiver.
     *
     * @param that The Object to compare this object against.
     * @return Returns true iff this === that
     */
    public boolean strictEquals(final Object that) {
        getIsolate().checkThread();
        checkReleased();
        if (that == this) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (!(that instanceof V8Value)) {
            return false;
        }
        if (isUndefined() && ((V8Value) that).isUndefined()) {
            return true;
        }
        if (((V8Value) that).isUndefined()) {
            return false;
        }
        return getContext().strictEquals(getHandle(), ((V8Value) that).getHandle());
    }

    long getHandle() {
        checkReleased();
        return objectHandle;
    }

    protected abstract V8Value createTwin();

    /*
     * (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object that) {
        return strictEquals(that);
    }

    /**
     * Performs a JS == on the parameter and the receiver.
     *
     * @param that The Object to compare this object against.
     * @return Returns true iff this == that
     */
    public boolean jsEquals(final Object that) {
        getIsolate().checkThread();
        checkReleased();
        if (that == this) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (!(that instanceof V8Value)) {
            return false;
        }
        if (isUndefined() && ((V8Value) that).isUndefined()) {
            return true;
        }
        if (((V8Value) that).isUndefined()) {
            return false;
        }
        return getContext().equals(getHandle(), ((V8Value) that).getHandle());
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        getIsolate().checkThread();
        checkReleased();
        return getContext().identityHash(getHandle());
    }
}
