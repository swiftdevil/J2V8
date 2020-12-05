package com.eclipsesource.v8.utils;

import com.eclipsesource.v8.*;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;


public class NodeJSEnv implements V8IsolateRunnable {
	private final LinkedBlockingQueue<V8QueueMessage> messages = new LinkedBlockingQueue<>();
	private final AtomicBoolean running = new AtomicBoolean(false);

	private final Consumer<V8Function> njsInit;
	private final Consumer<NodeJS> njsStartup;

	public NodeJSEnv(Consumer<V8Function> njsInit, Consumer<NodeJS> njsStartup) {
		this.njsInit = njsInit;
		this.njsStartup = njsStartup;
	}

	public void postMessage(V8QueueMessage qm) {
		messages.add(qm);
	}

	public void destroy() {
		running.set(false);
	}

	@Override
	public void run(V8Isolate isolate) {
		NodeJS njs = NodeJS.createNodeJS(isolate).setInitConsumer(njsInit);
		V8Context ctx = njs.getContext();
		MemoryManager mm = new MemoryManager(ctx);

		njs.start();

		if (njsStartup != null) {
			njsStartup.accept(njs);
		}

		try {
			running.set(true);
			while (running.get()) {
				try {
					pollQueue(njs);
				} catch (InterruptedException e) {
					running.set(false);
					Thread.currentThread().interrupt();

					return;
				}
			}
		} finally {
			messages.clear();
			mm.close();
			njs.closeContext(true);
		}
	}

	private void pollQueue(NodeJS njs) throws InterruptedException {
		V8QueueMessage qm = messages.poll(10, TimeUnit.MILLISECONDS);
		if (qm == null) {
			return;
		}

		try (V8Array args = V8ObjectUtils.toV8Array(njs.getContext(), Arrays.asList(qm.getArgs()))) {
			String[] methodPath = qm.getMethod().split("\\.");

			V8Object f = njs.getContext();
			for (String method : methodPath) {
				f = (V8Object) f.get(method);
			}

			if (!(f instanceof V8Function)) {
				throw new V8InvalidMethodException(qm.getMethod());
			}

			Object o = ((V8Function) f).call(f, args);
			njs.getContext().checkPendingException();
			njs.pump();

			if (qm.hasConsumer()) {
				qm.getConsumer().apply(njs.getContext(), qm, o);
			}
		} catch (V8RuntimeException e) {
			qm.setException(e);
		} finally {
			try {
				qm.getCallback().call();
			} catch (Exception e) {
				// should probably log this out or something
			}
		}
	}
}
