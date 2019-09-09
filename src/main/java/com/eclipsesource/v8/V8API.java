package com.eclipsesource.v8;

import java.nio.ByteBuffer;

public class V8API {
	public static final int NULL                         = 0;
	public static final int UNKNOWN                      = 0;
	public static final int INTEGER                      = 1;
	public static final int INT_32_ARRAY                 = 1;
	public static final int DOUBLE                       = 2;
	public static final int FLOAT_64_ARRAY               = 2;
	public static final int BOOLEAN                      = 3;
	public static final int STRING                       = 4;
	public static final int V8_ARRAY                     = 5;
	public static final int V8_OBJECT                    = 6;
	public static final int V8_FUNCTION                  = 7;
	public static final int V8_TYPED_ARRAY               = 8;
	public static final int BYTE                         = 9;
	public static final int INT_8_ARRAY                  = 9;
	public static final int V8_ARRAY_BUFFER              = 10;
	public static final int UNSIGNED_INT_8_ARRAY         = 11;
	public static final int UNSIGNED_INT_8_CLAMPED_ARRAY = 12;
	public static final int INT_16_ARRAY                 = 13;
	public static final int UNSIGNED_INT_16_ARRAY        = 14;
	public static final int UNSIGNED_INT_32_ARRAY        = 15;
	public static final int FLOAT_32_ARRAY               = 16;
	public static final int UNDEFINED                    = 99;

	private static V8API INSTANCE = new V8API();
	
	static V8API get() {
		return INSTANCE;
	}
	
	private V8API() {}
	
	native void _acquireLock(long v8RuntimePtr);

	native void _releaseLock(long v8RuntimePtr);

	native void _lowMemoryNotification(long v8RuntimePtr);

	native void _releaseRuntime(long v8RuntimePtr);

	native long _createIsolate(V8Isolate v8Isolate);

	native long _createContext(V8Context ctx, long v8RuntimePtr, String globalAlias);

	native long _getBuildID();

	native long _initNewV8Object(long v8ContextPtr);

	native long _initEmptyContainer(long v8ContextPtr);

	native void _createTwin(long v8ContextPtr, long objectHandle, long twinHandle);

	native int _executeIntegerScript(long v8ContextPtr, String script, String scriptName, int lineNumber);

	native double _executeDoubleScript(long v8ContextPtr, String script, String scriptName, int lineNumber);

	native String _executeStringScript(long v8ContextPtr, String script, String scriptName, int lineNumber);

	native boolean _executeBooleanScript(long v8ContextPtr, String script, String scriptName, int lineNumber);

	native Object _executeScript(long v8ContextPtr, int expectedType, String script, String scriptName, int lineNumber);

	native void _executeVoidScript(long v8ContextPtr, String script, String scriptName, int lineNumber);

	native void _release(long v8ContextPtr, long objectHandle);

	native void _releaseMethodDescriptor(long v8ContextPtr, long methodDescriptor);

	native boolean _contains(long v8ContextPtr, long objectHandle, String key);

	native String[] _getKeys(long v8ContextPtr, long objectHandle);

	native int _getInteger(long v8ContextPtr, long objectHandle, String key);

	native boolean _getBoolean(long v8ContextPtr, long objectHandle, String key);

	native double _getDouble(long v8ContextPtr, long objectHandle, String key);

	native String _getString(long v8ContextPtr, long objectHandle, String key);

	native Object _get(long v8ContextPtr, int expectedType, long objectHandle, String key);

	native int _executeIntegerFunction(long v8ContextPtr, long objectHandle, String name, long parametersHandle);

	native double _executeDoubleFunction(long v8ContextPtr, long objectHandle, String name, long parametersHandle);

	native String _executeStringFunction(long v8ContextPtr, long handle, String name, long parametersHandle);

	native boolean _executeBooleanFunction(long v8ContextPtr, long handle, String name, long parametersHandle);

	native Object _executeFunction(long v8ContextPtr, int expectedType, long objectHandle, String name, long parametersHandle);

	native Object _executeFunction(long v8ContextPtr, long receiverHandle, long functionHandle, long parametersHandle);

	native void _executeVoidFunction(long v8ContextPtr, long objectHandle, String name, long parametersHandle);

	native boolean _equals(long v8ContextPtr, long objectHandle, long that);

	native String _toString(long v8ContextPtr, long ObjectHandle);

	native boolean _strictEquals(long v8ContextPtr, long objectHandle, long that);

	native boolean _sameValue(long v8ContextPtr, long objectHandle, long that);

	native int _identityHash(long v8ContextPtr, long objectHandle);

	native void _add(long v8ContextPtr, long objectHandle, String key, int value);

	native void _addObject(long v8ContextPtr, long objectHandle, String key, long value);

	native void _add(long v8ContextPtr, long objectHandle, String key, boolean value);

	native void _add(long v8ContextPtr, long objectHandle, String key, double value);

	native void _add(long v8ContextPtr, long objectHandle, String key, String value);

	native void _addUndefined(long v8ContextPtr, long objectHandle, String key);

