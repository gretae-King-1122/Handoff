package com.handoff.executor;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.handoff.api.links.ParsedLink;
import com.handoff.rules.links.DouyinLinkParser;

/** Only attempts the recognized content URL; no browser chooser, guessed scheme or homepage fallback. */
public final class DouyinLauncher {
    public static final String PACKAGE = "com.ss.android.ugc.aweme";
    private static final DouyinLinkParser PARSER = new DouyinLinkParser();
    private DouyinLauncher() { }

    /** True means Android accepted the request, not that the app resolved the short link or played a video. */
    public static boolean launch(Context context, ParsedLink link) {
        if (link == null || !link.equals(PARSER.parseUrl(link.canonicalUrl()))) return false;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link.canonicalUrl()));
            intent.setPackage(PACKAGE);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
