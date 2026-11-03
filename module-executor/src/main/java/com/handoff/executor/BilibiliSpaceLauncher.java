package com.handoff.executor;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Opens only the requested personal space, never the app homepage or a browser chooser. */
public final class BilibiliSpaceLauncher {
    public static final String PACKAGE = "tv.danmaku.bili";

    private BilibiliSpaceLauncher() { }

    public static boolean launch(Context context, String uid) {
        if (uid == null || !uid.matches("[1-9][0-9]{0,18}")) return false;
        try { Long.parseLong(uid); } catch (NumberFormatException ignored) { return false; }
        return tryStart(context, "bilibili://space/" + uid)
                || tryStart(context, "https://space.bilibili.com/" + uid);
    }

    private static boolean tryStart(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
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
