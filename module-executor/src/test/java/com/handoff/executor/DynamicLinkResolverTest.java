package com.handoff.executor;

import android.content.ComponentName;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class DynamicLinkResolverTest {
    private static final String SHORT = "https://short.example.com/a", PAGE = "https://app.example.com/item/123";
    private final List<String> requests = new ArrayList<>();
    private final List<DynamicAppResolver.Candidate> apps = List.of(
            new DynamicAppResolver.Candidate(new ComponentName("example.app", "example.app.View"), "Example"));
    private DynamicLinkResolver resolver(String location) {
        return new DynamicLinkResolver(url -> url.startsWith(PAGE) ? apps : List.of(), (url, timeout) -> {
            requests.add(url); assertTrue(timeout > 0 && timeout <= 2500);
            return new DynamicLinkResolver.Redirect(302, location);
        });
    }
    @Test public void directDiscoveryDoesNotUseNetworkEvenWhenAllowed() {
        assertEquals(apps, resolver(PAGE).resolve(PAGE, true).candidates());
        assertTrue(requests.isEmpty());
    }
    @Test public void networkIsStrictlyOptIn() {
        assertNull(resolver(PAGE).resolve(SHORT, false)); assertTrue(requests.isEmpty());
    }
    @Test public void redirectKeepsQueryAndFragmentAndStopsBeforeLandingPageFetch() {
        var result = resolver(PAGE + "?share=private").resolve(SHORT + "#fragment", true);
        assertEquals(PAGE + "?share=private#fragment", result.url());
        assertEquals(List.of(SHORT + "#fragment"), requests);
    }
    @Test public void unsafeRedirectsAreNeverRequested() {
        for (String target : new String[]{"http://app.example.com/", "https://127.0.0.1/", "https://router.local/",
                "kwai://photo/123", "intent://anything", "https://v.kuaishou.com/a", "https://v.douyin.com/a",
                "https://user:pass@app.example.com/", "https://app.example.com:444/"}) {
            requests.clear(); assertNull(resolver(target).resolve(SHORT, true));
            assertEquals(List.of(SHORT), requests);
        }
    }
    @Test public void relativeRedirectsWorkAndCyclesFailClosed() {
        var r = new DynamicLinkResolver(url -> url.endsWith("/next") ? apps : List.of(),
                (url, timeout) -> new DynamicLinkResolver.Redirect(307, "/next"));
        assertEquals("https://short.example.com/next", r.resolve(SHORT, true).url());
        assertNull(resolver(SHORT).resolve(SHORT, true)); assertEquals(1, requests.size());
    }
    @Test public void limitsRequestsAndIgnoresHtmlAndMissingLocations() {
        var r = new DynamicLinkResolver(url -> List.of(), (url, timeout) -> {
            requests.add(url); return new DynamicLinkResolver.Redirect(302, url + "x");
        });
        assertNull(r.resolve(SHORT, true)); assertEquals(3, requests.size());
        for (var response : List.of(new DynamicLinkResolver.Redirect(200, PAGE),
                new DynamicLinkResolver.Redirect(302, null), new DynamicLinkResolver.Redirect(302, "x".repeat(4097))))
            assertNull(new DynamicLinkResolver(url -> List.of(), (url, timeout) -> response).resolve(SHORT, true));
        assertNull(new DynamicLinkResolver(url -> List.of(), (url, timeout) -> { throw new IOException(); }).resolve(SHORT, true));
    }
    @Test public void cancellationPreventsDiscoveryAndIo() {
        var r = resolver(PAGE); r.cancel(); assertNull(r.resolve(SHORT, true)); assertTrue(requests.isEmpty());
    }
    @Test public void ambiguityReturnsChoicesWithoutContactingTheServer() {
        var candidates = List.of(apps.get(0), new DynamicAppResolver.Candidate(new ComponentName("other.app", "View"), "Other"));
        var r = new DynamicLinkResolver(url -> candidates, (url, timeout) -> { fail("No I/O for ambiguous native handlers"); return null; });
        assertEquals(candidates, r.resolve(SHORT, true).candidates());
    }
    @Test public void dnsAddressPolicyRejectsPrivateReservedMappedAndTunneledAddresses() throws Exception {
        for (String address : new String[]{"0.0.0.0", "10.1.2.3", "100.64.0.1", "127.0.0.1", "169.254.169.254",
                "172.16.0.1", "192.168.1.1", "192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1",
                "224.0.0.1", "255.255.255.255", "::", "::1", "fc00::1", "fe80::1", "ff02::1",
                "::ffff:127.0.0.1", "64:ff9b::a00:1", "2001:db8::1", "2002:7f00:1::1", "3fff::1"})
            assertFalse(address, PublicAddressPolicy.allowed(InetAddress.getByName(address)));
        for (String address : new String[]{"8.8.8.8", "1.1.1.1", "2606:4700:4700::1111", "240e::1"})
            assertTrue(address, PublicAddressPolicy.allowed(InetAddress.getByName(address)));
    }
    @Test public void actualClientDnsRejectsMixedAnswersAndReturnsOnlyCheckedAddresses() throws Exception {
        var publicIp = InetAddress.getByName("1.1.1.1");
        assertSame(publicIp, DynamicLinkResolver.publicAddresses("public.example.com", host -> List.of(publicIp)).get(0));
        assertThrows(java.net.UnknownHostException.class, () -> DynamicLinkResolver.publicAddresses("mixed.example.com",
                host -> List.of(publicIp, InetAddress.getByName("127.0.0.1"))));
        assertThrows(java.net.UnknownHostException.class, () -> DynamicLinkResolver.publicAddresses("empty.example.com", host -> List.of()));
    }
}
