package com.handoff.storage;

import android.content.Context;
import com.handoff.api.health.HealthPolicy;
import org.json.JSONArray;
import org.json.JSONObject;

/** At most 20 explicitly user-created runs; never seeds current device results from developer records. */
public final class RegressionStore {
    private RegressionStore() { }
    public static synchronized JSONArray read(Context context) {
        try { return new JSONArray(context.getSharedPreferences("handoff_matrix", 0).getString("runs", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }
    public static synchronized void start(Context context, HealthPolicy.Environment env) {
        try {
            JSONArray old = read(context), runs = new JSONArray();
            runs.put(new JSONObject().put("started", System.currentTimeMillis()).put("environment", env.key())
                    .put("module", env.module()).put("qq", env.qq()).put("android", env.android())
                    .put("model", HealthPolicy.safe(env.model())).put("framework", HealthPolicy.safe(env.framework()))
                    .put("results", new JSONObject()));
            for (int i = 0; i < Math.min(19, old.length()); i++) runs.put(old.get(i));
            save(context, runs);
        } catch (Exception error) { throw new IllegalStateException("Cannot start run", error); }
    }
    public static synchronized void result(Context context, HealthPolicy.Environment env, HealthPolicy.Case test, String result) {
        if (!result.matches("PASS|FAIL|SKIP")) throw new IllegalArgumentException("Unknown result");
        try {
            JSONArray runs = read(context);
            if (runs.length() == 0) throw new IllegalStateException("Start a run first");
            JSONObject run = runs.getJSONObject(0);
            if (!env.key().equals(run.getString("environment"))) throw new IllegalStateException("Environment changed; start again");
            if ("PASS".equals(result) && !HealthPolicy.mayConfirm(test, run.getLong("started"), env,
                    HealthStore.evidence(HealthStore.read(context)))) throw new IllegalStateException("No matching fresh handoff evidence");
            run.getJSONObject("results").put(test.name(), result);
            save(context, runs);
        } catch (IllegalStateException error) { throw error; }
        catch (Exception error) { throw new IllegalStateException("Cannot save result", error); }
    }
    private static void save(Context context, JSONArray runs) {
        if (!context.getSharedPreferences("handoff_matrix", 0).edit().putString("runs", runs.toString()).commit())
            throw new IllegalStateException("Storage unavailable");
    }
    public static synchronized void clear(Context context) {
        if (!context.getSharedPreferences("handoff_matrix", 0).edit().clear().commit())
            throw new IllegalStateException("Storage unavailable");
    }
}
