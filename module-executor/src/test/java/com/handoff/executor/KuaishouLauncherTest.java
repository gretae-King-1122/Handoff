package com.handoff.executor;

import android.content.ActivityNotFoundException;
import android.content.ContextWrapper;
import android.content.Intent;
import com.handoff.api.links.ParsedLink;
import com.handoff.rules.links.DouyinLinkParser;
import com.handoff.rules.links.KuaishouLinkParser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class KuaishouLauncherTest {
    private static final String PAGE = "https://v.m.chenzhongtech.com/fw/photo/3xSampleVideo123";
    private static class RecordingContext extends ContextWrapper {
        Intent outgoing;
        RuntimeException failure;
        int calls;
        RecordingContext() { super(RuntimeEnvironment.getApplication()); }
        @Override public void startActivity(Intent intent) {
            calls++;
            if (failure != null) throw failure;
            outgoing = intent;
        }
    }

    @Test public void targetsOnlyMainlandKuaishouWithTheCleanContentUrl() {
        RecordingContext context = new RecordingContext();
        assertTrue(KuaishouLauncher.launch(context, new KuaishouLinkParser().parseUrl(PAGE + "?shareToken=private")));
        assertEquals(1, context.calls);
        assertEquals(Intent.ACTION_VIEW, context.outgoing.getAction());
        assertEquals(KuaishouLauncher.PACKAGE, context.outgoing.getPackage());
        assertEquals(PAGE + "?photoId=3xSampleVideo123", context.outgoing.getDataString());
        assertTrue(context.outgoing.hasCategory(Intent.CATEGORY_BROWSABLE));
        assertTrue((context.outgoing.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertNull(context.outgoing.getExtras()); assertNull(context.outgoing.getSelector());
        assertNull(context.outgoing.getComponent());
    }

    @Test public void missingOrDeniedAppDoesNotFallBackToHomepageOrAnotherApp() {
        for (RuntimeException failure : new RuntimeException[]{new ActivityNotFoundException(), new SecurityException()}) {
            RecordingContext context = new RecordingContext(); context.failure = failure;
            assertFalse(KuaishouLauncher.launch(context, new KuaishouLinkParser().parseUrl(PAGE)));
            assertEquals(1, context.calls); assertNull(context.outgoing);
        }
    }

    @Test public void forgedWrongPlatformAndNonCanonicalTargetsCannotStartActivities() {
        RecordingContext context = new RecordingContext();
        assertFalse(KuaishouLauncher.launch(context, null));
        assertFalse(KuaishouLauncher.launch(context, new DouyinLinkParser().parseUrl("https://v.douyin.com/G7dxP6TRhw4/")));
        for (String url : new String[]{"https://evil.example/", "kwai://home", PAGE + "?shareToken=private"})
            assertFalse(KuaishouLauncher.launch(context, new ParsedLink(ParsedLink.Platform.KUAISHOU,
                    ParsedLink.Kind.VIDEO, url, "3xSampleVideo123")));
        assertFalse(KuaishouLauncher.launch(context, new ParsedLink(ParsedLink.Platform.KUAISHOU,
                ParsedLink.Kind.VIDEO, PAGE + "?photoId=3xSampleVideo123", "other")));
        assertFalse(KuaishouLauncher.launch(context, new ParsedLink(ParsedLink.Platform.DOUYIN,
                ParsedLink.Kind.VIDEO, "https://v.m.chenzhongtech.com/fw/photo/123?photoId=123", "123")));
        assertEquals(0, context.calls);
    }

    @Test public void theExistingDouyinLauncherNeverAcceptsKuaishouTargets() {
        RecordingContext context = new RecordingContext();
        assertFalse(DouyinLauncher.launch(context, new KuaishouLinkParser().parseUrl(PAGE)));
        assertEquals(0, context.calls);
    }

    @Test public void shortLinksMustUseTheResolverNotTheNativeWebIntent() {
        RecordingContext context = new RecordingContext();
        assertFalse(KuaishouLauncher.launch(context, new KuaishouLinkParser().parseUrl("https://v.kuaishou.com/Fixture123")));
        assertEquals(0, context.calls);
    }

    private static class QqContext extends RecordingContext {
        @Override public String getPackageName() { return "com.tencent.mobileqq"; }
    }
    private static Intent original() {
        return new Intent().setClassName("com.tencent.mobileqq", "com.tencent.mobileqq.activity.QQBrowserActivity")
                .putExtra("url", "https://v.kuaishou.com/Fixture123?tracking=private")
                .putExtra("original_extra", "preserved");
    }

    @Test public void resolverUsesACanonicalUrlAndImmutableOneShotOriginalRequest() {
        QqContext context = new QqContext();
        var link = new KuaishouLinkParser().parseUrl("https://v.kuaishou.com/Fixture123");
        assertTrue(KuaishouLauncher.resolveShort(context, link, original(), null));
        assertEquals(1, context.calls);
        assertEquals("com.handoff.module", context.outgoing.getComponent().getPackageName());
        assertEquals(KuaishouResolveActivity.class.getName(), context.outgoing.getComponent().getClassName());
        assertEquals(link.canonicalUrl(), context.outgoing.getDataString());
        android.app.PendingIntent fallback = context.outgoing.getParcelableExtra(KuaishouResolveActivity.FALLBACK);
        assertTrue(fallback.isImmutable()); assertTrue(fallback.isActivity());
        Intent saved = org.robolectric.Shadows.shadowOf(fallback).getSavedIntent();
        assertEquals("preserved", saved.getStringExtra("original_extra"));
        assertEquals(original().getComponent(), saved.getComponent());
    }

    @Test public void malformedOrUntrustedFallbackCannotStartAResolver() {
        QqContext context = new QqContext();
        var link = new KuaishouLinkParser().parseUrl("https://v.kuaishou.com/Fixture123");
        assertFalse(KuaishouLauncher.resolveShort(new RecordingContext(), link, original(), null));
        assertFalse(KuaishouLauncher.resolveShort(context, link, null, null));
        assertFalse(KuaishouLauncher.resolveShort(context, new KuaishouLinkParser().parseUrl(PAGE), original(), null));
        assertFalse(KuaishouLauncher.resolveShort(context, link, original().setClassName("evil.test", "evil.test.Activity"), null));
        assertFalse(KuaishouLauncher.resolveShort(context, link, original().putExtra("url", "https://v.kuaishou.com/Other"), null));
        assertFalse(KuaishouLauncher.resolveShort(context, link, original().addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT), null));
        assertEquals(0, context.calls);
    }
}
