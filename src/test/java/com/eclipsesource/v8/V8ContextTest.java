package com.eclipsesource.v8;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class V8ContextTest {

	@Test
	public void singleContextTest() {
		V8 v8 = V8.createV8Runtime();

		V8Context a = v8.createContext();

		a.add("x", 7);
		a.add("x", 9);

		assertEquals(9, a.get("x"));
	}

	@Test
	public void multipleContextTest() {
		V8 v8 = V8.createV8Runtime();

		V8Context a = v8.createContext();
		V8Context b = v8.createContext();

		a.add("x", 7);
		b.add("x", 9);

		assertNotEquals(a.get("x"), b.get("x"));
	}

	@Test
	public void closeMultipleContextTest() {
		V8 v8 = V8.createV8Runtime();

		List<V8Context> contexts = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			contexts.add(v8.createContext());
		}

		contexts.forEach(V8Context::close);
		assertEquals(1, v8.getObjectReferenceCount());

		v8.close();
		assertEquals(0, v8.getObjectReferenceCount());
	}
}
