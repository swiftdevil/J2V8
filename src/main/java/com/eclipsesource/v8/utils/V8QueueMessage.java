package com.eclipsesource.v8.utils;

import com.eclipsesource.v8.V8ScriptException;

public class V8QueueMessage {
	private final String script;
	private final String[] args;
	private V8ResultConsumer consumer;
	private V8ScriptException exception;

	public V8QueueMessage(String script, String... args) {
		this.script = script;
		this.args = args;
	}

	public V8QueueMessage(V8ResultConsumer consumer, String script, String... args) {
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

	String[] getArgs() {
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
}
