package com.handoff.hook;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MiniAppDispatchTest {
    // Sanitised replay of the 2026-10-29 RESOLVED event, including inherited SDK fields.
    static class BaseInfo {
        String appId = "1109937557";
        FirstPage firstPage = new FirstPage();
        String openId = "must-not-read";
    }
    static class Info extends BaseInfo { Params launchParam = new Params(); }
    static class Params {
        String miniAppId = "1109937557";
        String entryPath = "pages/video/video.html?bvid=BV1ZQxV6NE6G&share_source=qq_ugc";
    }
    static class FirstPage { String pagePath = "pages/video/video?bvid=BV1ZQxV6NE6G"; }

    @Test void replayResolvedSdkModelRedirectsBeforeOriginalLaunch() throws Throwable {
        List<String> calls = new ArrayList<>();
        Object result = MiniAppDispatch.intercept(new Info(), true, video -> {
            assertEquals("BV1ZQxV6NE6G", video.id());
            assertEquals("https://www.bilibili.com/video/BV1ZQxV6NE6G", video.url());
            assertEquals("launchParam.entryPath", video.source());
            calls.add("bili");
            return true;
        }, () -> { calls.add("qq-mini"); return "original"; });
        assertNull(result);
        assertEquals(List.of("bili"), calls);
    }

    @Test void missingLaunchPathUsesFirstPageButDoesNotReusePreviousVideo() {
        Info info = new Info();
        info.launchParam.entryPath = null;
        info.launchParam.miniAppId = null;
        assertEquals("firstPage.pagePath", MiniAppVideo.read(info).source());
        assertNull(MiniAppVideo.read(new Object()));
        assertNull(MiniAppVideo.read(null));
        info.firstPage.pagePath = null;
        assertNull(MiniAppVideo.read(info));
    }

    @Test void decodesQueryOnceAndAcceptsPositiveAid() {
        Info info = new Info();
        info.launchParam.entryPath = "pages/video/video?bvid=%42V1ZQxV6NE6G";
        assertEquals("BV1ZQxV6NE6G", MiniAppVideo.read(info).id());
        info.launchParam.entryPath = "pages/video/video.html?aid=170001";
        info.firstPage.pagePath = "pages/video/video?aid=170001";
        assertEquals("av170001", MiniAppVideo.read(info).id());
        assertEquals("https://www.bilibili.com/video/av170001", MiniAppVideo.read(info).url());
    }

    @Test void otherAppsAndConflictingIdentitiesNeverRedirect() throws Throwable {
        Info info = new Info();
        info.appId = "other-app";
        pass(info, true);
        info.appId = "1109937557";
        info.launchParam.miniAppId = "other-app";
        pass(info, true);
        info.appId = null;
        info.launchParam.miniAppId = "1109937557";
        pass(info, true); // A launch parameter alone cannot establish the resolved app identity.
    }

    @Test void malformedOrUnrelatedAuthoritativePathNeverFallsBackToStaleFirstPage() throws Throwable {
        String[] paths = {
                "pages/index/index", "pages/pgcvideo/pgcvideo?ep_id=123",
                "https://evil.example/pages/video/video?bvid=BV1ZQxV6NE6G",
                "https://www.bilibili.com/video/BV1ZQxV6NE6G",
                "//evil.example/pages/video/video?bvid=BV1ZQxV6NE6G",
                "/pages/video/video?bvid=BV1ZQxV6NE6G",
                "pages/video/%76ideo?bvid=BV1ZQxV6NE6G",
                "pages/video/video/extra?bvid=BV1ZQxV6NE6G",
                "pages/video/video?url=https%3A%2F%2Fbilibili.com%2Fvideo%2FBV1ZQxV6NE6G",
                "pages/video/video?bvid=BVshort", "pages/video/video?bvid=%2542V1ZQxV6NE6G",
                "pages/video/video?bvid=BV1ZQxV6NE6G&bvid=BV1ZQxV6NE6G",
                "pages/video/video?bvid=BV1ZQxV6NE6G&aid=170001",
                "pages/video/video?bvid=BV1ZQxV6NE6G#other", "pages/video/video?bvid=%zz",
                "pages/video/video?aid=0", "pages/video/video?aid=-1", "pages/video/video?aid=001",
                "pages/video/video?aid=9223372036854775808", "pages/video/video?bvid=BV1ZQxV6NE6G\n",
                "pages/video/video?bvid=BV1ZQxV6NE6G&tracking=" + "x".repeat(4096)
        };
        for (String path : paths) {
            Info info = new Info();
            info.launchParam.entryPath = path;
            assertNull(MiniAppVideo.read(info), path);
            pass(info, true);
        }
    }

    @Test void contradictoryValidVideoCandidatesFailOpen() throws Throwable {
        Info info = new Info();
        info.firstPage.pagePath = "pages/video/video?bvid=BV1xx411c7mD";
        pass(info, true);
    }

    @Test void unsupportedCallerPassesEvenWithValidVideo() throws Throwable { pass(new Info(), false); }

    @Test void callbackIpcAndUnknownActivityContractsAreNotIntercepted() {
        String activity = "com.tencent.mobileqq.activity.QPublicTransFragmentActivity";
        assertTrue(MiniAppDispatch.supportedCaller(activity, false, Set.of()));
        assertFalse(MiniAppDispatch.supportedCaller(activity, true, Set.of()));
        assertFalse(MiniAppDispatch.supportedCaller(activity, false, Set.of("KEY_APPINFO")));
        assertFalse(MiniAppDispatch.supportedCaller(null, false, Set.of()));
        assertFalse(MiniAppDispatch.supportedCaller("com.tencent.mobileqq.activity.SplashActivity", false, Set.of()));
    }

    @Test void rejectedLaunchRunsOriginalExactlyOnceAfterAttempt() throws Throwable {
        List<String> calls = new ArrayList<>();
        Object result = MiniAppDispatch.intercept(new Info(), true, video -> {
            calls.add("failed"); return false;
        }, () -> { calls.add("original"); return 42; });
        assertEquals(42, result);
        assertEquals(List.of("failed", "original"), calls);
    }

    @Test void launchExceptionFailsOpen() throws Throwable {
        AtomicInteger original = new AtomicInteger();
        MiniAppDispatch.intercept(new Info(), true, video -> { throw new IllegalStateException(); },
                () -> original.incrementAndGet());
        assertEquals(1, original.get());
    }

    @Test void originalExceptionIsNotSwallowedOrRetriedAndGuardClears() throws Throwable {
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("original failure");
        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                MiniAppDispatch.intercept(new Info(), true, video -> false, () -> {
                    calls.incrementAndGet(); throw failure;
                })));
        assertEquals(1, calls.get());
        MiniAppDispatch.intercept(new Info(), true, video -> true, () -> fail("guard leaked"));
    }

    @Test void nestedLaunchDoesNotRepeatAttemptAndIndependentClicksAreNotDeduplicated() throws Throwable {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger originals = new AtomicInteger();
        MiniAppDispatch.intercept(new Info(), true, video -> { attempts.incrementAndGet(); return false; },
                () -> MiniAppDispatch.intercept(new Info(), true, video -> fail("reentry"),
                        () -> originals.incrementAndGet()));
        assertEquals(1, originals.get());
        MiniAppDispatch.intercept(new Info(), true, video -> { attempts.incrementAndGet(); return true; },
                () -> fail("must block"));
        assertEquals(2, attempts.get());
    }

    @Test void browserAndMiniGuardsAreIndependent() throws Throwable {
        BrowserDispatch.intercept(() -> {
            MiniAppDispatch.intercept(new Info(), true, video -> true, () -> fail("shared guard"));
            return true;
        }, () -> fail("must block"));
    }

    @Test void innerEntryCanResolveVideoAfterOuterModelWasIncomplete() throws Throwable {
        AtomicInteger attempts = new AtomicInteger();
        MiniAppDispatch.intercept(new Object(), true, video -> fail("outer has no video"),
                () -> MiniAppDispatch.intercept(new Info(), true, video -> {
                    attempts.incrementAndGet(); return true;
                }, () -> fail("resolved inner entry must block")));
        assertEquals(1, attempts.get());
    }

    private static void pass(Object model, boolean supported) throws Throwable {
        AtomicInteger original = new AtomicInteger();
        MiniAppDispatch.intercept(model, supported, video -> fail("must not redirect"),
                () -> original.incrementAndGet());
        assertEquals(1, original.get());
    }
}
