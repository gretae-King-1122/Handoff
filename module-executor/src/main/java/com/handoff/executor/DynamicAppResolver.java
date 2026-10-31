package com.handoff.executor;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import com.handoff.api.links.DynamicLinkPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Discover capabilities, not a platform/package table. Never let a generic browser win. */
final class DynamicAppResolver {
    record Candidate(ComponentName component, String label, boolean domainApproved) {
        Candidate(ComponentName component, String label) { this(component, label, false); }
    }
    private DynamicAppResolver() { }

    static Intent view(String url) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE);
    }

    static List<Candidate> find(Context context, String input) {
        String url = DynamicLinkPolicy.normalize(input);
        if (url == null) return List.of();
        PackageManager pm = context.getPackageManager();
        int flags = PackageManager.MATCH_ALL | PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_RESOLVED_FILTER;
        // A browser may have additional host-specific filters: exclude its whole package as well.
        Set<String> browsers = new HashSet<>();
        for (String probe : List.of("https://handoff-browser-probe.invalid/", "http://handoff-browser-probe.invalid/")) {
            for (ResolveInfo info : pm.queryIntentActivities(view(probe), flags))
                if (info.activityInfo != null) browsers.add(info.activityInfo.packageName);
        }
        List<Candidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Uri uri = Uri.parse(url);
        for (ResolveInfo info : pm.queryIntentActivities(view(url), flags)) {
            var activity = info.activityInfo;
            if (activity == null || !activity.exported || !activity.enabled || activity.applicationInfo == null
                    || !activity.applicationInfo.enabled
                    || (activity.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SUSPENDED) != 0
                    || !DynamicLinkPolicy.allowedPackage(activity.packageName) || browsers.contains(activity.packageName)
                    || (activity.permission != null && context.checkSelfPermission(activity.permission) != PackageManager.PERMISSION_GRANTED)
                    || !specificHost(info.filter, uri)) continue;
            // One choice per installed App, not one choice per matching Activity.
            if (!seen.add(activity.packageName)) continue;
            String label;
            try { label = info.loadLabel(pm).toString().replaceAll("[\\p{Cntrl}]", " "); }
            catch (RuntimeException ignored) { label = activity.packageName; }
            label = label.substring(0, Math.min(80, label.length()));
            result.add(new Candidate(new ComponentName(activity.packageName, activity.name), label,
                    domainApproved(context, activity.packageName, uri.getHost())));
        }
        result.sort(java.util.Comparator.comparing(c -> c.component().getPackageName()));
        return List.copyOf(result);
    }

    private static boolean domainApproved(Context context, String pkg, String host) {
        if (android.os.Build.VERSION.SDK_INT < 31) return false;
        try {
            var manager = context.getSystemService(android.content.pm.verify.domain.DomainVerificationManager.class);
            var state = manager == null ? null : manager.getDomainVerificationUserState(pkg);
            if (state == null || !state.isLinkHandlingAllowed()) return false;
            for (var entry : state.getHostToStateMap().entrySet()) {
                String domain = entry.getKey();
                boolean matches = host.equalsIgnoreCase(domain)
                        || (domain.startsWith("*.") && host.endsWith(domain.substring(1)));
                int value = entry.getValue();
                if (matches && (value == android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_VERIFIED
                        || value == android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_SELECTED)) return true;
            }
        } catch (Exception ignored) { /* Missing domain state is not approval. */ }
        return false;
    }

    private static boolean specificHost(IntentFilter filter, Uri uri) {
        if (filter == null) return false;
        for (int i = 0; i < filter.countDataAuthorities(); i++) {
            IntentFilter.AuthorityEntry authority = filter.getDataAuthority(i);
            String host = authority.getHost();
            if (host == null) continue;
            // *.com / * / suffix-only matches aren't platform capabilities.
            String suffix = host.startsWith("*.") ? host.substring(2) : host;
            if (suffix.indexOf('*') < 0 && suffix.contains(".") && authority.match(uri) >= 0) return true;
        }
        return false;
    }

    static boolean launch(Context context, String url, Candidate selected) {
        if (selected == null) return false;
        try {
            // Re-query after chooser delay: uninstall/disable/filter changes must fail back to QQ.
            if (find(context, url).stream().noneMatch(c -> c.component().equals(selected.component()))) return false;
            context.startActivity(view(url).setComponent(selected.component()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException ignored) { return false; }
    }
}
