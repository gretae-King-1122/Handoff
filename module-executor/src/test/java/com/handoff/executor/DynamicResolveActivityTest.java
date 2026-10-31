package com.handoff.executor;

import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.ResultReceiver;
import android.content.ComponentName;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class DynamicResolveActivityTest {
    private static final String SHORT = "https://short.example.com/Fixture123";
    private static final String PAGE = "https://app.example.com/item/123";
    private static ManualWorker worker;
    private static String location;
    private static int requests;
    private static boolean failLaunch;
    private static boolean approved;
    private static boolean multiple;
    private static final List<Intent> launches = new ArrayList<>();
    private final List<Integer> results = new ArrayList<>();
    private PendingIntent fallback;

    private static class ManualWorker extends AbstractExecutorService {
        final List<Runnable> tasks = new ArrayList<>();
        boolean stopped;
        @Override public void execute(Runnable task) { tasks.add(task); }
        @Override public void shutdown() { stopped = true; }
        @Override public List<Runnable> shutdownNow() {
            stopped = true; List<Runnable> result = new ArrayList<>(tasks); tasks.clear(); return result;
        }
        @Override public boolean isShutdown() { return stopped; }
        @Override public boolean isTerminated() { return stopped; }
        @Override public boolean awaitTermination(long t, TimeUnit u) { return stopped; }
        void resolve() { tasks.remove(0).run(); shadowOf(Looper.getMainLooper()).idle(); }
    }
    public static class TestActivity extends DynamicResolveActivity {
        @Override public String getCallingPackage() { return "com.tencent.mobileqq"; }
        @Override DynamicLinkResolver createResolver() {
            return new DynamicLinkResolver(url -> url.startsWith(PAGE) ? (multiple ? List.of(
                    new DynamicAppResolver.Candidate(new ComponentName("example.app", "example.app.View"), "Example", approved),
                    new DynamicAppResolver.Candidate(new ComponentName("other.app", "other.app.View"), "Other", false)) : List.of(
                    new DynamicAppResolver.Candidate(new ComponentName("example.app", "example.app.View"), "Example", approved))) : List.of(), (url, timeout) -> {
                requests++; return new DynamicLinkResolver.Redirect(302, location);
            });
        }
        @Override java.util.concurrent.ExecutorService createWorker() { return worker; }
        @Override boolean launchCandidate(String url, DynamicAppResolver.Candidate candidate) {
            if (failLaunch) return false;
            launches.add(DynamicAppResolver.view(url).setComponent(candidate.component()));
            return true;
        }
    }
    public static class UntrustedActivity extends TestActivity {
        @Override public String getCallingPackage() { return "example.attacker"; }
    }

    @Before public void setup() {
        worker = new ManualWorker(); location = PAGE; requests = 0; failLaunch = false; approved = true; multiple = false; launches.clear();
    }
    private static Context qqContext() {
        return new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public String getPackageName() { return "com.tencent.mobileqq"; }
        };
    }
    private Intent request() {
        Intent browser = new Intent().setClassName("com.tencent.mobileqq", "com.tencent.mobileqq.activity.QQBrowserActivity")
                .putExtra("url", SHORT).putExtra("original_extra", "preserved");
        fallback = PendingIntent.getActivity(qqContext(), 42, browser, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
        shadowOf(fallback).setCreatorPackage("com.tencent.mobileqq");
        assertEquals("com.tencent.mobileqq", fallback.getCreatorPackage());
        assertTrue(fallback.isImmutable());
        assertTrue(fallback.isActivity());
        return new Intent(Intent.ACTION_VIEW, Uri.parse(SHORT))
                .putExtra(DynamicLauncher.NETWORK, true)
                .putExtra(DynamicResolveActivity.FALLBACK, fallback)
                .putExtra(DynamicResolveActivity.RESULT, new ResultReceiver(null) {
                    @Override protected void onReceiveResult(int code, Bundle data) { results.add(code); }
                });
    }
    private ActivityController<TestActivity> open() {
        return Robolectric.buildActivity(TestActivity.class, request()).setup();
    }

    @Test public void resolutionIsDeferredAndOnlyAResolvedNativeLaunchCountsAsAccepted() {
        var controller = open();
        assertEquals(0, requests); assertTrue(results.isEmpty()); assertTrue(launches.isEmpty());
        worker.resolve();
        assertEquals(1, requests); assertEquals(1, launches.size());
        assertEquals("example.app", launches.get(0).getComponent().getPackageName());
        assertEquals(PAGE, launches.get(0).getDataString());
        assertEquals(List.of(DynamicLauncher.STARTED), results);
        assertTrue(shadowOf(fallback).isCanceled()); assertTrue(controller.get().isFinishing());
    }

    @Test public void failedResolutionReturnsTheOriginalQqIntentOnce() {
        location = "https://evil.test/page";
        var controller = open(); worker.resolve();
        assertTrue(launches.isEmpty()); assertEquals(List.of(DynamicLauncher.FAILED), results);
        Intent original = shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
        assertNotNull(original); assertEquals("com.tencent.mobileqq.activity.QQBrowserActivity", original.getComponent().getClassName());
        assertEquals("preserved", original.getStringExtra("original_extra"));
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
        assertTrue(controller.get().isFinishing());
    }

    @Test public void missingTargetAppAlsoReturnsToQqWithoutHomepageFallback() {
        failLaunch = true;
        open(); worker.resolve();
        assertTrue(launches.isEmpty());
        assertEquals(List.of(DynamicLauncher.FAILED), results);
        assertNotNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
    }

    @Test public void timeoutAndLateCompletionCannotStartTwoDestinations() {
        open(); Runnable stale = worker.tasks.get(0);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(7));
        assertEquals(List.of(DynamicLauncher.FAILED), results);
        stale.run(); shadowOf(Looper.getMainLooper()).idle();
        assertTrue(launches.isEmpty()); assertEquals(0, requests); assertEquals(1, results.size());
        assertNotNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
    }

    @Test public void backCancelsWithoutOpeningEitherAppAndIgnoresLateResults() {
        var controller = open(); Runnable stale = worker.tasks.get(0);
        controller.get().onBackPressed(); stale.run(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8));
        assertEquals(List.of(DynamicLauncher.CANCELLED), results);
        assertTrue(launches.isEmpty()); assertEquals(0, requests);
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
        assertTrue(shadowOf(fallback).isCanceled());
    }

    @Test public void leavingTheResolverCancelsInsteadOfOpeningAppsInTheBackground() {
        var controller = open(); controller.pause().stop();
        assertEquals(List.of(DynamicLauncher.CANCELLED), results);
        assertTrue(worker.isShutdown()); assertTrue(launches.isEmpty());
    }

    @Test public void recreationRetainsOneJobAndDeliversOnlyToTheNewActivity() {
        var controller = open(); controller.recreate();
        assertEquals(1, worker.tasks.size());
        worker.resolve();
        assertEquals(1, requests); assertEquals(1, launches.size());
        assertEquals(List.of(DynamicLauncher.STARTED), results);
    }

    @Test public void cancelledFallbackIsReportedSeparately() {
        location = "https://evil.test/"; open(); fallback.cancel(); worker.resolve();
        assertEquals(List.of(DynamicLauncher.FALLBACK_FAILED), results);
        assertTrue(launches.isEmpty());
    }

    @Test public void exportedActivityRejectsUntrustedAndMutableCapabilitiesBeforeNetwork() {
        for (boolean mutable : new boolean[]{false, true}) {
            Intent intent = request();
            Context creator = mutable ? qqContext() : RuntimeEnvironment.getApplication();
            PendingIntent invalid = PendingIntent.getActivity(creator, 77, new Intent().setPackage("com.tencent.mobileqq"),
                    mutable ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_IMMUTABLE);
            if (mutable) shadowOf(invalid).setCreatorPackage("com.tencent.mobileqq");
            intent.putExtra(DynamicResolveActivity.FALLBACK, invalid);
            assertTrue(Robolectric.buildActivity(TestActivity.class, intent).setup().get().isFinishing());
        }
        assertTrue(worker.tasks.isEmpty()); assertEquals(0, requests); assertTrue(launches.isEmpty());
    }
    @Test public void unverifiedSingleAppNeedsExplicitConfirmation() {
        approved = false; open(); worker.resolve();
        assertTrue(launches.isEmpty()); assertTrue(results.isEmpty());
        var dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        dialog.getListView().performItemClick(null, 0, 0);
        assertEquals(1, launches.size()); assertEquals(List.of(DynamicLauncher.STARTED), results);
    }
    @Test public void multipleAppsNeverAutoChooseAndRotationKeepsOneJob() {
        multiple = true; var controller = open(); worker.resolve();
        assertTrue(launches.isEmpty()); controller.recreate();
        assertTrue(worker.tasks.isEmpty()); assertEquals(1, requests);
        var dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        dialog.getListView().performItemClick(null, 1, 1);
        assertEquals("other.app", launches.get(0).getComponent().getPackageName());
        assertEquals(List.of(DynamicLauncher.STARTED), results);
    }
    @Test public void chooserCanReturnToOriginalQqBrowser() {
        multiple = true; open(); worker.resolve();
        var dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(launches.isEmpty()); assertEquals(List.of(DynamicLauncher.FAILED), results);
        assertNotNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
    }
    @Test public void disabledNetworkingFallsBackWithoutFetchingShortLink() {
        Intent intent = request().putExtra(DynamicLauncher.NETWORK, false);
        Robolectric.buildActivity(TestActivity.class, intent).setup(); worker.resolve();
        assertEquals(0, requests); assertTrue(launches.isEmpty());
        assertEquals(List.of(DynamicLauncher.FAILED), results);
    }
    @Test public void borrowedQqCapabilityCannotAuthorizeAForeignCaller() {
        var controller = Robolectric.buildActivity(UntrustedActivity.class, request()).setup();
        assertTrue(controller.get().isFinishing()); assertTrue(worker.tasks.isEmpty());
        assertTrue(launches.isEmpty()); assertEquals(0, requests);
    }

}
