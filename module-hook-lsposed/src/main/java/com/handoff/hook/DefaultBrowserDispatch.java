package com.handoff.hook;

import com.handoff.api.health.QqAdapters;
import com.handoff.rules.links.OrdinaryWebLink;

/** Shares the existing strict QQ/TIM browser boundary without broadening the social routes. */
final class DefaultBrowserDispatch {
    private DefaultBrowserDispatch() { }

    static boolean matches(String caller, String targetPackage, String targetClass, String url,
                           boolean destroyed, boolean selector, boolean forwardResult, QqAdapters.Contract contract) {
        return !destroyed && !selector && !forwardResult
                && contract.browserCaller().equals(caller) && contract.packageName().equals(targetPackage)
                && targetClass != null && contract.browsers().contains(targetClass)
                && OrdinaryWebLink.matches(url);
    }
}
