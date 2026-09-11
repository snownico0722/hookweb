package io.github.snownico0722.hookweb;

import android.app.Application;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class HookWebApp extends Application implements XposedServiceHelper.OnServiceListener {
    public interface ServiceStateListener {
        void onServiceStateChanged(XposedService service);
    }

    private static final Set<ServiceStateListener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile XposedService service;

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    public static void addServiceStateListener(ServiceStateListener listener, boolean notifyImmediately) {
        LISTENERS.add(listener);
        if (notifyImmediately) listener.onServiceStateChanged(service);
    }

    public static void removeServiceStateListener(ServiceStateListener listener) {
        LISTENERS.remove(listener);
    }

    private static void dispatch(XposedService value) {
        for (ServiceStateListener listener : LISTENERS) {
            listener.onServiceStateChanged(value);
        }
    }

    @Override
    public void onServiceBind(XposedService service) {
        HookWebApp.service = service;
        dispatch(service);
    }

    @Override
    public void onServiceDied(XposedService service) {
        if (HookWebApp.service == service) HookWebApp.service = null;
        dispatch(null);
    }
}
