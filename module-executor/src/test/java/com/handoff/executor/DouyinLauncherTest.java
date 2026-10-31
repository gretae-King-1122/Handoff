package com.handoff.executor;

import android.content.ActivityNotFoundException;
import android.content.ContextWrapper;
import android.content.Intent;
import com.handoff.api.links.ParsedLink;
import com.handoff.rules.links.DouyinLinkParser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class DouyinLauncherTest {
    private static final String SHORT = "https://v.douyin.com/G7dxP6TRhw4/";
    private static final class RecordingContext extends ContextWrapper {
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

    @Test public void shortLinkRequestTargetsOnlyMainlandDouyinAndContainsNoShareTracking() {
        RecordingContext context = new RecordingContext();
        var link = new DouyinLinkParser().parseUrl(SHORT + "?share_token=private");
        assertTrue(DouyinLauncher.launch(context, link));
        assertEquals(1, context.calls);
        assertEquals(Intent.ACTION_VIEW, context.outgoing.getAction());
        assertEquals(DouyinLauncher.PACKAGE, context.outgoing.getPackage());
        assertEquals(SHORT, context.outgoing.getDataString());
        assertTrue(context.outgoing.hasCategory(Intent.CATEGORY_BROWSABLE));
        assertTrue((context.outgoing.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertNull(context.outgoing.getExtras());
        assertNull(context.outgoing.getSelector());
        assertNull(context.outgoing.getComponent());
    }

    @Test public void videoRequestKeepsTheContentDestination() {
        RecordingContext context = new RecordingContext();
        assertTrue(DouyinLauncher.launch(context, new DouyinLinkParser().parseUrl("https://douyin.com/video/123")));
        assertEquals("https://www.douyin.com/video/123", context.outgoing.getDataString());
    }

    @Test public void unavailableOrDeniedAppNeverFallsBackToHomepageOrBrowser() {
        for (RuntimeException failure : new RuntimeException[]{new ActivityNotFoundException(), new SecurityException()}) {
            RecordingContext context = new RecordingContext(); context.failure = failure;
            assertFalse(DouyinLauncher.launch(context, new DouyinLinkParser().parseUrl(SHORT)));
            assertEquals(1, context.calls);
            assertNull(context.outgoing);
        }
    }

    @Test public void forgedOrNonCanonicalTargetsNeverStartAnActivity() {
        RecordingContext context = new RecordingContext();
        for (String url : new String[]{"https://evil.example/", "intent://evil/", SHORT + "?token=private"}) {
            assertFalse(DouyinLauncher.launch(context, new ParsedLink(ParsedLink.Platform.DOUYIN,
                    ParsedLink.Kind.SHORT_LINK, url, null)));
        }
        assertFalse(DouyinLauncher.launch(context, new ParsedLink(ParsedLink.Platform.DOUYIN,
                ParsedLink.Kind.VIDEO, "https://www.douyin.com/video/123", "456")));
        assertFalse(DouyinLauncher.launch(context, null));
        assertEquals(0, context.calls);
    }
}
