package com.handoff.executor;

import android.app.PendingIntent;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.ResultReceiver;
import com.handoff.api.health.QqAdapters;
import com.handoff.api.links.DynamicLinkPolicy;
import java.util.concurrent.atomic.AtomicInteger;

/** Cross-process bridge; only the owned Activity performs discovery and optional network I/O. */
public final class DynamicLauncher {
    public static final int STARTED = 1, FAILED = 2, CANCELLED = 3, FALLBACK_FAILED = 4;
    static final String NETWORK = "allow_dynamic_network";
    // Separate request-code space from the legacy Kuaishou bridge and QQ's own capabilities.
    private static final AtomicInteger REQUESTS = new AtomicInteger(java.util.concurrent.ThreadLocalRandom.current().nextInt());
    private DynamicLauncher() { }
    public static boolean resolve(Context context, String input, Intent originalBrowser,
                                  boolean network, ResultReceiver receiver) {
        String url = DynamicLinkPolicy.normalize(input);
        if (url == null || originalBrowser == null || !(context instanceof Activity)
                || !"com.tencent.mobileqq".equals(context.getPackageName())
                || originalBrowser.getComponent() == null
                || !"com.tencent.mobileqq".equals(originalBrowser.getComponent().getPackageName())
                || (originalBrowser.getPackage() != null && !"com.tencent.mobileqq".equals(originalBrowser.getPackage()))
                || !QqAdapters.STRICT.browsers().contains(originalBrowser.getComponent().getClassName())
                || !url.equals(DynamicLinkPolicy.normalize(originalBrowser.getStringExtra("url")))
                || originalBrowser.getSelector() != null
                || (originalBrowser.getFlags() & Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0) return false;
        PendingIntent fallback = null;
        try {
            fallback = PendingIntent.getActivity(context, REQUESTS.incrementAndGet(), new Intent(originalBrowser),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT);
            Intent resolve = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            resolve.setClassName("com.handoff.module", DynamicResolveActivity.class.getName());
            resolve.putExtra(DynamicResolveActivity.FALLBACK, fallback);
            resolve.putExtra(DynamicResolveActivity.RESULT, receiver);
            resolve.putExtra(NETWORK, network);
            // A real system caller identity, not a spoofable referrer or a borrowed QQ PendingIntent.
            // The QQ delegation page may finish normally; no result payload is required or returned.
            ((Activity) context).startActivityForResult(resolve, REQUESTS.incrementAndGet() & 0xffff);
            return true;
        } catch (RuntimeException ignored) {
            if (fallback != null) fallback.cancel();
            return false;
        }
    }
}
