package com.handoff.executor;

import android.content.ActivityNotFoundException;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class DefaultBrowserLauncherTest {
    private static final String URL = "https://example.com/article?q=a%2Fb&text=中文#section-2";
    private static final class RecordingContext extends ContextWrapper {
        Intent outgoing;
        int starts;
        RuntimeException failure;
        RecordingContext() { super(RuntimeEnvironment.getApplication()); }
        @Override public void startActivity(Intent intent) {
            starts++;
            if (failure != null) throw failure;
            outgoing = intent;
        }
    }

    @Test public void launchesOnlyCurrentDefaultBrowserAndPreservesCompleteOriginalUrl() {
        RecordingContext context = new RecordingContext();
        setDefault(browser("chosen.browser"));
        assertTrue(DefaultBrowserLauncher.launch(context, URL));
        assertEquals(1, context.starts);
        assertEquals("chosen.browser", context.outgoing.getPackage());
        assertEquals(URL, context.outgoing.getDataString());
        assertEquals(Intent.ACTION_VIEW, context.outgoing.getAction());
        assertTrue(context.outgoing.hasCategory(Intent.CATEGORY_BROWSABLE));
        assertTrue((context.outgoing.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertNull(context.outgoing.getExtras());
        assertNull(context.outgoing.getSelector());
        assertNull(context.outgoing.getComponent());
    }

    @Test public void changedSystemDefaultIsReadForTheNextClick() {
        RecordingContext context = new RecordingContext();
        setDefault(browser("first.browser"));
        assertTrue(DefaultBrowserLauncher.launch(context, URL));
        assertEquals("first.browser", context.outgoing.getPackage());
        setDefault(browser("second.browser"));
        assertTrue(DefaultBrowserLauncher.launch(context, URL));
        assertEquals("second.browser", context.outgoing.getPackage());
        assertEquals(2, context.starts);
    }

    @Test public void anAppLinkHandlerCannotReplaceTheDefaultBrowser() {
        RecordingContext context = new RecordingContext();
        setDefault(browser("chosen.browser"));
        ResolveInfo appLink = browser("content.app"); appLink.filter.addDataAuthority("example.com", null);
        Shadows.shadowOf(context.getPackageManager()).setResolveInfosForIntent(
                new Intent(Intent.ACTION_VIEW, Uri.parse(URL)).addCategory(Intent.CATEGORY_BROWSABLE), List.of(appLink));
        assertTrue(DefaultBrowserLauncher.launch(context, URL));
        assertEquals("chosen.browser", context.outgoing.getPackage());
    }

    @Test public void absentDefaultAndResolverChooserDoNotLaunchAnything() {
        RecordingContext context = new RecordingContext();
        setDefault(null);
        assertFalse(DefaultBrowserLauncher.launch(context, URL));
        ResolveInfo chooser = browser("android"); chooser.filter = null;
        setDefault(chooser);
        assertFalse(DefaultBrowserLauncher.launch(context, URL));
        assertEquals(0, context.starts);
    }

    @Test public void unavailableUnexportedAndHostOwnedHandlersFailOpen() {
        RecordingContext context = new RecordingContext();
        ResolveInfo disabled = browser("disabled.browser"); disabled.activityInfo.enabled = false;
        ResolveInfo privateActivity = browser("private.browser"); privateActivity.activityInfo.exported = false;
        ResolveInfo unrelated = browser("content.app"); unrelated.filter.addDataAuthority("example.com", null);
        for (ResolveInfo candidate : List.of(disabled, privateActivity, unrelated,
                browser("com.tencent.mobileqq"), browser("com.tencent.tim"), browser(context.getPackageName()))) {
            setDefault(candidate);
            assertFalse(DefaultBrowserLauncher.launch(context, URL));
        }
        assertEquals(0, context.starts);
    }

    @Test public void launchFailureHasNoChooserHomepageOrSecondLaunchFallback() {
        setDefault(browser("chosen.browser"));
        for (RuntimeException error : new RuntimeException[]{new ActivityNotFoundException(), new SecurityException()}) {
            RecordingContext context = new RecordingContext(); context.failure = error;
            assertFalse(DefaultBrowserLauncher.launch(context, URL));
            assertEquals(1, context.starts);
            assertNull(context.outgoing);
        }
    }

    @Test public void socialAndMalformedUrlsNeverReachTheBrowserEvenWithADefault() {
        RecordingContext context = new RecordingContext();
        setDefault(browser("chosen.browser"));
        for (String url : new String[]{null, "intent://example.com/", "https://b23.tv/abc",
                "https://v.douyin.com/abc/", "https://v.kuaishou.com/abc", "https://m.q.qq.com/a/s/abc"}) {
            assertFalse(DefaultBrowserLauncher.launch(context, url));
        }
        assertEquals(0, context.starts);
    }

    private static ResolveInfo browser(String pkg) {
        ResolveInfo resolved = new ResolveInfo();
        resolved.isDefault = true;
        resolved.filter = new IntentFilter(Intent.ACTION_VIEW);
        resolved.filter.addCategory(Intent.CATEGORY_DEFAULT);
        resolved.filter.addCategory(Intent.CATEGORY_BROWSABLE);
        resolved.filter.addDataScheme("http");
        resolved.filter.addDataScheme("https");
        resolved.activityInfo = new ActivityInfo();
        resolved.activityInfo.packageName = pkg;
        resolved.activityInfo.name = pkg + ".BrowserActivity";
        resolved.activityInfo.enabled = true;
        resolved.activityInfo.exported = true;
        resolved.activityInfo.applicationInfo = new ApplicationInfo();
        resolved.activityInfo.applicationInfo.packageName = pkg;
        resolved.activityInfo.applicationInfo.enabled = true;
        return resolved;
    }

    private static void setDefault(ResolveInfo resolved) {
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://handoff-browser.invalid/"))
                .addCategory(Intent.CATEGORY_BROWSABLE);
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
                .setResolveInfosForIntent(probe, resolved == null ? List.of() : List.of(resolved));
    }
}
