package com.eclipsesource.v8;

public class V8InvalidMethodException extends V8RuntimeException {
	public V8InvalidMethodException(String method) {
		super("Method " + method + " not found in the script environment");
	}
}
