package com.eclipsesource.v8.utils;

import com.eclipsesource.v8.*;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;


public class NodeJSEnv implements V8IsolateRunnable {
	private final LinkedBlockingQueue<V8QueueMessage> messages = new LinkedBlockingQueue<>();
	private final AtomicBoolean running = new AtomicBoolean(false);

	private final Consumer<NodeJS> njsInit;

	public NodeJSEnv(Consumer<NodeJS> njsInit) {
		this.njsInit = njsInit;
	}

	public void postMessage(V8QueueMessage qm) {
		messages.add(qm);
	}

	public void destroy() {
		running.set(false);
	}

	@Override
	public void run(V8Isolate isolate) {
		NodeJS njs = NodeJS.createNodeJS(isolate).start();
		V8Context ctx = njs.getContext();
		MemoryManager mm = new MemoryManager(ctx);

		if (njsInit != null) {
			njsInit.accept(njs);
		}

		try {
			running.set(true);
			while (running.get()) {
				try {
					V8QueueMessage qm = messages.poll(100, TimeUnit.MILLISECONDS);
					if (qm == null) {
						continue;
					}

					Object o = null;

					try (V8Array parameters = new V8Array(ctx);
						 V8Array args = new V8Array(ctx);
						 V8Function f = V8ObjectUtils.toV8Function(ctx, qm.getScript())) {

						if (f == null) {
							continue;
						}

						for (Object arg : qm.getArgs()) {
							args.push(arg);
						}
						parameters.push(args);

						o = f.call(f, args);
						ctx.checkPendingException();
						njs.pump();
					} catch (V8ScriptException e) {
						qm.setException(e);
					} finally {
						try {
							if (qm.hasConsumer()) {
								qm.getConsumer().apply(ctx, o);
							}
						} finally {
							try {
								qm.getCallback().call();
							} catch (Exception e) {
								// should probably do something here...
							}
						}
					}
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
}
