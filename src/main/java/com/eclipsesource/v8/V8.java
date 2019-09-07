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
import com.eclipsesource.v8.utils.V8Runnable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
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
public class V8 extends V8Object {

    private static final Object          lock                    = new Object();
    private volatile static int          runtimeCounter          = 0;
    private static String                v8Flags                 = null;
    private static boolean               initialized             = false;

    private V8Locker                     locker                  = null;
    private long                         objectReferences        = 0;
    private LinkedList<V8Context>        contexts                = new LinkedList<V8Context>();
    private List<Releasable>             resources               = null;
    private V8Map<V8Executor>            executors               = null;
    private boolean                      forceTerminateExecutors = false;

    private LinkedList<ReferenceHandler> referenceHandlers       = new LinkedList<ReferenceHandler>();
    private LinkedList<V8Runnable>       releaseHandlers         = new LinkedList<V8Runnable>();

    private static boolean               nativeLibraryLoaded     = false;
    private static Error                 nativeLoadError         = null;
    private static Exception             nativeLoadException     = null;
    private static V8Value               undefined               = new V8Object.Undefined();

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
    public static V8 createV8Runtime() {
        return createV8Runtime(null, null);
    }

    /**
     * Creates a new V8Runtime and loads the required native libraries if they
     * are not already loaded. An alias is also set for the global scope. For example,
     * 'window' can be set as the global scope name.
     *
     * The current thread is given the lock to this runtime.
     *
     * @param globalAlias The name to associate with the global scope.
     *
     * @return A new isolated V8 Runtime.
     */
    public static V8 createV8Runtime(final String globalAlias) {
        return createV8Runtime(globalAlias, null);
    }

    /**
     * Creates a new V8Runtime and loads the required native libraries if they
     * are not already loaded. An alias is also set for the global scope. For example,
     * 'window' can be set as the global scope name.
     *
     * The current thread is given the lock to this runtime.
     *
     * @param globalAlias The name to associate with the global scope.
     * @param tempDirectory The name of the directory to extract the native
     * libraries too.
     *
     * @return A new isolated V8 Runtime.
     */
    public static V8 createV8Runtime(final String globalAlias, final String tempDirectory) {
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

        // this is nasty load-order logic
        V8 runtime = new V8();
        V8Context context = new V8Context();

        long runtimePtr = V8API.get()._createIsolate(runtime);
        runtime.init(runtimePtr, context);

        long contextPtr = V8API.get()._createContext(context, runtimePtr, globalAlias);
        context.init(runtime, contextPtr);

        synchronized (lock) {
            runtimeCounter++;
        }

        return runtime;
    }

    private V8() {
        released = false;
    }

    private void init(long runtimePtr, V8Context defaultContext) {
        objectHandle = runtimePtr;
        locker = new V8Locker(this);
        setDefaultContext(defaultContext);
        checkThread();
    }

    @Override
    public V8 getRuntime() {
        return this;
    }

    @Override
    public V8Context getContext() {
        return getDefaultContext();
    }

    public V8Context getDefaultContext() {
        return contexts.getFirst();
    }

    private void setDefaultContext(V8Context context) {
        contexts.addFirst(context);
        addObjRef(context);
    }

    public void doAllContexts(Consumer<V8Context> contextConsumer) {
        contexts.forEach(contextConsumer);
    }

    /**
     * Adds a ReferenceHandler to track when new V8Objects are created.
     *
     * @param handler The ReferenceHandler to add
     */
    public void addReferenceHandler(final ReferenceHandler handler) {
        referenceHandlers.add(0, handler);
    }

    /**
     * Adds a handler that will be called when the runtime is being released.
     * The runtime will still be available when the handler is executed.
     *
     * @param handler The handler to invoke when the runtime, is being released
     */
    public void addReleaseHandler(final V8Runnable handler) {
        releaseHandlers.add(handler);
    }