	native void _addNull(long v8ContextPtr, long objectHandle, String key);

	native long _registerJavaMethod(long v8ContextPtr, long objectHandle, String functionName, boolean voidMethod);

	native long _initNewV8Array(long v8ContextPtr);

	native long[] _initNewV8Function(long v8ContextPtr);

	native int _arrayGetSize(long v8ContextPtr, long arrayHandle);

	native int _arrayGetInteger(long v8ContextPtr, long arrayHandle, int index);

	native boolean _arrayGetBoolean(long v8ContextPtr, long arrayHandle, int index);

	native byte _arrayGetByte(long v8ContextPtr, long arrayHandle, int index);

	native double _arrayGetDouble(long v8ContextPtr, long arrayHandle, int index);

	native String _arrayGetString(long v8ContextPtr, long arrayHandle, int index);

	native Object _arrayGet(long v8ContextPtr, int expectedType, long arrayHandle, int index);

	native void _addArrayIntItem(long v8ContextPtr, long arrayHandle, int value);

	native void _addArrayBooleanItem(long v8ContextPtr, long arrayHandle, boolean value);

	native void _addArrayDoubleItem(long v8ContextPtr, long arrayHandle, double value);

	native void _addArrayStringItem(long v8ContextPtr, long arrayHandle, String value);

	native void _addArrayObjectItem(long v8ContextPtr, long arrayHandle, long value);

	native void _addArrayUndefinedItem(long v8ContextPtr, long arrayHandle);

	native void _addArrayNullItem(long v8ContextPtr, long arrayHandle);

	native int _getType(long v8ContextPtr, long objectHandle, String key);

	native int _getType(long v8ContextPtr, long objectHandle, int index);

	native int _getArrayType(long v8ContextPtr, long objectHandle);

	native void _setPrototype(long v8ContextPtr, long objectHandle, long prototypeHandle);

	native String _getConstructorName(long v8ContextPtr, long objectHandle);

	native int _getType(long v8ContextPtr, long objectHandle);

	native int _getType(long v8ContextPtr, long objectHandle, int index, int length);

	native double[] _arrayGetDoubles(long v8ContextPtr, long objectHandle, int index, int length);

	native int[] _arrayGetIntegers(long v8ContextPtr, long objectHandle, int index, int length);

	native boolean[] _arrayGetBooleans(long v8ContextPtr, long objectHandle, int index, int length);

	native byte[] _arrayGetBytes(long v8ContextPtr, long objectHandle, int index, int length);

	native String[] _arrayGetStrings(long v8ContextPtr, long objectHandle, int index, int length);

	native int _arrayGetIntegers(long v8ContextPtr, long objectHandle, int index, int length, int[] resultArray);

	native int _arrayGetDoubles(long v8ContextPtr, long objectHandle, int index, int length, double[] resultArray);

	native int _arrayGetBooleans(long v8ContextPtr, long objectHandle, int index, int length, boolean[] resultArray);

	native int _arrayGetBytes(long v8ContextPtr, long objectHandle, int index, int length, byte[] resultArray);

	native int _arrayGetStrings(long v8ContextPtr, long objectHandle, int index, int length, String[] resultArray);

	native long _initNewV8ArrayBuffer(long v8ContextPtr, int capacity);

	native long _initNewV8ArrayBuffer(long v8ContextPtr, ByteBuffer buffer, int capacity);

	native long _initNewV8Int32Array(long v8ContextPtr, long bufferHandle, int offset, int size);

	native long _initNewV8UInt32Array(long v8ContextPtr, long bufferHandle, int offset, int size);

	native long _initNewV8Float32Array(long v8ContextPtr, long bufferHandle, int offset, int size);

	native long _initNewV8Float64Array(long v8ContextPtr, long bufferHandle, int offset, int size);

	native long _initNewV8Int16Array(long v8ContextPtr, long bufferHandle, int offset, int size);

	native long _initNewV8UInt16Array(long v8ContextPtr, long bufferHandle, int offset, int size);

	native long _initNewV8Int8Array(long v8ContextPtr, long bufferHandle, int offset, int size);

	native long _initNewV8UInt8Array(long v8ContextPtr, long bufferHandle, int offset, int size);

	native long _initNewV8UInt8ClampedArray(long v8ContextPtr, long bufferHandle, int offset, int size);

	native void _setWeak(long v8ContextPtr, long objectHandle);

	native void _clearWeak(long v8ContextPtr, long objectHandle);

	native boolean _isWeak(long v8ContextPtr, long objectHandle);

	native ByteBuffer _createV8ArrayBufferBackingStore(long v8ContextPtr, long objectHandle, int capacity);

	native void _terminateExecution(long v8ContextPtr);

	native long _getGlobalObject(long v8ContextPtr);


	native static String _getVersion();

	native static void _setFlags(String v8flags);

	native static boolean _isNodeCompatible();

	native static void _startNodeJS(long v8ContextPtr, String fileName);

	native static boolean _pumpMessageLoop(long v8ContextPtr);

	native static boolean _isRunning(long v8ContextPtr);
}
