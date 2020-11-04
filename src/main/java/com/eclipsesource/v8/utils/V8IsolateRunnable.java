package com.eclipsesource.v8.utils;

import com.eclipsesource.v8.V8Isolate;

/**
 * Classes can implement this interface to execute arbitrary code on
 * isolated V8 runtime on its own thread. Instances of classes that
 * implement this interface can be passed to V8Thread.
 */
public interface V8IsolateRunnable {
	/**
	 * Execute the code on the provided runtime.
	 *
	 * @param isolate The V8 runtime assigned to this runnable.
	 */
	public void run(final V8Isolate isolate);

	public void destroy();
}
