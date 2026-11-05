package io.github.snownico0722.hookweb.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AppScanResult {
    public final String label;
    public final String packageName;
    public final boolean systemApp;
    public final List<String> apkPaths;
    private final List<EngineEvidence> evidence;
    public final String error;

    public AppScanResult(
            String label,
            String packageName,
            boolean systemApp,
            List<String> apkPaths,
            List<EngineEvidence> evidence,
            String error
    ) {
        this.label = label;
        this.packageName = packageName;
        this.systemApp = systemApp;
        this.apkPaths = Collections.unmodifiableList(new ArrayList<>(apkPaths));
        this.evidence = Collections.unmodifiableList(new ArrayList<>(evidence));
        this.error = error;
    }

    public List<EngineEvidence> evidence() {
        return evidence;
    }

    public Set<EngineKind> engines() {
        LinkedHashSet<EngineKind> result = new LinkedHashSet<>();
        for (EngineEvidence item : evidence) result.add(item.engine);
        return result;
    }

    public boolean hasWebEvidence() {
        return !evidence.isEmpty();
    }

    public boolean hasRuntimeEvidence() {
        for (EngineEvidence item : evidence) {
            if (item.source != EngineEvidence.Source.STATIC_APK) return true;
        }
        return false;
    }

    public boolean hasRuntimeEngine(EngineKind engine) {
        for (EngineEvidence item : evidence) {
            if (item.engine == engine && item.source != EngineEvidence.Source.STATIC_APK) return true;
        }
        return false;
    }

    public AppScanResult withExtraEvidence(List<EngineEvidence> extra) {
        ArrayList<EngineEvidence> merged = new ArrayList<>(evidence);
        outer:
        for (EngineEvidence candidate : extra) {
            for (EngineEvidence existing : merged) {
                if (existing.engine == candidate.engine
                        && existing.source == candidate.source
                        && existing.detail.equals(candidate.detail)) {
                    continue outer;
                }
            }
            merged.add(candidate);
        }
        return new AppScanResult(label, packageName, systemApp, apkPaths, merged, error);
    }

    public AppScanResult withoutEvidenceSource(EngineEvidence.Source source) {
        ArrayList<EngineEvidence> kept = new ArrayList<>();
        for (EngineEvidence item : evidence) {
            if (item.source != source) kept.add(item);
        }
        return new AppScanResult(label, packageName, systemApp, apkPaths, kept, error);
    }
}
