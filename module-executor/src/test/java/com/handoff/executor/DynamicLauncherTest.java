package com.handoff.executor;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class DynamicLauncherTest {
    private static final String URL = "https://share.example.com/item?token=private";
    public static class RecordingContext extends android.app.Activity {
        String pkg = "com.tencent.mobileqq"; Intent outgoing; boolean fail;
        @Override public String getPackageName() { return pkg; }
        @Override public void startActivityForResult(Intent intent, int requestCode) {
            outgoing = intent;
            if (fail) throw new android.content.ActivityNotFoundException();
        }
    }
    private RecordingContext context() { return org.robolectric.Robolectric.buildActivity(RecordingContext.class).create().get(); }
    private Intent original() {
        return new Intent().setClassName("com.tencent.mobileqq", "com.tencent.mobileqq.activity.QQBrowserActivity")
                .putExtra("url", URL).putExtra("payload", "preserved");
    }
    @Test public void createsOneShotImmutableOriginalAndPassesNoQqPayloadToTheResolver() throws Exception {
        RecordingContext context = context();
        assertTrue(DynamicLauncher.resolve(context, URL, original(), false, null));
        assertEquals(DynamicResolveActivity.class.getName(), context.outgoing.getComponent().getClassName());
        assertEquals(URL, context.outgoing.getDataString());
        assertFalse(context.outgoing.getBooleanExtra(DynamicLauncher.NETWORK, true));
        assertNull(context.outgoing.getStringExtra("payload"));
        PendingIntent fallback = context.outgoing.getParcelableExtra(DynamicResolveActivity.FALLBACK);
        assertTrue(fallback.isImmutable()); assertTrue(fallback.isActivity());
        fallback.send();
        Intent restored = shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
        assertEquals("preserved", restored.getStringExtra("payload"));
        assertEquals(URL, restored.getStringExtra("url"));
        assertThrows(PendingIntent.CanceledException.class, fallback::send);
    }
    @Test public void synchronousFailureCancelsUnusedFallbackAndLetsCallerProceed() {
        RecordingContext context = context(); context.fail = true;
        assertFalse(DynamicLauncher.resolve(context, URL, original(), true, null));
        PendingIntent fallback = context.outgoing.getParcelableExtra(DynamicResolveActivity.FALLBACK);
        assertTrue(shadowOf(fallback).isCanceled());
    }
    @Test public void invalidBoundariesNeverCreateAnOwnedPage() {
        RecordingContext context = context();
        Intent selector = original(); selector.setSelector(new Intent(Intent.ACTION_VIEW, Uri.parse(URL)));
        for (Intent browser : new Intent[]{new Intent(), original().setPackage("other"),
                original().setClassName("other", "other.Browser"), original().putExtra("url", "https://other.example.com/"),
                original().addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT), selector}) {
            assertFalse(DynamicLauncher.resolve(context, URL, browser, true, null));
            assertNull(context.outgoing);
        }
        context.pkg = "other";
        assertFalse(DynamicLauncher.resolve(context, URL, original(), true, null));
        assertNull(context.outgoing);
    }
    @Test public void reservedPlatformAndNonHttpsUrlsNeverReachDynamicResolver() {
        RecordingContext context = context();
        for (String url : new String[]{"https://v.douyin.com/a", "https://b23.tv/a", "https://v.kuaishou.com/a", "http://share.example.com/a"})
            assertFalse(DynamicLauncher.resolve(context, url, original().putExtra("url", url), true, null));
        assertNull(context.outgoing);
    }
}
