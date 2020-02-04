package com.eclipsesource.v8;

import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class V8ContextTest {

	@Test
	public void singleContextTest() {
		V8Isolate isolate = V8Isolate.create();

		V8Context a = isolate.createContext();

		a.add("x", 7);
		a.add("x", 9);

		assertEquals(9, a.get("x"));
		isolate.close();
	}

	@Test
	public void multipleContextTest() {
		V8Isolate isolate = V8Isolate.create();

		V8Context a = isolate.createContext();
		V8Context b = isolate.createContext();

		a.add("x", 7);
		b.add("x", 9);

		assertNotEquals(a.get("x"), b.get("x"));
		isolate.close();
	}

	@Test
	public void closeMultipleContextTest() {
		V8Isolate isolate = V8Isolate.create();

		List<V8Context> contexts = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			contexts.add(isolate.createContext());
		}

		isolate.close();
		assertEquals(0, isolate.getObjectReferenceCount());
	}

	@Test
	public void closeIsolateIfLastContextTest() {
		V8Isolate isolate = V8Isolate.create();

		List<V8Context> contexts = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			contexts.add(isolate.createContext());
		}

		contexts.forEach(V8Context::close);
		assertFalse(isolate.isReleased());

		contexts.get(0).closeIsolateIfLastContext();
		assertTrue(isolate.isReleased());
	}
	
	@Test
	@Ignore
	public void benchmark() {
		AtomicLong total = new AtomicLong(0);
		AtomicLong t = new AtomicLong(System.currentTimeMillis());

		V8Isolate isolate = V8Isolate.create();
		System.out.println("isolate: " + time(t, total));

		V8Isolate.create();
		System.out.println("isolate: " + time(t, total));

		V8Isolate.create();
		System.out.println("isolate: " + time(t, total));

		for (int i = 0; i < 10; i++) {
			isolate.createContext();
			System.out.println("context: " + time(t, total));
		}

		isolate.close();
		System.out.println("close: " + time(t, total));

		System.out.println("total: " + total);
	}

	private static long time(AtomicLong t, AtomicLong total) {
		long v = System.currentTimeMillis() - t.getAndSet(System.currentTimeMillis());
		total.getAndAdd(v);
		return v;
	}
}
