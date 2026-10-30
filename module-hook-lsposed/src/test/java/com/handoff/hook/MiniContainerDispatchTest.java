package com.handoff.hook;

import com.tencent.qqmini.sdk.launcher.model.MiniAppInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MiniContainerDispatchTest {
    private static final String CALLER = "com.tencent.mobileqq.activity.QPublicTransFragmentActivity";
    private static final String TARGET = "com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI";

    private static MiniContainerDispatch.Envelope route(String caller, String pkg, String target,
                                                       int request, boolean forward, boolean selector) {
        return new MiniContainerDispatch.Envelope(new MiniContainerDispatch.Caller(caller, false, false),
                pkg, target, request, forward, selector);
    }

    private static MiniContainerDispatch.Envelope route() {
        return route(CALLER, "com.tencent.mobileqq", TARGET, -1, false, false);
    }

    private static MiniContainerDispatch.Payload payload() {
        return new MiniContainerDispatch.Payload(true, null, new MiniAppInfo());
    }

    private static MiniContainerDispatch.Envelope lifecycleRoute(boolean finishing, boolean destroyed) {
        return new MiniContainerDispatch.Envelope(new MiniContainerDispatch.Caller(CALLER, finishing, destroyed),
                "com.tencent.mobileqq", TARGET, -1, false, false);
    }

    @Test void postedContainerLaunchFromFinishingButNotDestroyedCallerStillHandsOff() throws Throwable {
        AtomicInteger attempts = new AtomicInteger();
        // QQ posts Activity.startActivity and may finish the preload Activity before that Runnable executes.
        assertNull(MiniContainerDispatch.intercept(lifecycleRoute(true, false), MiniContainerDispatchTest::payload,
                video -> { attempts.incrementAndGet(); return true; },
                () -> fail("finishing preload Activity must not hide an otherwise valid final dispatch")));
        assertEquals(1, attempts.get());
    }

    @Test void destroyedCallerPassesWithoutReadingExtrasOrLaunching() throws Throwable {
        for (boolean finishing : List.of(false, true)) {
            AtomicInteger originals = new AtomicInteger();
            assertEquals("qq", MiniContainerDispatch.intercept(lifecycleRoute(finishing, true),
                    () -> fail("destroyed Activity must not unmarshal extras"), video -> fail("destroyed caller"),
                    () -> { originals.incrementAndGet(); return "qq"; }));
            assertEquals(1, originals.get());
        }
    }

    @Test void finishingCallerStillRequiresValidPayloadAndFailedLaunchPassesExactlyOnce() throws Throwable {
        for (boolean valid : List.of(false, true)) {
            AtomicInteger attempts = new AtomicInteger();
            AtomicInteger originals = new AtomicInteger();
            MiniContainerDispatch.intercept(lifecycleRoute(true, false),
                    () -> new MiniContainerDispatch.Payload(valid, null, new MiniAppInfo()),
                    video -> { attempts.incrementAndGet(); return false; },
                    () -> originals.incrementAndGet());
            assertEquals(valid ? 1 : 0, attempts.get());
            assertEquals(1, originals.get());
        }
    }

    @Test void sdkModelAtFinalDispatchCanBlockEachExplicitContainer() throws Throwable {
        for (String target : List.of(TARGET, TARGET + "1", TARGET + "2", TARGET + "3")) {
            AtomicInteger attempts = new AtomicInteger();
            assertNull(MiniContainerDispatch.intercept(route(CALLER, "com.tencent.mobileqq", target, -1, false, false),
                    MiniContainerDispatchTest::payload, video -> {
                        assertEquals("BV1ZQxV6NE6G", video.id());
                        attempts.incrementAndGet(); return true;
                    }, () -> fail("must block only after accepted video launch")));
            assertEquals(1, attempts.get());
        }
    }

    @Test void unrelatedAndCallbackRoutesPassWithoutReadingAnyExtras() throws Throwable {
        for (MiniContainerDispatch.Envelope envelope : List.of(
                route("other", "com.tencent.mobileqq", TARGET, -1, false, false),
                route(CALLER, "other.app", TARGET, -1, false, false),
                route(CALLER, "com.tencent.mobileqq", TARGET + "4", -1, false, false),
                route(CALLER, "com.tencent.mobileqq", TARGET, 0, false, false),
                route(CALLER, "com.tencent.mobileqq", TARGET, -1, true, false),
                route(CALLER, "com.tencent.mobileqq", TARGET, -1, false, true))) {
            AtomicInteger originals = new AtomicInteger();
            Object result = MiniContainerDispatch.intercept(envelope, () -> fail("must not inspect extras"),
                    video -> fail("unknown route"), () -> { originals.incrementAndGet(); return "qq"; });
            assertEquals("qq", result);
            assertEquals(1, originals.get());
        }
    }

    @Test void missingSdkModeWrongModelAndNonNullReceiverNeverRedirect() throws Throwable {
        for (MiniContainerDispatch.Payload data : List.of(
                new MiniContainerDispatch.Payload(false, null, new MiniAppInfo()),
                new MiniContainerDispatch.Payload(true, new Object(), new MiniAppInfo()),
                new MiniContainerDispatch.Payload(true, null, new MiniAppDispatchTest.Info()),
                new MiniContainerDispatch.Payload(true, null, null))) {
            AtomicInteger originals = new AtomicInteger();
            MiniContainerDispatch.intercept(route(), () -> data, video -> fail("unknown payload"),
                    () -> originals.incrementAndGet());
            assertEquals(1, originals.get());
        }
    }

    @Test void unreadableParcelPassesAndOriginalExceptionIsNeverRetried() {
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException originalError = new IllegalStateException("QQ original error");
        assertSame(originalError, assertThrows(IllegalStateException.class, () ->
                MiniContainerDispatch.intercept(route(), () -> { throw new IllegalArgumentException("bad parcel"); },
                        video -> fail("unreadable payload"), () -> {
                            calls.incrementAndGet(); throw originalError;
                        })));
        assertEquals(1, calls.get());
    }

    @Test void finalDispatchCanResolveVideoMissingAtOuterEntry() throws Throwable {
        AtomicInteger attempts = new AtomicInteger();
        MiniAppDispatch.intercept(new Object(), true, video -> fail("outer has no identity"),
                () -> MiniContainerDispatch.intercept(route(), MiniContainerDispatchTest::payload, video -> {
                    attempts.incrementAndGet(); return true;
                }, () -> fail("final launch must block")));
        assertEquals(1, attempts.get());
    }

    @Test void runtimeModelFromDifferentLoaderDoesNotNeedInitialQqClassIdentity() throws Throwable {
        byte[] bytes;
        try (var stream = MiniAppInfo.class.getResourceAsStream("MiniAppInfo.class")) {
            assertNotNull(stream);
            bytes = stream.readAllBytes();
        }
        Class<?> patched = new ClassLoader(MiniAppInfo.class.getClassLoader()) {
            Class<?> copy() { return defineClass(MiniAppInfo.class.getName(), bytes, 0, bytes.length); }
        }.copy();
        assertNotSame(MiniAppInfo.class, patched);
        assertNotSame(MiniAppInfo.class.getClassLoader(), patched.getClassLoader());
        Object model = patched.getConstructor().newInstance();
        AtomicInteger attempts = new AtomicInteger();
        MiniContainerDispatch.intercept(route(), () -> new MiniContainerDispatch.Payload(true, null, model), video -> {
            assertEquals("BV1ZQxV6NE6G", video.id());
            attempts.incrementAndGet(); return true;
        }, () -> fail("runtime model must not be rejected because the initial QQ Class differs"));
        assertEquals(1, attempts.get());
    }

    @Test void failureAtOuterEntryIsNotRetriedAtInnerEntryAndNextClickIsIndependent() throws Throwable {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger originals = new AtomicInteger();
        MiniAppDispatch.Launcher failed = video -> { attempts.incrementAndGet(); return false; };
        MiniAppDispatch.intercept(new MiniAppInfo(), true, failed,
                () -> MiniContainerDispatch.intercept(route(), MiniContainerDispatchTest::payload, failed,
                        () -> originals.incrementAndGet()));
        assertEquals(1, attempts.get());
        assertEquals(1, originals.get());
        MiniContainerDispatch.intercept(route(), MiniContainerDispatchTest::payload, video -> {
            attempts.incrementAndGet(); return true;
        }, () -> fail("new click must not inherit failed attempt"));
        assertEquals(2, attempts.get());
    }

    @Test void acceptedVideoCannotReopenQqThroughAnotherEntryInTheSameChain() throws Throwable {
        AtomicInteger attempts = new AtomicInteger();
        MiniAppDispatch.intercept(new Object(), true, video -> fail("incomplete outer model"), () -> {
            for (int i = 0; i < 2; i++) {
                MiniContainerDispatch.intercept(route(), MiniContainerDispatchTest::payload, video -> {
                    attempts.incrementAndGet(); return true;
                }, () -> fail("already handed-off video must not reopen QQ through a second entry"));
            }
            return null;
        });
        assertEquals(1, attempts.get());
    }

    @Test void malformedOrNonBiliModelPassesAndEmptyPayloadIsSafe() throws Throwable {
        MiniAppInfo wrongApp = new MiniAppInfo();
        wrongApp.appId = "other";
        MiniAppInfo badPath = new MiniAppInfo();
        badPath.launchParam.entryPath = "pages/index/index";
        for (MiniContainerDispatch.Payload data : List.of(new MiniContainerDispatch.Payload(true, null, wrongApp),
                new MiniContainerDispatch.Payload(true, null, badPath))) {
            assertEquals("qq", MiniContainerDispatch.intercept(route(), () -> data,
                    video -> fail("invalid model"), () -> "qq"));
        }
        assertEquals("qq", MiniContainerDispatch.intercept(route(), () -> null,
                video -> fail("no payload"), () -> "qq"));
    }
}
