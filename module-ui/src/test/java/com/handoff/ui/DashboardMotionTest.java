package com.handoff.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.time.Duration;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DashboardMotionTest {
    private ActivityController<Activity> controller;
    private View content, first, second;
    private DashboardMotion motion;
    private float previousScale;
    @Before public void setup() {
        previousScale = ReflectionHelpers.callStaticMethod(ValueAnimator.class, "getDurationScale");
        durationScale(1f);
        controller = Robolectric.buildActivity(Activity.class).setup().visible();
        FrameLayout root = new FrameLayout(controller.get());
        content = new View(controller.get()); first = new View(controller.get()); second = new View(controller.get());
        root.addView(content); root.addView(first); root.addView(second); controller.get().setContentView(root);
        assertTrue(content.isAttachedToWindow()); motion = new DashboardMotion();
    }
    @After public void cleanup() { motion.stop(); controller.close(); durationScale(previousScale); }
    @Test public void pageAndSelectionAnimateThenReturnToExactRestingState() {
        assertTrue(ValueAnimator.areAnimatorsEnabled());
        motion.play(content, first);
        seek(32);
        assertTrue("alpha=" + content.getAlpha(), content.getAlpha() < 1f);
        assertTrue("y=" + content.getTranslationY(), content.getTranslationY() > 0f);
        assertTrue(first.getScaleX() < 1f);
        advance(300); assertResting(content, first);
    }
    @Test public void rapidSwitchResetsOldSelectionAndFinishesLatestPage() {
        motion.play(content, first); seek(32); motion.play(content, second);
        assertEquals(1f, first.getScaleX(), 0f); assertEquals(1f, first.getScaleY(), 0f);
        advance(300); assertResting(content, second);
    }
    @Test public void cancellationRestoresVisibilityAndCanBeRepeatedSafely() {
        motion.play(content, first); seek(32); motion.stop(); motion.stop();
        assertResting(content, first); advance(300); assertResting(content, first);
    }
    @Test public void disabledSystemAnimationsNeverHideOrTranslateContent() {
        durationScale(0f); assertFalse(ValueAnimator.areAnimatorsEnabled());
        motion.play(content, first); assertResting(content, first);
    }
    @Test public void detachedContentDoesNotStartAnInvisibleAnimation() {
        View detached = new View(controller.get()); motion.play(detached, first);
        assertResting(detached, first);
    }
    private void assertResting(View body, View selected) {
        assertEquals(1f, body.getAlpha(), 0f); assertEquals(0f, body.getTranslationY(), 0f);
        assertEquals(1f, selected.getScaleX(), 0f); assertEquals(1f, selected.getScaleY(), 0f);
    }
    private void durationScale(float value) {
        ReflectionHelpers.callStaticMethod(ValueAnimator.class, "setDurationScale", ReflectionHelpers.ClassParameter.from(float.class, value));
    }
    private void seek(long ms) {
        android.animation.AnimatorSet animation = ReflectionHelpers.getField(motion, "running");
        assertNotNull(animation); animation.setCurrentPlayTime(ms);
    }
    private void advance(long ms) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }
}
