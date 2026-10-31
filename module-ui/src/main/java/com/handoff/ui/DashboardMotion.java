package com.handoff.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Short, cancellable navigation motion. Polling and initial/restored renders never animate. */
final class DashboardMotion {
    private AnimatorSet running;
    private View content, selected;

    void play(View nextContent, View nextSelected) {
        stop();
        if (!ValueAnimator.areAnimatorsEnabled() || !nextContent.isAttachedToWindow()) return;
        content = nextContent; selected = nextSelected;
        float offset = 6 * content.getResources().getDisplayMetrics().density;
        AnimatorSet animation = new AnimatorSet();
        var fade = ObjectAnimator.ofFloat(content, View.ALPHA, 0.6f, 1f).setDuration(160);
        var slide = ObjectAnimator.ofFloat(content, View.TRANSLATION_Y, offset, 0f).setDuration(220);
        if (selected != null) animation.playTogether(fade, slide,
                ObjectAnimator.ofFloat(selected, View.SCALE_X, 0.94f, 1f).setDuration(180),
                ObjectAnimator.ofFloat(selected, View.SCALE_Y, 0.94f, 1f).setDuration(180));
        else animation.playTogether(fade, slide);
        animation.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        animation.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator finished) {
                if (running == finished) { running = null; resetViews(); }
            }
        });
        running = animation; animation.start();
    }
    void stop() {
        AnimatorSet previous = running; running = null;
        if (previous != null) { previous.removeAllListeners(); previous.cancel(); }
        resetViews();
    }
    private void resetViews() {
        if (content != null) { content.setAlpha(1f); content.setTranslationY(0f); }
        if (selected != null) { selected.setScaleX(1f); selected.setScaleY(1f); }
        content = null; selected = null;
    }
}
