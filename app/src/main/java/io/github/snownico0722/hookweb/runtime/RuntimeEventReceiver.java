package io.github.snownico0722.hookweb.runtime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.snownico0722.hookweb.model.EngineKind;

public final class RuntimeEventReceiver extends BroadcastReceiver {
    public static final String ACTION = "io.github.snownico0722.hookweb.RUNTIME_EVENT";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_PROCESS = "process";
    public static final String EXTRA_ENGINE = "engine";
    public static final String EXTRA_SOURCE = "source";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        String packageName = intent.getStringExtra(EXTRA_PACKAGE);
        String processName = intent.getStringExtra(EXTRA_PROCESS);
        EngineKind engine = EngineKind.fromCode(intent.getStringExtra(EXTRA_ENGINE));
        String source = intent.getStringExtra(EXTRA_SOURCE);
        if (!isPackageName(packageName) || engine == null) return;
        RuntimeEventStore.add(context, packageName, engine, source, processName);
    }

    private static boolean isPackageName(String value) {
        return value != null && value.length() <= 255 && value.matches("[A-Za-z0-9_.$]+(\\.[A-Za-z0-9_.$]+)+");
    }
}
