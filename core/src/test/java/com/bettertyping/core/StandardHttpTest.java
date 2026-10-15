package com.bettertyping.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The real {@link LanguagePacks#standardHttp} against a local server. */
class StandardHttpTest {
	private static final byte[] BODY = "word list".getBytes();

	private HttpServer server;
	private final LanguagePacks.Http http = LanguagePacks.standardHttp("test", Duration.ofSeconds(1));

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool(task -> {
			Thread thread = new Thread(task);
			thread.setDaemon(true);
			return thread;
		}));
		server.createContext("/pack", exchange -> {
			exchange.sendResponseHeaders(200, BODY.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(BODY);
			}
		});
		server.createContext("/missing", exchange -> {
			exchange.sendResponseHeaders(404, -1);
			exchange.close();
		});
		server.createContext("/stalls", exchange -> {
			exchange.sendResponseHeaders(200, 1_000);
			OutputStream out = exchange.getResponseBody();
			out.write(BODY);
			out.flush();
			try {
				Thread.sleep(30_000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private URI uri(String path) {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
	}

	@Test
	void returnsTheBody() throws IOException {
		try (InputStream in = http.get(uri("/pack"), 100)) {
			assertArrayEquals(BODY, in.readAllBytes());
		}
	}

	@Test
	void failsOnAnErrorStatus() {
		assertThrows(IOException.class, () -> http.get(uri("/missing"), 100));
	}

	@Test
	void failsWhenTheBodyIsLargerThanAllowed() {
		assertThrows(IOException.class, () -> http.get(uri("/pack"), 3));
	}

	@Test
	void givesUpOnABodyThatStopsArriving() {
		assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
			assertThrows(IOException.class, () -> http.get(uri("/stalls"), 2_000));
		});
	}
}
