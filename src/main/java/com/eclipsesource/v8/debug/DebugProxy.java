package com.eclipsesource.v8.debug;

import java.io.Closeable;
import java.util.function.Consumer;

public class DebugProxy<T> implements Closeable {
	private Consumer<T> client;
	private Consumer<T> server;

	public DebugProxy(Consumer<T> client) {
		this.client = client;
	}

	void setServer(Consumer<T> server) {
		this.server = server;
	}

	public void toServer(T t) {
		if (server != null)
			server.accept(t);
	}

	void toClient(T t) {
		if (client != null)
			client.accept(t);
	}

	@Override
	public void close() {
		client = null;
		server = null;
	}
}
