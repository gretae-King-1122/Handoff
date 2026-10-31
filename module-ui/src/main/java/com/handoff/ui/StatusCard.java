package com.handoff.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.widget.LinearLayout;

/** Original vector decoration; content and activation semantics remain ordinary accessible text. */
final class StatusCard extends LinearLayout {
    private final UiStyle style;
    private final boolean active;
    private final int color, outline;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    StatusCard(Context context, UiStyle style, boolean active, int color, int outline) {
        super(context); this.style = style; this.active = active; this.color = color; this.outline = outline;
        setOrientation(VERTICAL); setWillNotDraw(false);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        paint.reset(); paint.setAntiAlias(true); paint.setStyle(Paint.Style.STROKE);
        if (style == UiStyle.MATERIAL && !active) {
            paint.setColor(outline); paint.setStrokeWidth(d);
            paint.setPathEffect(new DashPathEffect(new float[]{5*d, 5*d}, 0));
            canvas.drawRoundRect(d, d, getWidth()-d, getHeight()-d, 28*d, 28*d, paint);
            paint.setPathEffect(null);
        }
        paint.setColor(color); paint.setAlpha(active ? 26 : 12); paint.setStrokeWidth(6*d);
        if (style == UiStyle.MATERIAL) {
            for (int i = 0; i < 3; i++) canvas.drawCircle(getWidth()-18*d, 28*d, (28+i*22)*d, paint);
        } else if (active) {
            paint.setStrokeWidth(10*d); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            Path check = new Path(); check.moveTo(getWidth()-112*d, getHeight()-61*d);
            check.lineTo(getWidth()-78*d, getHeight()-29*d); check.lineTo(getWidth()-22*d, getHeight()-102*d);
            canvas.drawPath(check, paint);
        }
    }
}
