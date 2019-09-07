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

import java.lang.reflect.Method;

/**
 * The concrete class for all V8 Objects. V8Objects are
 * JavaScript objects accessible in java. Specialized
 * subclasses exist for V8Arrays and V8Functions.
 *
 * V8Object are JavaScript object with key value pairs.
 * Specific get methods exist to access values as primitives.
 * General get methods also exist, which return Java Objects
 * and can be casted to the correct subclass.
 *
 * V8Object have native resources and must be released
 * when they are no longer need in Java.
 */
public class V8Object extends V8Value {

    /**
     * Create a new V8Object and associate it with a runtime.
     * Once created, it must be released.
     *
     * @param v8Context The context on which to associate the V8Object.
     */
    public V8Object(final V8Context v8Context) {
        this(v8Context, null);
    }

    protected V8Object(final V8Context v8Context, final Object data) {
        super(v8Context);
        if (v8Context != null) {
            getRuntime().checkThread();
            initialize(data);
        }
    }

    protected V8Object() {

    }

    @Override
    protected V8Value createTwin() {
        return new V8Object(getContext());
    }

    /*
     * (non-Javadoc)
     * @see com.eclipsesource.v8.V8Value#twin()
     */
    @Override
    public V8Object twin() {
        return (V8Object) super.twin();
    }

    /**
     * Determine if a key/value pair with this key exists in
     * the Object.
     *
     * @param key The key to check
     * @return True if the key exists, false otherwise.
     */
    public boolean contains(final String key) {
        getRuntime().checkThread();
        checkReleased();
        checkKey(key);
        return getContext().contains(objectHandle, key);
    }

    /**
     * Returns all the keys associated with this JavaScript Object.
     * Keys associated with the objects prototype are not returned.
     *
     * @return The keys associated with this JavaScript Object.
     */
    public String[] getKeys() {
        getRuntime().checkThread();
        checkReleased();
        return getContext().getKeys(objectHandle);
    }

    /**
     * Returns the type of the value associated with this Key, or
     * UNDEFINED if the key does not exist. Types are specified as
     * integer constants. The types are all defined in V8Value.
     *
     * @param key The key whose type to lookup.
     *
     * @return The Type of the value associated with this key
     */
    public int getType(final String key) {
        getRuntime().checkThread();
        checkReleased();
        checkKey(key);
        return getContext().getType(objectHandle, key);
    }

    /**
     * Returns the value associated with this key. Values are Java Objects.
     * If the value is a primitive, its boxed type is returned. If the
     * value is a V8Value, it must be released.
     *
     * @param key The key whose value to return.
     *
     * @return The value associated with this key.
     */
    public Object get(final String key) {
        getRuntime().checkThread();
        checkReleased();
        checkKey(key);
        return getContext().get(V8API.V8_OBJECT, objectHandle, key);
    }

    /**
     * Returns the integer value associated with this key. If the value
     * associated with this key does not exist, or if it's not an integer, then
     * V8ResultUndefined exception is thrown.
     *
     * @param key The key whose value to return.
     *
     * @return The integer value associated with this key, or V8ResultUndefined
     * if the key does not exist or the value is not an integer.
     */
    public int getInteger(final String key) {
        getRuntime().checkThread();
        checkReleased();
        checkKey(key);
        return getContext().getInteger(objectHandle, key);
    }

    /**
     * Returns the boolean value associated with this key. If the value
     * associated with this key does not exist, or if it's not a boolean, then
     * V8ResultUndefined exception is thrown.
     *
     * @param key The key whose value to return.
     *
     * @return The boolean value associated with this key, or V8ResultUndefined
     * if the key does not exist or the value is not a boolean.
     */
    public boolean getBoolean(final String key) {
        getRuntime().checkThread();
        checkReleased();
        checkKey(key);
        return getContext().getBoolean(objectHandle, key);
    }

