package io.github.snownico0722.hookweb.xposed;

import android.content.Context;
import android.util.Log;
import android.view.View;

import io.github.libxposed.api.XposedModule;
import io.github.snownico0722.hookweb.model.EngineKind;
import io.github.snownico0722.hookweb.runtime.RuntimeReporter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class HookWebModule extends XposedModule {
    private static final String TAG = "HookWeb";
    private static final String SELF_PACKAGE = "io.github.snownico0722.hookweb";
    private static final Map<String, EngineKind> TARGETS;

    static {
        HashMap<String, EngineKind> targets = new HashMap<>();
        targets.put("android.webkit.WebView", EngineKind.SYSTEM_WEBVIEW);
        targets.put("com.tencent.smtt.sdk.WebView", EngineKind.TBS_X5);
        targets.put("com.uc.webview.export.WebView", EngineKind.UC_U4);
        targets.put("com.tencent.xweb.WebView", EngineKind.XWEB);
        targets.put("org.mozilla.geckoview.GeckoView", EngineKind.GECKOVIEW);
        targets.put("org.xwalk.core.XWalkView", EngineKind.CROSSWALK);
        targets.put("org.cef.browser.CefBrowser", EngineKind.CEF);
        TARGETS = Collections.unmodifiableMap(targets);
    }

    private final Set<String> installedHooks = ConcurrentHashMap.newKeySet();

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "loaded in " + param.getProcessName() + ", API " + getApiVersion());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (SELF_PACKAGE.equals(packageName)) return;

        ClassLoader classLoader = param.getClassLoader();
        for (Map.Entry<String, EngineKind> target : TARGETS.entrySet()) {
            installConstructorHooks(classLoader, target.getKey(), target.getValue());
        }
    }

    private void installConstructorHooks(ClassLoader loader, String className, EngineKind engine) {
        final Class<?> type;
        try {
            type = Class.forName(className, false, loader);
        } catch (Throwable ignored) {
            return;
        }

        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            String hookKey = className + "#" + constructor.toGenericString();
            if (!installedHooks.add(hookKey)) continue;
            try {
                hook(constructor).intercept(chain -> {
                    Object result = chain.proceed();
                    Object instance = chain.getThisObject();
                    Context context = findContext(instance);
                    if (context != null) {
                        RuntimeReporter.report(context, engine, "constructor:" + className);
                        if (instance instanceof View view) {
                            view.postDelayed(
                                    () -> RuntimeReporter.report(context, engine, "delayed:" + className),
                                    350L
                            );
                        }
                    }
                    return result;
                });
            } catch (Throwable t) {
                installedHooks.remove(hookKey);
                log(Log.WARN, TAG, "hook failed: " + hookKey, t);
            }
        }
    }

    private static Context findContext(Object instance) {
        if (instance instanceof View view) return view.getContext();
        if (instance == null) return null;
        try {
            Method getContext = instance.getClass().getMethod("getContext");
            Object context = getContext.invoke(instance);
            return context instanceof Context ? (Context) context : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
