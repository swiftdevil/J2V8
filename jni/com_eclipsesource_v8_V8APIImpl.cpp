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
#include <jni.h>
#include <libplatform/libplatform.h>
#include <iostream>
#include <v8.h>
#include <string.h>
#include <v8-debug.h>
#include <map>
#include <cstdlib>
#include "com_eclipsesource_v8_V8APIImpl.h"

#ifdef NODE_COMPATIBLE
  #include <deps/uv/include/uv.h>
  #include <node.h>
#endif

#define TAG "J2V8_V8APIImpl"

#pragma comment(lib, "userenv.lib")
#pragma comment(lib, "IPHLPAPI.lib")
#pragma comment(lib, "Ws2_32.lib")
#pragma comment(lib, "WINMM.lib")
#pragma comment( lib, "psapi.lib" )

using namespace std;
using namespace v8;

class MethodDescriptor {
public:
  jlong methodID;
  jlong v8ContextPtr;
};

class WeakReferenceDescriptor {
public:
  jlong v8ContextPtr;
  jlong objectHandle;
};

class V8Runtime {
public:
  Isolate* isolate;
  Locker* locker;
  jobject v8;
  jthrowable pendingException;

#ifdef NODE_COMPATIBLE
  node::Environment* nodeEnvironment;
  node::IsolateData* isolateData;
  uv_loop_t* uvLoop;
  bool running;
#endif

};

class V8Context {
public:
  jlong v8RuntimePtr;
  jobject v8Ctx;
  jobject exLsnr;
  Persistent<Context> context;
  Persistent<Object>* globalObject;

  V8Context(jlong runtimePtr) {
    v8RuntimePtr = runtimePtr;
  }

  V8Runtime* getRuntime() {
    return reinterpret_cast<V8Runtime*>(v8RuntimePtr);
  }
};

v8::Platform* v8Platform;

const char* ToCString(const String::Utf8Value& value) {
  return *value ? *value : "<string conversion failed>";
}

JavaVM* jvm = NULL;
jclass v8cls = NULL;
jclass v8ctx = NULL;
jclass v8ObjectCls = NULL;
jclass v8ArrayCls = NULL;
jclass v8TypedArrayCls = NULL;
jclass v8ArrayBufferCls = NULL;
jclass v8FunctionCls = NULL;
jclass undefinedV8ObjectCls = NULL;
jclass undefinedV8ArrayCls = NULL;
jclass v8ResultsUndefinedCls = NULL;
jclass v8ScriptCompilationCls = NULL;
jclass v8ScriptExecutionExceptionCls = NULL;
jclass v8ScriptExecutionExceptionListenerCls = NULL;
jclass v8RuntimeExceptionCls = NULL;
jclass throwableCls = NULL;
jclass stringCls = NULL;
jclass integerCls = NULL;
jclass doubleCls = NULL;
jclass booleanCls = NULL;
jclass errorCls = NULL;
jclass unsupportedOperationExceptionCls = NULL;
jmethodID v8ArrayInitMethodID = NULL;
jmethodID v8TypedArrayInitMethodID = NULL;
jmethodID v8ArrayBufferInitMethodID = NULL;
jmethodID v8ArrayGetHandleMethodID = NULL;
jmethodID v8CallVoidMethodID = NULL;
jmethodID v8ObjectReleaseMethodID = NULL;
jmethodID v8DisposeMethodID = NULL;
jmethodID v8WeakReferenceReleased = NULL;
jmethodID v8ArrayReleaseMethodID = NULL;
jmethodID v8ObjectIsUndefinedMethodID = NULL;
jmethodID v8ObjectGetHandleMethodID = NULL;
jmethodID throwableGetMessageMethodID = NULL;
jmethodID integerIntValueMethodID = NULL;
jmethodID booleanBoolValueMethodID = NULL;
jmethodID doubleDoubleValueMethodID = NULL;
jmethodID v8CallObjectJavaMethodMethodID = NULL;
jmethodID v8ScriptCompilationInitMethodID = NULL;
jmethodID v8ScriptExecutionExceptionInitMethodID = NULL;
jmethodID v8ScriptExecutionExceptionListenerCallMethodID = NULL;
jmethodID undefinedV8ArrayInitMethodID = NULL;
jmethodID undefinedV8ObjectInitMethodID = NULL;
jmethodID integerInitMethodID = NULL;
jmethodID doubleInitMethodID = NULL;
jmethodID booleanInitMethodID = NULL;
jmethodID v8FunctionInitMethodID = NULL;
jmethodID v8ObjectInitMethodID = NULL;
jmethodID v8RuntimeExceptionInitMethodID = NULL;

void throwParseException(JNIEnv *env, Isolate* isolate, TryCatch* tryCatch);
void throwExecutionException(JNIEnv *env, Isolate* isolate, TryCatch* tryCatch, jlong v8ContextPtr);
void throwError(JNIEnv *env, const char *message);
void throwV8RuntimeException(JNIEnv *env,  String::Value *message);
void throwResultUndefinedException(JNIEnv *env, const char *message);
Isolate* getIsolate(JNIEnv *env, jlong handle);
int getType(Handle<Value> v8Value);
jobject getResult(JNIEnv *env, jobject &v8Ctx, jlong v8ContextPtr, Handle<Value> &result, jint expectedType);

#define SETUP(env, v8ContextPtr, errorReturnResult) getIsolate(env, v8ContextPtr);\
    if ( isolate == NULL ) {\
      return errorReturnResult;\
                                }\
    V8Context* v8Context = reinterpret_cast<V8Context*>(v8ContextPtr);\
    V8Runtime* runtime = v8Context->getRuntime();\
    Isolate::Scope isolateScope(isolate);\
    HandleScope handle_scope(isolate);\
    Local<Context> context = Local<Context>::New(isolate, v8Context->context);\
    Context::Scope context_scope(context);

#define ASSERT_IS_NUMBER(v8Value) \
    if (v8Value.IsEmpty() || v8Value->IsUndefined() || !v8Value->IsNumber()) {\
      throwResultUndefinedException(env, "");\
      return 0;\
                                }
#define ASSERT_IS_STRING(v8Value)\
    if (v8Value.IsEmpty() || v8Value->IsUndefined() || !v8Value->IsString()) {\
      if ( v8Value->IsNull() ) {\
        return 0;\
      }\
      throwResultUndefinedException(env, "");\
      return 0;\
                                }
#define ASSERT_IS_BOOLEAN(v8Value)\
    if (v8Value.IsEmpty() || v8Value->IsUndefined() || !v8Value->IsBoolean() ) {\
      throwResultUndefinedException(env, "");\
      return 0;\
                                }
void release(JNIEnv* env, jobject object) {
  env->CallVoidMethod(object, v8ObjectReleaseMethodID);
}

void releaseArray(JNIEnv* env, jobject object) {
  env->CallVoidMethod(object, v8ArrayReleaseMethodID);
}

int isUndefined(JNIEnv* env, jobject object) {
  return env->CallBooleanMethod(object, v8ObjectIsUndefinedMethodID);
}

jlong getHandle(JNIEnv* env, jobject object) {
  return env->CallLongMethod(object, v8ObjectGetHandleMethodID);
}

JNIEXPORT jstring JNICALL Java_com_eclipsesource_v8_V8API__1getVersion (JNIEnv *env, jclass) {
  const char* utfString = v8::V8::GetVersion();
  return env->NewStringUTF(utfString);
}


JNIEXPORT jstring JNICALL Java_com_eclipsesource_v8_V8API__1getConstructorName
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Object> object = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  String::Value unicodeString(object->GetConstructorName());
  return env->NewString(*unicodeString, unicodeString.length());
}

Local<String> createV8String(JNIEnv *env, Isolate *isolate, jstring &string) {
  const uint16_t* unicodeString = env->GetStringChars(string, NULL);
  int length = env->GetStringLength(string);
  Local<String> result = String::NewFromTwoByte(isolate, unicodeString, String::NewStringType::kNormalString, length);
  env->ReleaseStringChars(string, unicodeString);
  return result;
}

Handle<Value> getValueWithKey(JNIEnv* env, Isolate* isolate, jlong &objectHandle, jstring &key) {
  Handle<Object> object = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  Local<String> v8Key = createV8String(env, isolate, key);
  return object->Get(v8Key);
}

void addValueWithKey(JNIEnv* env, Isolate* isolate, jlong &objectHandle, jstring &key, Handle<Value> value) {
  Handle<Object> object = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  const uint16_t* unicodeString_key = env->GetStringChars(key, NULL);
  int length = env->GetStringLength(key);
  Local<String> v8Key = String::NewFromTwoByte(isolate, unicodeString_key, String::NewStringType::kNormalString, length);
  object->Set(v8Key, value);
  env->ReleaseStringChars(key, unicodeString_key);
}