    /**
     * Returns the double value associated with this key. If the value
     * associated with this key does not exist, or if it's not a double, then
     * V8ResultUndefined exception is thrown.
     *
     * @param key The key whose value to return.
     *
     * @return The double value associated with this key, or V8ResultUndefined
     * if the key does not exist or the value is not a double.
     */
    public double getDouble(final String key) {
        getRuntime().checkThread();
        checkReleased();
        checkKey(key);
        return getContext().getDouble(objectHandle, key);
    }

    /**
     * Returns the String value associated with this key. If the value
     * associated with this key does not exist, or if it's not a String, then
     * V8ResultUndefined exception is thrown.
     *
     * @param key The key whose value to return.
     *
     * @return The String value associated with this key, or V8ResultUndefined
     * if the key does not exist or the value is not a String.
     */
    public String getString(final String key) {
        getRuntime().checkThread();
        checkReleased();
        checkKey(key);
        return getContext().getString(objectHandle, key);
    }

    /**
     * Returns the V8Array value associated with this key. If the value
     * associated with this key does not exist then UNDEFINED is returned.
     * If the value exists but is not an array then
     * V8ResultUndefined exception is thrown.
     *
     * @param key The key whose value to return.
     *
     * @return The V8Array value associated with this key.
     */
    public V8Array getArray(final String key) {
        getRuntime().checkThread();
        checkReleased();
        checkKey(key);
        Object result = getContext().get(V8API.V8_ARRAY, objectHandle, key);
        if ((result == null) || (result instanceof V8Array)) {
            return (V8Array) result;
        }
        throw new V8ResultUndefined();
    }

    /**
     * Returns the V8Object value associated with this key. If the value
     * associated with this key does not exist then UNDEFINED is returned.
     * If the value exists but is not an JS Object then
     * V8ResultUndefined exception is thrown.
     *
     * @param key The key whose value to return.
     *
     * @return The V8Object value associated with this key.
     */
    public V8Object getObject(final String key) {
        getRuntime().checkThread();
        checkReleased();
        checkKey(key);
        Object result = getContext().get(V8API.V8_OBJECT, objectHandle, key);
        if ((result == null) || (result instanceof V8Object)) {
            return (V8Object) result;
        }
        throw new V8ResultUndefined();
    }

    /**
     * Invoke a JavaScript function and return the result as a integer. If the
     * result is not an integer, or does not exist, then V8ResultUndefined is thrown.
     *
     * @param name The name of the JS Function to call.
     *
     * @param parameters The parameters to pass to the function. Parameters must be released.
     *
     * @return An integer representing the result of the function call or V8ResultUndefined
     * if the result is not an integer.
     */
    public int executeIntegerFunction(final String name, final V8Array parameters) {
        getRuntime().checkThread();
        checkReleased();
        getRuntime().checkRuntime(parameters);
        long parametersHandle = parameters == null ? 0 : parameters.getHandle();
        return getContext().executeIntegerFunction(getHandle(), name, parametersHandle);
    }

    /**
     * Invoke a JavaScript function and return the result as a double. If the
     * result is not a double, or does not exist, then V8ResultUndefined is thrown.
     *
     * @param name The name of the JS Function to call.
     *
     * @param parameters The parameters to pass to the function. Parameters must be released.
     *
     * @return A double representing the result of the function call or V8ResultUndefined
     * if the result is not a double.
     */
    public double executeDoubleFunction(final String name, final V8Array parameters) {
        getRuntime().checkThread();
        checkReleased();
        getRuntime().checkRuntime(parameters);
        long parametersHandle = parameters == null ? 0 : parameters.getHandle();
        return getContext().executeDoubleFunction(getHandle(), name, parametersHandle);
    }

    /**
     * Invoke a JavaScript function and return the result as a String. If the
     * result is not a String, or does not exist, then V8ResultUndefined is thrown.
     *
     * @param name The name of the JS Function to call.
     *
     * @param parameters The parameters to pass to the function. Parameters must be released.
     *
     * @return A String representing the result of the function call or V8ResultUndefined
     * if the result is not a String.
     */
    public String executeStringFunction(final String name, final V8Array parameters) {
        getRuntime().checkThread();
        checkReleased();
        getRuntime().checkRuntime(parameters);
        long parametersHandle = parameters == null ? 0 : parameters.getHandle();
        return getContext().executeStringFunction(getHandle(), name, parametersHandle);
    }

