/**
 * Copyright 2016, Genuitec, LLC
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Genuitec LLC - initial API and implementation
 ******************************************************************************/
package com.eclipsesource.v8.debug;

import com.eclipsesource.v8.*;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * <p>V8DebugServer enables debuggers to connect to J2V8 via V8 server sockets debug protocol.
 * Server has to be created in the same thread as the provided V8 runtime has been created (the V8 thread).
 * You can specify port and whether the {@link #start()} method should
 * block until a client connects. {@link #setTraceCommunication(boolean)} allows to output
 * communication details for debugging purposes. Before creating V8 runtime you need to set V8 flag to expose
 * debug object. If you do not intend to set other flags, than you can use {@link #configureV8ForDebugging()}
 * method, otherwise set {@code -expose-debug-as=__j2v8_Debug} flag through {@link V8Isolate#setFlags(String)}.
 *
 * <p>Client connection is handled in a separate thread, however, commands are processed in the V8 thread.
 * Therefore it is vital to provide an opportunity to process requests by calling
 * {@link #processRequests(long)} method from the V8 thread. This will for instance
 * allow to install breakpoints before the JavaScript code starts to execute. It is also good to call that
 * method when V8 thread is idle to promptly provide responses to the debugger to avoid timeouts.
 *
 * <p>Example code:
 *
 * <code><br>
 * &nbsp;&nbsp;//configure for debugging before creating runtime<br>
 * &nbsp;&nbsp;V8DebugServer.configureV8ForDebugging();<br>
 * <br>
 * &nbsp;&nbsp;//create V8 runtime<br>
 * &nbsp;&nbsp;V8 runtime = V8.createV8Runtime();<br>
 * <br>
 * &nbsp;&nbsp;//create and start debug server<br>
 * &nbsp;&nbsp;int port = 0;<br>
 * &nbsp;&nbsp;boolean waitForConnection = true;<br>
 * &nbsp;&nbsp;server = new V8DebugServer(runtime, port, waitForConnection);<br>
 * &nbsp;&nbsp;System.out.println("V8 Debug Server listening on port "<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;+ server.getPort());<br>
 * &nbsp;&nbsp;server.start();<br>
 * <br>
 * &nbsp;&nbsp;//execute script and provide name for it<br>
 * &nbsp;&nbsp;runtime.executeVoidScript("var i = 15", "myscript.js", 0);<br>
 *
 * </code>
 *
 * @author piotr@genuitec.com
 */
@SuppressWarnings("nls")
public class V8DebugServer {

    /**
     * Name under which internal V8 debug object is going to be exposed in the runtime.
     * You can change the name if you are passing a different one through {@code -expose-debug-as}
     * flag.
     */
    public static String         DEBUG_OBJECT_NAME              = "__j2v8_Debug";

    private static final String  DEBUG_BREAK_HANDLER            = "__j2v8_debug_handler";
    private static final String  MAKE_BREAK_EVENT               = "__j2v8_MakeBreakEvent";
    private static final String  MAKE_COMPILE_EVENT             = "__j2v8_MakeCompileEvent";
    private static final String  SET_LISTENER                   = "setListener";
    private static final String  V8_DEBUG_OBJECT                = "Debug";

    /**
     * Utility method for simplification of configuring V8 for debugging support.
     */
    public static void configureV8ForDebugging() {
        try {
            V8Isolate.setFlags("-expose-debug-as=" + DEBUG_OBJECT_NAME);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private DebugProxy<String> proxy;

    private V8Context           runtime;
    private V8Object            debugObject;
    private V8Object            runningStateDcp;
    private V8Object            stoppedStateDcp;
    private boolean             traceCommunication = false;

    private List<String>        requests           = new LinkedList<>();

    /**
     * Creates V8DebugServer.
     *
     * @param v8Context
     * @param proxy
     */
    public V8DebugServer(final V8Context v8Context, final DebugProxy<String> proxy) {
        setupProxy(proxy);

        this.proxy = proxy;
        this.runtime = v8Context;

        V8Object debugScope = v8Context.getObject(DEBUG_OBJECT_NAME);
        if (debugScope == null) {
            System.err.println("Cannot initialize debugger server - global debug object not found.");
            return;
        }
        try {
            debugObject = debugScope.getObject(V8_DEBUG_OBJECT);
        } finally {
            debugScope.close();
        }

        v8Context.executeVoidScript("(function() {\n"
                + " " + DEBUG_OBJECT_NAME + ".Debug. " + MAKE_BREAK_EVENT + " = function (break_id,breakpoints_hit) {\n"
                + "  return new " + DEBUG_OBJECT_NAME + ".BreakEvent(break_id,breakpoints_hit);\n"
                + " }\n"
                + " " + DEBUG_OBJECT_NAME + ".Debug. " + MAKE_COMPILE_EVENT + " = function(script,type) {\n"
                + "  var scripts = " + DEBUG_OBJECT_NAME + ".Debug.scripts()\n"
                + "  for (var i in scripts) {\n"
                + "   if (scripts[i].id == script.id()) {\n"
                + "     return new " + DEBUG_OBJECT_NAME + ".CompileEvent(scripts[i], type);\n"
                + "   }\n"
                + "  }\n"
                + "  return {toJSONProtocol: function() {return ''}}\n"
                + " }\n"
                + "})()");
    }

    /**
     * Output all communication to the console. For purpose of debugging V8DebugServer itself.
     * @param value
     */
    public void setTraceCommunication(final boolean value) {
        traceCommunication = value;
    }

    /**
     * Starts accepting client connections and blocks until a client connects
     * if {@code waitForConnection} has been passed to V8DebugServer constructor.
     */
    public void start() {
        if (!hasClient()) {
            return;
        }
        setupEventHandler();

        runningStateDcp = runtime.executeObjectScript("(function() {return new " + DEBUG_OBJECT_NAME + ".DebugCommandProcessor(null, true)})()");
    }

    public void stop() {
        if (proxy != null) {
            proxy.close();
            proxy = null;
        }

        //release resources
        if (runningStateDcp != null) {
            runningStateDcp.close();
            runningStateDcp = null;
        }
        if (debugObject != null) {
            debugObject.close();
            debugObject = null;
        }
        if (stoppedStateDcp != null) {
            stoppedStateDcp.close();
            stoppedStateDcp = null;
        }
    }

    private void logError(final Throwable t) {
        t.printStackTrace();
    }

    private void sendJson(String json) throws IOException {
        if (!hasClient()) {
            throw new IOException("There is no connected client.");
        }

        json = json.replace("\\/", "/");

        //send contents to the client
        if (json.length() > 0) {
            proxy.toClient(json);
        }
    }

    private boolean hasClient() {
        return proxy != null;
    }

    public void processRequests(final long timeout) throws InterruptedException {
        long start = System.currentTimeMillis();
        do {
            String[] reqs;
            do {
                synchronized (requests) {
                    reqs = requests.toArray(new String[requests.size()]);
                    requests.clear();
                }
                for (String req : reqs) {
                    try {
                        processRequest(req);
                    } catch (Exception e) {
                        logError(e);
                    }
                }
            } while (reqs.length > 0);
            if (timeout > 0) {
                Thread.sleep(10);
            }
        } while ((timeout > 0) && ((start + timeout) > System.currentTimeMillis()));
    }

    private void processRequest(final String message) throws IOException {
        if (traceCommunication) {
            System.out.println("Got message: \n" + message.substring(0, Math.min(message.length(), 1000)));
        }
        V8Array params = new V8Array(runtime);
        params.push(message);

        @SuppressWarnings("resource")
        V8Object dcp = stoppedStateDcp != null ? stoppedStateDcp : runningStateDcp;
        Object result = dcp.executeFunction("processDebugJSONRequest", params);

        String json = result.toString();

        if ((stoppedStateDcp == null) && json.contains("\"running\":false")) {
            //XXX Need to implement functionality by adding to V8 class
            //    breakpoints before initial script or function execution
            json = json.replace("\"running\":false", "\"running\":true")
                    .replace("\"success\":true", "\"success\":false")
                    .replace("{\"", "{\"message\":\"Client requested suspension is not supported on J2V8.\",\"");
            dcp.add("running_", true);
        }

        if (traceCommunication) {
            System.out.println("Returning response: \n" + json.substring(0, Math.min(json.length(), 1000)));
        }
        sendJson(json);
    }

    private void setupProxy(DebugProxy<String> proxy) {
        proxy.setServer(requests::add);
    }

    private void setupEventHandler() {
        EventHandler handler = new EventHandler();
        debugObject.registerJavaMethod(handler, DEBUG_BREAK_HANDLER);
        V8Function debugHandler = null;
        V8Array parameters = null;
        try {
            debugHandler = (V8Function) debugObject.getObject(DEBUG_BREAK_HANDLER);
            parameters = new V8Array(runtime);
            parameters.push(debugHandler);
            debugObject.executeFunction(SET_LISTENER, parameters);
        } finally {
            if ((debugHandler != null) && !debugHandler.isReleased()) {
                debugHandler.close();
            }
            if ((parameters != null) && !parameters.isReleased()) {
                parameters.close();
            }
        }
    }

    private void enterBreakLoop(final V8Object execState, final V8Object eventData) throws IOException {
        try {
            V8Array params = new V8Array(runtime);
            try {
                params.push(false);
                stoppedStateDcp = execState.executeObjectFunction("debugCommandProcessor", params);
            } finally {
                params.close();
            }

            //send event to debugger
            int breakId = execState.getInteger("break_id");
            V8Array breakpointsHit = eventData.getArray("break_points_hit_");
            V8Object event = null;

            params = new V8Array(runtime);
            try {
                params.push(breakId);
                params.push(breakpointsHit);
                event = debugObject.executeObjectFunction(MAKE_BREAK_EVENT, params);
                String json = event.executeStringFunction("toJSONProtocol", null);
                if (traceCommunication) {
                    System.out.println("Sending event (Break):\n" + json);
                }
                sendJson(json);
            } finally {
                params.close();
                breakpointsHit.close();
                if (event != null) {
                    event.close();
                }
            }

            //process requests until one of the resumes execution
            while (hasClient() && !stoppedStateDcp.executeBooleanFunction("isRunning", null)) {
                try {
                    processRequests(10);
                } catch (InterruptedException e) {
                    //ignore
                }
            }
        } finally {
            stoppedStateDcp.close();
            stoppedStateDcp = null;
        }
    }

    private void sendCompileEvent(final V8Object eventData) throws IOException {
        if (!hasClient()) {
            return;
        }
        //send event to debugger
        int type = eventData.getInteger("type_");
        V8Object script = eventData.getObject("script_");
        V8Object event = null;

        V8Array params = new V8Array(runtime);
        try {
            params.push(script);
            params.push(type);
            event = debugObject.executeObjectFunction(MAKE_COMPILE_EVENT, params);
            String json = event.executeStringFunction("toJSONProtocol", null);
            if (traceCommunication) {
                System.out.println("Sending event (CompileEvent):\n" + json.substring(0, Math.min(json.length(), 1000)));
            }
            if (json.length() > 0) {
                sendJson(json);
            }
        } finally {
            params.close();
            script.close();
            if (event != null) {
                event.close();
            }
        }
    }

    private class EventHandler implements JavaVoidCallback {

        @Override
        public void invoke(final V8Object receiver, final V8Array parameters) {
            if ((parameters == null) || parameters.isUndefined()) {
                return;
            }
            V8Object execState = null;
            V8Object eventData = null;
            try {
                int event = parameters.getInteger(0);
                execState = parameters.getObject(1);
                eventData = parameters.getObject(2);

                if (traceCommunication) {
                    String type = "unknown";
                    switch (event) {
                        case 1:
                            type = "Break";
                            break;
                        case 2:
                            type = "Exception";
                            break;
                        case 3:
                            type = "NewFunction";
                            break;
                        case 4:
                            type = "BeforeCompile";
                            break;
                        case 5:
                            type = "AfterCompile";
                            break;
                        case 6:
                            type = "CompileError";
                            break;
                        case 7:
                            type = "PromiseEvent";
                            break;
                        case 8:
                            type = "AsyncTaskEvent";
                            break;
                    }
                    System.out.println("V8 has emmitted an event of type " + type);
                }

                if (!hasClient()) {
                    return;
                }

                switch (event) {
                    case 1: //Break
                        enterBreakLoop(execState, eventData);
                        break;
                    case 5: //afterCompile
                    case 6: //compileError
                        sendCompileEvent(eventData);
                        break;
                    case 2: //exception
                    default:
                }
            } catch (Exception e) {
                logError(e);
            } finally {
                safeRelease(execState);
                safeRelease(eventData);
            }
        }

        private void safeRelease(final Releasable object) {
            if ((object != null)) {
                object.release();
            }
        }
    }
}
