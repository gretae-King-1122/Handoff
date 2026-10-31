package com.handoff.executor;

import com.handoff.api.links.ParsedLink;
import com.handoff.rules.links.KuaishouLinkParser;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Bounded redirect-only resolution. Never requests the landing page or a third-party host. */
final class KuaishouShortResolver {
    record Redirect(int status, String location) { }
    interface Transport {
        Redirect fetch(String url, int timeoutMillis) throws IOException;
        default void cancel() { }
    }
    private static final KuaishouLinkParser PARSER = new KuaishouLinkParser();
    private final Transport transport;
    private volatile boolean cancelled;

    KuaishouShortResolver() { this(new HttpTransport()); }
    KuaishouShortResolver(Transport transport) { this.transport = transport; }

    ParsedLink resolve(ParsedLink input) {
        if (input == null || input.kind() != ParsedLink.Kind.SHORT_LINK
                || !input.equals(PARSER.parseUrl(input.canonicalUrl()))) return null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        String current = input.canonicalUrl();
        Set<String> visited = new HashSet<>();
        try {
            for (int hop = 0; hop < 3 && !cancelled && !Thread.currentThread().isInterrupted(); hop++) {
                long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remaining <= 0 || !visited.add(current)) return null;
                Redirect response = transport.fetch(current, (int) Math.max(1, Math.min(2500, remaining / 2)));
                if (cancelled || System.nanoTime() >= deadline || response == null
                        || !Set.of(301, 302, 303, 307, 308).contains(response.status())
                        || response.location() == null || response.location().length() > 4096) return null;
                URI next = new URI(current).resolve(response.location());
                // Do not downgrade TLS, execute a scheme, or make even one request to an unknown host.
                if (!"https".equalsIgnoreCase(next.getScheme())) return null;
                ParsedLink parsed = PARSER.parseUrl(next.toString());
                if (parsed == null) return null;
                if (parsed.kind() == ParsedLink.Kind.VIDEO) return parsed;
                current = parsed.canonicalUrl();
            }
        } catch (Exception ignored) { /* No raw URL, response body or token in diagnostics. */ }
        return null;
    }

    void cancel() { cancelled = true; transport.cancel(); }

    private static final class HttpTransport implements Transport {
        private volatile HttpURLConnection active;
        private volatile boolean cancelled;
        @Override public Redirect fetch(String url, int timeoutMillis) throws IOException {
            if (cancelled) throw new IOException("Cancelled");
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            active = connection;
            try {
                if (cancelled) throw new IOException("Cancelled");
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(timeoutMillis);
                connection.setReadTimeout(timeoutMillis);
                connection.setUseCaches(false);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/132.0.0.0 Mobile Safari/537.36");
                return new Redirect(connection.getResponseCode(), connection.getHeaderField("Location"));
            } finally {
                connection.disconnect();
                active = null;
            }
        }
        @Override public void cancel() {
            cancelled = true;
            HttpURLConnection connection = active;
            if (connection != null) connection.disconnect();
        }
    }
}
