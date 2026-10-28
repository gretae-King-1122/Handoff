package com.handoff.hook;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class LoaderInstallGuardTest {
    @Test void sameLoaderIsInstalledOnceEvenConcurrently() {
        LoaderInstallGuard guard = new LoaderInstallGuard();
        ClassLoader loader = new ClassLoader() { };
        assertEquals(1, IntStream.range(0, 1000).parallel().filter(i -> guard.claim(loader)).count());
        assertFalse(guard.claim(loader));
        assertFalse(guard.claim(null));
    }

    @Test void distinctLoadersNeverShareStateEvenIfEqualsClaimsEquality() {
        class EqualLoader extends ClassLoader {
            @Override public boolean equals(Object other) { return other instanceof EqualLoader; }
            @Override public int hashCode() { return 1; }
        }
        LoaderInstallGuard guard = new LoaderInstallGuard();
        ClassLoader first = new EqualLoader();
        ClassLoader second = new EqualLoader();
        assertEquals(first, second);
        assertTrue(guard.claim(first));
        assertTrue(guard.claim(second));
        assertFalse(guard.claim(first));
    }
}
