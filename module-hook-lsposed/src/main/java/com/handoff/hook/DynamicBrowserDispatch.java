package com.handoff.hook;

import com.handoff.api.health.QqAdapters;
import com.handoff.api.links.DynamicLinkPolicy;

final class DynamicBrowserDispatch {
    private DynamicBrowserDispatch() { }
    static String parse(String caller, String targetPackage, String targetClass, String url,
                        QqAdapters.Contract contract) {
        if (!contract.browserCaller().equals(caller) || !"com.tencent.mobileqq".equals(targetPackage)
                || targetClass == null || !contract.browsers().contains(targetClass)) return null;
        return DynamicLinkPolicy.normalize(url);
    }
}
