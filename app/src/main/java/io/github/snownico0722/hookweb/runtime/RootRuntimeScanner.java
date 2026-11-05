package io.github.snownico0722.hookweb.runtime;

import io.github.snownico0722.hookweb.model.EngineEvidence;
import io.github.snownico0722.hookweb.model.EngineKind;
import io.github.snownico0722.hookweb.root.RootShell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RootRuntimeScanner {
    private final RootShell rootShell;

    public RootRuntimeScanner(RootShell rootShell) {
        this.rootShell = rootShell;
    }

    public Map<String, List<EngineEvidence>> scan(Set<String> packageNames) {
        if (!rootShell.isRootAvailable() || packageNames.isEmpty()) return Collections.emptyMap();
        RootShell.Result ps = rootShell.exec("ps -A -o PID,NAME", 15_000);
        if (!ps.ok()) return Collections.emptyMap();

        List<String> sortedPackages = new ArrayList<>(packageNames);
        sortedPackages.sort(Comparator.comparingInt(String::length).reversed());
        HashMap<String, LinkedHashSet<EngineKind>> found = new HashMap<>();
        HashSet<Integer> seenPids = new HashSet<>();

        for (String line : ps.output.split("\\R")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            int split = line.indexOf(' ');
            if (split <= 0) continue;
            int pid;
            try {
                pid = Integer.parseInt(line.substring(0, split).trim());
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (!seenPids.add(pid)) continue;
            String processName = line.substring(split).trim();
            String ownerPackage = ownerPackage(processName, sortedPackages);
            if (ownerPackage == null) continue;

            String grep = "grep -Eai 'webview|chromium|monochrome|x5|tbs|xweb|xwalk|ucweb|libu4|libxul|mozglue|libcef' /proc/"
                    + pid + "/maps 2>/dev/null | head -n 512";
            RootShell.Result maps = rootShell.exec(grep, 8_000);
            if (!maps.ok() && maps.output.trim().isEmpty()) continue;
            for (String mapLine : maps.output.split("\\R")) {
                EngineKind engine = EngineKind.fromMapLine(mapLine);
                if (engine != null) {
                    found.computeIfAbsent(ownerPackage, ignored -> new LinkedHashSet<>()).add(engine);
                }
            }
        }

        HashMap<String, List<EngineEvidence>> result = new HashMap<>();
        for (Map.Entry<String, LinkedHashSet<EngineKind>> entry : found.entrySet()) {
            ArrayList<EngineEvidence> evidence = new ArrayList<>();
            for (EngineKind engine : entry.getValue()) {
                evidence.add(new EngineEvidence(engine, EngineEvidence.Source.ROOT_MAPS, "当前运行进程映射"));
            }
            result.put(entry.getKey(), evidence);
        }
        return result;
    }

    private static String ownerPackage(String processName, List<String> packages) {
        for (String packageName : packages) {
            if (processName.equals(packageName) || processName.startsWith(packageName + ":")) {
                return packageName;
            }
        }
        return null;
    }
}
