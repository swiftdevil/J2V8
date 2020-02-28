package com.eclipsesource.v8;

import com.eclipsesource.v8.utils.V8Runnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.*;

public class V8Context extends V8Object {
	private V8Isolate                          isolate                 = null;
	private long                               contextPtr              = 0L;
	private Map<String, Object>                data                    = null;
	private Map<Long, MethodDescriptor>        functionRegistry        = new HashMap<Long, MethodDescriptor>();
	private long                               objectReferences        = 0;
	private Map<Long, V8Value>                 v8WeakReferences        = new HashMap<Long, V8Value>();
	private LinkedList<ReferenceHandler>       referenceHandlers       = new LinkedList<ReferenceHandler>();
	private LinkedList<V8Runnable>             releaseHandlers         = new LinkedList<V8Runnable>();
	private V8ScriptException                  pendingException        = null;
	private static Object                      invalid                 = new Object();

	private static class MethodDescriptor {
		Object           object;
		Method           method;
		JavaCallback     callback;
		JavaVoidCallback voidCallback;
		boolean          includeReceiver;
	}

	V8Context(V8Isolate isolate, String globalAlias) {
		super((V8Context) null);

		released = false;
		this.isolate = isolate;

		contextPtr = V8API.get()._createContext(this, isolate.getIsolatePtr(), globalAlias);
		objectHandle = V8API.get()._getGlobalObject(contextPtr);
	}

	@Override
	public V8Isolate getIsolate() {
		return isolate;
	}
	
	private long getContextPtr() {
		return contextPtr;
	}

	public void close(boolean closeRuntime) {
		close();
		if (closeRuntime) {
			getIsolate().close();
		}
	}

	@Override
	public void close() {
		getIsolate().checkThread();
		if (!released) {
			released = true;
			V8API._releaseContext(getContextPtr());
		}
	}

	public void closeIsolateIfLastContext(boolean terminate) {
		List<Boolean> status = new ArrayList<>();
		getIsolate().doAllContexts(context -> status.add(context.isReleased()));

		if (!status.contains(false)) {
			if (terminate) {
				getIsolate().terminateExecution();
			}

			getIsolate().close();
		}
	}

