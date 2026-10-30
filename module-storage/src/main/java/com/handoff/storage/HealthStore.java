package com.handoff.storage;

import android.content.Context;
import android.os.Bundle;
import com.handoff.api.health.HealthEvent;
import com.handoff.api.health.HealthPolicy;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** App-private bounded storage. Only typed diagnostics are admitted; arbitrary log text is never stored. */
public final class HealthStore {
    private static final String PREFS = "handoff_health_v1";
    private HealthStore() { }
    public static synchronized JSONObject read(Context context) {
        try { return new JSONObject(context.getSharedPreferences(PREFS, 0).getString("data", "{}")); }
        catch (Exception ignored) { return new JSONObject(); }
    }
    public static synchronized void receive(Context context, Bundle input) {
        if (input == null || input.getInt("schema") != 1) throw new IllegalArgumentException("Unknown schema");
        try {
            var kind = HealthEvent.Kind.valueOf(input.getString("kind", ""));
            var entry = HealthEvent.Entry.valueOf(input.getString("entry", ""));
            var reason = HealthEvent.Reason.valueOf(input.getString("reason", ""));
            String session = input.getString("session", "");
            if (!session.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("Invalid session");
            long module = input.getLong("module"), qq = input.getLong("qq");
            if (module <= 0 || qq < 0) throw new IllegalArgumentException("Invalid version");
            JSONObject state = read(context);
            if (kind == HealthEvent.Kind.SESSION) state = new JSONObject();
            else if (state.has("session") && !session.equals(state.optString("session"))) return;
            long now = System.currentTimeMillis();
            long occurred = input.getLong("at");
            if (occurred <= 0 || occurred > now + 5000) throw new IllegalArgumentException("Invalid event time");
            state.put("session", session).put("module", module).put("qq", qq).put("at", occurred)
                    .put("framework", HealthPolicy.safe(input.getString("framework")))
                    .put("qqName", HealthPolicy.safe(input.getString("qqName")));
            JSONArray old = state.optJSONArray("events");
            JSONArray events = new JSONArray();
            if (old != null) for (int i = Math.max(0, old.length() - 95); i < old.length(); i++) events.put(old.get(i));
            events.put(new JSONObject().put("at", occurred).put("module", module).put("qq", qq)
                    .put("framework", HealthPolicy.safe(input.getString("framework")))
                    .put("kind", kind.name()).put("entry", entry.name()).put("reason", reason.name()));
            state.put("events", events);
            if (!context.getSharedPreferences(PREFS, 0).edit().putString("data", state.toString()).commit())
                throw new IllegalStateException("Storage unavailable");
        } catch (IllegalArgumentException error) { throw error; }
        catch (Exception error) { throw new IllegalArgumentException("Invalid typed report"); }
    }
    public static List<HealthPolicy.Evidence> evidence(JSONObject state) {
        List<HealthPolicy.Evidence> result = new ArrayList<>();
        JSONArray events = state.optJSONArray("events");
        if (events != null) for (int i = 0; i < events.length(); i++) {
            try {
                JSONObject e = events.getJSONObject(i);
                result.add(new HealthPolicy.Evidence(e.getLong("at"), e.getLong("module"), e.getLong("qq"),
                        new HealthEvent(HealthEvent.Kind.valueOf(e.getString("kind")),
                                HealthEvent.Entry.valueOf(e.getString("entry")), HealthEvent.Reason.valueOf(e.getString("reason"))),
                        e.optString("framework", "unknown")));
            } catch (Exception ignored) { }
        }
        return List.copyOf(result);
    }
    public static synchronized void clear(Context context) { context.getSharedPreferences(PREFS, 0).edit().clear().commit(); }
}
