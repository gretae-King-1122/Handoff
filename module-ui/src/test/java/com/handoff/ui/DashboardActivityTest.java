package com.handoff.ui;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

/** Android view-tree checks, not a substitute for real device rendering or LSPosed integration. */
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
    @Test public void threePagesNavigateAndFrameworklessSettingsAreReadOnly() {
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create()) {
            var activity = controller.get(); View root = activity.getWindow().getDecorView();
            assertNotNull(find(root, "看见每一次交接"));
            find(root, "▦  回归矩阵").performClick();
            assertNotNull(find(root, "把兼容性留下来"));
            assertNotNull(find(root, "还没有测试轮次。这里不会预填开发设备的成功结果。"));
            find(root, "⇄  适配管理").performClick();
            assertNotNull(find(root, "适配有据可查"));
            TextView toggle = find(root, "交接总开关");
            assertTrue(toggle instanceof Switch); assertFalse(toggle.isEnabled());
            find(root, "◉  自检").performClick();
            assertNotNull(find(root, "看见每一次交接"));
        }
    }
    @Test public void selectedPageSurvivesActivityRecreation() {
        Bundle saved = new Bundle();
        try (var first = Robolectric.buildActivity(DashboardActivity.class).create()) {
            find(first.get().getWindow().getDecorView(), "⇄  适配管理").performClick();
            first.saveInstanceState(saved);
        }
        try (var restored = Robolectric.buildActivity(DashboardActivity.class).create(saved)) {
            assertNotNull(find(restored.get().getWindow().getDecorView(), "适配有据可查"));
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = 35, qualifiers = "w393dp-h851dp-xhdpi")
    public void renderOfflinePreviewOfAllThreePages() throws Exception {
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create().visible()) {
            View root = controller.get().getWindow().getDecorView();
            String[] tabs = {"◉  自检", "▦  回归矩阵", "⇄  适配管理"};
            File folder = new File("build/outputs/ui-preview");
            assertTrue(folder.isDirectory() || folder.mkdirs());
            for (int i = 0; i < tabs.length; i++) {
                find(root, tabs[i]).performClick();
                root.measure(View.MeasureSpec.makeMeasureSpec(786, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1702, View.MeasureSpec.EXACTLY));
                root.layout(0, 0, 786, 1702);
                Bitmap bitmap = Bitmap.createBitmap(786, 1702, Bitmap.Config.ARGB_8888);
                root.draw(new Canvas(bitmap));
                try (var output = new FileOutputStream(new File(folder, "page-" + i + ".png"))) {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
                }
                bitmap.recycle();
            }
        }
    }
}
