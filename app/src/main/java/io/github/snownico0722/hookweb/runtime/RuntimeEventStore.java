package io.github.snownico0722.hookweb.runtime;

import android.content.Context;
import android.content.SharedPreferences;

import io.github.snownico0722.hookweb.model.EngineEvidence;
import io.github.snownico0722.hookweb.model.EngineKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RuntimeEventStore {
    private static final String PREFS = "runtime_events";
    private static final String PREFIX = "pkg:";

    private RuntimeEventStore() {}

    public static void add(Context context, String packageName, EngineKind engine, String source, String processName) {
        if (packageName == null || packageName.trim().isEmpty() || engine == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = PREFIX + packageName;
        Set<String> old = prefs.getStringSet(key, Collections.emptySet());
        HashSet<String> copy = new HashSet<>(old == null ? Collections.emptySet() : old);
        String safeSource = sanitize(source);
        String safeProcess = sanitize(processName);
        copy.add(engine.code + "|" + safeSource + "|" + safeProcess);
        if (copy.size() > 48) {
            ArrayList<String> trimmed = new ArrayList<>(copy);
            copy.clear();
            copy.addAll(trimmed.subList(Math.max(0, trimmed.size() - 48), trimmed.size()));
        }
        prefs.edit()
                .putStringSet(key, copy)
                .putLong("last:" + packageName, System.currentTimeMillis())
                .apply();
    }

    public static List<EngineEvidence> get(Context context, String packageName) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> entries = prefs.getStringSet(PREFIX + packageName, Collections.emptySet());
        if (entries == null || entries.isEmpty()) return Collections.emptyList();

        ArrayList<EngineEvidence> result = new ArrayList<>();
        HashSet<String> dedupe = new HashSet<>();
        for (String entry : entries) {
            String[] parts = entry.split("\\|", -1);
            EngineKind engine = EngineKind.fromCode(parts.length > 0 ? parts[0] : null);
            if (engine == null) continue;
            String source = parts.length > 1 ? parts[1] : "hook";
            String process = parts.length > 2 ? parts[2] : "";
            String detail = source + (process.isEmpty() ? "" : " · " + process);
            String key = engine.code + "|" + detail;
            if (dedupe.add(key)) {
                result.add(new EngineEvidence(engine, EngineEvidence.Source.LSPOSED_RUNTIME, detail));
            }
        }
        return result;
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        return value.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }
}
