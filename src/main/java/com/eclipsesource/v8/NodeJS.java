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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.function.Consumer;

/**
 * An isolate NodeJS runtime.
 *
 * This class is only available on some platforms. In particular any methods
 * on this class, on an Android device, will lead to an UnsupportedOperationException.
 */
public class NodeJS implements Closeable {

    private static final String     TMP_JS_EXT          = ".js.tmp";
    private static final String     NEXT_TICK           = "nextTick";
    private static final String     PROCESS             = "process";
    private static final String     GLOBAL              = "global";
    private static final String     J2V8_GLOBAL_NS      = "_j2v8";
    private static final String     NATIVE_MASK         = "nativeMask";
    private static final String     STARTUP_CALLBACK    = "__run";
    private static final String     STARTUP_SCRIPT      = "global." + STARTUP_CALLBACK + "(require, exports, module, __filename, __dirname);";
    private static final String     STARTUP_SCRIPT_NAME = "startup";
    private static final String     VERSIONS            = "versions";
    private static final String     NODE                = "node";

    private final V8Context         v8Context;

    private V8Function              require;
    private V8Object                j2v8GlobalNs;
    private Consumer<V8Function>    initConsumer;
	private String                  nodeVersion         = null;

    /**
     * Returns the version of Node.js that is runtime is built against.
     * This uses process.versions.node to get the version.
     *
     * @return The version of Node.js.
     */
    public String getNodeVersion() {
        if (nodeVersion != null) {
            return nodeVersion;
        }
        V8Object process = null;
        V8Object versions = null;
        try {
            process = v8Context.getObject(PROCESS);
            versions = process.getObject(VERSIONS);
            nodeVersion = versions.getString(NODE);
        } finally {
            safeRelease(process);
            safeRelease(versions);
        }
        return nodeVersion;
    }

    /**
     * Creates a NodeJS runtime and executes a JS Script
     *
     * @return The NodeJS runtime.
     *
     * May throw an UnsupportedOperationException if node.js integration has not
     * been compiled for your platform.
     */

    public static NodeJS createNodeJS() {
        V8Isolate isolate = V8Isolate.create();
        return createNodeJS(isolate);
    }

    public static NodeJS createNodeJS(final V8Isolate isolate) {
        V8Context context = isolate.createContext(GLOBAL);
        return createNodeJS(context);
    }

    public static NodeJS createNodeJS(final V8Context context) {
        final NodeJS node = new NodeJS(context);
        context.registerJavaMethod((receiver, parameters) -> {
			try (V8Function require = (V8Function) parameters.get(0)) {
				node.init(require.twin());
			}
		}, STARTUP_CALLBACK);

        node.setupJ2v8GlobalNs();
        return node;
    }

    private void setupJ2v8GlobalNs() {
    	V8Object global = v8Context.getObject(GLOBAL);
    	j2v8GlobalNs = new V8Object(v8Context);

    	global.add(J2V8_GLOBAL_NS, j2v8GlobalNs);
    	global.close();
	}

	private void releaseNativeMask() {
    	j2v8GlobalNs.close();
	}

	public NodeJS setNativeMask(JavaCallback nativeMask) {
    	j2v8GlobalNs.registerJavaMethod(nativeMask, NATIVE_MASK);
    	return this;
	}

    public NodeJS start() {
        return start(null);
    }

    public NodeJS start(File baseDir) {
        try {
            File startupScript = createTemporaryScriptFile(STARTUP_SCRIPT, STARTUP_SCRIPT_NAME, baseDir);
            try {
                getContext().createNodeRuntime(startupScript.getAbsolutePath());
            } finally {
                startupScript.delete();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return this;
    }

    public NodeJS setInitConsumer(Consumer<V8Function> consumer) {
        initConsumer = consumer;
        return this;
    }

    /**
     * Returns the V8 context being used for this NodeJS instance.
     *
     * @return The V8 Context.
     */
    public V8Context getContext() {
        return v8Context;
    }

    /**
     * Returns the V8 runtime being used for this NodeJS instance.
     *
     * @return The V8 Runtime.
     */
    public V8Isolate getRuntime() {
        return getContext().getIsolate();
    }

    /**
     * Handles the next message in the message loop. Returns True
     * if there are more messages to handle, false otherwise.
     *
     * @return True if there are more messages to handle, false otherwise.
     */
    public boolean handleMessage() {
        getRuntime().checkThread();
        return getContext().pumpMessageLoop();
    }

    /**
     * Releases the NodeJS runtime.
     */
    @Override
    public void close() {
        closeContext(true);

        if (!getContext().isReleased()) {
            getContext().close(true);
        }
    }

    public void closeContext(boolean closeRuntime) {
        getRuntime().checkThread();

        releaseNativeMask();

        if (!require.isReleased()) {
            require.close();
        }
        if (!getContext().isReleased()) {
            getContext().close(closeRuntime);
        }
    }

    /**
     * Returns true if there are more messages to process, false otherwise.
     *
     * @return True if there are more messages to process, false otherwise.
     */
    public boolean isRunning() {
        getRuntime().checkThread();
        return getContext().isRunning();
    }

    /**
     * Invokes NodeJS require() on the specified file. This will load the module, execute
     * it and return the exports object to the caller. The exports object must be released.
     *
     * @param path The module to load.
     * @return The exports object.
     */
    public V8Object require(final String path) {
        getRuntime().checkThread();
		try (V8Array requireParams = new V8Array(getContext())) {
			requireParams.push(path);
			return (V8Object) require.call(null, requireParams);
		}
    }

    /**
     * Execute a NodeJS script. This will load the script and execute it on the
     * next tick. This is the same as how NodeJS executes scripts at startup. Since
     * the script won't actually run until the next tick, this method does not return
     * a result.
     *
     * @param script The script to execute.
     */
    public void exec(final String script) {
        V8Function scriptExecution = createScriptExecutionCallback(script);
        V8Object process = null;
        V8Array parameters = null;
        try {
            process = getContext().getObject(PROCESS);
            parameters = new V8Array(getContext());
            parameters.push(scriptExecution);
            process.executeObjectFunction(NEXT_TICK, parameters);
        } finally {
            safeRelease(process);
            safeRelease(parameters);
            safeRelease(scriptExecution);
        }
    }

    public void execAndPump(final String script) {
        exec(script);
        getContext().checkPendingException();
        pump();
    }

    public void pump() {
        boolean running = true;
        while (running) {
            running = handleMessage();
            getContext().checkPendingException();
        }
    }

    private V8Function createScriptExecutionCallback(final String script) {
        return new V8Function(getContext(), (receiver, parameters) -> getContext().executeScript(script));
    }

    private void safeRelease(final Releasable releasable) {
        if (releasable != null) {
            releasable.release();
        }
    }

    private NodeJS(final V8Context v8Context) {
        this.v8Context = v8Context;
    }

    private void init(final V8Function require) {
        this.require = require;

        if (initConsumer != null) {
            initConsumer.accept(require);
        }
    }

    private static File createTemporaryScriptFile(final String script, final String name, final File baseDir) throws IOException {
        File tempFile = File.createTempFile(name, TMP_JS_EXT, baseDir);
        try (PrintWriter writer = new PrintWriter(tempFile, "UTF-8")) {
            writer.print(script);
        }
        return tempFile;
    }
}
