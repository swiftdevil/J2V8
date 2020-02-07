package com.eclipsesource.v8;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class V8ScriptExecutionExceptionListenerTest {
	@Test
	public void testExceptionListener() {
		V8Isolate isolate = V8Isolate.create();
		V8Context context = isolate.createContext();
		NodeJS njs = NodeJS.createNodeJS(context);

		ExListener exl = getExceptionListener(context);
		assertNull(exl.ex);

		njs.execAndPump("throw 'u suck'");
		assertNotNull(exl.ex);
	}

	private ExListener getExceptionListener(V8Context context) {
		ExListener exl = new ExListener();
		context.addExceptionListener(exl);

		return exl;
	}

	private static class ExListener implements V8ExceptionListener {
		Throwable ex;

		@Override
		public void onException(Throwable t) {
			ex = t;
		}
	}
}
