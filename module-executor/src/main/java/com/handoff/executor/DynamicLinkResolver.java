package com.handoff.executor;

import com.handoff.api.links.DynamicLinkPolicy;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Optional, bounded, HTTPS header-only redirects. No JS, HTML extraction, cookies or private schemes. */
final class DynamicLinkResolver {
    record Redirect(int status, String location) { }
    record Result(String url, List<DynamicAppResolver.Candidate> candidates) { }
    interface Discovery { List<DynamicAppResolver.Candidate> find(String url); }
    interface Transport {
        Redirect fetch(String url, int timeoutMillis) throws IOException;
        default void cancel() { }
    }
    private final Discovery discovery;
    private final Transport transport;
    private volatile boolean cancelled;
    DynamicLinkResolver(Discovery discovery) { this(discovery, new HttpTransport()); }
    DynamicLinkResolver(Discovery discovery, Transport transport) { this.discovery = discovery; this.transport = transport; }

    Result resolve(String input, boolean network) {
        String current = DynamicLinkPolicy.normalize(input);
        if (current == null) return null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        Set<String> visited = new HashSet<>();
        try {
            for (int hop = 0; hop <= 3 && !cancelled && !Thread.currentThread().isInterrupted(); hop++) {
                if (System.nanoTime() >= deadline || !visited.add(current)) return null;
                var candidates = discovery.find(current);
                if (cancelled) return null;
                if (!candidates.isEmpty()) return new Result(current, List.copyOf(candidates));
                if (!network || hop == 3) return null;
                long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remaining <= 0) return null;
                Redirect response = transport.fetch(current, (int) Math.max(1, Math.min(2500, remaining)));
                if (cancelled || response == null || System.nanoTime() >= deadline
                        || !Set.of(301, 302, 303, 307, 308).contains(response.status())
                        || response.location() == null || response.location().length() > 4096) return null;
                URI next = new URI(current).resolve(response.location());
                String normalized = DynamicLinkPolicy.normalize(next.toString());
                if (normalized == null) return null;
                // HTTP fragments are inherited unless Location specifies one (RFC 9110).
                if (next.getRawFragment() == null && new URI(current).getRawFragment() != null)
                    normalized += "#" + new URI(current).getRawFragment();
                current = normalized;
            }
        } catch (Exception ignored) { /* Never expose URL, tokens, response headers or raw errors. */ }
        return null;
    }
    void cancel() { cancelled = true; transport.cancel(); }

    static List<InetAddress> publicAddresses(String host, Dns source) throws UnknownHostException {
        List<InetAddress> answers = source.lookup(host);
        if (answers.isEmpty()) throw new UnknownHostException("No public address");
        for (InetAddress address : answers)
            if (!PublicAddressPolicy.allowed(address)) throw new UnknownHostException("Non-public address");
        return List.copyOf(answers);
    }

    private static final class HttpTransport implements Transport {
        private final OkHttpClient client = new OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
                // OkHttp connects to these checked addresses; no second unvalidated DNS lookup.
                .retryOnConnectionFailure(false).dns(host -> publicAddresses(host, Dns.SYSTEM)).build();
        private volatile Call active;
        private volatile boolean cancelled;
        @Override public Redirect fetch(String url, int timeoutMillis) throws IOException {
            if (cancelled) throw new IOException("Cancelled");
            Call call = client.newCall(new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/132.0.0.0 Mobile Safari/537.36")
                    .header("Accept-Encoding", "identity").build());
            call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS);
            active = call;
            try {
                if (cancelled) throw new IOException("Cancelled");
                try (Response response = call.execute()) { return new Redirect(response.code(), response.header("Location")); }
            } finally { active = null; }
        }
        @Override public void cancel() {
            cancelled = true;
            Call call = active;
            if (call != null) call.cancel();
            client.connectionPool().evictAll();
        }
    }
}