void getJNIEnv(JNIEnv*& env) {
  int getEnvStat = jvm->GetEnv((void **)&env, JNI_VERSION_1_6);
  if (getEnvStat == JNI_EDETACHED) {
#ifdef __ANDROID_API__
    if (jvm->AttachCurrentThread(&env, NULL) != 0) {
#else
    if (jvm->AttachCurrentThread((void **)&env, NULL) != 0) {
#endif
      std::cout << "Failed to attach" << std::endl;
    }
  }
  else if (getEnvStat == JNI_OK) {
  }
  else if (getEnvStat == JNI_EVERSION) {
    std::cout << "GetEnv: version not supported" << std::endl;
  }
}

static void jsWindowObjectAccessor(Local<String> property,
  const PropertyCallbackInfo<Value>& info) {
  info.GetReturnValue().Set(info.GetIsolate()->GetCurrentContext()->Global());
}

class ShellArrayBufferAllocator : public v8::ArrayBuffer::Allocator {
 public:
  virtual void* Allocate(size_t length) {
    void* data = AllocateUninitialized(length);
    return data == NULL ? data : memset(data, 0, length);
  }
  virtual void* AllocateUninitialized(size_t length) { return malloc(length); }
  virtual void Free(void* data, size_t) { free(data); }
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    jint onLoad_err = -1;
    if ( vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK ) {
        return onLoad_err;
    }
    if (env == NULL) {
        return onLoad_err;
    }
    v8::V8::InitializeICU();
    v8Platform = v8::platform::CreateDefaultPlatform();
    v8::V8::InitializePlatform(v8Platform);
    v8::V8::Initialize();

    // on first creation, store the JVM and a handle to J2V8 classes
    jvm = vm;
    v8cls = (jclass)env->NewGlobalRef((env)->FindClass("com/eclipsesource/v8/V8Isolate"));
    v8ctx = (jclass)env->NewGlobalRef((env)->FindClass("com/eclipsesource/v8/V8Context"));
    v8ObjectCls = (jclass)env->NewGlobalRef((env)->FindClass("com/eclipsesource/v8/V8Object"));
    v8ArrayCls = (jclass)env->NewGlobalRef((env)->FindClass("com/eclipsesource/v8/V8Array"));
    v8TypedArrayCls = (jclass)env->NewGlobalRef((env)->FindClass("com/eclipsesource/v8/V8TypedArray"));
    v8ArrayBufferCls = (jclass)env->NewGlobalRef((env)->FindClass("com/eclipsesource/v8/V8ArrayBuffer"));
    v8FunctionCls = (jclass)env->NewGlobalRef((env)->FindClass("com/eclipsesource/v8/V8Function"));
    undefinedV8ObjectCls = (jclass)env->NewGlobalRef((env)->FindClass("com/eclipsesource/v8/V8Object$Undefined"));
    undefinedV8ArrayCls = (jclass)env->NewGlobalRef((env)->FindClass("com/eclipsesource/v8/V8Array$Undefined"));
    stringCls = (jclass)env->NewGlobalRef((env)->FindClass("java/lang/String"));
    integerCls = (jclass)env->NewGlobalRef((env)->FindClass("java/lang/Integer"));
    doubleCls = (jclass)env->NewGlobalRef((env)->FindClass("java/lang/Double"));
    booleanCls = (jclass)env->NewGlobalRef((env)->FindClass("java/lang/Boolean"));
    throwableCls = (jclass)env->NewGlobalRef((env)->FindClass("java/lang/Throwable"));
    v8ResultsUndefinedCls = (jclass)env->NewGlobalRef((env)->FindClass("com/eclipsesource/v8/V8ResultUndefined"));
    v8ScriptCompilationCls = (jclass)env->NewGlobalRef((env)->FindClass("com/eclipsesource/v8/V8ScriptCompilationException"));
    v8ScriptExecutionExceptionCls = (jclass)env->NewGlobalRef((env)->FindClass("com/eclipsesource/v8/V8ScriptExecutionException"));
    v8ScriptExecutionExceptionListenerCls = (jclass)env->NewGlobalRef((env)->FindClass("com/eclipsesource/v8/V8ScriptExecutionExceptionListener"));
    v8RuntimeExceptionCls = (jclass)env->NewGlobalRef((env)->FindClass("com/eclipsesource/v8/V8RuntimeException"));
    errorCls = (jclass)env->NewGlobalRef((env)->FindClass("java/lang/Error"));
    unsupportedOperationExceptionCls = (jclass)env->NewGlobalRef((env)->FindClass("java/lang/UnsupportedOperationException"));

    // Get all method IDs
    v8ArrayInitMethodID = env->GetMethodID(v8ArrayCls, "<init>", "(Lcom/eclipsesource/v8/V8Context;)V");
	v8TypedArrayInitMethodID = env->GetMethodID(v8TypedArrayCls, "<init>", "(Lcom/eclipsesource/v8/V8Context;)V");
    v8ArrayBufferInitMethodID = env->GetMethodID(v8ArrayBufferCls, "<init>", "(Lcom/eclipsesource/v8/V8Context;Ljava/nio/ByteBuffer;)V");
    v8ArrayGetHandleMethodID = env->GetMethodID(v8ArrayCls, "getHandle", "()J");
    v8CallVoidMethodID = (env)->GetMethodID(v8ctx, "callVoidJavaMethod", "(JLcom/eclipsesource/v8/V8Object;Lcom/eclipsesource/v8/V8Array;)V");
    v8ObjectReleaseMethodID = env->GetMethodID(v8ObjectCls, "release", "()V");
    v8ArrayReleaseMethodID = env->GetMethodID(v8ArrayCls, "release", "()V");
    v8ObjectIsUndefinedMethodID = env->GetMethodID(v8ObjectCls, "isUndefined", "()Z");
    v8ObjectGetHandleMethodID = env->GetMethodID(v8ObjectCls, "getHandle", "()J");
    throwableGetMessageMethodID = env->GetMethodID(throwableCls, "getMessage", "()Ljava/lang/String;");
    integerIntValueMethodID = env->GetMethodID(integerCls, "intValue", "()I");
    booleanBoolValueMethodID = env->GetMethodID(booleanCls, "booleanValue", "()Z");
    doubleDoubleValueMethodID = env->GetMethodID(doubleCls, "doubleValue", "()D");
    v8CallObjectJavaMethodMethodID = (env)->GetMethodID(v8ctx, "callObjectJavaMethod", "(JLcom/eclipsesource/v8/V8Object;Lcom/eclipsesource/v8/V8Array;)Ljava/lang/Object;");
    v8DisposeMethodID = (env)->GetMethodID(v8ctx, "disposeMethodID", "(J)V");
    v8WeakReferenceReleased = (env)->GetMethodID(v8ctx, "weakReferenceReleased", "(J)V");
    v8ScriptCompilationInitMethodID = env->GetMethodID(v8ScriptCompilationCls, "<init>", "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;II)V");
    v8ScriptExecutionExceptionInitMethodID = env->GetMethodID(v8ScriptExecutionExceptionCls, "<init>", "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;IILjava/lang/String;Ljava/lang/Throwable;)V");
    v8ScriptExecutionExceptionListenerCallMethodID = env->GetMethodID(v8ScriptExecutionExceptionListenerCls, "onException", "(Ljava/lang/Throwable;)V");
    undefinedV8ArrayInitMethodID = env->GetMethodID(undefinedV8ArrayCls, "<init>", "()V");
    undefinedV8ObjectInitMethodID = env->GetMethodID(undefinedV8ObjectCls, "<init>", "()V");
    v8RuntimeExceptionInitMethodID = env->GetMethodID(v8RuntimeExceptionCls, "<init>", "(Ljava/lang/String;)V");
    integerInitMethodID = env->GetMethodID(integerCls, "<init>", "(I)V");
    doubleInitMethodID = env->GetMethodID(doubleCls, "<init>", "(D)V");
    booleanInitMethodID = env->GetMethodID(booleanCls, "<init>", "(Z)V");
    v8FunctionInitMethodID = env->GetMethodID(v8FunctionCls, "<init>", "(Lcom/eclipsesource/v8/V8Context;)V");
    v8ObjectInitMethodID = env->GetMethodID(v8ObjectCls, "<init>", "(Lcom/eclipsesource/v8/V8Context;)V");

    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1setFlags
 (JNIEnv *env, jclass, jstring v8flags) {
    if (v8flags) {
        char const* str = env->GetStringUTFChars(v8flags, NULL);
        v8::V8::SetFlagsFromString(str, env->GetStringUTFLength(v8flags));
        env->ReleaseStringUTFChars(v8flags, str);
    }
    v8::V8::Initialize();
}

ShellArrayBufferAllocator array_buffer_allocator;

#ifdef NODE_COMPATIBLE
extern "C" {
    void _register_async_wrap(void);
    void _register_cares_wrap(void);
    void _register_fs_event_wrap(void);
    void _register_js_stream(void);
    void _register_buffer(void);
    void _register_config(void);
    void _register_contextify(void);
    void _register_crypto(void);
    void _register_fs(void);
    void _register_http_parser(void);
    void _register_icu(void);
    void _register_os(void);
    void _register_url(void);
    void _register_util(void);
    void _register_v8(void);
    void _register_zlib(void);
    void _register_pipe_wrap(void);
    void _register_process_wrap(void);
    void _register_signal_wrap(void);
    void _register_spawn_sync(void);
    void _register_stream_wrap(void);
    void _register_tcp_wrap(void);
    void _register_timer_wrap(void);
    void _register_tls_wrap(void);
    void _register_tty_wrap(void);
    void _register_udp_wrap(void);
    void _register_uv(void);
  }
#endif


JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1startNodeJS
  (JNIEnv * jniEnv, jclass, jlong v8ContextPtr, jstring fileName) {
#ifdef NODE_COMPATIBLE
  Isolate* isolate = SETUP(jniEnv, v8ContextPtr, );
  setvbuf(stderr, NULL, _IOLBF, 1024);
  const char* utfFileName = jniEnv->GetStringUTFChars(fileName, NULL);
  const char *argv[] = {"j2v8", utfFileName, NULL};
  int argc = sizeof(argv) / sizeof(char*) - 1;
  if (v8ContextPtr == 1) {
  #if defined(_MSC_VER)
    // This is deadcode, but it ensures that libj2v8 'touches' all the
    // node modules. If the node modules are not 'touched' then the
    // linker will strip them out
    // @node-builtins-force-link
    _register_async_wrap();
    _register_cares_wrap();
    _register_fs_event_wrap();
    _register_js_stream();
    _register_buffer();
    _register_config();
    _register_contextify();
    _register_crypto();
    _register_fs();
    _register_http_parser();
    _register_icu();
    _register_os();
    _register_url();
    _register_util();
    _register_v8();
    _register_zlib();
    _register_pipe_wrap();
    _register_process_wrap();
    _register_signal_wrap();
    _register_spawn_sync();
    _register_stream_wrap();
    _register_tcp_wrap();
    _register_timer_wrap();
    _register_tls_wrap();
    _register_tty_wrap();
    _register_udp_wrap();
    _register_uv();
  #endif
  }
  runtime->uvLoop = uv_default_loop();
  runtime->isolateData = node::CreateIsolateData(isolate, runtime->uvLoop);
  node::Environment* env = node::CreateEnvironment(runtime->isolateData, context, argc, argv, 0, 0);
  node::LoadEnvironment(env);
  runtime->nodeEnvironment = env;

  runtime->running = true;
#endif
#ifndef NODE_COMPATIBLE
  (jniEnv)->ThrowNew(unsupportedOperationExceptionCls, "StartNodeJS Not Supported.");
#endif
}

JNIEXPORT jboolean JNICALL Java_com_eclipsesource_v8_V8API__1pumpMessageLoop
  (JNIEnv * env, jclass, jlong v8ContextPtr) {
#ifdef NODE_COMPATIBLE
  Isolate* isolate = SETUP(env, v8ContextPtr, false);
  node::Environment* environment = runtime->nodeEnvironment;
  SealHandleScope seal(isolate);
  v8::platform::PumpMessageLoop(v8Platform, isolate);
  runtime->running = uv_run(runtime->uvLoop, UV_RUN_ONCE);
  if (runtime->running == false) {
    v8::platform::PumpMessageLoop(v8Platform, isolate);
    node::EmitBeforeExit(environment);
    // Emit `beforeExit` if the loop became alive either after emitting
    // event, or after running some callbacks.
    runtime->running = uv_loop_alive(runtime->uvLoop);
    if (uv_run(runtime->uvLoop, UV_RUN_NOWAIT) != 0) {
      runtime->running = true;
    }
  }
  return runtime->running;
#endif
#ifndef NODE_COMPATIBLE
  (env)->ThrowNew(unsupportedOperationExceptionCls, "pumpMessageLoop Not Supported.");
  return false;
#endif
}

JNIEXPORT jboolean JNICALL Java_com_eclipsesource_v8_V8API__1isRunning
  (JNIEnv *env, jclass, jlong v8ContextPtr) {
 #ifdef NODE_COMPATIBLE
   Isolate* isolate = SETUP(env, v8ContextPtr, false);
   return runtime->running;
 #endif
 #ifndef NODE_COMPATIBLE
   (env)->ThrowNew(unsupportedOperationExceptionCls, "isRunning Not Supported.");
   return false;
 #endif
}

JNIEXPORT jboolean JNICALL Java_com_eclipsesource_v8_V8API__1isNodeCompatible
  (JNIEnv *, jclass) {
 #ifdef NODE_COMPATIBLE
   return true;
 #else
   return false;
 #endif
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1createIsolate
 (JNIEnv *env, jobject, jobject v8) {
  V8Runtime* runtime = new V8Runtime();
  v8::Isolate::CreateParams create_params;
  create_params.array_buffer_allocator = &array_buffer_allocator;
  runtime->isolate = v8::Isolate::New(create_params);
  runtime->locker = new Locker(runtime->isolate);
  v8::Isolate::Scope isolate_scope(runtime->isolate);
  runtime->v8 = env->NewGlobalRef(v8);
  runtime->pendingException = NULL;

  delete(runtime->locker);
  return reinterpret_cast<jlong>(runtime);
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1createContext
 (JNIEnv *env, jobject, jobject v8ctx, jlong v8RuntimePtr, jstring globalAlias) {
  V8Runtime* runtime = reinterpret_cast<V8Runtime*>(v8RuntimePtr);

  V8Context* v8Context = new V8Context(v8RuntimePtr);
  v8Context->v8Ctx = env->NewGlobalRef(v8ctx);

  v8::Isolate::Scope isolateScope(runtime->isolate);
  HandleScope handle_scope(runtime->isolate);

  Handle<ObjectTemplate> globalObject = ObjectTemplate::New();

  if (globalAlias == NULL) {
    Handle<Context> context = Context::New(runtime->isolate, NULL, globalObject);
    v8Context->context.Reset(runtime->isolate, context);
    v8Context->globalObject = new Persistent<Object>;
    v8Context->globalObject->Reset(runtime->isolate, context->Global()->GetPrototype()->ToObject(runtime->isolate));
  }
  else {
    Local<String> utfAlias = createV8String(env, runtime->isolate, globalAlias);
    globalObject->SetAccessor(utfAlias, jsWindowObjectAccessor);
    Handle<Context> context = Context::New(runtime->isolate, NULL, globalObject);
    v8Context->context.Reset(runtime->isolate, context);
    v8Context->globalObject = new Persistent<Object>;
    v8Context->globalObject->Reset(runtime->isolate, context->Global()->GetPrototype()->ToObject(runtime->isolate));
  }

  return reinterpret_cast<jlong>(v8Context);
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1setExceptionListener
  (JNIEnv *env, jobject, jlong v8ContextPtr, jobject lsnr) {
  V8Context* v8Context = reinterpret_cast<V8Context*>(v8ContextPtr);
  v8Context->exLsnr = env->NewGlobalRef(lsnr);
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1acquireLock
  (JNIEnv *env, jobject, jlong v8RuntimePtr) {
  V8Runtime* runtime = reinterpret_cast<V8Runtime*>(v8RuntimePtr);
  if(runtime->isolate->InContext()) {
    jstring exceptionString = env->NewStringUTF("Cannot acquire lock while in a V8 Context");
    jthrowable exception = (jthrowable)env->NewObject(v8RuntimeExceptionCls, v8RuntimeExceptionInitMethodID, exceptionString);
    (env)->Throw(exception);
    env->DeleteLocalRef(exceptionString);
    return;
  }
  runtime->locker = new Locker(runtime->isolate);
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1releaseLock
  (JNIEnv *env, jobject, jlong v8RuntimePtr) {
  V8Runtime* runtime = reinterpret_cast<V8Runtime*>(v8RuntimePtr);
  if(runtime->isolate->InContext()) {
    jstring exceptionString = env->NewStringUTF("Cannot release lock while in a V8 Context");
    jthrowable exception = (jthrowable)env->NewObject(v8RuntimeExceptionCls, v8RuntimeExceptionInitMethodID, exceptionString);
    (env)->Throw(exception);
    env->DeleteLocalRef(exceptionString);
    return;
  }
  delete(runtime->locker);
  runtime->locker = NULL;
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1lowMemoryNotification
  (JNIEnv *env, jobject, jlong v8RuntimePtr) {
  V8Runtime* runtime = reinterpret_cast<V8Runtime*>(v8RuntimePtr);
  runtime->isolate->LowMemoryNotification();
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1initEmptyContainer
(JNIEnv *env, jobject, jlong v8ContextPtr) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Persistent<Object>* container = new Persistent<Object>;
  return reinterpret_cast<jlong>(container);
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1initNewV8Object
(JNIEnv *env, jobject, jlong v8ContextPtr) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Local<Object> obj = Object::New(isolate);
  Persistent<Object>* container = new Persistent<Object>;
  container->Reset(runtime->isolate, obj);
  return reinterpret_cast<jlong>(container);
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1getGlobalObject
  (JNIEnv *env, jobject, jlong v8ContextPtr) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Local<Object> obj = Object::New(isolate);
  return reinterpret_cast<jlong>(v8Context->globalObject);
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1createTwin
  (JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jlong twinObjectHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  Handle<Object> obj = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  reinterpret_cast<Persistent<Object>*>(twinObjectHandle)->Reset(runtime->isolate, obj);
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1initNewV8Array
(JNIEnv *env, jobject, jlong v8ContextPtr) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Local<Array> array = Array::New(isolate);
  Persistent<Object>* container = new Persistent<Object>;
  container->Reset(runtime->isolate, array);
  return reinterpret_cast<jlong>(container);
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1initNewV8Int8Array
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong bufferHandle, jint offset, jint length) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<ArrayBuffer> arrayBuffer = Local<ArrayBuffer>::New(isolate, *reinterpret_cast<Persistent<ArrayBuffer>*>(bufferHandle));
  Local<Int8Array> array = Int8Array::New(arrayBuffer, offset, length);
  Persistent<Object>* container = new Persistent<Object>;
  container->Reset(runtime->isolate, array);
  return reinterpret_cast<jlong>(container);
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1initNewV8UInt8Array
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong bufferHandle, jint offset, jint length) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<ArrayBuffer> arrayBuffer = Local<ArrayBuffer>::New(isolate, *reinterpret_cast<Persistent<ArrayBuffer>*>(bufferHandle));
  Local<Uint8Array> array = Uint8Array::New(arrayBuffer, offset, length);
  Persistent<Object>* container = new Persistent<Object>;
  container->Reset(runtime->isolate, array);
  return reinterpret_cast<jlong>(container);
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1initNewV8UInt8ClampedArray
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong bufferHandle, jint offset, jint length) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<ArrayBuffer> arrayBuffer = Local<ArrayBuffer>::New(isolate, *reinterpret_cast<Persistent<ArrayBuffer>*>(bufferHandle));
  Local<Uint8ClampedArray> array = Uint8ClampedArray::New(arrayBuffer, offset, length);
  Persistent<Object>* container = new Persistent<Object>;
  container->Reset(runtime->isolate, array);
  return reinterpret_cast<jlong>(container);
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1initNewV8Int32Array
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong bufferHandle, jint offset, jint length) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<ArrayBuffer> arrayBuffer = Local<ArrayBuffer>::New(isolate, *reinterpret_cast<Persistent<ArrayBuffer>*>(bufferHandle));
  Local<Int32Array> array = Int32Array::New(arrayBuffer, offset, length);
  Persistent<Object>* container = new Persistent<Object>;
  container->Reset(runtime->isolate, array);
  return reinterpret_cast<jlong>(container);
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1initNewV8UInt32Array
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong bufferHandle, jint offset, jint length) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<ArrayBuffer> arrayBuffer = Local<ArrayBuffer>::New(isolate, *reinterpret_cast<Persistent<ArrayBuffer>*>(bufferHandle));
  Local<Uint32Array> array = Uint32Array::New(arrayBuffer, offset, length);
  Persistent<Object>* container = new Persistent<Object>;
  container->Reset(runtime->isolate, array);
  return reinterpret_cast<jlong>(container);
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1initNewV8UInt16Array
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong bufferHandle, jint offset, jint length) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<ArrayBuffer> arrayBuffer = Local<ArrayBuffer>::New(isolate, *reinterpret_cast<Persistent<ArrayBuffer>*>(bufferHandle));
  Local<Uint16Array> array = Uint16Array::New(arrayBuffer, offset, length);
  Persistent<Object>* container = new Persistent<Object>;
  container->Reset(runtime->isolate, array);
  return reinterpret_cast<jlong>(container);
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1initNewV8Int16Array
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong bufferHandle, jint offset, jint length) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<ArrayBuffer> arrayBuffer = Local<ArrayBuffer>::New(isolate, *reinterpret_cast<Persistent<ArrayBuffer>*>(bufferHandle));
  Local<Int16Array> array = Int16Array::New(arrayBuffer, offset, length);
  Persistent<Object>* container = new Persistent<Object>;
  container->Reset(runtime->isolate, array);
  return reinterpret_cast<jlong>(container);
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1initNewV8Float32Array
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong bufferHandle, jint offset, jint length) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<ArrayBuffer> arrayBuffer = Local<ArrayBuffer>::New(isolate, *reinterpret_cast<Persistent<ArrayBuffer>*>(bufferHandle));
  Local<Float32Array> array = Float32Array::New(arrayBuffer, offset, length);
  Persistent<Object>* container = new Persistent<Object>;
  container->Reset(runtime->isolate, array);
  return reinterpret_cast<jlong>(container);
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1initNewV8Float64Array
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong bufferHandle, jint offset, jint length) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<ArrayBuffer> arrayBuffer = Local<ArrayBuffer>::New(isolate, *reinterpret_cast<Persistent<ArrayBuffer>*>(bufferHandle));
  Local<Float64Array> array = Float64Array::New(arrayBuffer, offset, length);
  Persistent<Object>* container = new Persistent<Object>;
  container->Reset(runtime->isolate, array);
  return reinterpret_cast<jlong>(container);
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1initNewV8ArrayBuffer__JI
(JNIEnv *env, jobject, jlong v8ContextPtr, jint capacity) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Local<ArrayBuffer> arrayBuffer = ArrayBuffer::New(isolate, capacity);
  Persistent<Object>* container = new Persistent<Object>;
  container->Reset(runtime->isolate, arrayBuffer);
  return reinterpret_cast<jlong>(container);
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1initNewV8ArrayBuffer__JLjava_nio_ByteBuffer_2I
(JNIEnv *env, jobject, jlong v8ContextPtr, jobject byteBuffer, jint capacity) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Local<ArrayBuffer> arrayBuffer = ArrayBuffer::New(isolate, env->GetDirectBufferAddress(byteBuffer), capacity);
  Persistent<Object>* container = new Persistent<Object>;
  container->Reset(runtime->isolate, arrayBuffer);
  return reinterpret_cast<jlong>(container);
}