    /**
     * Invoke a JavaScript function and return the result as a boolean. If the
     * result is not a boolean, or does not exist, then V8ResultUndefined is thrown.
     *
     * @param name The name of the JS Function to call.
     *
     * @param parameters The parameters to pass to the function. Parameters must be released.
     *
     * @return A boolean representing the result of the function call or V8ResultUndefined
     * if the result is not a boolean.
     */
    public boolean executeBooleanFunction(final String name, final V8Array parameters) {
        getRuntime().checkThread();
        checkReleased();
        getRuntime().checkRuntime(parameters);
        long parametersHandle = parameters == null ? 0 : parameters.getHandle();
        return getContext().executeBooleanFunction(getHandle(), name, parametersHandle);
    }

    /**
     * Invoke a JavaScript function and return the result as a V8Array. If the
     * result is not a V8Array then V8ResultUndefined is thrown.
     *
     * @param name The name of the JS Function to call.
     *
     * @param parameters The parameters to pass to the function. Parameters must be released.
     *
     * @return A V8Array representing the result of the function call or V8ResultUndefined
     * if the result is not a V8Array. The result must be released.
     */
    public V8Array executeArrayFunction(final String name, final V8Array parameters) {
        getRuntime().checkThread();
        checkReleased();
        getRuntime().checkRuntime(parameters);
        long parametersHandle = parameters == null ? 0 : parameters.getHandle();
        Object result = getContext().executeFunction(V8API.V8_ARRAY, objectHandle, name, parametersHandle);
        if (result instanceof V8Array) {
            return (V8Array) result;
        }
        throw new V8ResultUndefined();
    }

    /**
     * Invoke a JavaScript function and return the result as a V8Object. If the
     * result is not a V8Object then V8ResultUndefined is thrown.
     *
     * @param name The name of the JS Function to call.
     *
     * @param parameters The parameters to pass to the function. Parameters must be released.
     *
     * @return A V8Object representing the result of the function call or V8ResultUndefined
     * if the result is not a V8Object. The result must be released.
     */
    public V8Object executeObjectFunction(final String name, final V8Array parameters) {
        getRuntime().checkThread();
        checkReleased();
        getRuntime().checkRuntime(parameters);
        long parametersHandle = parameters == null ? 0 : parameters.getHandle();
        Object result = getContext().executeFunction(V8API.V8_OBJECT, objectHandle, name, parametersHandle);
        if (result instanceof V8Object) {
            return (V8Object) result;
        }
        throw new V8ResultUndefined();
    }

    /**
     * Invoke a JavaScript function and return the result as a Java Object.
     *
     * @param name The name of the JS Function to call.
     *
     * @param parameters The parameters to pass to the function. Parameters must be released.
     *
     * @return A Java Object representing the result of the function call.
     */
    public Object executeFunction(final String name, final V8Array parameters) {
        getRuntime().checkThread();
        checkReleased();
        getRuntime().checkRuntime(parameters);
        long parametersHandle = parameters == null ? 0 : parameters.getHandle();
        return getContext().executeFunction(V8API.UNKNOWN, objectHandle, name, parametersHandle);
    }

    /**
     * Invoke a JavaScript function and return the result as a Java Object.
     *
     * @param name The name of the JS Function to call
     * @return The result of this JS Function
     */
    public Object executeJSFunction(final String name) {
        return executeFunction(name, null);
    }

