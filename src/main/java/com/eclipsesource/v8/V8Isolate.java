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

import com.eclipsesource.v8.utils.V8Executor;
import com.eclipsesource.v8.utils.V8Map;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * An isolated V8Runtime. All JavaScript execution must exist
 * on a single runtime, and data is not shared between runtimes.
 * A runtime must be created and released when finished.
 *
 * All access to a runtime must come from the same thread, unless
 * the thread explicitly gives up control using the V8Locker.
 *
 * A public static factory method can be used to create the runtime.
 *
 * V8 runtime = V8.createV8Runtime();
 *
 */
public class V8Isolate implements Releasable {

    private static final Object           lock                    = new Object();
    private volatile static AtomicInteger runtimeCounter          = new AtomicInteger(0);
    private static String                 v8Flags                 = null;
    private static boolean                initialized             = false;

    private long                          isolatePtr              = 0;
    private V8Locker                      locker                  = null;
    private LinkedList<V8Context>         contexts                = new LinkedList<V8Context>();
    private List<Releasable>              resources               = null;
    private V8Map<V8Executor>             executors               = null;
    private boolean                       forceTerminateExecutors = false;
    private boolean                       released                = false;

    private static boolean                nativeLibraryLoaded     = false;
    private static Error                  nativeLoadError         = null;
    private static Exception              nativeLoadException     = null;
    private static V8Value                undefined               = new V8Object.Undefined();

    private synchronized static void load(final String tmpDirectory) {
        try {
            LibraryLoader.loadLibrary(tmpDirectory);
            nativeLibraryLoaded = true;
        } catch (Error e) {
            nativeLoadError = e;
        } catch (Exception e) {
            nativeLoadException = e;
        }
    }

    /**
     * Determines if the native libraries are loaded.
     *
     * @return Returns true if the native libraries are loaded,
     * false otherwise.
     */
    public static boolean isLoaded() {
        return nativeLibraryLoaded;
    }

    /**
     * Sets the V8 flags on the platform. All runtimes will be created
     * with the same flags. Flags must be set before the runtime is
     * created.
     *
     * @param flags The flags to set on V8
     */
    public static void setFlags(final String flags) {
        v8Flags = flags;
        initialized = false;
    }

    /**
     * Creates a new V8Runtime and loads the required
     * native libraries if they are not already loaded.
     * The current thread is given the lock to this runtime.
     *
     * @return A new isolated V8 Runtime.
     */
    public static V8Isolate create() {
        return create(null);
    }

    /**
     * Creates a new V8Runtime and loads the required native libraries if they
     * are not already loaded. An alias is also set for the global scope. For example,
     * 'window' can be set as the global scope name.
     *
     * The current thread is given the lock to this runtime.
     *
     * @param tempDirectory The name of the directory to extract the native
     * libraries too.
     *
     * @return A new isolated V8 Runtime.
     */
    public static V8Isolate create(final String tempDirectory) {
        if (!nativeLibraryLoaded) {
            synchronized (lock) {
                if (!nativeLibraryLoaded) {
                    load(tempDirectory);
                }
            }
        }
        checkNativeLibraryLoaded();
        if (!initialized) {
            V8API._setFlags(v8Flags);
            initialized = true;
        }

        V8Isolate runtime = new V8Isolate();
        runtimeCounter.incrementAndGet();

        return runtime;
    }

    private V8Isolate() {
        isolatePtr = V8API.get()._createIsolate(this);
        locker = new V8Locker(this);
        checkThread();
    }

    public long getIsolatePtr() {
        return isolatePtr;
    }

    void doAllContexts(Consumer<V8Context> contextConsumer) {
        contexts.forEach(contextConsumer);
    }

    private static void checkNativeLibraryLoaded() {
        if (!nativeLibraryLoaded) {
            String vendorName = LibraryLoader.computeLibraryShortName(true);
            String baseName = LibraryLoader.computeLibraryShortName(false);
            String message = "J2V8 native library not loaded (" + baseName + "/" + vendorName + ")";

            if (nativeLoadError != null) {
                throw new IllegalStateException(message, nativeLoadError);
            } else if (nativeLoadException != null) {
                throw new IllegalStateException(message, nativeLoadException);
            } else {
                throw new IllegalStateException(message);
            }
        }
    }

    /**
     * Returns an UNDEFINED constant.
     *
     * @return The UNDEFINED constant value.
     */
    public static V8Value getUndefined() {
        return undefined;
    }

    /**
     * Returns the number of active runtimes.
     *
     * @return The number of active runtimes.
     */
    public static int getActiveRuntimes() {
        return runtimeCounter.get();
    }

    /**
     * Returns the number of Object References for this runtime.
     *
     * @return The number of Object References on this runtime.
     */
    public long getObjectReferenceCount() {
        AtomicLong refCount = new AtomicLong(0);
        doAllContexts(v8Ctx -> refCount.addAndGet(v8Ctx.objectReferenceCount()));

        return refCount.get();
    }

    /**
     * Gets the version of the V8 engine
     *
     * @return The version of the V8 Engine.
     */
    public static String getV8Version() {
        return V8API.get()._getVersion();
    }

    /**
     * Returns the revision ID of this version as specified
     * by the source code management system. Currently we use
     * Git, so this will return the commit ID for this revision.
     *
     * @return The revision ID of this version of J2V8
     */
    public static String getSCMRevision() {
        return "Unknown revision ID";
    }

    @Override
    public void close() {
        release(true);
    }

    @Override
    public boolean isReleased() {
        return released;
    }