JNIEXPORT jobject JNICALL Java_com_eclipsesource_v8_V8API__1createV8ArrayBufferBackingStore
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jint capacity) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<ArrayBuffer> arrayBuffer = Local<ArrayBuffer>::New(isolate, *reinterpret_cast<Persistent<ArrayBuffer>*>(objectHandle));
  void* dataPtr = arrayBuffer->GetContents().Data();
  jobject byteBuffer = env->NewDirectByteBuffer(arrayBuffer->GetContents().Data(), capacity);
  return byteBuffer;
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1release
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle) {
  if (v8ContextPtr == 0) {
    return;
  }
  Isolate* isolate = getIsolate(env, v8ContextPtr);
  Locker locker(isolate);
  HandleScope handle_scope(isolate);
  reinterpret_cast<Persistent<Object>*>(objectHandle)->Reset();
  delete(reinterpret_cast<Persistent<Object>*>(objectHandle));
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1terminateExecution
  (JNIEnv * env, jobject, jlong v8ContextPtr) {
	if (v8ContextPtr == 0) {
	  return;
	}
	Isolate* isolate = getIsolate(env, v8ContextPtr);
	V8::TerminateExecution(isolate);
	return;
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1releaseIsolate
(JNIEnv *env, jobject, jlong v8RuntimePtr) {
  if (v8RuntimePtr == 0) {
    return;
  }
  reinterpret_cast<V8Runtime*>(v8RuntimePtr)->isolate->Dispose();
  env->DeleteGlobalRef(reinterpret_cast<V8Runtime*>(v8RuntimePtr)->v8);
  delete(reinterpret_cast<V8Runtime*>(v8RuntimePtr));
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1releaseContext
(JNIEnv *env, jobject, jlong v8ContextPtr) {
  if (v8ContextPtr == 0) {
    return;
  }

  env->DeleteGlobalRef(reinterpret_cast<V8Context*>(v8ContextPtr)->v8Ctx);
  env->DeleteGlobalRef(reinterpret_cast<V8Context*>(v8ContextPtr)->exLsnr);
  delete(reinterpret_cast<V8Context*>(v8ContextPtr));
}

JNIEXPORT jboolean JNICALL Java_com_eclipsesource_v8_V8API__1contains
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jstring key) {
  Isolate* isolate = SETUP(env, v8ContextPtr, false);
  Handle<Object> object = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  Local<String> v8Key = createV8String(env, isolate, key);
  return object->Has(v8Key);
}

JNIEXPORT jobjectArray JNICALL Java_com_eclipsesource_v8_V8API__1getKeys
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, NULL);
  Handle<Object> object = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  Local<Array> properties = object->GetOwnPropertyNames();
  int size = properties->Length();
  jobjectArray keys = (env)->NewObjectArray(size, stringCls, NULL);
  for (int i = 0; i < size; i++) {
    String::Value unicodeString(properties->Get(i)->ToString(isolate));
    jobject key = (env)->NewString(*unicodeString, unicodeString.length());
    (env)->SetObjectArrayElement(keys, i, key);
    (env)->DeleteLocalRef(key);
  }
  return keys;
}

