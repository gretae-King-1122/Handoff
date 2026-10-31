package com.handoff.executor;

import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class DynamicAppResolverTest {
    private static final String URL = "https://app.example.com/item/123?share=secret";
    private final Context context = RuntimeEnvironment.getApplication();
    private ResolveInfo add(String pkg, String host) {
        ResolveInfo info = new ResolveInfo(); info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = pkg; info.activityInfo.name = pkg + ".View";
        info.activityInfo.enabled = true; info.activityInfo.exported = true;
        info.activityInfo.applicationInfo = new ApplicationInfo(); info.activityInfo.applicationInfo.enabled = true;
        info.activityInfo.applicationInfo.packageName = pkg; info.nonLocalizedLabel = "Example";
        info.filter = new IntentFilter(); info.filter.addAction("android.intent.action.VIEW");
        info.filter.addCategory("android.intent.category.DEFAULT"); info.filter.addCategory("android.intent.category.BROWSABLE");
        info.filter.addDataScheme("https"); if (host != null) info.filter.addDataAuthority(host, null);
        shadowOf(context.getPackageManager()).addResolveInfoForIntent(DynamicAppResolver.view(URL), info);
        return info;
    }
    @Test public void findsHostSpecificAppWithoutHardcodedPlatformAndDoesNotTreatDeclarationAsVerification() {
        add("example.app", "app.example.com");
        var candidates = DynamicAppResolver.find(context, URL);
        assertEquals(1, candidates.size()); assertEquals("example.app", candidates.get(0).component().getPackageName());
        assertFalse(candidates.get(0).domainApproved());
    }
    @Test public void excludesGenericBrowserAndItsSeparateHostSpecificActivity() {
        ResolveInfo browser = add("example.browser", "app.example.com");
        shadowOf(context.getPackageManager()).addResolveInfoForIntent(
                DynamicAppResolver.view("https://handoff-browser-probe.invalid/"), browser);
        add("example.app", "app.example.com");
        assertEquals(List.of("example.app"), DynamicAppResolver.find(context, URL).stream()
                .map(c -> c.component().getPackageName()).toList());
    }
    @Test public void rejectsAllWebFiltersMissingFiltersAndOverbroadAuthorities() {
        add("example.generic", null); add("example.topLevel", "*.com"); add("example.wildcard", "*");
        add("example.nofilter", "app.example.com").filter = null;
        add("example.wronghost", "other.example.com");
        assertTrue(DynamicAppResolver.find(context, URL).isEmpty());
    }
    @Test public void filtersInaccessibleSuspendedAndExistingPlatforms() {
        add("example.private", "app.example.com").activityInfo.exported = false;
        add("example.disabled", "app.example.com").activityInfo.enabled = false;
        add("example.disabledApp", "app.example.com").activityInfo.applicationInfo.enabled = false;
        add("example.suspended", "app.example.com").activityInfo.applicationInfo.flags |= ApplicationInfo.FLAG_SUSPENDED;
        add("example.secured", "app.example.com").activityInfo.permission = "example.signature.permission";
        for (String pkg : new String[]{"com.tencent.mobileqq", "com.handoff.module", "tv.danmaku.bili",
                "com.ss.android.ugc.aweme", "com.smile.gifmaker"}) add(pkg, "app.example.com");
        assertTrue(DynamicAppResolver.find(context, URL).isEmpty());
    }
    @Test public void deDuplicatesPackageAndSortsChoicesStably() {
        add("z.app", "*.example.com"); add("a.app", "app.example.com");
        ResolveInfo duplicate = add("z.app", "app.example.com"); duplicate.activityInfo.name = "z.app.Other";
        assertEquals(List.of("a.app", "z.app"), DynamicAppResolver.find(context, URL).stream()
                .map(c -> c.component().getPackageName()).toList());
    }
    @Test public void launchesOnlyExactResolvedComponentWithNoQqPayloadAndRechecksStaleChoices() {
        ResolveInfo app = add("example.app", "app.example.com");
        var selected = DynamicAppResolver.find(context, URL).get(0);
        assertTrue(DynamicAppResolver.launch(context, URL, selected));
        var intent = shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
        assertEquals(new ComponentName("example.app", "example.app.View"), intent.getComponent());
        assertEquals(URL, intent.getDataString()); assertNull(intent.getExtras()); assertNull(intent.getSelector());
        app.activityInfo.enabled = false;
        assertFalse(DynamicAppResolver.launch(context, URL, selected));
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
    }
}