    /**
     * Removes an existing ReferenceHandler from the collection of reference handlers.
     * If the ReferenceHandler does not exist in the collection, it is ignored.
     *
     * @param handler The reference handler to remove
     */
    public void removeReferenceHandler(final ReferenceHandler handler) {
        referenceHandlers.remove(handler);
    }

    /**
     * Removes an existing release handler from the collection of release handlers.
     * If the release handler does not exist in the collection, it is ignored.
     *
     * @param handler The handler to remove
     */
    public void removeReleaseHandler(final V8Runnable handler) {
        releaseHandlers.remove(handler);
    }


    private void notifyReleaseHandlers(final V8 runtime) {
        for (V8Runnable handler : releaseHandlers) {
            handler.run(runtime.getDefaultContext());
        }
    }

    private void notifyReferenceCreated(final V8Value object) {
        for (ReferenceHandler referenceHandler : referenceHandlers) {
            referenceHandler.v8HandleCreated(object);
        }
    }

    private void notifyReferenceDisposed(final V8Value object) {
        for (ReferenceHandler referenceHandler : referenceHandlers) {
            referenceHandler.v8HandleDisposed(object);
        }
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
        return runtimeCounter;
    }

    /**
     * Returns the number of Object References for this runtime.
     *
     * @return The number of Object References on this runtime.
     */
    public long getObjectReferenceCount() {
        AtomicLong refCount = new AtomicLong(objectReferences);
        doAllContexts(v8Ctx -> refCount.addAndGet(v8Ctx.weakReferenceCount()));

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

    /*
     * (non-Javadoc)
     * @see com.eclipsesource.v8.V8Value#close()
     */
    @Override
    public void close() {
        release(true);
    }

    /*
     * (non-Javadoc)
     * @see com.eclipsesource.v8.V8Value#release()
     */
    @Override
    @Deprecated
    public void release() {
        release(true);
    }

    /**
     * Terminates any JavaScript executing on this runtime. Once
     * the runtime is released, any executors that were spawned
     * will also be force terminated.
     */
    public void terminateExecution() {
        forceTerminateExecutors = true;
        getDefaultContext().terminateExecution();
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
            notifyReleaseHandlers(this);
        } finally {
            releaseResources();
            shutdownExecutors(forceTerminateExecutors);
            if (executors != null) {
                executors.clear();
            }
            doAllContexts(V8Context::releaseNativeMethodDescriptors);
            doAllContexts(V8Context::close);
            synchronized (lock) {
                runtimeCounter--;
            }
            V8API.get()._releaseRuntime(getHandle());
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
        checkThread();

        V8Context context = new V8Context();
		long contextPtr = V8API.get()._createContext(context, getHandle(), globalAlias);
		context.init(this, contextPtr);

        contexts.addLast(context);
        addObjRef(context);

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
        lowMemoryNotification(getHandle());
    }

    void checkRuntime(final V8Value value) {
        if ((value == null) || value.isUndefined()) {
            return;
        }
        V8 runtime = value.getRuntime();
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

    void createNodeRuntime(final String fileName) {
        getDefaultContext().startNodeJS(fileName);
    }

    protected void acquireLock(final long v8ContextPtr) {
        V8API.get()._acquireLock(v8ContextPtr);
    }

    protected void releaseLock(final long v8RuntimePtr) {
        V8API.get()._releaseLock(v8RuntimePtr);
    }

    protected void lowMemoryNotification(final long v8RuntimePtr) {
        V8API.get()._lowMemoryNotification(v8RuntimePtr);
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

    void addObjRef(final V8Value reference) {
        objectReferences++;
        if (!referenceHandlers.isEmpty()) {
            notifyReferenceCreated(reference);
        }
    }

    void releaseObjRef(final V8Value reference) {
        if (!referenceHandlers.isEmpty()) {
            notifyReferenceDisposed(reference);
        }
        objectReferences--;
    }
}