    /**
     * Invoke a JavaScript function and return the result as a Java Object.
     *
     * @param name The name of the JS Function to call.
     * @param parameters The parameters to pass to the function.
     * @return A Java Object representing the result of the function call.
     */
    public Object executeJSFunction(final String name, final Object... parameters) {
        if (parameters == null) {
            return executeFunction(name, null);
        }
        V8Array parameterArray = new V8Array(getContext());
        try {
            for (Object object : parameters) {
                if (object == null) {
                    parameterArray.pushNull();
                } else if (object instanceof V8Value) {
                    parameterArray.push((V8Value) object);
                } else if (object instanceof Integer) {
                    parameterArray.push(object);
                } else if (object instanceof Double) {
                    parameterArray.push(object);
                } else if (object instanceof Long) {
                    parameterArray.push(((Long) object).doubleValue());
                } else if (object instanceof Float) {
                    parameterArray.push(((Float) object).floatValue());
                } else if (object instanceof Boolean) {
                    parameterArray.push(object);
                } else if (object instanceof String) {
                    parameterArray.push((String) object);
                } else {
                    throw new IllegalArgumentException("Unsupported Object of type: " + object.getClass());
                }
            }
            return executeFunction(name, parameterArray);
        } finally {
            parameterArray.close();
        }
    }

    /**
     * Invokes a JavaScript function which does not return a result.
     *
     * @param name The name of the JS Function to call.
     *
     * @param parameters The parameters to pass to the function. Parameters must be released.
     */
    public void executeVoidFunction(final String name, final V8Array parameters) {
        getRuntime().checkThread();
        checkReleased();
        getRuntime().checkRuntime(parameters);
        long parametersHandle = parameters == null ? 0 : parameters.getHandle();
        getContext().executeVoidFunction(objectHandle, name, parametersHandle);
    }

    /**
     * Adds a key value pair to the receiver where the value is an integer.
     *
     * @param key The key to associate the value with.
     * @param value The value to add.
     *
     * @return The receiver.
     */
    public V8Object add(final String key, final int value) {
        getRuntime().checkThread();
        checkReleased();
        getContext().add(objectHandle, key, value);
        return this;
    }

    /**
     * Adds a key value pair to the receiver where the value is a boolean.
     *
     * @param key The key to associate the value with.
     * @param value The value to add.
     *
     * @return The receiver.
     */
    public V8Object add(final String key, final boolean value) {
        getRuntime().checkThread();
        checkReleased();
        getContext().add(objectHandle, key, value);
        return this;
    }

    /**
     * Adds a key value pair to the receiver where the value is a double.
     *
     * @param key The key to associate the value with.
     * @param value The value to add.
     *
     * @return The receiver.
     */
    public V8Object add(final String key, final double value) {
        getRuntime().checkThread();
        checkReleased();
        getContext().add(objectHandle, key, value);
        return this;
    }

    /**
     * Adds a key value pair to the receiver where the value is a String.
     *
     * @param key The key to associate the value with.
     * @param value The value to add.
     *
     * @return The receiver.
     */
    public V8Object add(final String key, final String value) {
        getRuntime().checkThread();
        checkReleased();
        if (value == null) {
            getContext().addNull(objectHandle, key);
        } else if (value.equals(V8.getUndefined())) {
            getContext().addUndefined(objectHandle, key);
        } else {
            getContext().add(objectHandle, key, value);
        }
        return this;
    }

    /**
     * Adds a key value pair to the receiver where the value is a V8Value.
     *
     * @param key The key to associate the value with.
     * @param value The value to add.
     *
     * @return The receiver.
     */
    public V8Object add(final String key, final V8Value value) {
        getRuntime().checkThread();
        checkReleased();
        getRuntime().checkRuntime(value);
        if (value == null) {
            getContext().addNull(objectHandle, key);
        } else if (value.equals(V8.getUndefined())) {
            getContext().addUndefined(objectHandle, key);
        } else {
            getContext().addObject(objectHandle, key, value.getHandle());
        }
        return this;
    }

    /**
     * Associate UNDEFINED with the given key.
     *
     * @param key The key to associate UNDEFINED with.
     *
     * @return The receiver.
     */
    public V8Object addUndefined(final String key) {
        getRuntime().checkThread();
        checkReleased();
        getContext().addUndefined(objectHandle, key);
        return this;
    }

    /**
     * Associate NULL with the given key.
     *
     * @param key The key to associate NULL with.
     *
     * @return The receiver.
     */
    public V8Object addNull(final String key) {
        getRuntime().checkThread();
        checkReleased();
        getContext().addNull(objectHandle, key);
        return this;
    }

