package com.handoff.hook;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class ReflectTest {
    // 必须 public，才能由另一个加载器实例化同名但独立的副本。
    public static class Fixture {
        public String appId = "fixture-app";
        public void launch(String value) { }
    }

    private static Class<?> independentCopy() throws IOException, ClassNotFoundException {
        String name = Fixture.class.getName();
        byte[] bytes;
        try (InputStream stream = Fixture.class.getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            assertNotNull(stream);
            bytes = stream.readAllBytes();
        }
        ClassLoader loader = new ClassLoader(Fixture.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String target, boolean resolve) throws ClassNotFoundException {
                if (!target.equals(name)) return super.loadClass(target, resolve);
                Class<?> result = findLoadedClass(target);
                if (result == null) result = defineClass(target, bytes, 0, bytes.length);
                if (resolve) resolveClass(result);
                return result;
            }
        };
        return loader.loadClass(name);
    }

    @Test void fieldCacheSeparatesIdenticalNamesFromDifferentLoaders() throws Exception {
        Class<?> first = independentCopy();
        Class<?> second = independentCopy();
        assertNotSame(first, second);
        Object a = first.getConstructor().newInstance();
        Object b = second.getConstructor().newInstance();
        assertEquals("fixture-app", Reflect.str(a, "appId"));
        assertEquals("fixture-app", Reflect.str(b, "appId"));
        assertSame(second, Reflect.field(second, "appId").getDeclaringClass());
    }

    @Test void methodCacheSeparatesIdenticalNamesFromDifferentLoaders() throws Exception {
        Class<?> first = independentCopy();
        Class<?> second = independentCopy();
        assertSame(first, Reflect.method(first, "launch", 1).getDeclaringClass());
        assertSame(second, Reflect.method(second, "launch", 1).getDeclaringClass());
    }
}
