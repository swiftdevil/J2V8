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
package com.eclipsesource.v8.utils;

import com.eclipsesource.v8.V8Isolate;

/**
 * A Thread with its own V8 runtime. The thread will create a runtime,
 * and execute a runnable on that runtime. When the thread ends,
 * the runtime will be released.
 *
 * It's suggested that you *DO NOT* release the lock on the runtime.
 * If the lock is released, you will need to ensure that the runtime
 * is properly released.
 */
public class V8Thread extends Thread {

    private final V8Runnable target;

    /**
     * Create as new Thread with its own V8Runtime.
     *
     * @param target The code to execute with the given runtime.
     */
    public V8Thread(final V8Runnable target) {
        this.target = target;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Thread#run()
     */
    @Override
    public void run() {
        V8Isolate runtime = V8Isolate.create();
        try {
            target.run(runtime.createContext());
        } finally {
            synchronized (this) {
                if (runtime.getLocker().hasLock()) {
                    runtime.close();
                    runtime = null;
                }
            }
        }
    }

}