    /**
     * Sets the prototype of the receiver.
     *
     * @param value The prototype to associate with this V8Object.
     *
     * @return The receiver.
     */
    public V8Object setPrototype(final V8Object value) {
        getRuntime().checkThread();
        checkReleased();
        getContext().setPrototype(objectHandle, value.getHandle());
        return this;
    }

    /**
     * Register a Java method as a JavaScript function. When the JS Function is invoked
     * the Java method will be called.
     *
     * @param callback The JavaCallback to call when the JSFunction is invoked.
     * @param jsFunctionName The name of the JSFunction.
     *
     * @return The receiver.
     */
    public V8Object registerJavaMethod(final JavaCallback callback, final String jsFunctionName) {
        getRuntime().checkThread();
        checkReleased();
        getContext().registerCallback(callback, getHandle(), jsFunctionName);
        return this;
    }

    /**
     * Register a void Java method as a JavaScript function. When the JS Function is invoked
     * the Java method will be called.
     *
     * @param callback The JavaVoidCallback to call when the JSFunction is invoked.
     * @param jsFunctionName The name of the JSFunction.
     *
     * @return The receiver.
     */
    public V8Object registerJavaMethod(final JavaVoidCallback callback, final String jsFunctionName) {
        getRuntime().checkThread();
        checkReleased();
        getContext().registerVoidCallback(callback, getHandle(), jsFunctionName);
        return this;
    }

    /**
     * Register a Java method reflectively given it's name a signature.
     *
     * @param object The Java Object on which the method is defined.
     * @param methodName The name of the method to register.
     * @param jsFunctionName The name of the JavaScript function to register the
     * method with.
     * @param parameterTypes The parameter types of the method.
     *
     * @return The receiver.
     */
    public V8Object registerJavaMethod(final Object object, final String methodName, final String jsFunctionName, final Class<?>[] parameterTypes) {
        return registerJavaMethod(object, methodName, jsFunctionName, parameterTypes, false);
    }

    /**
     * Register a Java method reflectively given it's name a signature. The option to include
     * the JS Object in the callback can be specified by setting includeReceiver true.
     *
     * @param object The Java Object on which the method is defined.
     * @param methodName The name of the method to register.
     * @param jsFunctionName The name of the JavaScript function to register the
     * method with.
     * @param parameterTypes The parameter types of the method.
     * @param includeReceiver True if the first parameter should include the JS Object,
     * false otherwise.
     *
     * @return The receiver.
     */
    public V8Object registerJavaMethod(final Object object, final String methodName, final String jsFunctionName, final Class<?>[] parameterTypes, final boolean includeReceiver) {
        getRuntime().checkThread();
        checkReleased();
        try {
            Method method = object.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            getContext().registerCallback(object, method, getHandle(), jsFunctionName, includeReceiver);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new IllegalStateException(e);
        }
        return this;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        if (isReleased() || getContext().isReleased()) {
            return "[Object released]";
        }
        getRuntime().checkThread();
        return getContext().toString(getHandle());
    }

    private void checkKey(final String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
    }

    static class Undefined extends V8Object {

