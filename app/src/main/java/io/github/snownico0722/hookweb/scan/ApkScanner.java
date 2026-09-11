package io.github.snownico0722.hookweb.scan;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import io.github.snownico0722.hookweb.model.AppScanResult;
import io.github.snownico0722.hookweb.model.EngineEvidence;
import io.github.snownico0722.hookweb.model.EngineKind;
import io.github.snownico0722.hookweb.root.RootShell;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ApkScanner {
    private static final long MAX_DEX_BYTES_PER_ENTRY = 96L * 1024L * 1024L;

    private final PackageManager packageManager;
    private final RootShell rootShell;
    private final BooleanSupplier cancelled;

    public ApkScanner(Context context, RootShell rootShell) {
        this(context, rootShell, () -> false);
    }

    public ApkScanner(Context context, RootShell rootShell, BooleanSupplier cancelled) {
        this.packageManager = context.getPackageManager();
        this.rootShell = rootShell;
        this.cancelled = cancelled == null ? () -> false : cancelled;
    }

    public AppScanResult scan(ApplicationInfo info) {
        checkCancelled();
        String label;
        try {
            label = String.valueOf(info.loadLabel(packageManager));
        } catch (Throwable ignored) {
            label = info.packageName;
        }

        boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        List<String> paths = new ArrayList<>();
        if (info.sourceDir != null) paths.add(info.sourceDir);
        if (info.splitSourceDirs != null) Collections.addAll(paths, info.splitSourceDirs);

        EnumMap<EngineKind, EngineEvidence> evidence = new EnumMap<>(EngineKind.class);
        ArrayList<String> errors = new ArrayList<>();
        for (String path : paths) {
            checkCancelled();
            scanPath(path, evidence, errors);
        }

        checkCancelled();
        EngineEvidence chromium = evidence.get(EngineKind.BUNDLED_CHROMIUM);
        if (chromium != null && !chromium.detail.contains("native:")) {
            evidence.remove(EngineKind.BUNDLED_CHROMIUM);
        }

        return new AppScanResult(
                label,
                info.packageName,
                system,
                paths,
                new ArrayList<>(evidence.values()),
                errors.isEmpty() ? null : String.join("; ", errors)
        );
    }

    private void scanPath(
            String sourcePath,
            Map<EngineKind, EngineEvidence> evidence,
            List<String> errors
    ) {
        File copied = null;
        try {
            checkCancelled();
            try {
                scanZip(sourcePath, sourcePath, evidence);
                return;
            } catch (CancellationException cancelled) {
                throw cancelled;
            } catch (Throwable directFailure) {
                checkCancelled();
                copied = rootShell.makeReadableCopy(sourcePath);
                checkCancelled();
                if (copied == null) throw directFailure;
                scanZip(copied.getAbsolutePath(), sourcePath, evidence);
            }
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (Throwable t) {
            errors.add(new File(sourcePath).getName() + ": " + t.getClass().getSimpleName());
        } finally {
            if (copied != null) {
                //noinspection ResultOfMethodCallIgnored
                copied.delete();
            }
        }
    }

    private void scanZip(
            String readablePath,
            String originalPath,
            Map<EngineKind, EngineEvidence> evidence
    ) throws Exception {
        try (ZipFile zip = new ZipFile(readablePath)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                checkCancelled();
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                String lowerName = entryName.toLowerCase(Locale.ROOT);

                for (Map.Entry<EngineKind, List<String>> signature : EngineCatalog.ENTRY_PATTERNS.entrySet()) {
                    for (String pattern : signature.getValue()) {
                        if (lowerName.contains(pattern.toLowerCase(Locale.ROOT))) {
                            String prefix = signature.getKey() == EngineKind.BUNDLED_CHROMIUM ? "native:" : "entry:";
                            evidence.put(signature.getKey(), new EngineEvidence(
                                    signature.getKey(),
                                    EngineEvidence.Source.STATIC_APK,
                                    prefix + shortPath(originalPath) + "!/" + entryName
                            ));
                            break;
                        }
                    }
                }

                if (!isDex(entryName) || entry.isDirectory()) continue;
                try (InputStream input = zip.getInputStream(entry)) {
                    Map<EngineKind, String> found = BytePatternScanner.scan(
                            input,
                            MAX_DEX_BYTES_PER_ENTRY,
                            cancelled
                    );
                    for (Map.Entry<EngineKind, String> item : found.entrySet()) {
                        EngineEvidence existing = evidence.get(item.getKey());
                        if (existing != null && item.getKey() == EngineKind.BUNDLED_CHROMIUM
                                && existing.detail.startsWith("native:")) {
                            continue;
                        }
                        evidence.putIfAbsent(item.getKey(), new EngineEvidence(
                                item.getKey(),
                                EngineEvidence.Source.STATIC_APK,
                                "dex:" + entryName + " → " + item.getValue()
                        ));
                    }
                }
            }
        }
    }

    private void checkCancelled() {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) {
            throw new CancellationException("scan cancelled");
        }
    }

    private static boolean isDex(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("classes") && lower.endsWith(".dex");
    }

    private static String shortPath(String path) {
        return new File(path).getName();
    }
}