ScriptOrigin* createScriptOrigin(JNIEnv * env, Isolate* isolate, jstring &jscriptName, jint jlineNumber = 0) {
  Local<String> scriptName = createV8String(env, isolate, jscriptName);
  return new ScriptOrigin(scriptName, Integer::New(isolate, jlineNumber));
}

bool compileScript(Isolate *isolate, jstring &jscript, JNIEnv *env, jstring jscriptName, jint &jlineNumber, Local<Script> &script, TryCatch* tryCatch) {
  Local<String> source = createV8String(env, isolate, jscript);
  ScriptOrigin* scriptOriginPtr = NULL;
  if (jscriptName != NULL) {
    scriptOriginPtr = createScriptOrigin(env, isolate, jscriptName, jlineNumber);
  }
  script = Script::Compile(source, scriptOriginPtr);
  if (scriptOriginPtr != NULL) {
    delete(scriptOriginPtr);
  }
  if (tryCatch->HasCaught()) {
    throwParseException(env, isolate, tryCatch);
    return false;
  }
  return true;
}

bool runScript(Isolate* isolate, JNIEnv *env, Local<Script> *script, TryCatch* tryCatch, jlong v8ContextPtr) {
  (*script)->Run();
  if (tryCatch->HasCaught()) {
    throwExecutionException(env, isolate, tryCatch, v8ContextPtr);
    return false;
  }
  return true;
}

bool runScript(Isolate* isolate, JNIEnv *env, Local<Script> *script, TryCatch* tryCatch, Local<Value> &result, jlong v8ContextPtr) {
  result = (*script)->Run();
  if (tryCatch->HasCaught()) {
    throwExecutionException(env, isolate, tryCatch, v8ContextPtr);
    return false;
  }
  return true;
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1executeVoidScript
(JNIEnv * env, jobject v8, jlong v8ContextPtr, jstring jjstring, jstring jscriptName = NULL, jint jlineNumber = 0) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  TryCatch tryCatch(isolate);
  Local<Script> script;
  if (!compileScript(isolate, jjstring, env, jscriptName, jlineNumber, script, &tryCatch))
    return;
  runScript(isolate, env, &script, &tryCatch, v8ContextPtr);
}

JNIEXPORT jdouble JNICALL Java_com_eclipsesource_v8_V8API__1executeDoubleScript
(JNIEnv * env, jobject v8, jlong v8ContextPtr, jstring jjstring, jstring jscriptName = NULL, jint jlineNumber = 0) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  TryCatch tryCatch(isolate);
  Local<Script> script;
  Local<Value> result;
  if (!compileScript(isolate, jjstring, env, jscriptName, jlineNumber, script, &tryCatch))
    return 0;
  if (!runScript(isolate, env, &script, &tryCatch, result, v8ContextPtr))
    return 0;
  ASSERT_IS_NUMBER(result);
  return result->NumberValue();
}

JNIEXPORT jboolean JNICALL Java_com_eclipsesource_v8_V8API__1executeBooleanScript
(JNIEnv *env, jobject v8, jlong v8ContextPtr, jstring jjstring, jstring jscriptName = NULL, jint jlineNumber = 0) {
  Isolate* isolate = SETUP(env, v8ContextPtr, false);
  TryCatch tryCatch(isolate);
  Local<Script> script;
  Local<Value> result;
  if (!compileScript(isolate, jjstring, env, jscriptName, jlineNumber, script, &tryCatch))
    return false;
  if (!runScript(isolate, env, &script, &tryCatch, result, v8ContextPtr))
    return false;
  ASSERT_IS_BOOLEAN(result);
  return result->BooleanValue();
}

JNIEXPORT jstring JNICALL Java_com_eclipsesource_v8_V8API__1executeStringScript
(JNIEnv *env, jobject v8, jlong v8ContextPtr, jstring jjstring, jstring jscriptName = NULL, jint jlineNumber = 0) {
  Isolate* isolate = SETUP(env, v8ContextPtr, NULL);
  TryCatch tryCatch(isolate);
  Local<Script> script;
  Local<Value> result;
  if (!compileScript(isolate, jjstring, env, jscriptName, jlineNumber, script, &tryCatch))
    return NULL;
  if (!runScript(isolate, env, &script, &tryCatch, result, v8ContextPtr))
    return NULL;
  ASSERT_IS_STRING(result);
  String::Value unicodeString(result->ToString(isolate));

  return env->NewString(*unicodeString, unicodeString.length());
}

JNIEXPORT jint JNICALL Java_com_eclipsesource_v8_V8API__1executeIntegerScript
(JNIEnv * env, jobject v8, jlong v8ContextPtr, jstring jjstring, jstring jscriptName = NULL, jint jlineNumber = 0) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  TryCatch tryCatch(isolate);
  Local<Script> script;
  Local<Value> result;
  if (!compileScript(isolate, jjstring, env, jscriptName, jlineNumber, script, &tryCatch))
    return 0;
  if (!runScript(isolate, env, &script, &tryCatch, result, v8ContextPtr))
    return 0;
  ASSERT_IS_NUMBER(result);
  return result->Int32Value();
}

JNIEXPORT jobject JNICALL Java_com_eclipsesource_v8_V8API__1executeScript
(JNIEnv *env, jobject v8, jlong v8ContextPtr, jint expectedType, jstring jjstring, jstring jscriptName = NULL, jint jlineNumber = 0) {
  Isolate* isolate = SETUP(env, v8ContextPtr, NULL);
  TryCatch tryCatch(isolate);
  Local<Script> script;
  Local<Value> result;
  if (!compileScript(isolate, jjstring, env, jscriptName, jlineNumber, script, &tryCatch)) { return NULL; }
  if (!runScript(isolate, env, &script, &tryCatch, result, v8ContextPtr)) { return NULL; }
  return getResult(env, v8Context->v8Ctx, v8ContextPtr, result, expectedType);
}

bool invokeFunction(JNIEnv *env, Isolate* isolate, jlong &v8ContextPtr, jlong &receiverHandle, jlong &functionHandle, jlong &parameterHandle, Handle<Value> &result) {
  int size = 0;
  Handle<Value>* args = NULL;
  if (parameterHandle != 0) {
    Handle<Object> parameters = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(parameterHandle));
    size = Array::Cast(*parameters)->Length();
    args = new Handle<Value>[size];
    for (int i = 0; i < size; i++) {
      args[i] = parameters->Get(i);
    }
  }
  Handle<Object> object = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(functionHandle));
  Handle<Object> receiver = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(receiverHandle));
  Handle<Function> func = Handle<Function>::Cast(object);
  TryCatch tryCatch(isolate);
  result = func->Call(receiver, size, args);
  if (args != NULL) {
    delete(args);
  }
  if (tryCatch.HasCaught()) {
    throwExecutionException(env, isolate, &tryCatch, v8ContextPtr);
    return false;
  }
  return true;
}

bool invokeFunction(JNIEnv *env, Isolate* isolate, jlong &v8ContextPtr, jlong &objectHandle, jstring &jfunctionName, jlong &parameterHandle, Handle<Value> &result) {
  Local<String> functionName = createV8String(env, isolate, jfunctionName);
  Handle<Object> parentObject = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  int size = 0;
  Handle<Value>* args = NULL;
  if (parameterHandle != 0) {
    Handle<Object> parameters = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(parameterHandle));
    size = Array::Cast(*parameters)->Length();
    args = new Handle<Value>[size];
    for (int i = 0; i < size; i++) {
      args[i] = parameters->Get(i);
    }
  }
  Handle<Value> value = parentObject->Get(functionName);
  Handle<Function> func = Handle<Function>::Cast(value);
  TryCatch tryCatch(isolate);
  result = func->Call(parentObject, size, args);
  if (args != NULL) {
    delete(args);
  }
  if (tryCatch.HasCaught()) {
    throwExecutionException(env, isolate, &tryCatch, v8ContextPtr);
    return false;
  }
  return true;
}

JNIEXPORT jobject JNICALL Java_com_eclipsesource_v8_V8API__1executeFunction__JJJJ
  (JNIEnv *env, jobject v8, jlong v8ContextPtr, jlong receiverHandle, jlong functionHandle, jlong parameterHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, NULL);
  Handle<Value> result;
  if (!invokeFunction(env, isolate, v8ContextPtr, receiverHandle, functionHandle, parameterHandle, result))
    return NULL;
  return getResult(env, v8Context->v8Ctx, v8ContextPtr, result, com_eclipsesource_v8_V8API_UNKNOWN);
}

JNIEXPORT jobject JNICALL Java_com_eclipsesource_v8_V8API__1executeFunction__JIJLjava_lang_String_2J
(JNIEnv *env, jobject v8, jlong v8ContextPtr, jint expectedType, jlong objectHandle, jstring jfunctionName, jlong parameterHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, NULL);
  Handle<Value> result;
  if (!invokeFunction(env, isolate, v8ContextPtr, objectHandle, jfunctionName, parameterHandle, result))
    return NULL;
  return getResult(env, v8Context->v8Ctx, v8ContextPtr, result, expectedType);
}

JNIEXPORT jint JNICALL Java_com_eclipsesource_v8_V8API__1executeIntegerFunction
(JNIEnv *env, jobject v8, jlong v8ContextPtr, jlong objectHandle, jstring jfunctionName, jlong parameterHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Value> result;
  if (!invokeFunction(env, isolate, v8ContextPtr, objectHandle, jfunctionName, parameterHandle, result))
    return 0;
  ASSERT_IS_NUMBER(result);
  return result->Int32Value();
}

JNIEXPORT jdouble JNICALL Java_com_eclipsesource_v8_V8API__1executeDoubleFunction
(JNIEnv *env, jobject v8, jlong v8ContextPtr, jlong objectHandle, jstring jfunctionName, jlong parameterHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Value> result;
  if (!invokeFunction(env, isolate, v8ContextPtr, objectHandle, jfunctionName, parameterHandle, result))
    return 0;
  ASSERT_IS_NUMBER(result);
  return result->NumberValue();
}

JNIEXPORT jboolean JNICALL Java_com_eclipsesource_v8_V8API__1executeBooleanFunction
(JNIEnv *env, jobject v8, jlong v8ContextPtr, jlong objectHandle, jstring jfunctionName, jlong parameterHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, false);
  Handle<Value> result;
  if (!invokeFunction(env, isolate, v8ContextPtr, objectHandle, jfunctionName, parameterHandle, result))
    return false;
  ASSERT_IS_BOOLEAN(result);
  return result->BooleanValue();
}

