package io.github.snownico0722.hookweb.model;

import java.util.Locale;

public enum EngineKind {
    SYSTEM_WEBVIEW("SYSTEM_WEBVIEW", "系统 WebView"),
    TBS_X5("TBS_X5", "腾讯 TBS / X5"),
    UC_U4("UC_U4", "UC / U4"),
    XWEB("XWEB", "腾讯 XWeb"),
    GECKOVIEW("GECKOVIEW", "GeckoView"),
    CROSSWALK("CROSSWALK", "Crosswalk"),
    CEF("CEF", "CEF"),
    BUNDLED_CHROMIUM("BUNDLED_CHROMIUM", "内置 Chromium");

    public final String code;
    public final String label;

    EngineKind(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static EngineKind fromCode(String code) {
        if (code == null) return null;
        for (EngineKind value : values()) {
            if (value.code.equalsIgnoreCase(code)) return value;
        }
        return null;
    }

    public static EngineKind fromMapLine(String raw) {
        if (raw == null) return null;
        String line = raw.toLowerCase(Locale.ROOT);
        if (containsAny(line, "libxul.so", "libmozglue.so")) return GECKOVIEW;
        if (containsAny(line, "libxwebcore.so", "/xweb/", "xwebcore")) return XWEB;
        if (containsAny(line, "libxwalkcore.so", "/xwalk/")) return CROSSWALK;
        if (containsAny(line, "libwebviewuc.so", "libu4", "/u4/", "/ucweb/")) return UC_U4;
        if (containsAny(line, "libmttwebview.so", "libx5webview", "/app_tbs/", "/tbs/")) return TBS_X5;
        if (containsAny(line, "libcef.so", "/cef/")) return CEF;
        if (containsAny(line, "libwebviewchromium.so", "com.google.android.webview", "com.android.webview")) return SYSTEM_WEBVIEW;
        if (containsAny(line, "libmonochrome.so", "libmonochrome_64.so", "libchromium.so")) return BUNDLED_CHROMIUM;
        return null;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }
}
