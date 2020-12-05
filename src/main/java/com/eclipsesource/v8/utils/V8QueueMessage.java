package com.eclipsesource.v8.utils;

import com.eclipsesource.v8.V8RuntimeException;

import java.util.concurrent.Callable;

public class V8QueueMessage {
	private final String method;
	private final Object[] args;
	private final Callable<Void> callback;
	private V8ResultConsumer consumer;
	private V8RuntimeException exception;

	public V8QueueMessage(Callable<Void> callback, String method, Object... args) {
		this.callback = callback;
		this.method = method;
		this.args = args;
	}

	public V8QueueMessage(Callable<Void> callback, V8ResultConsumer consumer, String method, Object... args) {
		this.callback = callback;
		this.method = method;
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

	String getMethod() {
		return method;
	}

	void setException(V8RuntimeException exception) {
		this.exception = exception;
	}

	public void checkResult() throws V8RuntimeException {
		if (exception != null) {
			throw exception;
		}
	}

	Callable<Void> getCallback() {
		return callback;
	}
}
