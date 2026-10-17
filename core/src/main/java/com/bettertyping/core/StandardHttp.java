package com.bettertyping.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The {@link LanguagePacks.Http} the game uses, over the JDK's client. The client starts on the first
 * download, and a download has {@code deadline} to finish, however slowly the bytes arrive.
 */
public final class StandardHttp implements LanguagePacks.Http {
	private final String userAgent;
	private final Duration deadline;
	private HttpClient client;

	public StandardHttp(String userAgent, Duration deadline) {
		this.userAgent = userAgent;
		this.deadline = deadline;
	}

	@Override
	public InputStream get(URI uri, long maxBytes) throws IOException {
		return new ByteArrayInputStream(fetch(uri, maxBytes));
	}

	private synchronized HttpClient client() {
		if (client == null) {
			client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		}
		return client;
	}

	private byte[] fetch(URI uri, long maxBytes) throws IOException {
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(deadline).header("User-Agent", userAgent).build();
		CompletableFuture<HttpResponse<byte[]>> response = client().sendAsync(request, info ->
			info.statusCode() == 200 ? new LimitedBody(maxBytes) : HttpResponse.BodySubscribers.replacing(null));
		try {
			HttpResponse<byte[]> result = response.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
			if (result.statusCode() != 200) {
				throw new IOException("HTTP " + result.statusCode() + " from " + uri);
			}
			return result.body();
		} catch (TimeoutException e) {
			response.cancel(true);
			throw new IOException("No complete answer from " + uri + " within " + deadline.toSeconds() + " s", e);
		} catch (ExecutionException e) {
			throw e.getCause() instanceof IOException io ? io : new IOException("Couldn't fetch " + uri, e.getCause());
		} catch (InterruptedException e) {
			response.cancel(true);
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while fetching " + uri, e);
		}
	}

	/** Collects a response body, giving up as soon as it's larger than allowed. */
	private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
		private final CompletableFuture<byte[]> result = new CompletableFuture<>();
		private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		private final long maxBytes;
		private Flow.Subscription subscription;

		LimitedBody(long maxBytes) {
			this.maxBytes = maxBytes;
		}

		@Override
		public CompletionStage<byte[]> getBody() {
			return result;
		}

		@Override
		public void onSubscribe(Flow.Subscription subscription) {
			this.subscription = subscription;
			subscription.request(Long.MAX_VALUE);
		}

		@Override
		public void onNext(List<ByteBuffer> buffers) {
			if (result.isDone()) {
				return;
			}
			for (ByteBuffer buffer : buffers) {
				byte[] chunk = new byte[buffer.remaining()];
				buffer.get(chunk);
				bytes.writeBytes(chunk);
			}
			if (bytes.size() > maxBytes) {
				subscription.cancel();
				result.completeExceptionally(new IOException("Larger than the expected " + maxBytes + " bytes"));
			}
		}

		@Override
		public void onError(Throwable error) {
			result.completeExceptionally(error);
		}

		@Override
		public void onComplete() {
			result.complete(bytes.toByteArray());
		}
	}
}