	public void setException(Throwable t) {
		if (t instanceof V8ScriptException) {
			pendingException = (V8ScriptException) t;
		}
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


	void notifyReleaseHandlers() {
		for (V8Runnable handler : releaseHandlers) {
			handler.run(this);
		}
	}

	void notifyReferenceCreated(final V8Value object) {
		for (ReferenceHandler referenceHandler : referenceHandlers) {
			referenceHandler.v8HandleCreated(object);
		}
	}

	void notifyReferenceDisposed(final V8Value object) {
		for (ReferenceHandler referenceHandler : referenceHandlers) {
			referenceHandler.v8HandleDisposed(object);
		}
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

	/**
	 * Associates an arbitrary object with this runtime.
	 *
	 * @param key The key used to reference this object
	 * @param value The object to associate with this runtime
	 */
	public synchronized void setData(final String key, final Object value) {
		if (data == null) {
			data = new HashMap<String, Object>();
		}
		data.put(key, value);
	}

	/**
	 * Returns the data object associated with this runtime, null if no object
	 * has been associated.
	 *
	 * @param key The key used to reference this object
	 *
	 * @return The data object associated with this runtime, or null.
	 */
	public Object getData(final String key) {
		if (data == null) {
			return null;
		}
		return data.get(key);
	}

	public int executeIntegerScript(final String script) {
		return executeIntegerScript(script, null, 0);
	}

	/**
	 * Executes a JS Script on this runtime and returns the result as an integer.
	 * If the result is not an integer, then a V8ResultUndefinedException is thrown.
	 *
	 * @param script The script to execute.
	 * @param scriptName The name of the script
	 * @param lineNumber The line number that is considered to be the first line of
	 * the script. Typically 0, but could be set to another value for excepton purposes.
	 *
	 * @return The result of the script as an integer, or V8ResultUndefinedException if
	 * the result is not an integer.
	 */
	public int executeIntegerScript(final String script, final String scriptName, final int lineNumber) {
		getIsolate().checkThread();
		checkScript(script);
		int i = V8API.get()._executeIntegerScript(getContextPtr(), script, scriptName, lineNumber);
		checkPendingException();
		return i;
	}

	void createTwin(final V8Value value, final V8Value twin) {
		getIsolate().checkThread();
		createTwin(value.getHandle(), twin.getHandle());
	}

	/**
	 * Executes a JS Script on this runtime and returns the result as a double.
	 * If the result is not a double, then a V8ResultUndefinedException is thrown.
	 *
	 * @param script The script to execute.
	 *
	 * @return The result of the script as a double, or V8ResultUndefinedException if
	 * the result is not a double.
	 */
	public double executeDoubleScript(final String script) {
		return executeDoubleScript(script, null, 0);
	}

	/**
	 * Executes a JS Script on this runtime and returns the result as a double.
	 * If the result is not a double, then a V8ResultUndefinedException is thrown.
	 *
	 * @param script The script to execute.
	 * @param scriptName The name of the script
	 * @param lineNumber The line number that is considered to be the first line of
	 * the script. Typically 0, but could be set to another value for exception stack trace purposes.
	 *
	 * @return The result of the script as a double, or V8ResultUndefinedException if
	 * the result is not a double.
	 */
	public double executeDoubleScript(final String script, final String scriptName, final int lineNumber) {
		getIsolate().checkThread();
		checkScript(script);
		double d = V8API.get()._executeDoubleScript(getContextPtr(), script, scriptName, lineNumber);
		checkPendingException();
		return d;
	}

	/**
	 * Executes a JS Script on this runtime and returns the result as a String.
	 * If the result is not a String, then a V8ResultUndefinedException is thrown.
	 *
	 * @param script The script to execute.
	 *
	 * @return The result of the script as a String, or V8ResultUndefinedException if
	 * the result is not a String.
	 */
	public String executeStringScript(final String script) {
		return executeStringScript(script, null, 0);
	}

	/**
	 * Executes a JS Script on this runtime and returns the result as a String.
	 * If the result is not a String, then a V8ResultUndefinedException is thrown.
	 *
	 * @param script The script to execute.
	 * @param scriptName The name of the script
	 * @param lineNumber The line number that is considered to be the first line of
	 * the script. Typically 0, but could be set to another value for exception stack trace purposes.
	 *
	 * @return The result of the script as a String, or V8ResultUndefinedException if
	 * the result is not a String.
	 */
	public String executeStringScript(final String script, final String scriptName, final int lineNumber) {
		getIsolate().checkThread();
		checkScript(script);
		String s = V8API.get()._executeStringScript(getContextPtr(), script, scriptName, lineNumber);
		checkPendingException();
		return s;
	}

	/**
	 * Executes a JS Script on this runtime and returns the result as a boolean.
	 * If the result is not a boolean, then a V8ResultUndefinedException is thrown.
	 *
	 * @param script The script to execute.
	 *
	 * @return The result of the script as a boolean, or V8ResultUndefinedException if
	 * the result is not a boolean.
	 */
	public boolean executeBooleanScript(final String script) {
		return executeBooleanScript(script, null, 0);
	}

	/**
	 * Executes a JS Script on this runtime and returns the result as a boolean.
	 * If the result is not a boolean, then a V8ResultUndefinedException is thrown.
	 *
	 * @param script The script to execute.
	 * @param scriptName The name of the script
	 * @param lineNumber The line number that is considered to be the first line of
	 * the script. Typically 0, but could be set to another value for exception stack trace purposes.
	 *
	 * @return The result of the script as a boolean, or V8ResultUndefinedException if
	 * the result is not a boolean.
	 */
	public boolean executeBooleanScript(final String script, final String scriptName, final int lineNumber) {
		getIsolate().checkThread();
		checkScript(script);
		boolean b = V8API.get()._executeBooleanScript(getContextPtr(), script, scriptName, lineNumber);
		checkPendingException();
		return b;
	}

	/**
	 * Executes a JS Script on this runtime and returns the result as a V8Array.
	 * If the result is not a V8Array, then a V8ResultUndefinedException is thrown.
	 *
	 * @param script The script to execute.
	 *
	 * @return The result of the script as a V8Array, or V8ResultUndefinedException if
	 * the result is not a V8Array.
	 */
	public V8Array executeArrayScript(final String script) {
		return executeArrayScript(script, null, 0);
	}

	/**
	 * Executes a JS Script on this runtime and returns the result as a V8Array.
	 * If the result is not a V8Array, then a V8ResultUndefinedException is thrown.
	 *
	 * @param script The script to execute.
	 * @param scriptName The name of the script
	 * @param lineNumber The line number that is considered to be the first line of
	 * the script. Typically 0, but could be set to another value for exception stack trace purposes.
	 *
	 * @return The result of the script as a V8Array, or V8ResultUndefinedException if
	 * the result is not a V8Array.
	 */
	public V8Array executeArrayScript(final String script, final String scriptName, final int lineNumber) {
		getIsolate().checkThread();
		Object result = executeScript(script, scriptName, lineNumber);
		if (result instanceof V8Array) {
			return (V8Array) result;
		}
		throw new V8ResultUndefined();
	}

	/**
	 * Executes a JS Script on this runtime and returns the result as a Java Object.
	 * Primitives will be boxed.
	 *
	 * @param script The script to execute.
	 *
	 * @return The result of the script as a Java Object.
	 */
	public Object executeScript(final String script) {
		return executeScript(script, null, 0);
	}

	/**
	 * Executes a JS Script on this runtime and returns the result as a Java Object.
	 * Primitives will be boxed.
	 *
	 * @param script The script to execute.
	 * @param scriptName The name of the script
	 * @param lineNumber The line number that is considered to be the first line of
	 * the script. Typically 0, but could be set to another value for exception stack trace purposes.
	 *
	 * @return The result of the script as a Java Object.
	 */
	public Object executeScript(final String script, final String scriptName, final int lineNumber) {
		getIsolate().checkThread();
		checkScript(script);
		return executeScript(V8API.UNKNOWN, script, scriptName, lineNumber);
	}

	/**
	 * Executes a JS Script on this runtime and returns the result as a V8Object.
	 * If the result is not a V8Object, then a V8ResultUndefinedException is thrown.
	 *
	 * @param script The script to execute.
	 *
	 * @return The result of the script as a V8Object, or V8ResultUndefinedException if
	 * the result is not a V8Object.
	 */
	public V8Object executeObjectScript(final String script) {
		return executeObjectScript(script, null, 0);
	}

	/**
	 * Executes a JS Script on this runtime and returns the result as a V8Object.
	 * If the result is not a V8Object, then a V8ResultUndefinedException is thrown.
	 *
	 * @param script The script to execute.
	 * @param scriptName The name of the script
	 * @param lineNumber The line number that is considered to be the first line of
	 * the script. Typically 0, but could be set to another value for exception stack trace purposes.
	 *
	 * @return The result of the script as a V8Object, or V8ResultUndefinedException if
	 * the result is not a V8Object.
	 */
	public V8Object executeObjectScript(final String script, final String scriptName, final int lineNumber) {
		getIsolate().checkThread();
		Object result = this.executeScript(script, scriptName, lineNumber);
		if (result instanceof V8Object) {
			return (V8Object) result;
		}
		throw new V8ResultUndefined();
	}

	/**
	 * Executes a JS Script on this runtime.
	 *
	 * @param script The script to execute.
	 */
	public void executeVoidScript(final String script) {
		executeVoidScript(script, null, 0);
	}

	/**
	 * Executes a JS Script on this runtime.
	 *
	 * @param script The script to execute.
	 * @param scriptName The name of the script
	 * @param lineNumber The line number that is considered to be the first line of
	 * the script. Typically 0, but could be set to another value for exception stack trace purposes.
	 */
	public void executeVoidScript(final String script, final String scriptName, final int lineNumber) {
		getIsolate().checkThread();
		checkScript(script);
		V8API.get()._executeVoidScript(getContextPtr(), script, scriptName, lineNumber);
		checkPendingException();
	}

	void registerCallback(final Object object, final Method method, final long objectHandle, final String jsFunctionName, final boolean includeReceiver) {
		MethodDescriptor methodDescriptor = new MethodDescriptor();
		methodDescriptor.object = object;
		methodDescriptor.method = method;
		methodDescriptor.includeReceiver = includeReceiver;
		long methodID = registerJavaMethod(objectHandle, jsFunctionName, isVoidMethod(method));
		functionRegistry.put(methodID, methodDescriptor);
	}

	void registerVoidCallback(final JavaVoidCallback callback, final long objectHandle, final String jsFunctionName) {
		MethodDescriptor methodDescriptor = new MethodDescriptor();
		methodDescriptor.voidCallback = callback;
		long methodID = registerJavaMethod(objectHandle, jsFunctionName, true);
		functionRegistry.put(methodID, methodDescriptor);
	}

	void registerCallback(final JavaCallback callback, final long objectHandle, final String jsFunctionName) {
		long methodID = registerJavaMethod(objectHandle, jsFunctionName, false);
		createAndRegisterMethodDescriptor(callback, methodID);
	}

	void createAndRegisterMethodDescriptor(final JavaCallback callback, final long methodID) {
		MethodDescriptor methodDescriptor = new MethodDescriptor();
		methodDescriptor.callback = callback;
		functionRegistry.put(methodID, methodDescriptor);
	}

	void releaseNativeMethodDescriptors() {
		Set<Long> nativeMethodDescriptors = functionRegistry.keySet();
		for (Long nativeMethodDescriptor : nativeMethodDescriptors) {
			releaseMethodDescriptor(nativeMethodDescriptor);
		}
	}

	private boolean isVoidMethod(final Method method) {
		Class<?> returnType = method.getReturnType();
		if (returnType.equals(Void.TYPE)) {
			return true;
		}
		return false;
	}

	private Object getDefaultValue(final Class<?> type) {
		if (type.equals(V8Object.class)) {
			return new V8Object.Undefined();
		} else if (type.equals(V8Array.class)) {
			return new V8Array.Undefined();
		}
		return invalid;
	}

	void disposeMethodID(final long methodID) {
		functionRegistry.remove(methodID);
	}

	void weakReferenceAdded(final long objectID, V8Value value) {
		v8WeakReferences.put(objectID, value);
	}

	void weakReferenceRemoved(final long objectID) {
		v8WeakReferences.remove(objectID);
	}

	void weakReferenceReleased(final long objectID) {
		V8Value v8Value = v8WeakReferences.get(objectID);
		if (v8Value != null) {
			weakReferenceRemoved(objectID);
			try {
				v8Value.close();
			} catch (Exception e) {
				// Swallow these exceptions. The V8 GC is running, and
				// if we return to V8 with Java exception on our stack,
				// we will be in a world of hurt.
			}
		}
	}

	long objectReferenceCount() {
		return objectReferences - v8WeakReferences.size();
	}

	Object callObjectJavaMethod(final long methodID, final V8Object receiver, final V8Array parameters) throws Throwable {
		MethodDescriptor methodDescriptor = functionRegistry.get(methodID);
		if (methodDescriptor.callback != null) {
			return checkResult(methodDescriptor.callback.invoke(receiver, parameters));
		}
		boolean hasVarArgs = methodDescriptor.method.isVarArgs();
		Object[] args = getArgs(receiver, methodDescriptor, parameters, hasVarArgs);
		checkArgs(args);
		try {
			Object result = methodDescriptor.method.invoke(methodDescriptor.object, args);
			return checkResult(result);
		} catch (InvocationTargetException e) {
			throw e.getTargetException();
		} catch (IllegalAccessException | IllegalArgumentException e) {
			throw e;
		} finally {
			releaseArguments(args, hasVarArgs);
		}
	}

	static void checkScript(final String script) {
		if (script == null) {
			throw new NullPointerException("Script is null");
		}
	}

	private Object checkResult(final Object result) {
		if (result == null) {
			return result;
		}
		if (result instanceof Float) {
			return ((Float) result).doubleValue();
		}
		if ((result instanceof Integer) || (result instanceof Double) || (result instanceof Boolean)
				|| (result instanceof String)) {
			return result;
		}
		if (result instanceof V8Value) {
			if (((V8Value) result).isReleased()) {
				throw new V8RuntimeException("V8Value already released");
			}
			return result;
		}
		throw new V8RuntimeException("Unknown return type: " + result.getClass());
	}

	void checkPendingException() {
		if (pendingException != null) {
			if (pendingException.getCause() != null && (pendingException.getCause() instanceof V8ScriptException)) {
				pendingException = (V8ScriptException) pendingException.getCause();
			}

			throw pendingException;
		}
	}

	protected void callVoidJavaMethod(final long methodID, final V8Object receiver, final V8Array parameters) throws Throwable {
		MethodDescriptor methodDescriptor = functionRegistry.get(methodID);
		if (methodDescriptor.voidCallback != null) {
			methodDescriptor.voidCallback.invoke(receiver, parameters);
			return;
		}
		boolean hasVarArgs = methodDescriptor.method.isVarArgs();
		Object[] args = getArgs(receiver, methodDescriptor, parameters, hasVarArgs);
		checkArgs(args);
		try {
			methodDescriptor.method.invoke(methodDescriptor.object, args);
		} catch (InvocationTargetException e) {
			throw e.getTargetException();
		} catch (IllegalAccessException | IllegalArgumentException e) {
			throw e;
		} finally {
			releaseArguments(args, hasVarArgs);
			checkPendingException();
		}
	}

	private void checkArgs(final Object[] args) {
		for (Object argument : args) {
			if (argument == invalid) {
				throw new IllegalArgumentException("argument type mismatch");
			}
		}
	}

	private void releaseArguments(final Object[] args, final boolean hasVarArgs) {
		if (hasVarArgs && ((args.length > 0) && (args[args.length - 1] instanceof Object[]))) {
			Object[] varArgs = (Object[]) args[args.length - 1];
			for (Object object : varArgs) {
				if (object instanceof V8Value) {
					((V8Value) object).close();
				}
			}
		}
		for (Object arg : args) {
			if (arg instanceof V8Value) {
				((V8Value) arg).close();
			}
		}
	}

	private Object[] getArgs(final V8Object receiver, final MethodDescriptor methodDescriptor, final V8Array parameters, final boolean hasVarArgs) {
		int numberOfParameters = methodDescriptor.method.getParameterTypes().length;
		int varArgIndex = hasVarArgs ? numberOfParameters - 1 : numberOfParameters;
		Object[] args = setDefaultValues(new Object[numberOfParameters], methodDescriptor.method.getParameterTypes(), receiver, methodDescriptor.includeReceiver);
		List<Object> varArgs = new ArrayList<Object>();
		populateParamters(parameters, varArgIndex, args, varArgs, methodDescriptor.includeReceiver);
		if (hasVarArgs) {
			Object varArgContainer = getVarArgContainer(methodDescriptor.method.getParameterTypes(), varArgs.size());
			System.arraycopy(varArgs.toArray(), 0, varArgContainer, 0, varArgs.size());
			args[varArgIndex] = varArgContainer;
		}
		return args;
	}

	private Object getVarArgContainer(final Class<?>[] parameterTypes, final int size) {
		Class<?> clazz = parameterTypes[parameterTypes.length - 1];
		if (clazz.isArray()) {
			clazz = clazz.getComponentType();
		}
		Object result = java.lang.reflect.Array.newInstance(clazz, size);
		return result;
	}

	private void populateParamters(final V8Array parameters, final int varArgIndex, final Object[] args, final List<Object> varArgs, final boolean includeReceiver) {
		int start = 0;
		if (includeReceiver) {
			start = 1;
		}
		for (int i = start; i < (parameters.length() + start); i++) {
			if (i >= varArgIndex) {
				varArgs.add(getArrayItem(parameters, i - start));
			} else {
				args[i] = getArrayItem(parameters, i - start);
			}
		}
	}

	private Object[] setDefaultValues(final Object[] parameters, final Class<?>[] parameterTypes, final V8Object receiver, final boolean includeReceiver) {
		int start = 0;
		if (includeReceiver) {
			start = 1;
			parameters[0] = receiver;
		}
		for (int i = start; i < parameters.length; i++) {
			parameters[i] = getDefaultValue(parameterTypes[i]);
		}
		return parameters;
	}

	private Object getArrayItem(final V8Array array, final int index) {
		try {
			int type = array.getType(index);
			switch (type) {
				case V8API.INTEGER:
					return array.getInteger(index);
				case V8API.DOUBLE:
					return array.getDouble(index);
				case V8API.BOOLEAN:
					return array.getBoolean(index);
				case V8API.STRING:
					return array.getString(index);
				case V8API.V8_ARRAY:
				case V8API.V8_TYPED_ARRAY:
					return array.getArray(index);
				case V8API.V8_OBJECT:
				case V8API.V8_FUNCTION:
					return array.getObject(index);
				case V8API.V8_ARRAY_BUFFER:
					return array.get(index);
				case V8API.UNDEFINED:
					return V8Isolate.getUndefined();
			}
		} catch (V8ResultUndefined e) {
			// do nothing
		}
		return null;
	}

	long getGlobalObject() {
		return V8API.get()._getGlobalObject(getContextPtr());
	}

	void startNodeJS(String fileName) {
		V8API._startNodeJS(getContextPtr(), fileName);
	}

	boolean pumpMessageLoop() {
		return V8API._pumpMessageLoop(getContextPtr());
	}

	boolean isRunning() {
		return V8API._isRunning(getContextPtr());
	}

	long initNewV8Object() {
		return V8API.get()._initNewV8Object(getContextPtr());
	}

	long initEmptyContainer() {
		return V8API.get()._initEmptyContainer(getContextPtr());
	}

	void createTwin(final long objectHandle, final long twinHandle) {
		V8API.get()._createTwin(getContextPtr(), objectHandle, twinHandle);
	}

	Object executeScript(final int expectedType, final String script, final String scriptName, final int lineNumber) {
		Object o = V8API.get()._executeScript(getContextPtr(), expectedType, script, scriptName, lineNumber);
		checkPendingException();
		return o;
	}

	void setWeak(final long objectHandle) {
		V8API.get()._setWeak(getContextPtr(), objectHandle);
	}

	void clearWeak(final long objectHandle) {
		V8API.get()._clearWeak(getContextPtr(), objectHandle);
	}

	boolean isWeak(final long objectHandle) {
		return V8API.get()._isWeak(getContextPtr(), objectHandle);
	}

	void release(final long objectHandle) {
		V8API.get()._release(getContextPtr(), objectHandle);
	}

	boolean contains(final long objectHandle, final String key) {
		return V8API.get()._contains(getContextPtr(), objectHandle, key);
	}

	String[] getKeys(final long objectHandle) {
		return V8API.get()._getKeys(getContextPtr(), objectHandle);
	}

	int getInteger(final long objectHandle, final String key) {
		return V8API.get()._getInteger(getContextPtr(), objectHandle, key);
	}

	boolean getBoolean(final long objectHandle, final String key) {
		return V8API.get()._getBoolean(getContextPtr(), objectHandle, key);
	}

	double getDouble(final long objectHandle, final String key) {
		return V8API.get()._getDouble(getContextPtr(), objectHandle, key);
	}

	String getString(final long objectHandle, final String key) {
		return V8API.get()._getString(getContextPtr(), objectHandle, key);
	}

	Object get(final int expectedType, final long objectHandle, final String key) {
		return V8API.get()._get(getContextPtr(), expectedType, objectHandle, key);
	}

	int executeIntegerFunction(final long objectHandle, final String name, final long parametersHandle) {
		int i = V8API.get()._executeIntegerFunction(getContextPtr(), objectHandle, name, parametersHandle);
		checkPendingException();
		return i;
	}

	double executeDoubleFunction(final long objectHandle, final String name, final long parametersHandle) {
		double d = V8API.get()._executeDoubleFunction(getContextPtr(), objectHandle, name, parametersHandle);
		checkPendingException();
		return d;
	}

	String executeStringFunction(final long handle, final String name, final long parametersHandle) {
		String s = V8API.get()._executeStringFunction(getContextPtr(), handle, name, parametersHandle);
		checkPendingException();
		return s;
	}

	boolean executeBooleanFunction(final long handle, final String name, final long parametersHandle) {
		boolean b = V8API.get()._executeBooleanFunction(getContextPtr(), handle, name, parametersHandle);
		checkPendingException();
		return b;
	}

	Object executeFunction(final int expectedType, final long objectHandle, final String name, final long parametersHandle) {
		Object o = V8API.get()._executeFunction(getContextPtr(), expectedType, objectHandle, name, parametersHandle);
		checkPendingException();
		return o;
	}

	Object executeFunction(final long receiverHandle, final long functionHandle, final long parametersHandle) {
		Object o = V8API.get()._executeFunction(getContextPtr(), receiverHandle, functionHandle, parametersHandle);
		checkPendingException();
		return o;
	}

	void executeVoidFunction(final long objectHandle, final String name, final long parametersHandle) {
		V8API.get()._executeVoidFunction(getContextPtr(), objectHandle, name, parametersHandle);
		checkPendingException();
	}

	boolean equals(final long objectHandle, final long that) {
		return V8API.get()._equals(getContextPtr(), objectHandle, that);
	}

	String toString(final long objectHandle) {
		return V8API.get()._toString(getContextPtr(), objectHandle);
	}

	boolean strictEquals(final long objectHandle, final long that) {
		return V8API.get()._strictEquals(getContextPtr(), objectHandle, that);
	}

	boolean sameValue(final long objectHandle, final long that) {
		return V8API.get()._sameValue(getContextPtr(), objectHandle, that);
	}

	int identityHash(final long objectHandle) {
		return V8API.get()._identityHash(getContextPtr(), objectHandle);
	}

	void add(final long objectHandle, final String key, final int value) {
		V8API.get()._add(getContextPtr(), objectHandle, key, value);
	}

	void addObject(final long objectHandle, final String key, final long value) {
		V8API.get()._addObject(getContextPtr(), objectHandle, key, value);
	}

	void add(final long objectHandle, final String key, final boolean value) {
		V8API.get()._add(getContextPtr(), objectHandle, key, value);
	}

	void add(final long objectHandle, final String key, final double value) {
		V8API.get()._add(getContextPtr(), objectHandle, key, value);
	}

	void add(final long objectHandle, final String key, final String value) {
		V8API.get()._add(getContextPtr(), objectHandle, key, value);
	}

	void addUndefined(final long objectHandle, final String key) {
		V8API.get()._addUndefined(getContextPtr(), objectHandle, key);
	}

	void addNull(final long objectHandle, final String key) {
		V8API.get()._addNull(getContextPtr(), objectHandle, key);
	}

	long registerJavaMethod(final long objectHandle, final String functionName, final boolean voidMethod) {
		return V8API.get()._registerJavaMethod(getContextPtr(), objectHandle, functionName, voidMethod);
	}

	long initNewV8ArrayBuffer(final ByteBuffer buffer, final int capacity) {
		return V8API.get()._initNewV8ArrayBuffer(getContextPtr(), buffer, capacity);
	}

	long initNewV8ArrayBuffer(final int capacity) {
		return V8API.get()._initNewV8ArrayBuffer(getContextPtr(), capacity);
	}

	public long initNewV8Int32Array(final long bufferHandle, final int offset, final int size) {
		return V8API.get()._initNewV8Int32Array(getContextPtr(), bufferHandle, offset, size);
	}

	public long initNewV8Float32Array(final long bufferHandle, final int offset, final int size) {
		return V8API.get()._initNewV8Float32Array(getContextPtr(), bufferHandle, offset, size);
	}

	public long initNewV8Float64Array(final long bufferHandle, final int offset, final int size) {
		return V8API.get()._initNewV8Float64Array(getContextPtr(), bufferHandle, offset, size);
	}

	public long initNewV8UInt32Array(final long bufferHandle, final int offset, final int size) {
		return V8API.get()._initNewV8UInt32Array(getContextPtr(), bufferHandle, offset, size);
	}

	public long initNewV8UInt16Array(final long bufferHandle, final int offset, final int size) {
		return V8API.get()._initNewV8UInt16Array(getContextPtr(), bufferHandle, offset, size);
	}

	public long initNewV8Int16Array(final long bufferHandle, final int offset, final int size) {
		return V8API.get()._initNewV8Int16Array(getContextPtr(), bufferHandle, offset, size);
	}

	public long initNewV8UInt8Array(final long bufferHandle, final int offset, final int size) {
		return V8API.get()._initNewV8UInt8Array(getContextPtr(), bufferHandle, offset, size);
	}

	public long initNewV8Int8Array(final long bufferHandle, final int offset, final int size) {
		return V8API.get()._initNewV8Int8Array(getContextPtr(), bufferHandle, offset, size);
	}

	public long initNewV8UInt8ClampedArray(final long bufferHandle, final int offset, final int size) {
		return V8API.get()._initNewV8UInt8ClampedArray(getContextPtr(), bufferHandle, offset, size);
	}

	ByteBuffer createV8ArrayBufferBackingStore(final long objectHandle, final int capacity) {
		return V8API.get()._createV8ArrayBufferBackingStore(getContextPtr(), objectHandle, capacity);
	}

	long initNewV8Array() {
		return V8API.get()._initNewV8Array(getContextPtr());
	}

	long[] initNewV8Function() {
		return V8API.get()._initNewV8Function(getContextPtr());
	}

	int arrayGetSize(final long arrayHandle) {
		return V8API.get()._arrayGetSize(getContextPtr(), arrayHandle);
	}

	int arrayGetInteger(final long arrayHandle, final int index) {
		return V8API.get()._arrayGetInteger(getContextPtr(), arrayHandle, index);
	}

	boolean arrayGetBoolean(final long arrayHandle, final int index) {
		return V8API.get()._arrayGetBoolean(getContextPtr(), arrayHandle, index);
	}

	byte arrayGetByte(final long arrayHandle, final int index) {
		return V8API.get()._arrayGetByte(getContextPtr(), arrayHandle, index);
	}

	double arrayGetDouble(final long arrayHandle, final int index) {
		return V8API.get()._arrayGetDouble(getContextPtr(), arrayHandle, index);
	}

	String arrayGetString(final long arrayHandle, final int index) {
		return V8API.get()._arrayGetString(getContextPtr(), arrayHandle, index);
	}

	Object arrayGet(final int expectedType, final long arrayHandle, final int index) {
		return V8API.get()._arrayGet(getContextPtr(), expectedType, arrayHandle, index);
	}

	void addArrayIntItem(final long arrayHandle, final int value) {
		V8API.get()._addArrayIntItem(getContextPtr(), arrayHandle, value);
	}

	void addArrayBooleanItem(final long arrayHandle, final boolean value) {
		V8API.get()._addArrayBooleanItem(getContextPtr(), arrayHandle, value);
	}

	void addArrayDoubleItem(final long arrayHandle, final double value) {
		V8API.get()._addArrayDoubleItem(getContextPtr(), arrayHandle, value);
	}

	void addArrayStringItem(final long arrayHandle, final String value) {
		V8API.get()._addArrayStringItem(getContextPtr(), arrayHandle, value);
	}

	void addArrayObjectItem(final long arrayHandle, final long value) {
		V8API.get()._addArrayObjectItem(getContextPtr(), arrayHandle, value);
	}

	void addArrayUndefinedItem(final long arrayHandle) {
		V8API.get()._addArrayUndefinedItem(getContextPtr(), arrayHandle);
	}

	void addArrayNullItem(final long arrayHandle) {
		V8API.get()._addArrayNullItem(getContextPtr(), arrayHandle);
	}

	String getConstructorName(final long objectHandle) {
		return V8API.get()._getConstructorName(getContextPtr(), objectHandle);
	}

	int getType(final long objectHandle) {
		return V8API.get()._getType(getContextPtr(), objectHandle);
	}

	int getType(final long objectHandle, final String key) {
		return V8API.get()._getType(getContextPtr(), objectHandle, key);
	}

	int getType(final long objectHandle, final int index) {
		return V8API.get()._getType(getContextPtr(), objectHandle, index);
	}

	int getArrayType(final long objectHandle) {
		return V8API.get()._getArrayType(getContextPtr(), objectHandle);
	}

	int getType(final long objectHandle, final int index, final int length) {
		return V8API.get()._getType(getContextPtr(), objectHandle, index, length);
	}

	void setPrototype(final long objectHandle, final long prototypeHandle) {
		V8API.get()._setPrototype(getContextPtr(), objectHandle, prototypeHandle);
	}

	int[] arrayGetIntegers(final long objectHandle, final int index, final int length) {
		return V8API.get()._arrayGetIntegers(getContextPtr(), objectHandle, index, length);
	}

	double[] arrayGetDoubles(final long objectHandle, final int index, final int length) {
		return V8API.get()._arrayGetDoubles(getContextPtr(), objectHandle, index, length);
	}

	boolean[] arrayGetBooleans(final long objectHandle, final int index, final int length) {
		return V8API.get()._arrayGetBooleans(getContextPtr(), objectHandle, index, length);
	}

	byte[] arrayGetBytes(final long objectHandle, final int index, final int length) {
		return V8API.get()._arrayGetBytes(getContextPtr(), objectHandle, index, length);
	}

	String[] arrayGetStrings(final long objectHandle, final int index, final int length) {
		return V8API.get()._arrayGetStrings(getContextPtr(), objectHandle, index, length);
	}

	int arrayGetIntegers(final long objectHandle, final int index, final int length, final int[] resultArray) {
		return V8API.get()._arrayGetIntegers(getContextPtr(), objectHandle, index, length, resultArray);
	}

	int arrayGetDoubles(final long objectHandle, final int index, final int length, final double[] resultArray) {
		return V8API.get()._arrayGetDoubles(getContextPtr(), objectHandle, index, length, resultArray);
	}

	int arrayGetBooleans(final long objectHandle, final int index, final int length, final boolean[] resultArray) {
		return V8API.get()._arrayGetBooleans(getContextPtr(), objectHandle, index, length, resultArray);
	}

	int arrayGetBytes(final long objectHandle, final int index, final int length, final byte[] resultArray) {
		return V8API.get()._arrayGetBytes(getContextPtr(), objectHandle, index, length, resultArray);
	}

	int arrayGetStrings(final long objectHandle, final int index, final int length, final String[] resultArray) {
		return V8API.get()._arrayGetStrings(getContextPtr(), objectHandle, index, length, resultArray);
	}

	void releaseMethodDescriptor(final long methodDescriptor) {
		V8API.get()._releaseMethodDescriptor(getContextPtr(), methodDescriptor);
	}

	void createNodeRuntime(final String fileName) {
		startNodeJS(fileName);
	}
}
