package com.handoff.ui;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.lang.reflect.Proxy;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class UiStyleStoreTest {
    @Test public void newInstallDefaultsToMaterialAndUnknownValuesFallBack() {
        SharedPreferences prefs = RuntimeEnvironment.getApplication().getSharedPreferences("test-style", Context.MODE_PRIVATE);
        prefs.edit().clear().commit(); UiStyleStore store = new UiStyleStore(prefs);
        assertEquals(UiStyle.MATERIAL, store.read());
        prefs.edit().putString("style", "future-style").commit(); assertEquals(UiStyle.MATERIAL, store.read());
        prefs.edit().putInt("style", 23).commit(); assertEquals(UiStyle.MATERIAL, store.read());
        assertEquals(UiStyle.MATERIAL, UiStyle.parse(null));
    }
    @Test public void bothStylesPersistAcrossStoreInstancesWithoutRemoteSettings() {
        Context context = RuntimeEnvironment.getApplication();
        for (UiStyle style : UiStyle.values()) {
            assertTrue(new UiStyleStore(context).write(style)); assertEquals(style, new UiStyleStore(context).read());
        }
        assertTrue(context.getSharedPreferences("handoff_settings", Context.MODE_PRIVATE).getAll().isEmpty());
    }
    @Test public void unavailablePreferencesFallBackAndReportWriteFailure() {
        SharedPreferences broken = (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (p, m, a) -> { throw new IllegalStateException("unavailable"); });
        UiStyleStore store = new UiStyleStore(broken);
        assertEquals(UiStyle.MATERIAL, store.read()); assertFalse(store.write(UiStyle.MIUIX));
    }
    @Test public void failedCommitIsNotReportedAsSuccess() {
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SharedPreferences.Editor.class}, (p, m, a) -> m.getName().equals("commit") ? false : p);
        SharedPreferences prefs = (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (p, m, a) -> m.getName().equals("edit") ? editor : "material");
        assertFalse(new UiStyleStore(prefs).write(UiStyle.MIUIX));
    }
    @Test public void failedDiskCommitAlsoRollsBackTheInMemoryChoice() {
        SharedPreferences real = RuntimeEnvironment.getApplication().getSharedPreferences("test-failed-style", Context.MODE_PRIVATE);
        real.edit().putString("style", "material").commit();
        SharedPreferences prefs = (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("edit")) return method.invoke(real, args);
                    SharedPreferences.Editor delegate = real.edit();
                    return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class}, (p, m, a) -> {
                        Object result = m.invoke(delegate, a);
                        if (m.getName().equals("commit")) return false; // Deliberately fail after mutating the actual map.
                        return result instanceof SharedPreferences.Editor ? p : result;
                    });
                });
        UiStyleStore store = new UiStyleStore(prefs);
        assertFalse(store.write(UiStyle.MIUIX)); assertEquals(UiStyle.MATERIAL, store.read());
    }
}