JNIEXPORT jstring JNICALL Java_com_eclipsesource_v8_V8API__1executeStringFunction
(JNIEnv *env, jobject v8, jlong v8ContextPtr, jlong objectHandle, jstring jfunctionName, jlong parameterHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, NULL);
  Handle<Value> result;
  if (!invokeFunction(env, isolate, v8ContextPtr, objectHandle, jfunctionName, parameterHandle, result))
    return NULL;
  ASSERT_IS_STRING(result);
  String::Value unicodeString(result->ToString(isolate));

  return env->NewString(*unicodeString, unicodeString.length());
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1executeVoidFunction
(JNIEnv *env, jobject v8, jlong v8ContextPtr, jlong objectHandle, jstring jfunctionName, jlong parameterHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  Handle<Value> result;
  invokeFunction(env, isolate, v8ContextPtr, objectHandle, jfunctionName, parameterHandle, result);
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1addUndefined
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jstring key) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  addValueWithKey(env, isolate, objectHandle, key, Undefined(isolate));
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1addNull
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jstring key) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  addValueWithKey(env, isolate, objectHandle, key, Null(isolate));
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1add__JJLjava_lang_String_2I
(JNIEnv * env, jobject, jlong v8ContextPtr, jlong objectHandle, jstring key, jint value) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  addValueWithKey(env, isolate, objectHandle, key, Int32::New(isolate, value));
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1add__JJLjava_lang_String_2D
(JNIEnv * env, jobject, jlong v8ContextPtr, jlong objectHandle, jstring key, jdouble value) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  addValueWithKey(env, isolate, objectHandle, key, Number::New(isolate, value));
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1add__JJLjava_lang_String_2Ljava_lang_String_2
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jstring key, jstring value) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  Handle<Value> v8Value = createV8String(env, isolate, value);
  addValueWithKey(env, isolate, objectHandle, key, v8Value);
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1add__JJLjava_lang_String_2Z
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jstring key, jboolean value) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  addValueWithKey(env, isolate, objectHandle, key, Boolean::New(isolate, value));
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1addObject
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jstring key, jlong valueHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  Handle<Value> value = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(valueHandle));
  addValueWithKey(env, isolate, objectHandle, key, value);
}

JNIEXPORT jobject JNICALL Java_com_eclipsesource_v8_V8API__1get
(JNIEnv *env, jobject v8, jlong v8ContextPtr, jint expectedType, jlong objectHandle, jstring key) {
  Isolate* isolate = SETUP(env, v8ContextPtr, NULL);
  Handle<Value> result = getValueWithKey(env, isolate, objectHandle, key);
  return getResult(env, v8Context->v8Ctx, v8ContextPtr, result, expectedType);
}

JNIEXPORT jint JNICALL Java_com_eclipsesource_v8_V8API__1getInteger
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jstring key) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Value> v8Value = getValueWithKey(env, isolate, objectHandle, key);
  ASSERT_IS_NUMBER(v8Value);
  return v8Value->Int32Value();
}

JNIEXPORT jdouble JNICALL Java_com_eclipsesource_v8_V8API__1getDouble
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jstring key) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Value> v8Value = getValueWithKey(env, isolate, objectHandle, key);
  ASSERT_IS_NUMBER(v8Value);
  return v8Value->NumberValue();
}

JNIEXPORT jstring JNICALL Java_com_eclipsesource_v8_V8API__1getString
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jstring key) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Value> v8Value = getValueWithKey(env, isolate, objectHandle, key);
  ASSERT_IS_STRING(v8Value);
  String::Value unicode(v8Value->ToString(isolate));

  return env->NewString(*unicode, unicode.length());
}

JNIEXPORT jboolean JNICALL Java_com_eclipsesource_v8_V8API__1getBoolean
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jstring key) {
  Isolate* isolate = SETUP(env, v8ContextPtr, false);
  Handle<Value> v8Value = getValueWithKey(env, isolate, objectHandle, key);
  ASSERT_IS_BOOLEAN(v8Value);
  return v8Value->BooleanValue();
}

JNIEXPORT jint JNICALL Java_com_eclipsesource_v8_V8API__1getType__JJLjava_lang_String_2
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jstring key) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Value> v8Value = getValueWithKey(env, isolate, objectHandle, key);
  int type = getType(v8Value);
  if (type < 0) {
    throwResultUndefinedException(env, "");
  }
  return type;
}

bool isNumber(int type) {
  return type == com_eclipsesource_v8_V8API_DOUBLE || type == com_eclipsesource_v8_V8API_INTEGER;
}

bool isObject(int type) {
  return type == com_eclipsesource_v8_V8API_V8_OBJECT || type == com_eclipsesource_v8_V8API_V8_ARRAY;
}

bool isNumber(int type1, int type2) {
  return isNumber(type1) && isNumber(type2);
}

bool isObject(int type1, int type2) {
  return isObject(type1) && isObject(type2);
}

JNIEXPORT jint JNICALL Java_com_eclipsesource_v8_V8API__1getArrayType
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  int length = 0;
  if ( array->IsTypedArray() ) {
      if ( array->IsFloat64Array() ) {
        return com_eclipsesource_v8_V8API_DOUBLE;
      } else if ( array->IsFloat32Array() ) {
        return com_eclipsesource_v8_V8API_FLOAT_32_ARRAY;
      } else if ( array->IsInt32Array() ) {
        return com_eclipsesource_v8_V8API_INT_32_ARRAY;
      } else if ( array->IsUint32Array() ) {
        return com_eclipsesource_v8_V8API_UNSIGNED_INT_32_ARRAY;
      } else if ( array->IsInt16Array() ) {
        return com_eclipsesource_v8_V8API_INT_16_ARRAY;
      } else if ( array->IsUint16Array() ) {
        return com_eclipsesource_v8_V8API_UNSIGNED_INT_16_ARRAY;
      } else if ( array->IsInt8Array() ) {
        return com_eclipsesource_v8_V8API_INT_8_ARRAY;
      } else if ( array->IsUint8Array() ) {
        return com_eclipsesource_v8_V8API_UNSIGNED_INT_8_ARRAY;
      } else if ( array->IsUint8ClampedArray() ) {
        return com_eclipsesource_v8_V8API_UNSIGNED_INT_8_CLAMPED_ARRAY;
      }
      return com_eclipsesource_v8_V8API_INTEGER;
  } else {
      length = Array::Cast(*array)->Length();
  }
  int arrayType = com_eclipsesource_v8_V8API_UNDEFINED;
  for (int index = 0; index < length; index++) {
    int type = getType(array->Get(index));
    if (type < 0) {
      throwResultUndefinedException(env, "");
    }
    else if (index == 0) {
      arrayType = type;
    }
    else if (type == arrayType) {
      // continue
    }
    else if (isNumber(arrayType, type)) {
      arrayType = com_eclipsesource_v8_V8API_DOUBLE;
    }
    else if (isObject(arrayType, type)) {
      arrayType = com_eclipsesource_v8_V8API_V8_OBJECT;
    }
    else {
      return com_eclipsesource_v8_V8API_UNDEFINED;
    }
  }
  return arrayType;
}

JNIEXPORT jint JNICALL Java_com_eclipsesource_v8_V8API__1arrayGetSize
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  if ( array->IsTypedArray() ) {
	  return TypedArray::Cast(*array)->Length();
  }
  return Array::Cast(*array)->Length();
}

JNIEXPORT jint JNICALL Java_com_eclipsesource_v8_V8API__1arrayGetInteger
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jint index) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  Handle<Value> v8Value = array->Get(index);
  ASSERT_IS_NUMBER(v8Value);
  return v8Value->Int32Value();
}

int fillIntArray(JNIEnv *env, Handle<Object> &array, int start, int length, jintArray &result) {
  jint * fill = new jint[length];
  for (int i = start; i < start + length; i++) {
    Handle<Value> v8Value = array->Get(i);
    ASSERT_IS_NUMBER(v8Value);
    fill[i - start] = v8Value->Int32Value();
  }
  (env)->SetIntArrayRegion(result, 0, length, fill);
  delete[] fill;
  return length;
}

int fillDoubleArray(JNIEnv *env, Handle<Object> &array, int start, int length, jdoubleArray &result) {
  jdouble * fill = new jdouble[length];
  for (int i = start; i < start + length; i++) {
    Handle<Value> v8Value = array->Get(i);
    ASSERT_IS_NUMBER(v8Value);
    fill[i - start] = v8Value->NumberValue();
  }
  (env)->SetDoubleArrayRegion(result, 0, length, fill);
  delete[] fill;
  return length;
}

int fillByteArray(JNIEnv *env, Handle<Object> &array, int start, int length, jbyteArray &result) {
  jbyte * fill = new jbyte[length];
  for (int i = start; i < start + length; i++) {
    Handle<Value> v8Value = array->Get(i);
    ASSERT_IS_NUMBER(v8Value);
    fill[i - start] = (jbyte)v8Value->Int32Value();
  }
  (env)->SetByteArrayRegion(result, 0, length, fill);
  delete[] fill;
  return length;
}

int fillBooleanArray(JNIEnv *env, Handle<Object> &array, int start, int length, jbooleanArray &result) {
  jboolean * fill = new jboolean[length];
  for (int i = start; i < start + length; i++) {
    Handle<Value> v8Value = array->Get(i);
    ASSERT_IS_BOOLEAN(v8Value);
    fill[i - start] = v8Value->BooleanValue();
  }
  (env)->SetBooleanArrayRegion(result, 0, length, fill);
  delete[] fill;
  return length;
}

int fillStringArray(JNIEnv *env, Isolate* isolate, Handle<Object> &array, int start, int length, jobjectArray &result) {
  for (int i = start; i < start + length; i++) {
    Handle<Value> v8Value = array->Get(i);
    ASSERT_IS_STRING(v8Value);
    String::Value unicodeString(v8Value->ToString(isolate));
    jstring string = env->NewString(*unicodeString, unicodeString.length());
    env->SetObjectArrayElement(result, i - start, string);
    (env)->DeleteLocalRef(string);
  }

  return length;
}

JNIEXPORT jint JNICALL Java_com_eclipsesource_v8_V8API__1arrayGetIntegers__JJII_3I
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jint start, jint length, jintArray result) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));

  return fillIntArray(env, array, start, length, result);
}

JNIEXPORT jintArray JNICALL Java_com_eclipsesource_v8_V8API__1arrayGetIntegers__JJII
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jint start, jint length) {
  Isolate* isolate = SETUP(env, v8ContextPtr, NULL);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  jintArray result = env->NewIntArray(length);
  fillIntArray(env, array, start, length, result);

  return result;
}

JNIEXPORT jint JNICALL Java_com_eclipsesource_v8_V8API__1arrayGetDoubles__JJII_3D
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jint start, jint length, jdoubleArray result) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  return fillDoubleArray(env, array, start, length, result);
}

JNIEXPORT jdoubleArray JNICALL Java_com_eclipsesource_v8_V8API__1arrayGetDoubles__JJII
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jint start, jint length) {
  Isolate* isolate = SETUP(env, v8ContextPtr, NULL);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  jdoubleArray result = env->NewDoubleArray(length);
  fillDoubleArray(env, array, start, length, result);
  return result;
}

JNIEXPORT jint JNICALL Java_com_eclipsesource_v8_V8API__1arrayGetBooleans__JJII_3Z
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jint start, jint length, jbooleanArray result) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  return fillBooleanArray(env, array, start, length, result);
}

