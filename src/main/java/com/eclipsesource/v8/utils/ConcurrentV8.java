/*******************************************************************************
 * Copyright (c) 2016 Brandon Sanders
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Brandon Sanders - initial API and implementation and/or initial documentation
 ******************************************************************************/
package com.eclipsesource.v8.utils;

import com.eclipsesource.v8.V8Context;
import com.eclipsesource.v8.V8Isolate;

/**
 * Wrapper class for an {@link V8Context} instance that allows
 * a V8 context to be invoked from across threads without explicitly acquiring
 * or releasing locks.
 *
 * This class does not guarantee the safety of any objects stored in or accessed
 * from the wrapped V8 context; it only enables callers to interact with a V8
 * context from any thread. The V8 context represented by this class should
 * still be treated with thread safety in mind
 *
 * @author Brandon Sanders [brandon@alicorn.io]
 * @author R. Ian Bull - Additional API
 */
public final class ConcurrentV8 {
    private V8Context v8Context = null;

    /**
     * Create a new ConcurrentV8. A ConcurrentV8 allows multiple
     * threads to work with the same V8 engine by releasing
     * the locks between calls.
     */
    public ConcurrentV8() {
        v8Context = V8Isolate.create().createContext();
        v8Context.getIsolate().getLocker().release();
    }

    /**
     * Returns the V8 runtime backing by this ConcurrentV8
     *
     * @return The V8 runtime backing this ConcurrentV8
     */
    public V8Isolate getV8() {
        return v8Context.getIsolate();
    }

    /**
     * Runs an {@link V8ContextRunnable} on the V8 thread.
     *
     * <b>Note: </b> This method executes synchronously, not asynchronously;
     * it will not return until the passed {@link V8ContextRunnable} is done
     * executing. The method is also synchronized, so it will block until it
     * gets a chance to run.
     *
     * @param runnable {@link V8ContextRunnable} to run.
     */
    public synchronized void run(final V8ContextRunnable runnable) {
        try {
            v8Context.getIsolate().getLocker().acquire();
            runnable.run(v8Context);
        } finally {
            if ((v8Context != null) && (v8Context.getIsolate().getLocker() != null) && v8Context.getIsolate().getLocker().hasLock()) {
                v8Context.getIsolate().getLocker().release();
            }
        }
    }

    /**
     * Releases the underlying {@link V8Isolate} instance.
     *
     * This method should be invoked once you're done using this object,
     * otherwise a large amount of garbage could be left on the JVM due to
     * native resources.
     *
     * <b>Note:</b> If this method has already been called once, it
     * will do nothing.
     */
    public void release() {
        if ((v8Context != null) && !v8Context.isReleased()) {
            // Release the V8 instance from the V8 thread context.
            run(new V8ContextRunnable() {
                @Override
                public void run(final V8Context v8Context) {
                    if ((v8Context != null) && !v8Context.isReleased()) {
                        v8Context.close(true);
                    }
                }
            });
        }
    }
}
