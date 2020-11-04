package com.eclipsesource.v8.utils;

import com.eclipsesource.v8.V8ScriptException;

import java.util.concurrent.Callable;

public class V8QueueMessage {
	private final String script;
	private final Object[] args;
	private final Callable<Void> callback;
	private V8ResultConsumer consumer;
	private V8ScriptException exception;

	public V8QueueMessage(Callable<Void> callback, String script, Object... args) {
		this.callback = callback;
		this.script = script;
		this.args = args;
	}

	public V8QueueMessage(Callable<Void> callback, V8ResultConsumer consumer, String script, Object... args) {
		this.callback = callback;
		this.script = script;
		this.consumer = consumer;
		this.args = args;
	}

	boolean hasConsumer() {
		return consumer != null;
	}

	V8ResultConsumer getConsumer() {
		return consumer;
	}

	Object[] getArgs() {
		return args;
	}

	String getScript() {
		return script;
	}

	void setException(V8ScriptException exception) {
		this.exception = exception;
	}

	public void checkResult() throws V8ScriptException {
		if (exception != null) {
			throw exception;
		}
	}

	Callable<Void> getCallback() {
		return callback;
	}
}