JNIEXPORT jbyteArray JNICALL Java_com_eclipsesource_v8_V8API__1arrayGetBytes__JJII
  (JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jint start, jint length) {
  Isolate* isolate = SETUP(env, v8ContextPtr, NULL);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  jbyteArray result = env->NewByteArray(length);
  fillByteArray(env, array, start, length, result);
  return result;
}

JNIEXPORT jbooleanArray JNICALL Java_com_eclipsesource_v8_V8API__1arrayGetBooleans__JJII
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jint start, jint length) {
  Isolate* isolate = SETUP(env, v8ContextPtr, NULL);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  jbooleanArray result = env->NewBooleanArray(length);
  fillBooleanArray(env, array, start, length, result);
  return result;
}

JNIEXPORT jint JNICALL Java_com_eclipsesource_v8_V8API__1arrayGetBytes__JJII_3B
  (JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jint start, jint length, jbyteArray result) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  return fillByteArray(env, array, start, length, result);
}

JNIEXPORT jint JNICALL Java_com_eclipsesource_v8_V8API__1arrayGetStrings__JJII_3Ljava_lang_String_2
(JNIEnv * env, jobject, jlong v8ContextPtr, jlong arrayHandle, jint start, jint length, jobjectArray result) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));

  return fillStringArray(env, isolate, array, start, length, result);
}

JNIEXPORT jobjectArray JNICALL Java_com_eclipsesource_v8_V8API__1arrayGetStrings__JJII
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jint start, jint length) {
  Isolate* isolate = SETUP(env, v8ContextPtr, NULL);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  jobjectArray result = env->NewObjectArray(length, stringCls, NULL);
  fillStringArray(env, isolate, array, start, length, result);

  return result;
}

JNIEXPORT jboolean JNICALL Java_com_eclipsesource_v8_V8API__1arrayGetBoolean
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jint index) {
  Isolate* isolate = SETUP(env, v8ContextPtr, false);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  Handle<Value> v8Value = array->Get(index);
  ASSERT_IS_BOOLEAN(v8Value);
  return v8Value->BooleanValue();
}

JNIEXPORT jbyte JNICALL Java_com_eclipsesource_v8_V8API__1arrayGetByte
  (JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jint index) {
  Isolate* isolate = SETUP(env, v8ContextPtr, false);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  Handle<Value> v8Value = array->Get(index);
  ASSERT_IS_NUMBER(v8Value);
  return v8Value->Int32Value();
}

JNIEXPORT jdouble JNICALL Java_com_eclipsesource_v8_V8API__1arrayGetDouble
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jint index) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  Handle<Value> v8Value = array->Get(index);
  ASSERT_IS_NUMBER(v8Value);
  return v8Value->NumberValue();
}

JNIEXPORT jstring JNICALL Java_com_eclipsesource_v8_V8API__1arrayGetString
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jint index) {
  Isolate* isolate = SETUP(env, v8ContextPtr, NULL);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  Handle<Value> v8Value = array->Get(index);
  ASSERT_IS_STRING(v8Value);
  String::Value unicodeString(v8Value->ToString(isolate));

  return env->NewString(*unicodeString, unicodeString.length());
}

JNIEXPORT jobject JNICALL Java_com_eclipsesource_v8_V8API__1arrayGet
(JNIEnv *env, jobject v8, jlong v8ContextPtr, jint expectedType, jlong arrayHandle, jint index) {
  Isolate* isolate = SETUP(env, v8ContextPtr, NULL);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  Handle<Value> result = array->Get(index);
  return getResult(env, v8Context->v8Ctx, v8ContextPtr, result, expectedType);
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1addArrayNullItem
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  if ( array->IsTypedArray() ) {
     Local<String> string = String::NewFromUtf8(isolate, "Cannot push to a Typed Array.");
     v8::String::Value strValue(string);
     throwV8RuntimeException(env, &strValue);
     return;
  }
  int index = Array::Cast(*array)->Length();
  array->Set(index, Null(isolate));
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1addArrayUndefinedItem
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  if ( array->IsTypedArray() ) {
     Local<String> string = String::NewFromUtf8(isolate, "Cannot push to a Typed Array.");
     v8::String::Value strValue(string);
     throwV8RuntimeException(env, &strValue);
     return;
  }
  int index = Array::Cast(*array)->Length();
  array->Set(index, Undefined(isolate));
}


JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1addArrayIntItem
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jint value) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  if ( array->IsTypedArray() ) {
     Local<String> string = String::NewFromUtf8(isolate, "Cannot push to a Typed Array.");
     v8::String::Value strValue(string);
     throwV8RuntimeException(env, &strValue);
     return;
  }
  Local<Value> v8Value = Int32::New(isolate, value);
  int index = Array::Cast(*array)->Length();
  array->Set(index, v8Value);
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1addArrayDoubleItem
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jdouble value) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  if ( array->IsTypedArray() ) {
     Local<String> string = String::NewFromUtf8(isolate, "Cannot push to a Typed Array.");
     v8::String::Value strValue(string);
     throwV8RuntimeException(env, &strValue);
     return;
  }
  Local<Value> v8Value = Number::New(isolate, value);
  int index = Array::Cast(*array)->Length();
  array->Set(index, v8Value);
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1addArrayBooleanItem
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jboolean value) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  if ( array->IsTypedArray() ) {
     Local<String> string = String::NewFromUtf8(isolate, "Cannot push to a Typed Array.");
     v8::String::Value strValue(string);
     throwV8RuntimeException(env, &strValue);
     return;
  }
  Local<Value> v8Value = Boolean::New(isolate, value);
  int index = Array::Cast(*array)->Length();
  array->Set(index, v8Value);
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1addArrayStringItem
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jstring value) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  if ( array->IsTypedArray() ) {
     Local<String> string = String::NewFromUtf8(isolate, "Cannot push to a Typed Array.");
     v8::String::Value strValue(string);
     throwV8RuntimeException(env, &strValue);
     return;
  }
  int index = Array::Cast(*array)->Length();
  Local<String> v8Value = createV8String(env, isolate, value);
  array->Set(index, v8Value);
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1addArrayObjectItem
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jlong valueHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  if ( array->IsTypedArray() ) {
     Local<String> string = String::NewFromUtf8(isolate, "Cannot push to a Typed Array.");
     v8::String::Value strValue(string);
     throwV8RuntimeException(env, &strValue);
     return;
  }
  int index = Array::Cast(*array)->Length();
  Local<Value> v8Value = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(valueHandle));
  array->Set(index, v8Value);
}

int getType(Handle<Value> v8Value) {
  if (v8Value.IsEmpty() || v8Value->IsUndefined()) {
    return com_eclipsesource_v8_V8API_UNDEFINED;
  }
  else if (v8Value->IsNull()) {
    return com_eclipsesource_v8_V8API_NULL;
  }
  else if (v8Value->IsInt32()) {
    return com_eclipsesource_v8_V8API_INTEGER;
  }
  else if (v8Value->IsNumber()) {
    return com_eclipsesource_v8_V8API_DOUBLE;
  }
  else if (v8Value->IsBoolean()) {
    return com_eclipsesource_v8_V8API_BOOLEAN;
  }
  else if (v8Value->IsString()) {
    return com_eclipsesource_v8_V8API_STRING;
  }
  else if (v8Value->IsFunction()) {
    return com_eclipsesource_v8_V8API_V8_FUNCTION;
  }
  else if (v8Value->IsArrayBuffer()) {
    return com_eclipsesource_v8_V8API_V8_ARRAY_BUFFER;
  }
  else if (v8Value->IsTypedArray()) {
    return com_eclipsesource_v8_V8API_V8_TYPED_ARRAY;
  }
  else if (v8Value->IsArray()) {
    return com_eclipsesource_v8_V8API_V8_ARRAY;
  }
  else if (v8Value->IsObject()) {
    return com_eclipsesource_v8_V8API_V8_OBJECT;
  }
  return -1;
}

JNIEXPORT jint JNICALL Java_com_eclipsesource_v8_V8API__1getType__JJI
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jint index) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  Handle<Value> v8Value = array->Get(index);
  int type = getType(v8Value);
  if (type < 0) {
    throwResultUndefinedException(env, "");
  }
  return type;
}

JNIEXPORT jint JNICALL Java_com_eclipsesource_v8_V8API__1getType__JJ
  (JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Value> v8Value = Local<Value>::New(isolate, *reinterpret_cast<Persistent<Value>*>(objectHandle));
  return getType(v8Value);
}

JNIEXPORT jint JNICALL Java_com_eclipsesource_v8_V8API__1getType__JJII
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong arrayHandle, jint start, jint length) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Object> array = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(arrayHandle));
  int result = -1;
  for (int i = start; i < start + length; i++) {
    Handle<Value> v8Value = array->Get(i);
    int type = getType(v8Value);
    if (result >= 0 && result != type) {
      throwResultUndefinedException(env, "");
      return -1;
    }
    else if (type < 0) {
      throwResultUndefinedException(env, "");
      return -1;
    }
    result = type;
  }
  if (result < 0) {
    throwResultUndefinedException(env, "");
  }
  return result;
}

jobject createParameterArray(JNIEnv* env, jlong v8ContextPtr, jobject v8, int size, const FunctionCallbackInfo<Value>& args) {
  Isolate* isolate = getIsolate(env, v8ContextPtr);
  jobject result = env->NewObject(v8ArrayCls, v8ArrayInitMethodID, v8);
  jlong parameterHandle = env->CallLongMethod(result, v8ArrayGetHandleMethodID);
  Handle<Object> parameters = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(parameterHandle));
  for (int i = 0; i < size; i++) {
    parameters->Set(i, args[i]);
  }
  return result;
}

void voidCallback(const FunctionCallbackInfo<Value>& args) {
  int size = args.Length();
  Local<External> data = Local<External>::Cast(args.Data());
  void *methodDescriptorPtr = data->Value();
  MethodDescriptor* md = static_cast<MethodDescriptor*>(methodDescriptorPtr);
  jobject v8Ctx = reinterpret_cast<V8Context*>(md->v8ContextPtr)->v8Ctx;
  Isolate* isolate = reinterpret_cast<V8Context*>(md->v8ContextPtr)->getRuntime()->isolate;
  Isolate::Scope isolateScope(isolate);
  JNIEnv * env;
  getJNIEnv(env);
  jobject parameters = createParameterArray(env, md->v8ContextPtr, v8Ctx, size, args);
  Handle<Value> receiver = args.This();
  jobject jreceiver = getResult(env, v8Ctx, md->v8ContextPtr, receiver, com_eclipsesource_v8_V8API_UNKNOWN);
  env->CallVoidMethod(v8Ctx, v8CallVoidMethodID, md->methodID, jreceiver, parameters);
  if (env->ExceptionCheck()) {
    Isolate* isolate = getIsolate(env, md->v8ContextPtr);
    reinterpret_cast<V8Context*>(md->v8ContextPtr)->getRuntime()->pendingException = env->ExceptionOccurred();
    env->ExceptionClear();
    jstring exceptionMessage = (jstring)env->CallObjectMethod(reinterpret_cast<V8Context*>(md->v8ContextPtr)->getRuntime()->pendingException, throwableGetMessageMethodID);
    if (exceptionMessage != NULL) {
      Local<String> v8String = createV8String(env, isolate, exceptionMessage);
      isolate->ThrowException(v8String);
    }
    else {
      isolate->ThrowException(String::NewFromUtf8(isolate, "Unhandled Java Exception"));
    }
  }
  env->CallVoidMethod(parameters, v8ArrayReleaseMethodID);
  env->CallVoidMethod(jreceiver, v8ObjectReleaseMethodID);
  env->DeleteLocalRef(jreceiver);
  env->DeleteLocalRef(parameters);
}

