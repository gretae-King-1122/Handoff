package com.handoff.hook;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Shared video launch attempts. An App homepage is deliberately not a video success. */
final class BiliLauncher {
    static final String PACKAGE = "tv.danmaku.bili";

    private BiliLauncher() { }

    static String launchVideo(Context context, String url, String id) {
        if (tryStart(context, url)) return "http+setPackage";
        if (id.startsWith("av")) {
            if (tryStart(context, "bilibili://video/" + id.substring(2))) return "scheme:aid";
            if (tryStart(context, "bilibili://video/" + id)) return "scheme:av";
        } else if (tryStart(context, "bilibili://video/" + id)) {
            return "scheme:bvid";
        }
        return null;
    }

    private static boolean tryStart(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setPackage(PACKAGE);
            context.startActivity(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
