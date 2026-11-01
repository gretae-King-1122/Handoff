package com.handoff.hook;

import com.handoff.api.health.QqAdapters;
import com.handoff.api.links.DynamicLinkPolicy;
import com.handoff.api.links.ParsedLink;
import com.handoff.rules.links.DouyinLinkParser;
import com.handoff.rules.links.KuaishouLinkParser;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** One caller/target check and one URI parse per outgoing browser request. No cross-click cache. */
record BrowserRoute(ParsedLink link, String biliId, String dynamicUrl) {
    private static final DouyinLinkParser DOUYIN = new DouyinLinkParser();
    private static final KuaishouLinkParser KUAISHOU = new KuaishouLinkParser();

    static BrowserRoute parse(String caller, String targetPackage, String targetClass, String url,
                              QqAdapters.Contract contract) {
        if (!contract.browserCaller().equals(caller) || !"com.tencent.mobileqq".equals(targetPackage)
                || targetClass == null || !contract.browsers().contains(targetClass) || url == null) return null;
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) return null;
            return switch (host.toLowerCase(Locale.ROOT)) {
                case "v.douyin.com", "douyin.com", "www.douyin.com" -> platform(DOUYIN.parseUri(uri));
                case "v.kuaishou.com", "v.m.chenzhongtech.com" -> platform(KUAISHOU.parseUri(uri));
                case "bilibili.com", "www.bilibili.com", "m.bilibili.com" -> {
                    String id = BrowserDispatch.videoId(uri);
                    yield id == null ? null : new BrowserRoute(null, id, null);
                }
                default -> {
                    String dynamic = DynamicLinkPolicy.normalizeUri(uri);
                    yield dynamic == null ? null : new BrowserRoute(null, null, dynamic);
                }
            };
        } catch (URISyntaxException ignored) { return null; }
    }

    private static BrowserRoute platform(ParsedLink link) {
        return link == null ? null : new BrowserRoute(link, null, null);
    }
}
