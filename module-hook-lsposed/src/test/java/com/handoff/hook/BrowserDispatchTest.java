package com.handoff.hook;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BrowserDispatchTest {
    private static final String DELEGATE = "com.tencent.mobileqq.activity.QQBrowserDelegationActivity";
    private static final String QQ = "com.tencent.mobileqq";
    private static final String BROWSER = "com.tencent.mobileqq.activity.QQBrowserActivity";
    private static final String VIDEO = "https://www.bilibili.com/video/BV1xx411c7mD?share_source=qq";

    @Test void successfulRedirectNeverStartsOriginalBrowser() throws Throwable {
        List<String> calls = new ArrayList<>();
        Object result = BrowserDispatch.intercept(() -> {
            calls.add("bili");
            return true;
        }, () -> {
            calls.add("qq-browser");
            return "original";
        });
        assertNull(result);
        assertEquals(List.of("bili"), calls);
    }

    @Test void failedRedirectStartsOriginalExactlyOnceAndOnlyAfterAttempt() throws Throwable {
        List<String> calls = new ArrayList<>();
        Object result = BrowserDispatch.intercept(() -> {
            calls.add("bili-failed");
            return false;
        }, () -> {
            calls.add("qq-browser");
            return "original";
        });
        assertEquals("original", result);
        assertEquals(List.of("bili-failed", "qq-browser"), calls);
    }

    @Test void extractionOrLaunchExceptionFailsOpen() throws Throwable {
        AtomicInteger original = new AtomicInteger();
        BrowserDispatch.intercept(() -> {
            throw new IllegalStateException("unreadable intent / unavailable target");
        }, () -> original.incrementAndGet());
        assertEquals(1, original.get());
    }

    @Test void originalExceptionPropagatesWithoutRetryAndGuardIsCleared() throws Throwable {
        AtomicInteger original = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("QQ failure");
        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                BrowserDispatch.intercept(() -> false, () -> {
                    original.incrementAndGet();
                    throw failure;
                })));
        assertEquals(1, original.get());
        assertNull(BrowserDispatch.intercept(() -> true, () -> fail("guard leaked")));
    }

    @Test void nestedOverloadDoesNotRepeatExternalAttempt() throws Throwable {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger original = new AtomicInteger();
        BrowserDispatch.intercept(() -> {
            attempts.incrementAndGet();
            return false;
        }, () -> BrowserDispatch.intercept(() -> {
            attempts.incrementAndGet();
            return true;
        }, () -> original.incrementAndGet()));
        assertEquals(1, attempts.get());
        assertEquals(1, original.get());
        // A subsequent independent tap must not inherit the recursion guard.
        BrowserDispatch.intercept(() -> { attempts.incrementAndGet(); return true; },
                () -> fail("must redirect"));
        assertEquals(2, attempts.get());
    }

    @Test void externalStartReentryPassesThrough() throws Throwable {
        AtomicInteger external = new AtomicInteger();
        AtomicInteger browser = new AtomicInteger();
        BrowserDispatch.intercept(() -> {
            BrowserDispatch.intercept(() -> fail("recursive redirect"), () -> external.incrementAndGet());
            return true;
        }, () -> browser.incrementAndGet());
        assertEquals(1, external.get());
        assertEquals(0, browser.get());
    }

    @Test void acceptsOnlyKnownDelegationToBrowserRoutes() {
        for (String target : List.of("QQBrowserActivity", "QQH5BrowserActivity", "QQTranslucentBrowserActivity")) {
            assertEquals("BV1xx411c7mD", BrowserDispatch.videoId(DELEGATE, QQ,
                    "com.tencent.mobileqq.activity." + target, VIDEO));
        }
        assertNull(BrowserDispatch.videoId("com.tencent.mobileqq.activity.SplashActivity", QQ, BROWSER, VIDEO));
        assertNull(BrowserDispatch.videoId(DELEGATE, "other.package", BROWSER, VIDEO));
        assertNull(BrowserDispatch.videoId(DELEGATE, QQ, DELEGATE, VIDEO));
        assertNull(BrowserDispatch.videoId(DELEGATE, QQ, null, VIDEO));
        assertNull(BrowserDispatch.videoId(null, QQ, BROWSER, VIDEO));
    }

    @Test void parsesExactSupportedVideoUrlsWithoutReadingTrackingQuery() {
        assertEquals("BV1xx411c7mD", id(VIDEO));
        assertEquals("av117510254955147", id("https://www.bilibili.com/video/av117510254955147/?p=2"));
        assertEquals("av170001", id("http://bilibili.com:80/video/av170001"));
        assertEquals("BV1xx411c7mD", id("HTTPS://M.BILIBILI.COM:443/video/BV1xx411c7mD#reply"));
    }

    @Test void unrelatedAndSpoofedUrlsPassThrough() throws Throwable {
        String[] urls = {
                null, "", "not a url", "https://example.com/",
                "https://bilibili.com.evil.example/video/BV1xx411c7mD",
                "https://evil.example/bilibili.com/video/BV1xx411c7mD",
                "https://evil.example/?url=https://bilibili.com/video/BV1xx411c7mD",
                "https://www.bilibili.com@evil.example/video/BV1xx411c7mD",
                "https://evil.example@www.bilibili.com/video/BV1xx411c7mD",
                "https://foo.bilibili.com/video/BV1xx411c7mD",
                "javascript://www.bilibili.com/video/BV1xx411c7mD",
                "https://www.bilibili.com:444/video/BV1xx411c7mD",
                "https://www.bilibili.com/video/BV1xx411c7mDextra",
                "https://www.bilibili.com/video/av170001/other",
                "https://www.bilibili.com/video/%42V1xx411c7mD",
                "https://www.bilibili.com/video/BVshort",
                "https://www.bilibili.com/other?url=/video/BV1xx411c7mD"
        };
        for (String url : urls) {
            assertNull(id(url), String.valueOf(url));
            AtomicInteger original = new AtomicInteger();
            BrowserDispatch.intercept(() -> id(url) != null, () -> original.incrementAndGet());
            assertEquals(1, original.get(), String.valueOf(url));
        }
    }

    private String id(String url) {
        return BrowserDispatch.videoId(DELEGATE, QQ, BROWSER, url);
    }
}
