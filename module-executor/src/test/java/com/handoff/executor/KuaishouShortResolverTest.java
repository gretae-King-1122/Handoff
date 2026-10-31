package com.handoff.executor;

import com.handoff.api.links.ParsedLink;
import com.handoff.rules.links.DouyinLinkParser;
import com.handoff.rules.links.KuaishouLinkParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class KuaishouShortResolverTest {
    private static final KuaishouLinkParser PARSER = new KuaishouLinkParser();
    private static final ParsedLink SHORT = PARSER.parseUrl("https://v.kuaishou.com/Fixture123");
    private static final String PAGE = "https://v.m.chenzhongtech.com/fw/photo/3xSynthetic";
    private static class Transport implements KuaishouShortResolver.Transport {
        final List<String> requests = new ArrayList<>();
        final List<KuaishouShortResolver.Redirect> responses = new ArrayList<>();
        boolean cancelled;
        @Override public KuaishouShortResolver.Redirect fetch(String url, int timeout) throws IOException {
            assertTrue(timeout > 0 && timeout <= 2500);
            requests.add(url);
            return responses.get(requests.size() - 1);
        }
        @Override public void cancel() { cancelled = true; }
    }

    @Test public void resolvesActual302ShapeWithoutRequestingOrRetainingTheLandingPage() {
        for (String route : new String[]{"photo", "long-video"}) {
            Transport transport = new Transport();
            transport.responses.add(new KuaishouShortResolver.Redirect(302,
                    PAGE.replace("photo", route) + "?photoId=3xSynthetic&shareToken=private"));
            ParsedLink video = new KuaishouShortResolver(transport).resolve(SHORT);
            assertEquals(ParsedLink.Kind.VIDEO, video.kind());
            assertEquals("3xSynthetic", video.contentId());
            assertFalse(video.canonicalUrl().contains("private"));
            assertEquals(List.of(SHORT.canonicalUrl()), transport.requests);
        }
    }

    @Test public void allowsOnlyRecognizedHttpsRedirectsAndNeverFetchesRejectedDestinations() {
        for (String location : new String[]{"https://evil.test/x", "https://127.0.0.1/x", "http://v.kuaishou.com/Next",
                "file:///private", "kwai://home", "https://v.kuaishou.com.evil.test/x", "https://user@v.kuaishou.com/Next",
                "https://v.kuaishou.com:444/Next", PAGE + "?photoId=wrong", "https://v.m.chenzhongtech.com/fw/user/account"}) {
            Transport transport = new Transport();
            transport.responses.add(new KuaishouShortResolver.Redirect(302, location));
            assertNull(location, new KuaishouShortResolver(transport).resolve(SHORT));
            assertEquals(1, transport.requests.size());
        }
    }

    @Test public void followsRelativeShortRedirectsWithABoundAndDetectsLoops() {
        Transport transport = new Transport();
        transport.responses.add(new KuaishouShortResolver.Redirect(307, "/Next?tracking=private"));
        transport.responses.add(new KuaishouShortResolver.Redirect(308, PAGE));
        assertNotNull(new KuaishouShortResolver(transport).resolve(SHORT));
        assertEquals("https://v.kuaishou.com/Next", transport.requests.get(1));
        Transport loop = new Transport();
        loop.responses.add(new KuaishouShortResolver.Redirect(301, SHORT.canonicalUrl()));
        assertNull(new KuaishouShortResolver(loop).resolve(SHORT));
        assertEquals(1, loop.requests.size());
        Transport chain = new Transport();
        for (int i = 0; i < 4; i++) chain.responses.add(new KuaishouShortResolver.Redirect(302, "/Hop" + i));
        assertNull(new KuaishouShortResolver(chain).resolve(SHORT));
        assertEquals(3, chain.requests.size());
    }

    @Test public void unsupportedResponsesMissingLocationsAndNetworkFailuresFailClosed() {
        for (var response : List.of(new KuaishouShortResolver.Redirect(200, PAGE),
                new KuaishouShortResolver.Redirect(404, PAGE), new KuaishouShortResolver.Redirect(302, null),
                new KuaishouShortResolver.Redirect(302, "x".repeat(4097)))) {
            Transport transport = new Transport(); transport.responses.add(response);
            assertNull(new KuaishouShortResolver(transport).resolve(SHORT));
        }
        assertNull(new KuaishouShortResolver((url, timeout) -> { throw new IOException("private token"); }).resolve(SHORT));
    }

    @Test public void invalidInputsAndCancellationNeverMakeRequests() {
        Transport transport = new Transport();
        KuaishouShortResolver resolver = new KuaishouShortResolver(transport);
        assertNull(resolver.resolve(null));
        assertNull(resolver.resolve(PARSER.parseUrl(PAGE)));
        assertNull(resolver.resolve(new DouyinLinkParser().parseUrl("https://v.douyin.com/Fixture")));
        assertNull(resolver.resolve(new ParsedLink(ParsedLink.Platform.KUAISHOU, ParsedLink.Kind.SHORT_LINK,
                "https://evil.test/Fixture", null)));
        resolver.cancel();
        assertNull(resolver.resolve(SHORT));
        assertTrue(transport.cancelled);
        assertTrue(transport.requests.isEmpty());
    }
}
