package com.eclipsesource.v8;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class V8ContextTest {

	@Test
	public void singleContextTest() {
		V8Isolate v8Isolate = V8Isolate.create();

		V8Context a = v8Isolate.createContext();

		a.add("x", 7);
		a.add("x", 9);

		assertEquals(9, a.get("x"));
		v8Isolate.close();
	}

	@Test
	public void multipleContextTest() {
		V8Isolate v8Isolate = V8Isolate.create();

		V8Context a = v8Isolate.createContext();
		V8Context b = v8Isolate.createContext();

		a.add("x", 7);
		b.add("x", 9);

		assertNotEquals(a.get("x"), b.get("x"));
		v8Isolate.close();
	}

	@Test
	public void closeMultipleContextTest() {
		V8Isolate v8Isolate = V8Isolate.create();

		List<V8Context> contexts = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			contexts.add(v8Isolate.createContext());
		}

		v8Isolate.close();
		assertEquals(0, v8Isolate.getObjectReferenceCount());
	}
}
