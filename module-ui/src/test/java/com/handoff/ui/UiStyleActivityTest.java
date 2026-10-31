package com.handoff.ui;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.card.MaterialCardView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Deterministic offline renders. No device, framework connection or successful video click is simulated. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class UiStyleActivityTest {
    @Before public void setup() { new UiStyleStore(RuntimeEnvironment.getApplication()).write(UiStyle.MATERIAL); }
    @After public void cleanup() { RuntimeEnvironment.setFontScale(1f); new UiStyleStore(RuntimeEnvironment.getApplication()).write(UiStyle.MATERIAL); }

    @Test public void stylePickerWorksOfflineAndRestoresSettingsPage() throws Exception {
        Bundle saved = new Bundle();
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create().visible()) {
            var activity = controller.get(); DashboardNavigation nav = activity.findViewById(R.id.dashboard_navigation);
            nav.setSelectedItemId(R.id.nav_settings); View choice = activity.findViewById(R.id.appearance_style);
            assertTrue(choice.isEnabled()); choice.performClick();
            AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog(); assertTrue(dialog.isShowing());
            dialog.getListView().performItemClick(null, UiStyle.MIUIX.ordinal(), UiStyle.MIUIX.ordinal());
            ExecutorService worker = ReflectionHelpers.getField(activity, "worker"); worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
            assertEquals(UiStyle.MIUIX, new UiStyleStore(activity).read());
            activity.onSaveInstanceState(saved);
        }
        try (var restored = Robolectric.buildActivity(DashboardActivity.class).create(saved).visible()) {
            assertEquals(UiStyle.MIUIX, ReflectionHelpers.getField(restored.get(), "style"));
            assertEquals(R.id.nav_settings, ((DashboardNavigation)restored.get().findViewById(R.id.dashboard_navigation)).getSelectedItemId());
            assertTrue(restored.get().findViewById(R.id.appearance_style).getContentDescription().toString().contains("Miuix"));
        }
    }
    @Test public void cancellingOrSelectingCurrentStyleDoesNotWriteOrRecreate() {
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create()) {
            var activity = controller.get(); ((DashboardNavigation)activity.findViewById(R.id.dashboard_navigation)).setSelectedItemId(R.id.nav_settings);
            activity.findViewById(R.id.appearance_style).performClick();
            ((AlertDialog)ShadowDialog.getLatestDialog()).getButton(Dialog.BUTTON_NEGATIVE).performClick();
            ReflectionHelpers.callInstanceMethod(activity, "changeStyle", ReflectionHelpers.ClassParameter.from(UiStyle.class, UiStyle.MATERIAL));
            assertFalse(ReflectionHelpers.getField(activity, "styleSaving")); assertEquals(UiStyle.MATERIAL, new UiStyleStore(activity).read());
        }
    }
    @Test public void failedAppearanceWriteLeavesCurrentStyleIntact() throws Exception {
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create()) {
            var activity = controller.get();
            android.content.SharedPreferences broken = (android.content.SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{android.content.SharedPreferences.class},
                    (proxy, method, args) -> { throw new IllegalStateException("unavailable"); });
            ReflectionHelpers.setField(activity, "styleStore", new UiStyleStore(broken));
            ReflectionHelpers.callInstanceMethod(activity, "changeStyle", ReflectionHelpers.ClassParameter.from(UiStyle.class, UiStyle.MIUIX));
            ExecutorService worker = ReflectionHelpers.getField(activity, "worker"); worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(UiStyle.MATERIAL, ReflectionHelpers.getField(activity, "style"));
            assertFalse(ReflectionHelpers.getField(activity, "styleSaving"));
            assertEquals("外观保存失败，未切换风格", org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "w393dp-h851dp-notnight-xhdpi")
    public void activeHeroesUseDistinctPalettesButNotFabricatedHandoffResults() throws Exception {
        for (UiStyle style : UiStyle.values()) {
            new UiStyleStore(RuntimeEnvironment.getApplication()).write(style);
            try (var controller = Robolectric.buildActivity(DashboardActivity.class).create().visible()) {
                var activity = controller.get();
                ReflectionHelpers.setField(activity, "checked", true);
                ReflectionHelpers.setField(activity, "frameworkSnapshot", new FrameworkSnapshot(true, "Fixture", "1", 1, 102,
                        true, true, FrameworkSnapshot.RuntimeState.CURRENT));
                ReflectionHelpers.setField(activity, "frameworkConnected", true);
                ReflectionHelpers.setField(activity, "scope", "已授权");
                ReflectionHelpers.callInstanceMethod(activity, "render");
                assertFalse((boolean)ReflectionHelpers.callInstanceMethod(activity, "current"));
                assertNull(ReflectionHelpers.callInstanceMethod(activity, "latestOutcome"));
                MaterialCardView card = (MaterialCardView)activity.findViewById(R.id.home_status).getParent();
                assertEquals(activity.getColor(style == UiStyle.MATERIAL ? R.color.handoff_primary : R.color.miuix_success), card.getCardBackgroundColor().getDefaultColor());
                View root = activity.getWindow().getDecorView(); layout(root,786,1702);
                save(root,786,1702,new File("build/outputs/ui-preview/"+style.key+"-light/active-fixture.png"));
            }
        }
    }
    @Test @Config(qualifiers = "w393dp-h851dp-notnight-xhdpi")
    public void bothStylesAvoidGestureAndThreeButtonSystemBars() {
        for (UiStyle style : UiStyle.values()) {
            new UiStyleStore(RuntimeEnvironment.getApplication()).write(style);
            try (var controller = Robolectric.buildActivity(DashboardActivity.class).create().visible()) {
                var activity = controller.get(); View content = ReflectionHelpers.getField(activity, "root");
                for (int bottom : new int[]{24,96}) {
                    androidx.core.view.ViewCompat.dispatchApplyWindowInsets(content,new androidx.core.view.WindowInsetsCompat.Builder()
                            .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(),androidx.core.graphics.Insets.of(0,48,0,bottom)).build());
                    View root = activity.getWindow().getDecorView(); layout(root,786,1702);
                    View nav = activity.findViewById(R.id.dashboard_navigation);
                    assertTrue(y(nav)+nav.getHeight()<=root.getHeight()-bottom);
                }
            }
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "w393dp-h851dp-notnight-xhdpi") public void largeHeaderCollapsesIntoToolbarAndReturnsAtTop() {
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create().visible()) {
            var activity = controller.get(); View root = activity.getWindow().getDecorView(); layout(root,786,1702);
            ScrollView scroll = ReflectionHelpers.getField(activity, "scroll");
            com.google.android.material.appbar.MaterialToolbar toolbar = ReflectionHelpers.getField(activity, "toolbar");
            assertEquals("", toolbar.getTitle()); scroll.scrollTo(0, 200); Shadows.shadowOf(Looper.getMainLooper()).idle(); assertEquals("Handoff", toolbar.getTitle());
            scroll.scrollTo(0, 0); Shadows.shadowOf(Looper.getMainLooper()).idle(); assertEquals("", toolbar.getTitle());
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "w393dp-h851dp-notnight-xhdpi")
    public void renderBothStylesLight() throws Exception { renderProfiles("light",786,1702); }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "w393dp-h851dp-night-xhdpi")
    public void renderBothStylesDark() throws Exception { renderProfiles("dark",786,1702); }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "w320dp-h640dp-notnight-xhdpi")
    public void renderBothStylesWithLargeFontAndNarrowViewport() throws Exception {
        RuntimeEnvironment.setFontScale(1.5f); renderProfiles("large-font",640,1280);
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "w320dp-h640dp-notnight-xhdpi")
    public void veryLargeFontFallsBackToLabeledVerticalNavigation() throws Exception {
        RuntimeEnvironment.setFontScale(2.5f);
        new UiStyleStore(RuntimeEnvironment.getApplication()).write(UiStyle.MATERIAL);
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create().visible()) {
            var activity = controller.get(); View root=activity.getWindow().getDecorView(); layout(root,640,1280);
            DashboardNavigation nav=activity.findViewById(R.id.dashboard_navigation);
            for (int i=0;i<4;i++) {
                ViewGroup item=nav.findViewById(nav.getMenu().getItem(i).getItemId());
                TextView label=(TextView)item.getChildAt(1); assertEquals(View.VISIBLE,label.getVisibility());
                assertTrue(label.getHeight()>=label.getLayout().getHeight());
            }
            save(root,640,1280,new File("build/outputs/ui-preview/material-very-large-font/home.png"));
        }
    }
    @Test @Config(sdk=26) public void bothStylesOpenOnMinimumAndroidWithoutFramework() {
        for (UiStyle style : UiStyle.values()) {
            new UiStyleStore(RuntimeEnvironment.getApplication()).write(style);
            try (var controller = Robolectric.buildActivity(DashboardActivity.class).create()) {
                assertNotNull(controller.get().findViewById(R.id.home_status));
                assertEquals(style, ReflectionHelpers.getField(controller.get(), "style"));
            }
        }
    }
    @Test @Config(qualifiers = "w851dp-h393dp-land-xhdpi") public void bothStylesKeepContentAboveNavigationInLandscape() throws Exception {
        renderProfiles("landscape",1702,786);
    }
    private void renderProfiles(String mode, int width, int height) throws Exception {
        for (UiStyle style : UiStyle.values()) {
            new UiStyleStore(RuntimeEnvironment.getApplication()).write(style);
            try (var controller = Robolectric.buildActivity(DashboardActivity.class).create().visible()) {
                var activity = controller.get(); View root = activity.getWindow().getDecorView();
                ReflectionHelpers.setField(activity, "checked", true); ReflectionHelpers.callInstanceMethod(activity, "render");
                DashboardNavigation nav = activity.findViewById(R.id.dashboard_navigation);
                var island = (MaterialCardView) nav.getParent();
                assertEquals(style == UiStyle.MATERIAL ? activity.getColor(R.color.handoff_surface_container) : activity.getColor(R.color.miuix_card), island.getCardBackgroundColor().getDefaultColor());
                assertEquals(style == UiStyle.MATERIAL ? 64f : 0f, island.getRadius(), 0f);
                for (int i = 0; i < 4; i++) {
                    nav.setSelectedItemId(nav.getMenu().getItem(i).getItemId());
                    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400)); layout(root,width,height);
                    for (int j=0; j<4; j++) {
                        View item = nav.findViewById(nav.getMenu().getItem(j).getItemId());
                        assertTrue(item.getWidth()>=96); assertTrue(item.getHeight()>=96);
                        assertEquals(i==j, item.isSelected()); assertEquals(nav.getMenu().getItem(j).getTitle(), item.getContentDescription());
                        View label = ((ViewGroup)item).getChildAt(1);
                        assertEquals(style==UiStyle.MIUIX || i==j ? View.VISIBLE : View.GONE, label.getVisibility());
                        if (label.getVisibility()==View.VISIBLE) assertEquals(0, ((TextView)label).getLayout().getEllipsisCount(0));
                    }
                    save(root,width,height,new File("build/outputs/ui-preview/"+style.key+"-"+mode+"/page-"+i+".png"));
                    ScrollView scroll = ReflectionHelpers.getField(activity,"scroll");
                    ViewGroup body = ReflectionHelpers.getField(activity,"body"); scroll.scrollTo(0,body.getHeight());
                    View last = body.getChildAt(body.getChildCount()-1);
                    assertTrue(y(last)+last.getHeight()<=y(scroll)+scroll.getHeight());
                    assertTrue(y(scroll)+scroll.getHeight()<=y(nav));
                }
            }
        }
    }
    private int y(View view) { int[] p=new int[2]; view.getLocationInWindow(p); return p[1]; }
    private void layout(View root,int width,int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        root.layout(0,0,width,height); Shadows.shadowOf(Looper.getMainLooper()).idle();
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY)); root.layout(0,0,width,height);
    }
    private void save(View root,int width,int height,File path) throws Exception {
        assertTrue(path.getParentFile().isDirectory() || path.getParentFile().mkdirs());
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888); root.draw(new Canvas(bitmap));
        try(var output=new FileOutputStream(path)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output)); }
        bitmap.recycle();
    }
}
