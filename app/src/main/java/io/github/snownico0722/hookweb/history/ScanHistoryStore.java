package io.github.snownico0722.hookweb.history;

import android.content.Context;
import android.util.AtomicFile;

import io.github.snownico0722.hookweb.model.AppScanResult;
import io.github.snownico0722.hookweb.model.EngineEvidence;
import io.github.snownico0722.hookweb.model.EngineKind;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public final class ScanHistoryStore {
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String STOPPED = "STOPPED";
    public static final String FAILED = "FAILED";

    private static final int MAX_HISTORY = 12;
    private static final String DIR = "scan_history";
    private static final Object LOCK = new Object();

    public static final class Snapshot {
        public final long id;
        public final long createdAt;
        public final long updatedAt;
        public final String status;
        public final boolean includeSystem;
        public final int scannedApps;
        public final int totalApps;
        public final List<AppScanResult> results;

        public Snapshot(
                long id,
                long createdAt,
                long updatedAt,
                String status,
                boolean includeSystem,
                int scannedApps,
                int totalApps,
                List<AppScanResult> results
        ) {
            this.id = id;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.status = status == null ? FAILED : status;
            this.includeSystem = includeSystem;
            this.scannedApps = scannedApps;
            this.totalApps = totalApps;
            this.results = Collections.unmodifiableList(new ArrayList<>(results));
        }

        public boolean isFinished() {
            return COMPLETED.equals(status) || STOPPED.equals(status) || FAILED.equals(status);
        }

        public String displayTitle() {
            String state = switch (status) {
                case COMPLETED -> "完成";
                case STOPPED -> "已停止";
                case RUNNING -> "中断";
                default -> "失败";
            };
            String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(updatedAt));
            return time + " · " + state + " · " + scannedApps + "/" + totalApps
                    + " · 命中 " + detectedCount();
        }

        public int detectedCount() {
            int count = 0;
            for (AppScanResult item : results) if (item.hasWebEvidence()) count++;
            return count;
        }
    }

    private final File directory;

    public ScanHistoryStore(Context context) {
        directory = new File(context.getApplicationContext().getFilesDir(), DIR);
    }

    public void save(Snapshot snapshot) throws Exception {
        synchronized (LOCK) {
            ensureDirectory();
            File target = fileFor(snapshot.id);
            AtomicFile atomicFile = new AtomicFile(target);
            FileOutputStream output = null;
            try {
                output = atomicFile.startWrite();
                output.write(toJson(snapshot).toString().getBytes(StandardCharsets.UTF_8));
                output.flush();
                atomicFile.finishWrite(output);
                output = null;
            } finally {
                if (output != null) atomicFile.failWrite(output);
            }
            pruneLocked();
        }
    }

    public Snapshot loadLatest() {
        List<Snapshot> snapshots = list();
        return snapshots.isEmpty() ? null : snapshots.get(0);
    }

    public List<Snapshot> list() {
        synchronized (LOCK) {
            if (!directory.isDirectory()) return Collections.emptyList();
            File[] files = directory.listFiles((dir, name) -> name.startsWith("scan-") && name.endsWith(".json"));
            if (files == null || files.length == 0) return Collections.emptyList();
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            ArrayList<Snapshot> result = new ArrayList<>();
            for (File file : files) {
                Snapshot snapshot = read(file);
                if (snapshot != null) result.add(snapshot);
            }
            result.sort(Comparator.comparingLong((Snapshot value) -> value.updatedAt).reversed());
            return result;
        }
    }

    public void clear() {
        synchronized (LOCK) {
            if (!directory.isDirectory()) return;
            File[] files = directory.listFiles();
            if (files == null) return;
            for (File file : files) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private Snapshot read(File file) {
        try {
            byte[] bytes;
            try (FileInputStream input = new AtomicFile(file).openRead()) {
                bytes = input.readAllBytes();
            }
            return fromJson(new JSONObject(new String(bytes, StandardCharsets.UTF_8)));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void ensureDirectory() {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("无法创建扫描历史目录");
        }
    }

    private File fileFor(long id) {
        return new File(directory, "scan-" + id + ".json");
    }

    private void pruneLocked() {
        File[] files = directory.listFiles((dir, name) -> name.startsWith("scan-") && name.endsWith(".json"));
        if (files == null || files.length <= MAX_HISTORY) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (int i = MAX_HISTORY; i < files.length; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }

    private static JSONObject toJson(Snapshot snapshot) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("id", snapshot.id);
        root.put("createdAt", snapshot.createdAt);
        root.put("updatedAt", snapshot.updatedAt);
        root.put("status", snapshot.status);
        root.put("includeSystem", snapshot.includeSystem);
        root.put("scannedApps", snapshot.scannedApps);
        root.put("totalApps", snapshot.totalApps);

        JSONArray results = new JSONArray();
        for (AppScanResult item : snapshot.results) results.put(toJson(item));
        root.put("results", results);
        return root;
    }

    private static JSONObject toJson(AppScanResult item) throws Exception {
        JSONObject result = new JSONObject();
        result.put("label", item.label);
        result.put("packageName", item.packageName);
        result.put("systemApp", item.systemApp);
        result.put("error", item.error == null ? JSONObject.NULL : item.error);

        JSONArray paths = new JSONArray();
        for (String path : item.apkPaths) paths.put(path);
        result.put("apkPaths", paths);

        JSONArray evidence = new JSONArray();
        for (EngineEvidence itemEvidence : item.evidence()) {
            JSONObject value = new JSONObject();
            value.put("engine", itemEvidence.engine.code);
            value.put("source", itemEvidence.source.name());
            value.put("detail", itemEvidence.detail);
            evidence.put(value);
        }
        result.put("evidence", evidence);
        return result;
    }

    private static Snapshot fromJson(JSONObject root) throws Exception {
        long id = root.getLong("id");
        long createdAt = root.optLong("createdAt", id);
        long updatedAt = root.optLong("updatedAt", createdAt);
        String status = root.optString("status", FAILED);
        boolean includeSystem = root.optBoolean("includeSystem", false);
        int scannedApps = root.optInt("scannedApps", 0);
        int totalApps = root.optInt("totalApps", scannedApps);
        ArrayList<AppScanResult> results = new ArrayList<>();
        JSONArray values = root.optJSONArray("results");
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value != null) results.add(resultFromJson(value));
            }
        }
        return new Snapshot(id, createdAt, updatedAt, status, includeSystem, scannedApps, totalApps, results);
    }

    private static AppScanResult resultFromJson(JSONObject value) throws Exception {
        String label = value.optString("label", "");
        String packageName = value.getString("packageName");
        boolean systemApp = value.optBoolean("systemApp", false);
        String error = value.isNull("error") ? null : value.optString("error", null);

        ArrayList<String> paths = new ArrayList<>();
        JSONArray pathValues = value.optJSONArray("apkPaths");
        if (pathValues != null) {
            for (int i = 0; i < pathValues.length(); i++) {
                String path = pathValues.optString(i, null);
                if (path != null) paths.add(path);
            }
        }

        ArrayList<EngineEvidence> evidence = new ArrayList<>();
        JSONArray evidenceValues = value.optJSONArray("evidence");
        if (evidenceValues != null) {
            for (int i = 0; i < evidenceValues.length(); i++) {
                JSONObject evidenceValue = evidenceValues.optJSONObject(i);
                if (evidenceValue == null) continue;
                EngineKind engine = EngineKind.fromCode(evidenceValue.optString("engine", null));
                if (engine == null) continue;
                EngineEvidence.Source source;
                try {
                    source = EngineEvidence.Source.valueOf(evidenceValue.optString("source", "STATIC_APK"));
                } catch (IllegalArgumentException ignored) {
                    source = EngineEvidence.Source.STATIC_APK;
                }
                evidence.add(new EngineEvidence(engine, source, evidenceValue.optString("detail", "")));
            }
        }

        return new AppScanResult(label, packageName, systemApp, paths, evidence, error);
    }
}
