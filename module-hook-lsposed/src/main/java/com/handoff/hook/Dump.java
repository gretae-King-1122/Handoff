package com.handoff.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** 反射打印对象全部实例字段，用于探针阶段的「字段普查」。 */
final class Dump {

    private Dump() {
    }

    static String describe(Object target, int depth) {
        if (target == null) return "null";
        StringBuilder sb = new StringBuilder(512);
        sb.append(target.getClass().getSimpleName()).append('{');
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] fields;
            try {
                fields = c.getDeclaredFields();
            } catch (Throwable ignored) {
                continue;
            }
            for (Field f : fields) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Object v;
                try {
                    f.setAccessible(true);
                    v = f.get(target);
                } catch (Throwable ignored) {
                    continue;
                }
                sb.append(f.getName()).append('=');
                if (v == null) {
                    sb.append("null");
                } else if (v instanceof String || v instanceof Number || v instanceof Boolean || v instanceof Character) {
                    String s = String.valueOf(v);
                    sb.append(s.length() > 300 ? s.substring(0, 300) + "…" : s);
                } else if (depth > 0 && v.getClass().getName().startsWith("com.tencent")) {
                    sb.append(describe(v, depth - 1));
                } else {
                    sb.append('<').append(v.getClass().getSimpleName()).append('>');
                }
                sb.append(", ");
            }
        }
        sb.append('}');
        return sb.toString();
    }
}
