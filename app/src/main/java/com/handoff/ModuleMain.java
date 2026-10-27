package com.handoff;

import android.util.Log;

import com.handoff.api.HookBackend;
import com.handoff.hook.LsposedBackend;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 模块入口。
 *
 * <p>libxposed 会在 {@code attachFramework()} 之后调用 {@link #onModuleLoaded}，
 * 因此这里不要在构造函数里做任何事。
 */
public final class ModuleMain extends XposedModule {

    private static final String TAG = "Handoff";
    private static final String QQ_PACKAGE_PREFIX = "com.tencent.mobileqq";

    private PipelineImpl pipeline;
    private String processName = "";

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        try {
            processName = param.getProcessName();
            pipeline = new PipelineImpl(this, processName);
            log(Log.INFO, TAG, "loaded in " + processName
                    + " | " + getFrameworkName() + " " + getFrameworkVersion()
                    + " | api=" + getApiVersion());
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "onModuleLoaded failed", t);
        }
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (param == null) return;
        try {
            if (!param.getPackageName().startsWith(QQ_PACKAGE_PREFIX)) return;
            if (pipeline == null) return;

            HookBackend backend = new LsposedBackend(this, pipeline.reporter());
            backend.install(param.getClassLoader(), processName, pipeline::handle);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "onPackageReady failed", t);
        }
    }

    XposedInterface self() {
        return this;
    }
}