int getReturnType(JNIEnv* env, jobject &object) {
  int result = com_eclipsesource_v8_V8API_NULL;
  if (env->IsInstanceOf(object, integerCls)) {
    result = com_eclipsesource_v8_V8API_INTEGER;
  }
  else if (env->IsInstanceOf(object, doubleCls)) {
    result = com_eclipsesource_v8_V8API_DOUBLE;
  }
  else if (env->IsInstanceOf(object, booleanCls)) {
    result = com_eclipsesource_v8_V8API_BOOLEAN;
  }
  else if (env->IsInstanceOf(object, stringCls)) {
    result = com_eclipsesource_v8_V8API_STRING;
  }
  else if (env->IsInstanceOf(object, v8ArrayCls)) {
    result = com_eclipsesource_v8_V8API_V8_ARRAY;
  }
  else if (env->IsInstanceOf(object, v8ObjectCls)) {
    result = com_eclipsesource_v8_V8API_V8_OBJECT;
  }
  else if (env->IsInstanceOf(object, v8ArrayBufferCls)) {
    result = com_eclipsesource_v8_V8API_V8_ARRAY_BUFFER;
  }
  return result;
}

int getInteger(JNIEnv* env, jobject &object) {
  return env->CallIntMethod(object, integerIntValueMethodID);
}

bool getBoolean(JNIEnv* env, jobject &object) {
  return env->CallBooleanMethod(object, booleanBoolValueMethodID);
}

double getDouble(JNIEnv* env, jobject &object) {
  return env->CallDoubleMethod(object, doubleDoubleValueMethodID);
}

void objectCallback(const FunctionCallbackInfo<Value>& args) {
  int size = args.Length();
  Local<External> data = Local<External>::Cast(args.Data());
  void *methodDescriptorPtr = data->Value();
  MethodDescriptor* md = static_cast<MethodDescriptor*>(methodDescriptorPtr);
  jobject v8Ctx = reinterpret_cast<V8Context*>(md->v8ContextPtr)->v8Ctx;
  Isolate* isolate = reinterpret_cast<V8Context*>(md->v8ContextPtr)->getRuntime()->isolate;
  Isolate::Scope isolateScope(isolate);
  JNIEnv * env;
  getJNIEnv(env);
  jobject parameters = createParameterArray(env, md->v8ContextPtr, v8Ctx, size, args);
  Handle<Value> receiver = args.This();
  jobject jreceiver = getResult(env, v8Ctx, md->v8ContextPtr, receiver, com_eclipsesource_v8_V8API_UNKNOWN);
  jobject resultObject = env->CallObjectMethod(v8Ctx, v8CallObjectJavaMethodMethodID, md->methodID, jreceiver, parameters);
  if (env->ExceptionCheck()) {
    resultObject = NULL;
    Isolate* isolate = getIsolate(env, md->v8ContextPtr);
    reinterpret_cast<V8Context*>(md->v8ContextPtr)->getRuntime()->pendingException = env->ExceptionOccurred();
    env->ExceptionClear();
    jstring exceptionMessage = (jstring)env->CallObjectMethod(reinterpret_cast<V8Context*>(md->v8ContextPtr)->getRuntime()->pendingException, throwableGetMessageMethodID);
    if (exceptionMessage != NULL) {
      Local<String> v8String = createV8String(env, isolate, exceptionMessage);
      isolate->ThrowException(v8String);
    }
    else {
      isolate->ThrowException(String::NewFromUtf8(isolate, "Unhandled Java Exception"));
    }
  }
  else if (resultObject == NULL) {
    args.GetReturnValue().SetNull();
  }
  else {
    int returnType = getReturnType(env, resultObject);
    if (returnType == com_eclipsesource_v8_V8API_INTEGER) {
      args.GetReturnValue().Set(getInteger(env, resultObject));
    }
    else if (returnType == com_eclipsesource_v8_V8API_BOOLEAN) {
      args.GetReturnValue().Set(getBoolean(env, resultObject));
    }
    else if (returnType == com_eclipsesource_v8_V8API_DOUBLE) {
      args.GetReturnValue().Set(getDouble(env, resultObject));
    }
    else if (returnType == com_eclipsesource_v8_V8API_STRING) {
      jstring stringResult = (jstring)resultObject;
      Local<String> result = createV8String(env, reinterpret_cast<V8Context*>(md->v8ContextPtr)->getRuntime()->isolate, stringResult);
      args.GetReturnValue().Set(result);
    }
    else if (returnType == com_eclipsesource_v8_V8API_V8_ARRAY) {
      if (isUndefined(env, resultObject)) {
        args.GetReturnValue().SetUndefined();
      }
      else {
        jlong resultHandle = getHandle(env, resultObject);
        Handle<Object> result = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(resultHandle));
        releaseArray(env, resultObject);
        args.GetReturnValue().Set(result);
      }
    }
    else if (returnType == com_eclipsesource_v8_V8API_V8_OBJECT) {
      if (isUndefined(env, resultObject)) {
        args.GetReturnValue().SetUndefined();
      }
      else {
        jlong resultHandle = getHandle(env, resultObject);
        Handle<Object> result = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(resultHandle));
        release(env, resultObject);
        args.GetReturnValue().Set(result);
      }
    }
    else if (returnType == com_eclipsesource_v8_V8API_V8_ARRAY_BUFFER) {
      if (isUndefined(env, resultObject)) {
        args.GetReturnValue().SetUndefined();
      }
      else {
        jlong resultHandle = getHandle(env, resultObject);
        Handle<Object> result = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(resultHandle));
        release(env, resultObject);
        args.GetReturnValue().Set(result);
      }
    }
    else {
      args.GetReturnValue().SetUndefined();
    }
  }
  if (resultObject != NULL) {
    env->DeleteLocalRef(resultObject);
  }
  env->CallVoidMethod(parameters, v8ArrayReleaseMethodID);
  env->CallVoidMethod(jreceiver, v8ObjectReleaseMethodID);
  env->DeleteLocalRef(jreceiver);
  env->DeleteLocalRef(parameters);
}

JNIEXPORT jlongArray JNICALL Java_com_eclipsesource_v8_V8API__1initNewV8Function
(JNIEnv *env, jobject, jlong v8ContextPtr) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  MethodDescriptor* md = new MethodDescriptor();
  Local<External> ext = External::New(isolate, md);
  Persistent<External> pext(isolate, ext);
  isolate->IdleNotification(1000);
  pext.SetWeak(md, [](v8::WeakCallbackInfo<MethodDescriptor> const& data) {
    MethodDescriptor* md = data.GetParameter();
    jobject v8Ctx = reinterpret_cast<V8Context*>(md->v8ContextPtr)->v8Ctx;
    JNIEnv * env;
    getJNIEnv(env);
    env->CallVoidMethod(v8Ctx, v8DisposeMethodID, md->methodID);
    delete(md);
  }, WeakCallbackType::kParameter);

  Local<Function> function = Function::New(isolate, objectCallback, ext);
  md->v8ContextPtr = v8ContextPtr;
  Persistent<Object>* container = new Persistent<Object>;
  container->Reset(runtime->isolate, function);
  md->methodID = reinterpret_cast<jlong>(md);

  // Position 0 is the pointer to the container, position 1 is the pointer to the descriptor
  jlongArray result = env->NewLongArray(2);
  jlong * fill = new jlong[2];
  fill[0] = reinterpret_cast<jlong>(container);
  fill[1] = md->methodID;
  (env)->SetLongArrayRegion(result, 0, 2, fill);
  return result;
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1setWeak
  (JNIEnv * env, jobject, jlong v8ContextPtr, jlong objectHandle) {
    Isolate* isolate = SETUP(env, v8ContextPtr, );
    WeakReferenceDescriptor* wrd = new WeakReferenceDescriptor();
    wrd->v8ContextPtr = v8ContextPtr;
    wrd->objectHandle = objectHandle;
    reinterpret_cast<Persistent<Object>*>(objectHandle)->SetWeak(wrd, [](v8::WeakCallbackInfo<WeakReferenceDescriptor> const& data) {
      WeakReferenceDescriptor* wrd = data.GetParameter();
      JNIEnv * env;
      getJNIEnv(env);
      jobject v8Ctx = reinterpret_cast<V8Context*>(wrd->v8ContextPtr)->v8Ctx;
      env->CallVoidMethod(v8Ctx, v8WeakReferenceReleased, wrd->objectHandle);
      delete(wrd);
    }, WeakCallbackType::kFinalizer);
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1clearWeak
  (JNIEnv * env, jobject, jlong v8ContextPtr, jlong objectHandle) {
    Isolate* isolate = SETUP(env, v8ContextPtr, );
    reinterpret_cast<Persistent<Object>*>(objectHandle)->ClearWeak();
}

JNIEXPORT jboolean JNICALL Java_com_eclipsesource_v8_V8API__1isWeak
  (JNIEnv * env, jobject, jlong v8ContextPtr, jlong objectHandle) {
    Isolate* isolate = SETUP(env, v8ContextPtr, 0);
    return reinterpret_cast<Persistent<Object>*>(objectHandle)->IsWeak();
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1registerJavaMethod
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jstring functionName, jboolean voidMethod) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  FunctionCallback callback = voidCallback;
  if (!voidMethod) {
    callback = objectCallback;
  }
  Handle<Object> object = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  Local<String> v8FunctionName = createV8String(env, isolate, functionName);
  isolate->IdleNotification(1000);
  MethodDescriptor* md= new MethodDescriptor();
  Local<External> ext =  External::New(isolate, md);
  Persistent<External> pext(isolate, ext);
  pext.SetWeak(md, [](v8::WeakCallbackInfo<MethodDescriptor> const& data) {
    MethodDescriptor* md = data.GetParameter();
    jobject v8Ctx = reinterpret_cast<V8Context*>(md->v8ContextPtr)->v8Ctx;
    JNIEnv * env;
    getJNIEnv(env);
    env->CallVoidMethod(v8Ctx, v8DisposeMethodID, md->methodID);
    delete(md);
  }, WeakCallbackType::kParameter);

  md->methodID = reinterpret_cast<jlong>(md);
  md->v8ContextPtr = v8ContextPtr;
  object->Set(v8FunctionName, Function::New(isolate, callback, ext));
  return md->methodID;
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1releaseMethodDescriptor
  (JNIEnv *, jobject, jlong, jlong methodDescriptorPtr) {
  MethodDescriptor* md = reinterpret_cast<MethodDescriptor*>(methodDescriptorPtr);
  delete(md);
}

JNIEXPORT void JNICALL Java_com_eclipsesource_v8_V8API__1setPrototype
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jlong prototypeHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, );
  Handle<Object> object = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  Handle<Object> prototype = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(prototypeHandle));
  object->SetPrototype(prototype);
}

JNIEXPORT jboolean JNICALL Java_com_eclipsesource_v8_V8API__1equals
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jlong thatHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, false);
  Handle<Object> object = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  Handle<Object> that = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  if (objectHandle == 0) {
    object = context->Global();
  }
  if (thatHandle == 0) {
  	that = context->Global();
  }
  return object->Equals(that);
}

JNIEXPORT jstring JNICALL Java_com_eclipsesource_v8_V8API__1toString
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, 0);
  Handle<Object> object = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  String::Value unicodeString(object->ToString(isolate));

  return env->NewString(*unicodeString, unicodeString.length());
}

