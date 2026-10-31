package com.handoff.hook;

import com.handoff.api.health.QqAdapters;
import com.handoff.api.links.ParsedLink;
import com.handoff.rules.links.DouyinLinkParser;

/** Douyin-only route policy. The existing Bilibili parser and launcher remain independent. */
final class DouyinBrowserDispatch {
    private static final DouyinLinkParser PARSER = new DouyinLinkParser();
    private DouyinBrowserDispatch() { }

    static ParsedLink parse(String caller, String targetPackage, String targetClass, String url,
                            QqAdapters.Contract contract) {
        if (!contract.browserCaller().equals(caller) || !"com.tencent.mobileqq".equals(targetPackage)
                || targetClass == null || !contract.browsers().contains(targetClass)) return null;
        return PARSER.parseUrl(url);
    }
}
