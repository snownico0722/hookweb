package io.github.snownico0722.hookweb.runtime;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

import io.github.snownico0722.hookweb.model.EngineKind;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RuntimeReporter {
    private static final String MODULE_PACKAGE = "io.github.snownico0722.hookweb";
    private static final String RECEIVER = "io.github.snownico0722.hookweb.runtime.RuntimeEventReceiver";
    private static final Set<String> SENT = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private RuntimeReporter() {}

    public static void report(Context context, EngineKind wrapperEngine, String source) {
        if (context == null) return;
        String packageName = context.getPackageName();
        String processName = currentProcessName();
        sendOnce(context, packageName, processName, wrapperEngine, source);

        for (EngineKind mapped : detectSelfMaps()) {
            sendOnce(context, packageName, processName, mapped, "self-maps");
        }
    }

    private static void sendOnce(
            Context context,
            String packageName,
            String processName,
            EngineKind engine,
            String source
    ) {
        if (engine == null) return;
        String key = packageName + "|" + processName + "|" + engine.code + "|" + source;
        if (!SENT.add(key)) return;
        try {
            Intent intent = new Intent(RuntimeEventReceiver.ACTION)
                    .setComponent(new ComponentName(MODULE_PACKAGE, RECEIVER))
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    .putExtra(RuntimeEventReceiver.EXTRA_PACKAGE, packageName)
                    .putExtra(RuntimeEventReceiver.EXTRA_PROCESS, processName)
                    .putExtra(RuntimeEventReceiver.EXTRA_ENGINE, engine.code)
                    .putExtra(RuntimeEventReceiver.EXTRA_SOURCE, source);
            context.sendBroadcast(intent);
        } catch (Throwable ignored) {
        }
    }

    private static EnumSet<EngineKind> detectSelfMaps() {
        EnumSet<EngineKind> result = EnumSet.noneOf(EngineKind.class);
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                EngineKind engine = EngineKind.fromMapLine(line);
                if (engine != null) result.add(engine);
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    private static String currentProcessName() {
        try {
            return android.app.Application.getProcessName();
        } catch (Throwable ignored) {
            return "pid-" + Process.myPid();
        }
    }
}