    /**
     * Terminates any JavaScript executing on this runtime. Once
     * the runtime is released, any executors that were spawned
     * will also be force terminated.
     */
    public void terminateExecution() {
        forceTerminateExecutors = true;
        doAllContexts(V8Context::terminateExecution);
    }

    /**
     * Release native resources associated with this runtime. Once
     * released, a runtime cannot be reused.
     *
     * @param reportMemoryLeaks True if memory leaks should be
     * reported by throwing an IllegalStateException if any
     * objects were not released.
     */
    public void release(final boolean reportMemoryLeaks) {
        if (isReleased()) {
            return;
        }
        checkThread();
        try {
            doAllContexts(V8Context::notifyReleaseHandlers);
        } finally {
            releaseResources();
            shutdownExecutors(forceTerminateExecutors);
            if (executors != null) {
                executors.clear();
            }
            doAllContexts(V8Context::releaseNativeMethodDescriptors);
            doAllContexts(V8Context::close);
            runtimeCounter.decrementAndGet();
            V8API.get()._releaseRuntime(isolatePtr);
            released = true;
            if (reportMemoryLeaks && (getObjectReferenceCount() > 0)) {
                throw new IllegalStateException(getObjectReferenceCount() + " Object(s) still exist in runtime");
            }
        }
    }

    private void releaseResources() {
        if (resources != null) {
            for (Releasable releasable : resources) {
                releasable.release();
            }
            resources.clear();
            resources = null;
        }
    }

    /**
     * Registers an executor with this runtime. An executor is another
     * runtime with its own thread. By registering an executor, it can be
     * terminated when this runtime is released.
     *
     * @param key The key to associate the executor with.
     * @param executor The executor itself.
     */
    public void registerV8Executor(final V8Object key, final V8Executor executor) {
        checkThread();
        if (executors == null) {
            executors = new V8Map<V8Executor>();
        }
        executors.put(key, executor);
    }

    /**
     * Removes the executor from this runtime. The executor is
     * *NOT* shutdown, simply removed from the list of known
     * executors.
     *
     * @param key The key the executor was associated with.
     * @return The executor or null if it does not exist.
     */
    public V8Executor removeExecutor(final V8Object key) {
        checkThread();
        if (executors == null) {
            return null;
        }
        return executors.remove(key);
    }

    /**
     * Returns the executor associated with the given key.
     *
     * @param key The key the executor was associated with.
     * @return The executor or null if it does not exist.
     */
    public V8Executor getExecutor(final V8Object key) {
        checkThread();
        if (executors == null) {
            return null;
        }
        return executors.get(key);
    }

    /**
     * Shutdown all executors associated with this runtime.
     * If force terminate is specified, it will forcefully terminate
     * the executors, otherwise it will simply signal that they
     * should terminate.
     *
     * @param forceTerminate Specify if the executors should be
     * forcefully terminated, or simply notified to shutdown when ready.
     */
    public void shutdownExecutors(final boolean forceTerminate) {
        checkThread();
        if (executors == null) {
            return;
        }
        for (V8Executor executor : executors.values()) {
            if (forceTerminate) {
                executor.forceTermination();
            } else {
                executor.shutdown();
            }
        }
    }

    /**
     * Registers a resource with this runtime. All registered
     * resources will be released before the runtime is released.
     *
     * @param resource The resource to register.
     */
    public void registerResource(final Releasable resource) {
        checkThread();
        if (resources == null) {
            resources = new ArrayList<Releasable>();
        }
        resources.add(resource);
    }


    /**
     * Creates a new context within the runtime.
     */
    public V8Context createContext() {
        return createContext(null);
    }

    /**
     * Creates a new context within the runtime.
     * 
     * @param globalAlias The name to associate with the global scope.
     */
    public V8Context createContext(String globalAlias) {
        V8Context context = new V8Context(this, globalAlias);
        contexts.addLast(context);

        return context;
    }

    /**
     * Returns the locker associated with this runtime. The locker allows
     * threads to give up control of the runtime and other threads to acquire
     * control.
     *
     * @return The locker associated with this runtime.
     */
    public V8Locker getLocker() {
        return locker;
    }

    /**
     * Returns the unique build ID of the native library.
     *
     * @return The unique build ID of the Native library.
     */
    public long getBuildID() {
        return V8API.get()._getBuildID();
    }

    /**
     * Indicates to V8 that the system is low on memory.
     * V8 may use this to attempt to recover space by running
     * the garbage collector.
     */
    public void lowMemoryNotification() {
        checkThread();
        V8API.get()._lowMemoryNotification(getIsolatePtr());
    }

    void checkRuntime(final V8Value value) {
        if ((value == null) || value.isUndefined()) {
            return;
        }
        V8Isolate runtime = value.getIsolate();
        if ((runtime == null) ||
                runtime.isReleased() ||
                (runtime != this)) {
            throw new Error("Invalid target runtime");
        }
    }

    void checkThread() {
        locker.checkThread();
        if (isReleased()) {
            throw new Error("Runtime disposed error");
        }
    }

    protected void acquireLock() {
        V8API.get()._acquireLock(getIsolatePtr());
    }

    protected void releaseLock() {
        V8API.get()._releaseLock(getIsolatePtr());
    }

    public static boolean isNodeCompatible() {
        if (!nativeLibraryLoaded) {
            synchronized (lock) {
                if (!nativeLibraryLoaded) {
                    load(null);
                }
            }
        }

        return V8API._isNodeCompatible();
    }
}
