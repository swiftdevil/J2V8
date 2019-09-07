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

/**
 * A V8Value that represents a JavaScript function.
 * JavaScript functions cannot be created in Java, but
 * can be returned as the result of invoking a JS script
 * or JS Function.
 */
public class V8Function extends V8Object {

    /**
     * Create a JavaScript function, that when invoked will call
     * the javaCallback passed to the receiver.
     *
     * @param v8Context The v8 context on which to create this function
     * @param javaCallback The callback to invoke
     */
    public V8Function(final V8Context v8Context, final JavaCallback javaCallback) {
        super(v8Context, javaCallback);
    }

    protected V8Function(final V8Context v8Context) {
        this(v8Context, null);
    }

    @Override
    protected V8Value createTwin() {
        return new V8Function(getContext());
    }

    /*
     * (non-Javadoc)
     * @see com.eclipsesource.v8.V8Object#toString()
     */
    @Override
    public String toString() {
        if (released || getContext().isReleased()) {
            return "[Function released]";
        }
        return super.toString();
    }

    @Override
    protected void initialize(final Object data) {
        if (data == null) {
            super.initialize(null);
            return;
        }
        JavaCallback javaCallback = (JavaCallback) data;
        long[] pointers = getContext().initNewV8Function();
        // position 0 is the object reference, position 1 is the function reference
        getContext().createAndRegisterMethodDescriptor(javaCallback, pointers[1]);
        released = false;
        addObjectReference(pointers[0]);
    }

    /*
     * (non-Javadoc)
     * @see com.eclipsesource.v8.V8Object#twin()
     */
    @Override
    public V8Function twin() {
        return (V8Function) super.twin();
    }

    /**
     * Invoke the JavaScript function on the current runtime.
     *
     * @param receiver The object on which to call the function on. The
     * receiver will be mapped to 'this' in JavaScript. If receiver is null
     * or undefined, then the V8 runtime will be used instead.
     * @param parameters The parameters passed to the JS Function.
     *
     * @return The result of JavaScript function.
     */
    @SuppressWarnings("resource")
    public Object call(V8Object receiver, final V8Array parameters) {
        getRuntime().checkThread();
        checkReleased();
        getRuntime().checkRuntime(receiver);
        getRuntime().checkRuntime(parameters);
        receiver = receiver != null ? receiver : getContext();
        long parametersHandle = parameters == null ? 0 : parameters.getHandle();
        long receiverHandle = receiver.isUndefined() ? getContext().getHandle() : receiver.getHandle();
        return getContext().executeFunction(receiverHandle, objectHandle, parametersHandle);
    }

}