        public Undefined() {
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Value#isUndefined()
         */
        @Override
        public boolean isUndefined() {
            return true;
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Value#isReleased()
         */
        @Override
        public boolean isReleased() {
            return false;
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Value#close()
         */
        @Override
        public void close() {
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Value#release()
         */
        @Deprecated
        @Override
        public void release() {
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#twin()
         */
        @Override
        public Undefined twin() {
            return (Undefined) super.twin();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#toString()
         */
        @Override
        public String toString() {
            return "undefined";
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Value#equals(java.lang.Object)
         */
        @Override
        public boolean equals(final Object that) {
            if ((that instanceof V8Object) && ((V8Object) that).isUndefined()) {
                return true;
            }
            return false;
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Value#hashCode()
         */
        @Override
        public int hashCode() {
            return 919;
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#add(java.lang.String, boolean)
         */
        @Override
        public V8Object add(final String key, final boolean value) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Value#getRuntime()
         */
        @Override
        public V8 getRuntime() {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#add(java.lang.String, double)
         */
        @Override
        public V8Object add(final String key, final double value) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#add(java.lang.String, int)
         */
        @Override
        public V8Object add(final String key, final int value) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#executeJSFunction(java.lang.String, java.lang.Object[])
         */
        @Override
        public Object executeJSFunction(final String name, final Object... parameters) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#executeFunction(java.lang.String, com.eclipsesource.v8.V8Array)
         */
        @Override
        public Object executeFunction(final String name, final V8Array parameters) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#add(java.lang.String, java.lang.String)
         */
        @Override
        public V8Object add(final String key, final String value) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#add(java.lang.String, com.eclipsesource.v8.V8Value)
         */
        @Override
        public V8Object add(final String key, final V8Value value) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#addUndefined(java.lang.String)
         */
        @Override
        public V8Object addUndefined(final String key) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#contains(java.lang.String)
         */
        @Override
        public boolean contains(final String key) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#executeArrayFunction(java.lang.String, com.eclipsesource.v8.V8Array)
         */
        @Override
        public V8Array executeArrayFunction(final String name, final V8Array parameters) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#executeBooleanFunction(java.lang.String, com.eclipsesource.v8.V8Array)
         */
        @Override
        public boolean executeBooleanFunction(final String name, final V8Array parameters) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#executeDoubleFunction(java.lang.String, com.eclipsesource.v8.V8Array)
         */
        @Override
        public double executeDoubleFunction(final String name, final V8Array parameters) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#executeIntegerFunction(java.lang.String, com.eclipsesource.v8.V8Array)
         */
        @Override
        public int executeIntegerFunction(final String name, final V8Array parameters) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#executeObjectFunction(java.lang.String, com.eclipsesource.v8.V8Array)
         */
        @Override
        public V8Object executeObjectFunction(final String name, final V8Array parameters) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#executeStringFunction(java.lang.String, com.eclipsesource.v8.V8Array)
         */
        @Override
        public String executeStringFunction(final String name, final V8Array parameters) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#executeVoidFunction(java.lang.String, com.eclipsesource.v8.V8Array)
         */
        @Override
        public void executeVoidFunction(final String name, final V8Array parameters) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#getArray(java.lang.String)
         */
        @Override
        public V8Array getArray(final String key) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#getBoolean(java.lang.String)
         */
        @Override
        public boolean getBoolean(final String key) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#getDouble(java.lang.String)
         */
        @Override
        public double getDouble(final String key) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#getInteger(java.lang.String)
         */
        @Override
        public int getInteger(final String key) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#getKeys()
         */
        @Override
        public String[] getKeys() {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#getObject(java.lang.String)
         */
        @Override
        public V8Object getObject(final String key) throws V8ResultUndefined {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#getString(java.lang.String)
         */
        @Override
        public String getString(final String key) throws V8ResultUndefined {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#getType(java.lang.String)
         */
        @Override
        public int getType(final String key) throws V8ResultUndefined {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#registerJavaMethod(com.eclipsesource.v8.JavaCallback, java.lang.String)
         */
        @Override
        public V8Object registerJavaMethod(final JavaCallback callback, final String jsFunctionName) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#registerJavaMethod(com.eclipsesource.v8.JavaVoidCallback, java.lang.String)
         */
        @Override
        public V8Object registerJavaMethod(final JavaVoidCallback callback, final String jsFunctionName) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#registerJavaMethod(java.lang.Object, java.lang.String, java.lang.String, java.lang.Class[], boolean)
         */
        @Override
        public V8Object registerJavaMethod(final Object object, final String methodName, final String jsFunctionName, final Class<?>[] parameterTypes, final boolean includeReceiver) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * @see com.eclipsesource.v8.V8Object#setPrototype(com.eclipsesource.v8.V8Object)
         */
        @Override
        public V8Object setPrototype(final V8Object value) {
            throw new UnsupportedOperationException();
        }

    }

}
