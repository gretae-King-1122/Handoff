package com.handoff.hook;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Package-ready may be reported repeatedly. Loader identity, not its equals(), defines one install. */
final class LoaderInstallGuard {
    private final Set<ClassLoader> installed = Collections.newSetFromMap(new IdentityHashMap<>());

    synchronized boolean claim(ClassLoader loader) {
        return loader != null && installed.add(loader);
    }
}
