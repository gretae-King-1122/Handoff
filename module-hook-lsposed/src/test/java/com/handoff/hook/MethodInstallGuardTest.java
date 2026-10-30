package com.handoff.hook;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class MethodInstallGuardTest {
    public static class Fixture { public void launch() { } }

    @Test void sameResolvedMethodInstallsOnceButPatchedSameNameClassInstallsSeparately() throws Throwable {
        MethodInstallGuard guard = new MethodInstallGuard();
        AtomicInteger installs = new AtomicInteger();
        assertTrue(guard.install(Fixture.class.getMethod("launch"), () -> installs.incrementAndGet()));
        assertFalse(guard.install(Fixture.class.getMethod("launch"), () -> installs.incrementAndGet()));
        byte[] bytes;
        try (var stream = Fixture.class.getResourceAsStream("MethodInstallGuardTest$Fixture.class")) {
            assertNotNull(stream);
            bytes = stream.readAllBytes();
        }
        Class<?> patched = new ClassLoader(Fixture.class.getClassLoader()) {
            Class<?> copy() { return defineClass(Fixture.class.getName(), bytes, 0, bytes.length); }
        }.copy();
        assertTrue(guard.install(patched.getMethod("launch"), () -> installs.incrementAndGet()));
        assertEquals(2, installs.get());
    }

    @Test void failedMethodInstallationCanBeRetried() throws Throwable {
        MethodInstallGuard guard = new MethodInstallGuard();
        var method = Fixture.class.getMethod("launch");
        IllegalStateException error = new IllegalStateException("framework hook failure");
        assertSame(error, assertThrows(IllegalStateException.class,
                () -> guard.install(method, () -> { throw error; })));
        assertTrue(guard.install(method, () -> { }));
        assertFalse(guard.install(method, () -> fail("already installed")));
    }

    @Test void concurrentAttemptsInstallOneHook() throws Exception {
        MethodInstallGuard guard = new MethodInstallGuard();
        var method = Fixture.class.getMethod("launch");
        AtomicInteger installs = new AtomicInteger();
        long successes = IntStream.range(0, 1000).parallel().filter(i -> {
            try { return guard.install(method, () -> installs.incrementAndGet()); }
            catch (Throwable error) { throw new AssertionError(error); }
        }).count();
        assertEquals(1, successes);
        assertEquals(1, installs.get());
    }
}
