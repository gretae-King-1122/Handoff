package com.handoff.storage;

import android.content.Context;
import android.os.Bundle;
import com.handoff.api.HostApps;
import com.handoff.api.health.HealthEvent;
import com.handoff.api.health.HealthPolicy;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** App-private bounded storage. Only typed diagnostics are admitted; arbitrary log text is never stored. */
public final class HealthStore {
    private static final String PREFS = "handoff_health_v1";
    private static final int MAX_EVENTS = 96;
    private static final Pattern SESSION_ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private HealthStore() { }
    public static synchronized JSONObject read(Context context) { return read(context, HostApps.QQ); }
    public static synchronized JSONObject read(Context context, String packageName) {
        if (!HostApps.isSupported(packageName)) throw new IllegalArgumentException("Unknown host");
        try { return new JSONObject(context.getSharedPreferences(PREFS, 0).getString(storageKey(packageName), "{}")); }
        catch (Exception ignored) { return new JSONObject(); }
    }
    public static synchronized void receive(Context context, Bundle input) {
        if (input == null || input.getInt("schema") != 1) throw new IllegalArgumentException("Unknown schema");
        try {
            Report report = readReport(input);
            JSONObject state = read(context, report.packageName());
            long occurred = input.getLong("at");
            long started = input.getLong("startedAt", report.event().kind() == HealthEvent.Kind.SESSION
                    ? occurred : state.optLong("started", occurred));
            if (started <= 0 || started > occurred) throw new IllegalArgumentException("Invalid session time");
            if (ignoreReport(report, state, started)) return;
            if (report.event().kind() == HealthEvent.Kind.SESSION) state = new JSONObject();
            else if (state.has("session")) validateEnvironment(input, state);
            updateState(state, input, report, started);
            if (!context.getSharedPreferences(PREFS, 0).edit().putString(storageKey(report.packageName()), state.toString()).commit())
                throw new IllegalStateException("Storage unavailable");
        } catch (IllegalArgumentException error) { throw error; }
        catch (Exception error) { throw new IllegalArgumentException("Invalid typed report"); }
    }

    /** Only whitelisted typed fields cross into storage; the caller's Bundle is never mutated. */
    private record Report(String packageName, String session, HealthEvent event) { }

    private static Report readReport(Bundle input) {
        String packageName = input.getString("packageName", HostApps.QQ);
        if (!HostApps.isSupported(packageName)) throw new IllegalArgumentException("Unknown host");
        HealthEvent event = new HealthEvent(HealthEvent.Kind.valueOf(input.getString("kind", "")),
                HealthEvent.Entry.valueOf(input.getString("entry", "")), HealthEvent.Reason.valueOf(input.getString("reason", "")));
        String session = input.getString("session", "");
        if (!SESSION_ID.matcher(session).matches())
            throw new IllegalArgumentException("Invalid session");
        if (input.getLong("module") <= 0 || input.getLong("qq") < 0) throw new IllegalArgumentException("Invalid version");
        long occurred = input.getLong("at");
        if (occurred <= 0 || occurred > System.currentTimeMillis() + 5000) throw new IllegalArgumentException("Invalid event time");
        return new Report(packageName, session, event);
    }

    private static boolean ignoreReport(Report report, JSONObject state, long started) {
        if (report.event().kind() == HealthEvent.Kind.SESSION) {
            // A duplicate start is idempotent; a delayed old process cannot replace a newer session.
            return report.session().equals(state.optString("session"))
                    || started < state.optLong("started", state.optLong("at"));
        }
        return state.has("session") && !report.session().equals(state.optString("session"));
    }

    private static void validateEnvironment(Bundle input, JSONObject state) {
        if (input.getLong("module") != state.optLong("module") || input.getLong("qq") != state.optLong("qq")
                || !HealthPolicy.safe(input.getString("framework")).equals(state.optString("framework")))
            throw new IllegalArgumentException("Session environment changed");
    }

    private static void updateState(JSONObject state, Bundle input, Report report, long started) throws JSONException {
        state.put("started", started);
        state.put("packageName", report.packageName()).put("session", report.session())
                .put("module", input.getLong("module")).put("qq", input.getLong("qq")).put("at", input.getLong("at"))
                .put("framework", HealthPolicy.safe(input.getString("framework")))
                .put("qqName", HealthPolicy.safe(input.getString("qqName")));
        JSONObject event = new JSONObject().put("packageName", report.packageName()).put("at", input.getLong("at"))
                .put("module", input.getLong("module")).put("qq", input.getLong("qq"))
                .put("framework", HealthPolicy.safe(input.getString("framework")))
                .put("kind", report.event().kind().name()).put("entry", report.event().entry().name())
                .put("reason", report.event().reason().name());
        state.put("events", appendEvent(state.optJSONArray("events"), event));
    }

    private static JSONArray appendEvent(JSONArray old, JSONObject event) throws JSONException {
        JSONArray events = new JSONArray();
        if (old != null) {
            for (int i = Math.max(0, old.length() - (MAX_EVENTS - 1)); i < old.length(); i++) events.put(old.get(i));
        }
        events.put(event);
        return events;
    }

    public static List<HealthPolicy.Evidence> evidence(JSONObject state) {
        List<HealthPolicy.Evidence> result = new ArrayList<>();
        JSONArray events = state.optJSONArray("events");
        if (events != null) for (int i = 0; i < events.length(); i++) {
            try {
                result.add(readEvidence(events.getJSONObject(i)));
            } catch (Exception ignored) {
                // Skip only the corrupt row; retain independent valid evidence on either side.
            }
        }
        return List.copyOf(result);
    }

    private static HealthPolicy.Evidence readEvidence(JSONObject event) throws JSONException {
        return new HealthPolicy.Evidence(event.getLong("at"), event.getLong("module"), event.getLong("qq"),
                new HealthEvent(HealthEvent.Kind.valueOf(event.getString("kind")),
                        HealthEvent.Entry.valueOf(event.getString("entry")), HealthEvent.Reason.valueOf(event.getString("reason"))),
                event.optString("framework", "unknown"), event.optString("packageName", HostApps.QQ));
    }
    private static String storageKey(String packageName) { return HostApps.QQ.equals(packageName) ? "data" : "data:" + packageName; }
    public static synchronized void clear(Context context) {
        if (!context.getSharedPreferences(PREFS, 0).edit().clear().commit())
            throw new IllegalStateException("Storage unavailable");
    }
}
