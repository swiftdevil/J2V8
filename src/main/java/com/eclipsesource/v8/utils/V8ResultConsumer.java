package com.eclipsesource.v8.utils;

import com.eclipsesource.v8.V8Context;
import com.eclipsesource.v8.V8RuntimeException;

import java.util.function.Consumer;
import java.util.function.Function;

public class V8ResultConsumer {
	private Consumer<V8RuntimeException> exceptionHandler;
	private Consumer<Object> consumer;
	private Function<V8Context, Object> retriever;
	private String resultVar;

	public V8ResultConsumer(Consumer<V8RuntimeException> exceptionHandler) {
		this.exceptionHandler = exceptionHandler;
	}

	public V8ResultConsumer(Consumer<V8RuntimeException> exceptionHandler, Consumer<Object> consumer) {
		this.exceptionHandler = exceptionHandler;
		this.consumer = consumer;
	}

	public V8ResultConsumer(Consumer<V8RuntimeException> exceptionHandler, Consumer<Object> consumer, Function<V8Context, Object> retriever) {
		this.exceptionHandler = exceptionHandler;
		this.consumer = consumer;
		this.retriever = retriever;
	}

	public V8ResultConsumer(Consumer<V8RuntimeException> exceptionHandler, Consumer<Object> consumer, String resultVar) {
		this.exceptionHandler = exceptionHandler;
		this.consumer = consumer;
		this.resultVar = resultVar;
	}

	void apply(V8Context context, V8QueueMessage qm, Object o) {
		if (exceptionHandler != null) {
			try {
				qm.checkResult();
			} catch (V8RuntimeException e) {
				exceptionHandler.accept(e);
			}
		}

		if (consumer != null) {
			Object result = o;

			if (retriever != null) {
				result = retriever.apply(context);
			} else if (resultVar != null) {
				result = context.get(resultVar);
			}

			consumer.accept(result);
		}
	}
}
