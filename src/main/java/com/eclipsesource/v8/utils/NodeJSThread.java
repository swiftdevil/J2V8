package com.eclipsesource.v8.utils;

import com.eclipsesource.v8.V8Isolate;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A Thread with its own V8 runtime. The thread will create a runtime,
 * and execute runnables on that runtime. When the thread ends,
 * the runtime will be released.
 *
 * This is meant to be run as part of a nodeJS executor pool.
 *
 * It's suggested that you *DO NOT* release the lock on the runtime.
 * If the lock is released, you will need to ensure that the runtime
 * is properly released.
 */
public class NodeJSThread extends Thread {
	private NodeJSEnv nodeEnv;
	private final AtomicBoolean running;
	private final Object monitor;

	public NodeJSThread() {
		running = new AtomicBoolean(false);
		monitor = new Object();
	}

	public NodeJSEnv getNodeEnv() {
		return nodeEnv;
	}

	public void setNodeEnv(NodeJSEnv nodeEnv) {
		this.nodeEnv = nodeEnv;
		synchronized (monitor) {
			monitor.notify();
		}
	}

	public void clearNodeEnv() {
		if (nodeEnv != null) {
			nodeEnv.destroy();
			nodeEnv = null;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {
		//V8Isolate isolate = V8Isolate.create();
		running.set(true);

		while (running.get()) {
			try {
				while (nodeEnv == null) {
					synchronized (monitor) {
						monitor.wait(TimeUnit.SECONDS.toMillis(100));
					}
				}

				nodeEnv.run(V8Isolate.create());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} finally {
				clearNodeEnv();
			}
		}
	}

	public void shutdown() {
		running.set(false);
	}
}
