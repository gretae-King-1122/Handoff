package com.handoff.hook;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/** Method equality includes the declaring Class identity, unlike just a class/method name. */
final class MethodInstallGuard {
    interface Installation { void run() throws Throwable; }
    private final Set<Method> installed = new HashSet<>();

    synchronized boolean install(Method method, Installation installation) throws Throwable {
        if (installed.contains(method)) return false;
        installation.run();
        installed.add(method); // A failed installation must remain retryable.
        return true;
    }
}
