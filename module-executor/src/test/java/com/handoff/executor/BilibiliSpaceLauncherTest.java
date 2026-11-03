package com.handoff.executor;

import android.content.ActivityNotFoundException;
import android.content.ContextWrapper;
import android.content.Intent;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class BilibiliSpaceLauncherTest {
    private static final class RecordingContext extends ContextWrapper {
        final List<Intent> attempts = new ArrayList<>();
        RuntimeException failure;
        boolean rejectScheme;
        RecordingContext() { super(RuntimeEnvironment.getApplication()); }
        @Override public void startActivity(Intent intent) {
            attempts.add(intent);
            if (failure != null) throw failure;
            if (rejectScheme && "bilibili".equals(intent.getScheme())) throw new ActivityNotFoundException();
        }
    }

    @Test public void opensTheRequestedPersonalSpaceInMainlandBilibili() {
        RecordingContext context = new RecordingContext();
        assertTrue(BilibiliSpaceLauncher.launch(context, "14606305"));
        assertEquals(1, context.attempts.size());
        assertRequest(context.attempts.get(0), "bilibili://space/14606305");
    }

    @Test public void unsupportedSchemeFallsBackToTheSameSpaceHttpsUrl() {
        RecordingContext context = new RecordingContext(); context.rejectScheme = true;
        assertTrue(BilibiliSpaceLauncher.launch(context, "14606305"));
        assertEquals(2, context.attempts.size());
        assertRequest(context.attempts.get(0), "bilibili://space/14606305");
        assertRequest(context.attempts.get(1), "https://space.bilibili.com/14606305");
    }

    @Test public void unavailableOrDeniedAppDoesNotFallBackToTheHomepageOrDefaultBrowser() {
        for (RuntimeException failure : new RuntimeException[]{new ActivityNotFoundException(), new SecurityException()}) {
            RecordingContext context = new RecordingContext(); context.failure = failure;
            assertFalse(BilibiliSpaceLauncher.launch(context, "14606305"));
            assertEquals(2, context.attempts.size());
            assertRequest(context.attempts.get(0), "bilibili://space/14606305");
            assertRequest(context.attempts.get(1), "https://space.bilibili.com/14606305");
        }
    }

    @Test public void invalidUidNeverStartsAnActivity() {
        RecordingContext context = new RecordingContext();
        for (String uid : new String[]{null, "", "0", "-1", "014606305", "9223372036854775808",
                "14606305?other=1", "14606305/video", "https://evil.example/", "%31", " 14606305"}) {
            assertFalse(String.valueOf(uid), BilibiliSpaceLauncher.launch(context, uid));
        }
        assertTrue(context.attempts.isEmpty());
    }

    private static void assertRequest(Intent intent, String url) {
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals(BilibiliSpaceLauncher.PACKAGE, intent.getPackage());
        assertEquals(url, intent.getDataString());
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE));
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertNull(intent.getExtras());
        assertNull(intent.getSelector());
        assertNull(intent.getComponent());
    }
}
