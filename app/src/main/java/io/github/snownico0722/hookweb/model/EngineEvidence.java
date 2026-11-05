package io.github.snownico0722.hookweb.model;

public final class EngineEvidence {
    public enum Source {
        STATIC_APK,
        ROOT_MAPS,
        LSPOSED_RUNTIME
    }

    public final EngineKind engine;
    public final Source source;
    public final String detail;

    public EngineEvidence(EngineKind engine, Source source, String detail) {
        this.engine = engine;
        this.source = source;
        this.detail = detail == null ? "" : detail;
    }

    public String displayText() {
        String sourceText = switch (source) {
            case STATIC_APK -> "静态";
            case ROOT_MAPS -> "ROOT 运行时";
            case LSPOSED_RUNTIME -> "LSPosed 运行时";
        };
        return engine.label + " · " + sourceText + (detail.isEmpty() ? "" : " · " + detail);
    }
}