JNIEXPORT jboolean JNICALL Java_com_eclipsesource_v8_V8API__1strictEquals
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jlong thatHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, false);
  Handle<Object> object = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  Handle<Object> that = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(thatHandle));
  if (objectHandle == reinterpret_cast<jlong>(v8Context->globalObject)) {
    object = context->Global();
  }
  if (thatHandle == reinterpret_cast<jlong>(v8Context->globalObject)) {
  	that = context->Global();
  }
  return object->StrictEquals(that);
}

JNIEXPORT jboolean JNICALL Java_com_eclipsesource_v8_V8API__1sameValue
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle, jlong thatHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, false);
  Handle<Object> object = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  Handle<Object> that = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  if (objectHandle == reinterpret_cast<jlong>(v8Context->globalObject)) {
    object = context->Global();
  }
  if (thatHandle == reinterpret_cast<jlong>(v8Context->globalObject)) {
  	that = context->Global();
  }
  return object->SameValue(that);
}

JNIEXPORT jint JNICALL Java_com_eclipsesource_v8_V8API__1identityHash
(JNIEnv *env, jobject, jlong v8ContextPtr, jlong objectHandle) {
  Isolate* isolate = SETUP(env, v8ContextPtr, false);
  Handle<Object> object = Local<Object>::New(isolate, *reinterpret_cast<Persistent<Object>*>(objectHandle));
  if (objectHandle == reinterpret_cast<jlong>(v8Context->globalObject)) {
    object = context->Global();
  }
  return object->GetIdentityHash();
}

Isolate* getIsolate(JNIEnv *env, jlong v8ContextPtr) {
  if (v8ContextPtr == 0) {
    throwError(env, "V8 isolate not found.");
    return NULL;
  }
  V8Context* ctx = reinterpret_cast<V8Context*>(v8ContextPtr);
  return ctx->getRuntime()->isolate;
}

void throwResultUndefinedException(JNIEnv *env, const char *message) {
  (env)->ThrowNew(v8ResultsUndefinedCls, message);
}

void throwParseException(JNIEnv *env, const char* fileName, int lineNumber, String::Value *message,
  String::Value *sourceLine, int startColumn, int endColumn) {
  jstring jfileName = env->NewStringUTF(fileName);
  jstring jmessage = env->NewString(**message, message->length());
  jstring jsourceLine = env->NewString(**sourceLine, sourceLine->length());
  jthrowable result = (jthrowable)env->NewObject(v8ScriptCompilationCls, v8ScriptCompilationInitMethodID, jfileName, lineNumber, jmessage, jsourceLine, startColumn, endColumn);
  env->DeleteLocalRef(jfileName);
  env->DeleteLocalRef(jmessage);
  env->DeleteLocalRef(jsourceLine);
  (env)->Throw(result);
}

void throwExecutionException(JNIEnv *env, const char* fileName, int lineNumber, String::Value *message,
  String::Value* sourceLine, int startColumn, int endColumn, const char* stackTrace, jlong v8ContextPtr) {
  jstring jfileName = env->NewStringUTF(fileName);
  jstring jmessage = env->NewString(**message, message->length());
  jstring jsourceLine = env->NewString(**sourceLine, sourceLine->length());
  jstring jstackTrace = NULL;
  if (stackTrace != NULL) {
    jstackTrace = env->NewStringUTF(stackTrace);
  }
  jthrowable wrappedException = NULL;
  if (env->ExceptionCheck()) {
    wrappedException = env->ExceptionOccurred();
    env->ExceptionClear();
  }
  if (reinterpret_cast<V8Context*>(v8ContextPtr)->getRuntime()->pendingException != NULL) {
    wrappedException = reinterpret_cast<V8Context*>(v8ContextPtr)->getRuntime()->pendingException;
    reinterpret_cast<V8Context*>(v8ContextPtr)->getRuntime()->pendingException = NULL;
  }
  if ( wrappedException != NULL && !env->IsInstanceOf( wrappedException, throwableCls) ) {
    std::cout << "Wrapped Exception is not a Throwable" << std::endl;
    wrappedException = NULL;
  }
  jthrowable result = (jthrowable)env->NewObject(v8ScriptExecutionExceptionCls, v8ScriptExecutionExceptionInitMethodID, jfileName, lineNumber, jmessage, jsourceLine, startColumn, endColumn, jstackTrace, wrappedException);
  env->DeleteLocalRef(jfileName);
  env->DeleteLocalRef(jmessage);
  env->DeleteLocalRef(jsourceLine);

  env->CallVoidMethod(reinterpret_cast<V8Context*>(v8ContextPtr)->exLsnr, v8ScriptExecutionExceptionListenerCallMethodID, result);

  (env)->Throw(result);
}

void throwParseException(JNIEnv *env, Isolate* isolate, TryCatch* tryCatch) {
  String::Value exception(tryCatch->Exception());
  Handle<Message> message = tryCatch->Message();
  if (message.IsEmpty()) {
    throwV8RuntimeException(env, &exception);
  }
  else {
    String::Utf8Value filename(message->GetScriptResourceName());
    int lineNumber = message->GetLineNumber();
    String::Value sourceline(message->GetSourceLine());
    int start = message->GetStartColumn();
    int end = message->GetEndColumn();
    const char* filenameString = ToCString(filename);
    throwParseException(env, filenameString, lineNumber, &exception, &sourceline, start, end);
  }
}

void throwExecutionException(JNIEnv *env, Isolate* isolate, TryCatch* tryCatch, jlong v8ContextPtr) {
  String::Value exception(tryCatch->Exception());
  Handle<Message> message = tryCatch->Message();
  if (message.IsEmpty()) {
    throwV8RuntimeException(env, &exception);
  }
  else {
    String::Utf8Value filename(message->GetScriptResourceName());
    int lineNumber = message->GetLineNumber();
    String::Value sourceline(message->GetSourceLine());
    int start = message->GetStartColumn();
    int end = message->GetEndColumn();
    const char* filenameString = ToCString(filename);
    String::Utf8Value stack_trace(tryCatch->StackTrace());
    const char* stackTrace = NULL;
    if (stack_trace.length() > 0) {
      stackTrace = ToCString(stack_trace);
    }
    throwExecutionException(env, filenameString, lineNumber, &exception, &sourceline, start, end, stackTrace, v8ContextPtr);
  }
}

void throwV8RuntimeException(JNIEnv *env, String::Value *message) {
  jstring exceptionString = env->NewString(**message, message->length());
  jthrowable exception = (jthrowable)env->NewObject(v8RuntimeExceptionCls, v8RuntimeExceptionInitMethodID, exceptionString);
  (env)->Throw(exception);
  env->DeleteLocalRef(exceptionString);
}

void throwError(JNIEnv *env, const char *message) {
  (env)->ThrowNew(errorCls, message);
}

jobject getResult(JNIEnv *env, jobject &v8Ctx, jlong v8ContextPtr, Handle<Value> &result, jint expectedType) {
  if (result->IsUndefined() && expectedType == com_eclipsesource_v8_V8API_V8_ARRAY) {
    jobject objectResult = env->NewObject(undefinedV8ArrayCls, undefinedV8ArrayInitMethodID, v8Ctx);
    return objectResult;
  }
  else if (result->IsUndefined() && (expectedType == com_eclipsesource_v8_V8API_V8_OBJECT || expectedType == com_eclipsesource_v8_V8API_NULL)) {
    jobject objectResult = env->NewObject(undefinedV8ObjectCls, undefinedV8ObjectInitMethodID, v8Ctx);
    return objectResult;
  }
  else if (result->IsInt32()) {
    return env->NewObject(integerCls, integerInitMethodID, result->Int32Value());
  }
  else if (result->IsNumber()) {
    return env->NewObject(doubleCls, doubleInitMethodID, result->NumberValue());
  }
  else if (result->IsBoolean()) {
    return env->NewObject(booleanCls, booleanInitMethodID, result->BooleanValue());
  }
  else if (result->IsString()) {
    v8::Isolate* isolate = reinterpret_cast<V8Context*>(v8ContextPtr)->getRuntime()->isolate;

    String::Value unicodeString(result->ToString(isolate));

    return env->NewString(*unicodeString, unicodeString.length());
  }
  else if (result->IsFunction()) {
    jobject objectResult = env->NewObject(v8FunctionCls, v8FunctionInitMethodID, v8Ctx);
    jlong resultHandle = getHandle(env, objectResult);

    v8::Isolate* isolate = reinterpret_cast<V8Context*>(v8ContextPtr)->getRuntime()->isolate;

    reinterpret_cast<Persistent<Object>*>(resultHandle)->Reset(isolate, result->ToObject(isolate));

    return objectResult;
  }
  else if (result->IsArray()) {
    jobject objectResult = env->NewObject(v8ArrayCls, v8ArrayInitMethodID, v8Ctx);
    jlong resultHandle = getHandle(env, objectResult);

    v8::Isolate* isolate = reinterpret_cast<V8Context*>(v8ContextPtr)->getRuntime()->isolate;

    reinterpret_cast<Persistent<Object>*>(resultHandle)->Reset(isolate, result->ToObject(isolate));

    return objectResult;
  }
  else if (result->IsTypedArray()) {
      jobject objectResult = env->NewObject(v8TypedArrayCls, v8TypedArrayInitMethodID, v8Ctx);
      jlong resultHandle = getHandle(env, objectResult);

      v8::Isolate* isolate = reinterpret_cast<V8Context*>(v8ContextPtr)->getRuntime()->isolate;

      reinterpret_cast<Persistent<Object>*>(resultHandle)->Reset(isolate, result->ToObject(isolate));

      return objectResult;
  }
  else if (result->IsArrayBuffer()) {
    v8::Isolate* isolate = reinterpret_cast<V8Context*>(v8ContextPtr)->getRuntime()->isolate;

    ArrayBuffer* arrayBuffer = ArrayBuffer::Cast(*result);
    if ( arrayBuffer->ByteLength() == 0 || arrayBuffer->GetContents().Data() == NULL ) {
      jobject objectResult = env->NewObject(v8ArrayBufferCls, v8ArrayBufferInitMethodID, v8Ctx, NULL);
      jlong resultHandle = getHandle(env, objectResult);
      reinterpret_cast<Persistent<Object>*>(resultHandle)->Reset(isolate, result->ToObject(isolate));
      return objectResult;
    }
    jobject byteBuffer = env->NewDirectByteBuffer(arrayBuffer->GetContents().Data(), arrayBuffer->ByteLength());
    jobject objectResult = env->NewObject(v8ArrayBufferCls, v8ArrayBufferInitMethodID, v8Ctx, byteBuffer);
    jlong resultHandle = getHandle(env, objectResult);

    reinterpret_cast<Persistent<Object>*>(resultHandle)->Reset(isolate, result->ToObject(isolate));

    return objectResult;
  }
  else if (result->IsObject()) {
    jobject objectResult = env->NewObject(v8ObjectCls, v8ObjectInitMethodID, v8Ctx);
    jlong resultHandle = getHandle(env, objectResult);

    v8::Isolate* isolate = reinterpret_cast<V8Context*>(v8ContextPtr)->getRuntime()->isolate;

    reinterpret_cast<Persistent<Object>*>(resultHandle)->Reset(isolate, result->ToObject(isolate));

    return objectResult;
  }

  return NULL;
}

JNIEXPORT jlong JNICALL Java_com_eclipsesource_v8_V8API__1getBuildID
  (JNIEnv *, jobject) {
  return 2;
}
