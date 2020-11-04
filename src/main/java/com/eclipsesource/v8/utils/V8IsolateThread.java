package com.eclipsesource.v8.utils;

import com.eclipsesource.v8.V8Isolate;

/**
 * A Thread with its own V8 runtime. The thread will create a runtime,
 * and execute a runnable on that runtime. When the thread ends,
 * the runtime will be released.
 *
 * It's suggested that you *DO NOT* release the lock on the runtime.
 * If the lock is released, you will need to ensure that the runtime
 * is properly released.
 */
public class V8IsolateThread extends Thread {

	private final V8IsolateRunnable target;

	/**
	 * Create as new Thread with its own V8Runtime.
	 *
	 * @param target The code to execute with the given runtime.
	 */
	public V8IsolateThread(final V8IsolateRunnable target) {
		this.target = target;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {
		V8Isolate runtime = V8Isolate.create();
		try {
			target.run(runtime);
		} finally {
			synchronized (this) {
				if (runtime.getLocker().hasLock()) {
					runtime.close();
					runtime = null;
				}
			}
		}
	}

}
