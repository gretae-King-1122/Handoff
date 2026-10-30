package com.handoff.storage;

import android.content.Context;
import android.os.Bundle;
import com.handoff.api.health.HealthPolicy;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowBinder;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class HealthStorageTest {
    private Context context;
    private static final String SESSION = "11111111-1111-1111-1111-111111111111";
    private final HealthPolicy.Environment env = new HealthPolicy.Environment(5, 16580, 35, "Test", "LSPosed 2.2.1");

    @Before public void clear() {
        context = RuntimeEnvironment.getApplication();
        HealthStore.clear(context); RegressionStore.clear(context);
    }
    private Bundle report(String kind) {
        Bundle b = new Bundle(); b.putInt("schema", 1); b.putString("session", SESSION);
        b.putString("kind", kind); b.putString("entry", "BROWSER"); b.putString("reason", "NONE");
        b.putLong("module", 5); b.putLong("qq", 16580); b.putLong("at", System.currentTimeMillis());
        b.putString("framework", env.framework()); return b;
    }
    @Test public void boundedStoreIgnoresUnknownFieldsAndPreservesOccurrenceTime() throws Exception {
        Bundle b = report("ACCEPTED"); b.putString("rawUrl", "private data"); b.putLong("at", System.currentTimeMillis() - 10000);
        for (int i = 0; i < 110; i++) HealthStore.receive(context, b);
        JSONObject data = HealthStore.read(context);
        assertEquals(96, data.getJSONArray("events").length());
        assertEquals(b.getLong("at"), data.getLong("at"));
        assertFalse(data.toString().contains("private data"));
        assertFalse(data.toString().contains("rawUrl"));
    }
    @Test public void staleSessionCannotOverwriteNewSession() {
        HealthStore.receive(context, report("SESSION"));
        Bundle stale = report("ACCEPTED"); stale.putString("session", "22222222-2222-2222-2222-222222222222");
        HealthStore.receive(context, stale);
        assertEquals(1, HealthStore.evidence(HealthStore.read(context)).size());
    }
    @Test public void unknownSchemaEnumAndFutureTimeAreRejected() {
        Bundle schema = report("SESSION"); schema.putInt("schema", 2);
        assertThrows(IllegalArgumentException.class, () -> HealthStore.receive(context, schema));
        assertThrows(IllegalArgumentException.class, () -> HealthStore.receive(context, report("RAW_LOG")));
        Bundle future = report("ACCEPTED"); future.putLong("at", System.currentTimeMillis() + 60000);
        assertThrows(IllegalArgumentException.class, () -> HealthStore.receive(context, future));
        assertTrue(HealthStore.evidence(HealthStore.read(context)).isEmpty());
    }
    @Test public void newSessionResetsPreviousSignals() {
        HealthStore.receive(context, report("ACCEPTED"));
        Bundle next = report("SESSION"); next.putString("session", "22222222-2222-2222-2222-222222222222");
        HealthStore.receive(context, next);
        assertEquals(1, HealthStore.evidence(HealthStore.read(context)).size());
    }
    @Test public void positiveResultRequiresMatchingFreshEvidence() throws Exception {
        RegressionStore.start(context, env);
        assertThrows(IllegalStateException.class, () -> RegressionStore.result(context, env, HealthPolicy.Case.WEB, "PASS"));
        Bundle stale = report("ACCEPTED"); stale.putLong("at", 1);
        HealthStore.receive(context, stale);
        assertThrows(IllegalStateException.class, () -> RegressionStore.result(context, env, HealthPolicy.Case.WEB, "PASS"));
        HealthStore.receive(context, report("ACCEPTED"));
        RegressionStore.result(context, env, HealthPolicy.Case.WEB, "PASS");
        assertEquals("PASS", RegressionStore.read(context).getJSONObject(0).getJSONObject("results").getString("WEB"));
        assertThrows(IllegalStateException.class, () -> RegressionStore.result(context, env, HealthPolicy.Case.MINI, "PASS"));
    }
    @Test public void changedEnvironmentCannotAmendOldRunAndRunsAreBounded() {
        for (int i = 0; i < 24; i++) RegressionStore.start(context, env);
        assertEquals(20, RegressionStore.read(context).length());
        var changed = new HealthPolicy.Environment(5, 16581, 35, "Test", env.framework());
        assertThrows(IllegalStateException.class, () -> RegressionStore.result(context, changed, HealthPolicy.Case.OTHER, "PASS"));
        RegressionStore.result(context, env, HealthPolicy.Case.OTHER, "PASS");
    }
    @Test public void providerHasNoReadOrMutationApi() {
        HealthProvider provider = new HealthProvider();
        assertThrows(SecurityException.class, () -> provider.query(null, null, null, null, null));
        assertThrows(SecurityException.class, () -> provider.insert(null, null));
        assertThrows(SecurityException.class, () -> provider.update(null, null, null, null));
        assertThrows(SecurityException.class, () -> provider.delete(null, null, null));
    }
    @Test public void providerRejectsForeignUidAndUnknownMethod() {
        HealthProvider provider = Robolectric.buildContentProvider(HealthProvider.class).create().get();
        assertThrows(SecurityException.class, () -> provider.call("clear", null, null));
        ShadowBinder.setCallingUid(99999);
        try {
            assertThrows(SecurityException.class, () -> provider.call("report", null, report("SESSION")));
        } finally { ShadowBinder.setCallingUid(android.os.Process.myUid()); }
        assertTrue(HealthStore.evidence(HealthStore.read(context)).isEmpty());
    }
}
