package com.handoff.hook;

/** Bind both package-ready and actual Application loaders without swallowing QQ's original exception. */
final class RuntimeLoaderBinding {
    interface Source { ClassLoader get() throws Throwable; }
    interface Installer { void install(ClassLoader loader) throws Throwable; }
    interface Failure { void report(Throwable error); }
    interface Original { Object proceed() throws Throwable; }

    private final LoaderInstallGuard loaders = new LoaderInstallGuard();

    void install(ClassLoader loader, Installer installer) throws Throwable {
        if (loaders.claim(loader)) installer.install(loader);
    }

    Object beforeApplication(Source source, Installer installer, Failure failure, Original original) throws Throwable {
        try {
            install(source.get(), installer);
        } catch (Throwable error) {
            try { failure.report(error); } catch (Throwable ignored) { }
        }
        // Original invocation must stay outside the recovery boundary: never retry QQ on an exception.
        return original.proceed();
    }
}
