package com.handoff.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 极简反射工具：libxposed API 102 不提供 XposedHelpers，只能自己来。
 *
 * <p>全部查找结果静态缓存；字段读失败一律返回 null，绝不抛给上层。
 */
public final class Reflect {

    // Class 身份包含 ClassLoader；QQ 动态 Dex 可同时存在同名但不兼容的类。
    // Cache misses too: optional fields absent in a QQ version must not throw on every event.
    private static final Map<Class<?>, Map<String, Optional<Field>>> FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Optional<Method>>> METHODS = new ConcurrentHashMap<>();

    private Reflect() {
    }

    /** 沿父类链查字段并 setAccessible；找不到返回 null。 */
    public static Field field(Class<?> cls, String name) {
        if (cls == null || name == null) return null;
        Map<String, Optional<Field>> cache = FIELDS.computeIfAbsent(cls, ignored -> new ConcurrentHashMap<>());
        Optional<Field> cached = cache.get(name);
        if (cached != null) return cached.orElse(null);
        return cache.computeIfAbsent(name, ignored -> Optional.ofNullable(findField(cls, name))).orElse(null);
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // 继续沿父类找
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    /** 读字段值；任何异常返回 null。 */
    public static Object get(Object target, String name) {
        if (target == null) return null;
        try {
            Field f = field(target.getClass(), name);
            return f == null ? null : f.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 读成字符串；null 保持 null，非字符串走 toString。 */
    public static String str(Object target, String name) {
        Object v = get(target, name);
        if (v == null) return null;
        if (v instanceof String) {
            String s = (String) v;
            return s.isEmpty() ? null : s;
        }
        return String.valueOf(v);
    }

    /** 读成 Integer；读不到或类型不符返回 null。 */
    public static Integer integer(Object target, String name) {
        Object v = get(target, name);
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Number) return ((Number) v).intValue();
        return null;
    }

    /**
     * 按「名字 + 参数个数」找方法，不锁定精确参数类型（抗 QQ 改版）。
     *
     * @param paramCount 期望参数个数；&lt; 0 表示不限
     */
    public static Method method(Class<?> cls, String name, int paramCount) {
        if (cls == null || name == null) return null;
        String key = name + '/' + paramCount;
        Map<String, Optional<Method>> cache = METHODS.computeIfAbsent(cls, ignored -> new ConcurrentHashMap<>());
        Optional<Method> cached = cache.get(key);
        if (cached != null) return cached.orElse(null);
        return cache.computeIfAbsent(key, ignored -> Optional.ofNullable(findMethod(cls, name, paramCount))).orElse(null);
    }

    private static Method findMethod(Class<?> cls, String name, int paramCount) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                if (paramCount >= 0 && m.getParameterCount() != paramCount) continue;
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                    // 不可访问也照样 hook，框架不需要 setAccessible
                }
                return m;
            }
        }
        return null;
    }

    /** 类名是否以指定前缀开头（用于 `AppBrandUI` / `AppBrandUI1~3` 这种多实例容器）。 */
    public static boolean simpleNameStartsWith(Class<?> cls, String prefix) {
        return cls != null && cls.getSimpleName().startsWith(prefix);
    }
}
