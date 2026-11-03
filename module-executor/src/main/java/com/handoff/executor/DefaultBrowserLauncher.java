package com.handoff.executor;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import com.handoff.api.HostApps;
import com.handoff.rules.links.OrdinaryWebLink;

/** Resolves the current browser preference before launching the original URL into that package. */
public final class DefaultBrowserLauncher {
    private DefaultBrowserLauncher() { }

    public static boolean launch(Context context, String url) {
        if (!OrdinaryWebLink.matches(url)) return false;
        try {
            // A neutral, non-resolving domain avoids selecting an app that owns the real URL's App Link.
            // This is a local package-manager query; the probe URL is never opened or requested.
            Intent probe = webIntent("https://handoff-browser.invalid/");
            PackageManager packages = context.getPackageManager();
            ResolveInfo resolved = packages.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
            ActivityInfo activity = resolved == null ? null : resolved.activityInfo;
            // A system chooser is not a default browser. Do not guess from installed browsers.
            if (activity == null || !activity.exported || !activity.enabled
                    || activity.packageName == null || activity.name == null || "android".equals(activity.packageName)
                    || context.getPackageName().equals(activity.packageName) || HostApps.isSupported(activity.packageName)) return false;
            if (!isBrowser(packages, probe, activity)) return false;
            Intent outgoing = webIntent(url);
            outgoing.setPackage(activity.packageName);
            outgoing.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(outgoing);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Intent webIntent(String url) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(url).normalizeScheme()).addCategory(Intent.CATEGORY_BROWSABLE);
    }

    private static boolean isBrowser(PackageManager packages, Intent probe, ActivityInfo selected) {
        // GET_RESOLVED_FILTER is public on every supported Android version. A resolver/chooser
        // isn't a candidate, and a domain-specific app is not a general web browser.
        for (ResolveInfo candidate : packages.queryIntentActivities(probe,
                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_RESOLVED_FILTER)) {
            ActivityInfo activity = candidate.activityInfo;
            IntentFilter filter = candidate.filter;
            if (activity != null && selected.packageName.equals(activity.packageName) && selected.name.equals(activity.name)
                    && filter != null && filter.hasCategory(Intent.CATEGORY_BROWSABLE)
                    && filter.hasDataScheme("https") && filter.countDataAuthorities() == 0) return true;
        }
        return false;
    }
}
