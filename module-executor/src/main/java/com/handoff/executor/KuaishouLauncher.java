package com.handoff.executor;

import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.os.ResultReceiver;
import android.net.Uri;
import com.handoff.api.links.ParsedLink;
import com.handoff.rules.links.KuaishouLinkParser;
import com.handoff.api.health.QqAdapters;
import java.util.concurrent.atomic.AtomicInteger;

/** The installed mainland Kuaishou registers this share host. No guessed scheme or homepage fallback. */
public final class KuaishouLauncher {
    public static final String PACKAGE = "com.smile.gifmaker";
    public static final int STARTED = 1, FAILED = 2, CANCELLED = 3, FALLBACK_FAILED = 4;
    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static final KuaishouLinkParser PARSER = new KuaishouLinkParser();
    private KuaishouLauncher() { }

    /** Acceptance is a dispatch signal, not proof of the intended work being visible. */
    public static boolean launch(Context context, ParsedLink link) {
        if (link == null || link.kind() != ParsedLink.Kind.VIDEO
                || !link.equals(PARSER.parseUrl(link.canonicalUrl()))) return false;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link.canonicalUrl()));
            intent.setPackage(PACKAGE);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (RuntimeException ignored) { return false; }
    }

    /** Only accepted resolver startup is synchronous; the receiver reports the actual terminal outcome. */
    public static boolean resolveShort(Context context, ParsedLink link, Intent originalBrowser,
                                       ResultReceiver receiver) {
        if (link == null || link.kind() != ParsedLink.Kind.SHORT_LINK
                || !link.equals(PARSER.parseUrl(link.canonicalUrl())) || originalBrowser == null
                || !"com.tencent.mobileqq".equals(context.getPackageName())
                || originalBrowser.getComponent() == null
                || !"com.tencent.mobileqq".equals(originalBrowser.getComponent().getPackageName())
                || !QqAdapters.STRICT.browsers().contains(originalBrowser.getComponent().getClassName())
                || !link.equals(PARSER.parseUrl(originalBrowser.getStringExtra("url")))
                || originalBrowser.getSelector() != null
                || (originalBrowser.getFlags() & Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0) return false;
        PendingIntent fallback = null;
        try {
            // Unique one-shot immutable capability: no caller-supplied fill-in Intent or arbitrary target.
            fallback = PendingIntent.getActivity(context, REQUESTS.incrementAndGet(), new Intent(originalBrowser),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT);
            Intent resolve = new Intent(Intent.ACTION_VIEW, Uri.parse(link.canonicalUrl()));
            resolve.setClassName("com.handoff.module", KuaishouResolveActivity.class.getName());
            resolve.putExtra(KuaishouResolveActivity.FALLBACK, fallback);
            resolve.putExtra(KuaishouResolveActivity.RESULT, receiver);
            context.startActivity(resolve);
            return true;
        } catch (RuntimeException ignored) {
            if (fallback != null) fallback.cancel();
            return false;
        }
    }
}
