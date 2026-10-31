package com.handoff.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.time.Duration;
import static org.junit.Assert.*;

/** Checks the actual selected item background, including the area behind its visible label. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class NavigationSelectionTest {
    @Test @Config(qualifiers = "w393dp-h851dp-notnight-xhdpi")
    public void lightCapsuleIncludesIconAndLabelAndMovesToEveryTab() { verify(786, 1702); }

    @Test @Config(qualifiers = "w393dp-h851dp-night-xhdpi")
    public void darkCapsuleIncludesIconAndLabelAndMovesToEveryTab() { verify(786, 1702); }

    @Test @Config(qualifiers = "w320dp-h640dp-notnight-xhdpi")
    public void narrowScreenAndLargeFontKeepLabelInsideCapsule() {
        RuntimeEnvironment.setFontScale(1.5f);
        try { verify(640, 1280); } finally { RuntimeEnvironment.setFontScale(1f); }
    }
    @Test @Config(qualifiers = "w393dp-h851dp-notnight-xhdpi")
    public void miuixLightUsesLabelsWithoutFilledSelectionCapsules() { verify(786, 1702, UiStyle.MIUIX); }
    @Test @Config(qualifiers = "w393dp-h851dp-night-xhdpi")
    public void miuixDarkUsesLabelsWithoutFilledSelectionCapsules() { verify(786, 1702, UiStyle.MIUIX); }
    private void verify(int width, int height) { verify(width, height, UiStyle.MATERIAL); }
    private void verify(int width, int height, UiStyle style) {
        new UiStyleStore(RuntimeEnvironment.getApplication()).write(style);
        try (var controller = Robolectric.buildActivity(DashboardActivity.class).create().visible()) {
            var activity = controller.get(); View root = activity.getWindow().getDecorView();
            var nav = (DashboardNavigation) activity.findViewById(R.id.dashboard_navigation);
            for (int selected = 0; selected < nav.getMenu().size(); selected++) {
                nav.setSelectedItemId(nav.getMenu().getItem(selected).getItemId());
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                root.layout(0, 0, width, height);
                for (int i = 0; i < nav.getMenu().size(); i++) {
                    var menu = nav.getMenu().getItem(i); View item = nav.findViewById(menu.getItemId());
                    TextView label = visibleLabel(item, menu.getTitle().toString());
                    ImageView icon = image(item);
                    assertNotNull(icon);
                    if (style == UiStyle.MATERIAL && i != selected) { assertNull("Only the selected destination has a visible label", label); continue; }
                    assertNotNull(label);
                    assertEquals(0, label.getLayout().getEllipsisCount(0));
                    Bitmap background = Bitmap.createBitmap(item.getWidth(), item.getHeight(), Bitmap.Config.ARGB_8888);
                    item.getBackground().setBounds(0, 0, item.getWidth(), item.getHeight());
                    item.getBackground().draw(new Canvas(background));
                    int expected = style == UiStyle.MATERIAL && i == selected ? activity.getColor(R.color.handoff_primary) : 0;
                    assertColorAtChild(background, item, icon, icon.getHeight() / 2, expected);
                    assertColorAtChild(background, item, label, label.getHeight() / 2, expected);
                    assertColorAtChild(background, item, label, label.getHeight() - 2, expected);
                    background.recycle();
                }
            }
        } finally { new UiStyleStore(RuntimeEnvironment.getApplication()).write(UiStyle.MATERIAL); }
    }
    private void assertColorAtChild(Bitmap bitmap, View parent, View child, int dy, int expected) {
        int[] p = new int[2], c = new int[2]; parent.getLocationInWindow(p); child.getLocationInWindow(c);
        assertEquals("The capsule must contain both icon and label", expected,
                bitmap.getPixel(c[0] - p[0] + child.getWidth() / 2, c[1] - p[1] + dy));
    }
    private TextView visibleLabel(View view, String title) {
        if (!view.isShown()) return null;
        if (view instanceof TextView t && title.contentEquals(t.getText())) return t;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            var found = visibleLabel(group.getChildAt(i), title); if (found != null) return found;
        }
        return null;
    }
    private ImageView image(View view) {
        if (view instanceof ImageView icon) return icon;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            var found = image(group.getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
}
