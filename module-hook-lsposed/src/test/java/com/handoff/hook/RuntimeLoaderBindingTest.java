package com.handoff.hook;

import com.tencent.qqmini.sdk.launcher.model.MiniAppInfo;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeLoaderBindingTest {
    @Test void patchedRuntimeClassIsBoundBeforeApplicationRunsAndRepeatedCallbackIsIdempotent() throws Throwable {
        byte[] bytes;
        try (var stream = MiniAppInfo.class.getResourceAsStream("MiniAppInfo.class")) {
            assertNotNull(stream);
            bytes = stream.readAllBytes();
        }
        ClassLoader runtime = new ClassLoader(MiniAppInfo.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!MiniAppInfo.class.getName().equals(name)) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) type = defineClass(name, bytes, 0, bytes.length);
                if (resolve) resolveClass(type);
                return type;
            }
        };
        Class<?> actual = Class.forName(MiniAppInfo.class.getName(), false, runtime);
        assertNotSame(MiniAppInfo.class, actual);
        RuntimeLoaderBinding binding = new RuntimeLoaderBinding();
        Set<Class<?>> hooked = new HashSet<>();
        AtomicInteger installs = new AtomicInteger();
        RuntimeLoaderBinding.Installer installer = loader -> {
            installs.incrementAndGet();
            hooked.add(Class.forName(MiniAppInfo.class.getName(), false, loader));
        };
        binding.install(MiniAppInfo.class.getClassLoader(), installer);
        assertFalse(hooked.contains(actual));
        for (int i = 0; i < 2; i++) {
            assertEquals("qq", binding.beforeApplication(() -> runtime, installer, error -> fail(error), () -> {
                assertTrue(hooked.contains(actual), "the Class actually used by QQ must already be bound");
                return "qq";
            }));
        }
        assertEquals(2, installs.get());
    }

    @Test void unchangedOrMissingLoaderNeverReinstallsAndPreservesOriginalReturn() throws Throwable {
        RuntimeLoaderBinding binding = new RuntimeLoaderBinding();
        ClassLoader loader = getClass().getClassLoader();
        AtomicInteger installs = new AtomicInteger();
        RuntimeLoaderBinding.Installer installer = ignored -> installs.incrementAndGet();
        binding.install(loader, installer);
        Object marker = new Object();
        for (ClassLoader current : new ClassLoader[]{loader, loader, null}) {
            assertSame(marker, binding.beforeApplication(() -> current, installer, error -> fail(error), () -> marker));
        }
        assertEquals(1, installs.get());
    }

    @Test void loaderReadOrInstallFailureStillInvokesOriginalOnceAndPreservesItsException() {
        for (boolean readFailure : new boolean[]{false, true}) {
            RuntimeLoaderBinding binding = new RuntimeLoaderBinding();
            AtomicInteger originals = new AtomicInteger();
            AtomicInteger failures = new AtomicInteger();
            IllegalStateException originalError = new IllegalStateException("QQ onCreate");
            assertSame(originalError, assertThrows(IllegalStateException.class, () -> binding.beforeApplication(
                    () -> { if (readFailure) throw new Exception("loader unavailable"); return getClass().getClassLoader(); },
                    loader -> { throw new Exception("hook unavailable"); },
                    error -> { failures.incrementAndGet(); throw new IllegalArgumentException("logger unavailable"); },
                    () -> { originals.incrementAndGet(); throw originalError; })));
            assertEquals(1, originals.get());
            assertEquals(1, failures.get());
        }
    }

    @Test void differentLoaderDelegatingToSameClassDoesNotDuplicateAnActualMethodHook() throws Throwable {
        RuntimeLoaderBinding binding = new RuntimeLoaderBinding();
        MethodInstallGuard methods = new MethodInstallGuard();
        AtomicInteger installs = new AtomicInteger();
        ClassLoader initial = getClass().getClassLoader();
        ClassLoader wrapper = new ClassLoader(initial) { };
        RuntimeLoaderBinding.Installer installer = loader -> methods.install(
                Class.forName(MethodInstallGuardTest.Fixture.class.getName(), false, loader).getMethod("launch"),
                () -> installs.incrementAndGet());
        binding.install(initial, installer);
        binding.beforeApplication(() -> wrapper, installer, error -> fail(error), () -> null);
        assertEquals(1, installs.get());
    }
}
