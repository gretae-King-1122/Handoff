package com.handoff.hook;

import com.handoff.api.health.QqAdapters;
import com.handoff.api.links.ParsedLink;
import com.handoff.rules.links.KuaishouLinkParser;

/** Independent Kuaishou route; it must not consume Douyin, Bilibili or arbitrary browser requests. */
final class KuaishouBrowserDispatch {
    private static final KuaishouLinkParser PARSER = new KuaishouLinkParser();
    private KuaishouBrowserDispatch() { }

    static ParsedLink parse(String caller, String targetPackage, String targetClass, String url,
                            QqAdapters.Contract contract) {
        if (!contract.browserCaller().equals(caller) || !"com.tencent.mobileqq".equals(targetPackage)
                || targetClass == null || !contract.browsers().contains(targetClass)) return null;
        return PARSER.parseUrl(url);
    }
}
