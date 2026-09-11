package io.github.snownico0722.hookweb.scan;

import io.github.snownico0722.hookweb.model.EngineKind;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class EngineCatalog {
    private EngineCatalog() {}

    public static final Map<EngineKind, List<String>> DEX_PATTERNS = new EnumMap<>(EngineKind.class);
    public static final Map<EngineKind, List<String>> ENTRY_PATTERNS = new EnumMap<>(EngineKind.class);

    static {
        DEX_PATTERNS.put(EngineKind.TBS_X5, Arrays.asList(
                "com/tencent/smtt/sdk/WebView",
                "com/tencent/smtt/sdk/QbSdk",
                "com.tencent.smtt.sdk.WebView",
                "com.tencent.smtt.sdk.QbSdk"
        ));
        DEX_PATTERNS.put(EngineKind.UC_U4, Arrays.asList(
                "com/uc/webview/export/WebView",
                "com/uc/webview/export/extension/UCCore",
                "com.uc.webview.export.WebView",
                "com.uc.webview.export.extension.UCCore"
        ));
        DEX_PATTERNS.put(EngineKind.XWEB, Arrays.asList(
                "com/tencent/xweb/WebView",
                "com.tencent.xweb.WebView"
        ));
        DEX_PATTERNS.put(EngineKind.GECKOVIEW, Arrays.asList(
                "org/mozilla/geckoview/GeckoView",
                "org.mozilla.geckoview.GeckoView"
        ));
        DEX_PATTERNS.put(EngineKind.CROSSWALK, Arrays.asList(
                "org/xwalk/core/XWalkView",
                "org.xwalk.core.XWalkView"
        ));
        DEX_PATTERNS.put(EngineKind.CEF, Arrays.asList(
                "org/cef/CefApp",
                "org.cef.CefApp"
        ));
        DEX_PATTERNS.put(EngineKind.BUNDLED_CHROMIUM, Arrays.asList(
                "org/chromium/content/browser/",
                "org.chromium.content.browser."
        ));
        DEX_PATTERNS.put(EngineKind.SYSTEM_WEBVIEW, Arrays.asList(
                "android/webkit/WebView",
                "android.webkit.WebView"
        ));

        ENTRY_PATTERNS.put(EngineKind.TBS_X5, Arrays.asList("libmttwebview.so", "libx5webview", "/tbs/", "tbs_sdk"));
        ENTRY_PATTERNS.put(EngineKind.UC_U4, Arrays.asList("libwebviewuc.so", "libu4", "/u4/", "ucweb"));
        ENTRY_PATTERNS.put(EngineKind.XWEB, Arrays.asList("libxwebcore.so", "/xweb/"));
        ENTRY_PATTERNS.put(EngineKind.GECKOVIEW, Arrays.asList("libxul.so", "libmozglue.so"));
        ENTRY_PATTERNS.put(EngineKind.CROSSWALK, Arrays.asList("libxwalkcore.so", "/xwalk/"));
        ENTRY_PATTERNS.put(EngineKind.CEF, Arrays.asList("libcef.so", "/cef/"));
        ENTRY_PATTERNS.put(EngineKind.BUNDLED_CHROMIUM, Arrays.asList("libmonochrome.so", "libmonochrome_64.so", "libchromium.so"));
    }
}
