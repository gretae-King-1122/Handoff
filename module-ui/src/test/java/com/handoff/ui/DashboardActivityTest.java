package com.handoff.ui;

import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ScrollView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import java.time.Duration;
import org.json.JSONObject;
import static org.junit.Assert.*;

/** View checks and simulated renders, not real-device screenshots or LSPosed integration tests. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DashboardActivityTest {
    private TextView find(View view, String text) {
        if (view instanceof TextView t && text.contentEquals(t.getText())) return t;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = find(group.getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private void select(DashboardActivity activity, int id) {
        ((DashboardNavigation) activity.findViewById(R.id.dashboard_navigation)).setSelectedItemId(id);
    }
    @Test public void homeFocusesOnFrameworkActivationAndVersions() {
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create()) {
            var activity = controller.get(); View root = activity.getWindow().getDecorView();
            assertNotNull(find(root, "激活状态")); assertNotNull(find(root, "正在检查激活状态"));
            assertNotNull(find(root, "LSPosed / 框架信息")); assertNotNull(find(root, "版本与设备"));
            assertNotNull(find(root, "libxposed API"));
            assertTrue(find(root, "激活状态").getParent().getParent() instanceof MaterialCardView);
            assertNull(find(root, "最近信号"));
            find(root, "查看入口与交接诊断").performClick();
            assertNotNull(find(root, "入口与交接诊断"));
        }
    }
    @Test public void fourPagesNavigateAndFrameworklessSettingsAreReadOnly() {
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create()) {
            var activity = controller.get(); View root = activity.getWindow().getDecorView();
            select(activity, R.id.nav_regression);
            assertNotNull(find(root, "还没有测试轮次。这里不会预填开发设备的成功结果。"));
            select(activity, R.id.nav_settings);
            TextView toggle = find(root, "交接总开关");
            assertTrue(toggle instanceof MaterialSwitch); assertFalse(toggle.isEnabled());
            select(activity, R.id.nav_diagnostics);
            assertNotNull(find(root, "最近信号"));
            select(activity, R.id.nav_home);
            assertNotNull(find(root, "LSPosed / 框架信息"));
        }
    }
    @Test public void selectedPageSurvivesActivityRecreation() {
        Bundle saved = new Bundle();
        try (var first = Robolectric.buildActivity(DashboardActivity.class).create()) {
            select(first.get(), R.id.nav_settings); first.saveInstanceState(saved);
        }
        try (var restored = Robolectric.buildActivity(DashboardActivity.class).create(saved)) {
            assertNotNull(find(restored.get().getWindow().getDecorView(), "交接总开关"));
            assertEquals(R.id.nav_settings, ((DashboardNavigation) restored.get().findViewById(R.id.dashboard_navigation)).getSelectedItemId());
        }
    }
    @Test public void disabledHandoffIsSeparateFromConfirmedActivation() {
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create()) {
            var activity = controller.get();
            ReflectionHelpers.setField(activity, "checked", true);
            ReflectionHelpers.setField(activity, "frameworkSnapshot", new FrameworkSnapshot(true, "LSPosed", "2.2.1", 7268, 102,
                    true, true, FrameworkSnapshot.RuntimeState.CURRENT));
            ReflectionHelpers.setField(activity, "settingsReadable", true);
            ReflectionHelpers.setField(activity, "settings", Map.of("enabled", false));
            ReflectionHelpers.callInstanceMethod(activity, "render");
            View root = activity.getWindow().getDecorView();
            assertNotNull(find(root, "已在 QQ 激活"));
            assertNotNull(find(root, "视频交接  ·  已关闭"));
        }
    }
    @Test public void historicalFrameworkEvidenceCannotBecomeCurrent() throws Exception {
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create()) {
            var activity = controller.get();
            ReflectionHelpers.setField(activity, "frameworkConnected", true);
            ReflectionHelpers.setField(activity, "moduleCode", 6L); ReflectionHelpers.setField(activity, "qqCode", 16580L);
            ReflectionHelpers.setField(activity, "framework", "LSPosed 2.2.1");
            ReflectionHelpers.setField(activity, "health", new JSONObject().put("module", 6).put("qq", 16580).put("framework", "OldFramework 1"));
            assertFalse((boolean) ReflectionHelpers.callInstanceMethod(activity, "current"));
        }
    }
    @Test public void navigationResetsScrollButRefreshKeepsPosition() {
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create().visible()) {
            var activity = controller.get(); View root = activity.getWindow().getDecorView();
            select(activity, R.id.nav_diagnostics);
            root.measure(View.MeasureSpec.makeMeasureSpec(786, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1702, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 786, 1702); Shadows.shadowOf(Looper.getMainLooper()).idle();
            ScrollView scroll = ReflectionHelpers.getField(activity, "scroll");
            scroll.scrollTo(0, 300); assertEquals(300, scroll.getScrollY());
            ReflectionHelpers.callInstanceMethod(activity, "render");
            Shadows.shadowOf(Looper.getMainLooper()).idle(); assertEquals(300, scroll.getScrollY());
            select(activity, R.id.nav_home);
            Shadows.shadowOf(Looper.getMainLooper()).idle(); assertEquals(0, scroll.getScrollY());
        }
    }
    @Test public void islandNavigationIsInsetAndEveryPageCanRevealItsLastItem() {
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create().visible()) {
            var activity = controller.get(); View root = activity.getWindow().getDecorView();
            var nav = (DashboardNavigation) activity.findViewById(R.id.dashboard_navigation);
            assertTrue("Navigation must sit in a rounded Material island", nav.getParent() instanceof MaterialCardView);
            for (int id : new int[]{R.id.nav_home, R.id.nav_diagnostics, R.id.nav_regression, R.id.nav_settings}) {
                select(activity, id); layout(root, 786, 1702);
                assertLastItemClearsNavigation(activity);
                View island = (View) nav.getParent(); int[] position = location(island);
                assertTrue(position[0] > 0); assertTrue(position[0] + island.getWidth() < root.getWidth());
            }
        }
    }
    @Test @Config(sdk = 35, qualifiers = "w393dp-h851dp-notnight-xhdpi")
    public void expressiveIslandHasTonalBackgroundAndKeepsTouchTargets() {
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create().visible()) {
            var activity = controller.get(); layout(activity.getWindow().getDecorView(), 786, 1702);
            var nav = (DashboardNavigation) activity.findViewById(R.id.dashboard_navigation);
            var island = (MaterialCardView) nav.getParent();
            assertEquals(activity.getColor(R.color.handoff_surface_container), island.getCardBackgroundColor().getDefaultColor());
            assertEquals(0, island.getStrokeWidth()); assertEquals(12f, island.getCardElevation(), 0f);
            assertTrue(nav.getHeight() <= 128); // 64dp, previously 80dp.
            for (int i = 0; i < nav.getMenu().size(); i++) {
                View item = nav.findViewById(nav.getMenu().getItem(i).getItemId());
                assertTrue(item.getWidth() >= 96); assertTrue(item.getHeight() >= 96); // 48dp touch target.
            }
            assertLastItemClearsNavigation(activity);
        }
    }
    @Test public void initialRenderRefreshAndPauseLeaveContentFullyVisible() {
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create().start().resume().visible()) {
            var activity = controller.get();
            View body = ReflectionHelpers.getField(activity, "body");
            assertEquals(1f, body.getAlpha(), 0f); assertEquals(0f, body.getTranslationY(), 0f);
            select(activity, R.id.nav_diagnostics);
            ReflectionHelpers.callInstanceMethod(activity, "render");
            assertEquals(1f, body.getAlpha(), 0f); assertEquals(0f, body.getTranslationY(), 0f);
            select(activity, R.id.nav_settings); controller.pause();
            assertEquals(1f, body.getAlpha(), 0f); assertEquals(0f, body.getTranslationY(), 0f);
        }
    }
    @Test public void islandRespectsGestureAndThreeButtonSystemInsets() {
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create().visible()) {
            var activity = controller.get(); View root = activity.getWindow().getDecorView();
            View contentRoot = ReflectionHelpers.getField(activity, "root");
            for (int bottom : new int[]{24, 96}) {
                ViewCompat.dispatchApplyWindowInsets(contentRoot, new WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 48, 0, bottom)).build());
                layout(root, 786, 1702);
                View nav = activity.findViewById(R.id.dashboard_navigation);
                assertTrue(location(nav)[1] + nav.getHeight() < root.getHeight() - bottom);
                assertLastItemClearsNavigation(activity);
            }
        }
    }
    @Test @Config(sdk = 35, qualifiers = "w851dp-h393dp-land-xhdpi")
    public void landscapeIslandIsCenteredAndDoesNotCoverShortViewport() {
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create().visible()) {
            var activity = controller.get(); View root = activity.getWindow().getDecorView();
            layout(root, 1702, 786);
            View island = (View) activity.findViewById(R.id.dashboard_navigation).getParent();
            assertTrue(island instanceof MaterialCardView);
            assertTrue(island.getWidth() <= 960); // 480dp at xhdpi.
            assertEquals(root.getWidth() / 2, location(island)[0] + island.getWidth() / 2, 1);
            assertLastItemClearsNavigation(activity);
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = 35, qualifiers = "w320dp-h640dp-notnight-xhdpi")
    public void renderLargeFontOnNarrowScreen() throws Exception {
        RuntimeEnvironment.setFontScale(1.5f);
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create().visible()) {
            View root = controller.get().getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 640, 1280);
            TextView title = find(root, "正在检查激活状态");
            assertTrue(title.getWidth() > 0); assertEquals(0, title.getLayout().getEllipsisCount(0));
            writePreview(root, 640, 1280, new File("build/outputs/ui-preview/material-large-font/home.png"));
            assertLastItemClearsNavigation(controller.get());
        } finally { RuntimeEnvironment.setFontScale(1f); }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = 35, qualifiers = "w393dp-h851dp-notnight-xhdpi")
    public void renderLightPreview() throws Exception { renderPages("light"); }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = 35, qualifiers = "w393dp-h851dp-night-xhdpi")
    public void renderDarkPreview() throws Exception { renderPages("dark"); }

    @Test @Config(sdk = 26)
    public void minimumSupportedAndroidCanOpenHome() {
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create()) {
            assertNotNull(find(controller.get().getWindow().getDecorView(), "激活状态"));
        }
    }
    private void renderPages(String mode) throws Exception {
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create().visible()) {
            var activity = controller.get(); View root = activity.getWindow().getDecorView();
            int[] tabs = {R.id.nav_home, R.id.nav_diagnostics, R.id.nav_regression, R.id.nav_settings};
            File folder = new File("build/outputs/ui-preview/material-" + mode);
            assertTrue(folder.isDirectory() || folder.mkdirs());
            for (int i = 0; i < tabs.length; i++) {
                select(activity, tabs[i]);
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
                root.measure(View.MeasureSpec.makeMeasureSpec(786, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1702, View.MeasureSpec.EXACTLY));
                root.layout(0, 0, 786, 1702);
                writePreview(root, 786, 1702, new File(folder, "page-" + i + ".png"));
                assertLastItemClearsNavigation(activity);
                if (i == 0) writePreview(root, 786, 1702, new File(folder, "home-bottom.png"));
            }
        }
    }
    private void writePreview(View root, int width, int height, File file) throws Exception {
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        try (var output = new FileOutputStream(file)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); }
        bitmap.recycle();
    }
    private void layout(View root, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height); Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private int[] location(View view) { int[] p = new int[2]; view.getLocationInWindow(p); return p; }
    private void assertLastItemClearsNavigation(DashboardActivity activity) {
        ScrollView scroll = ReflectionHelpers.getField(activity, "scroll");
        ViewGroup body = ReflectionHelpers.getField(activity, "body");
        View nav = activity.findViewById(R.id.dashboard_navigation);
        scroll.scrollTo(0, body.getHeight());
        View last = body.getChildAt(body.getChildCount() - 1);
        int scrollBottom = location(scroll)[1] + scroll.getHeight();
        assertTrue("Last item must scroll fully into the content viewport", location(last)[1] + last.getHeight() <= scrollBottom);
        assertTrue("Content viewport must not extend behind the island", scrollBottom <= location(nav)[1]);
    }
}
