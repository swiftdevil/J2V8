package com.eclipsesource.v8;

import java.util.ArrayList;
import java.util.List;

public class V8ScriptExecutionExceptionListener {
	private List<V8ExceptionListener> listeners = new ArrayList<>();

	public void addListener(V8ExceptionListener listener) {
		listeners.add(listener);
	}

	public void removeListener(V8ExceptionListener listener) {
		listeners.remove(listener);
	}

	public void onException(Throwable t) {
		listeners.forEach(l -> l.onException(t));
	}
}
