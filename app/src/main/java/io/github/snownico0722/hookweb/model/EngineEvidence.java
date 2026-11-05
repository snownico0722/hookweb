package io.github.snownico0722.hookweb.model;

public final class EngineEvidence {
    public enum Source {
        STATIC_APK,
        ROOT_MAPS,
        LSPOSED_WRAPPER,
        LSPOSED_RUNTIME
    }

    public final EngineKind engine;
    public final Source source;
    public final String detail;

    public EngineEvidence(EngineKind engine, Source source, String detail) {
        this.engine = engine;
        this.detail = detail == null ? "" : detail;
        // Older stored events used LSPOSED_RUNTIME for both wrappers and native cores.
        this.source = source == Source.LSPOSED_RUNTIME
                && (this.detail.startsWith("constructor:") || this.detail.startsWith("delayed:"))
                ? Source.LSPOSED_WRAPPER : source;
    }

    public boolean isRuntimeCore() {
        return source == Source.ROOT_MAPS || source == Source.LSPOSED_RUNTIME;
    }

    public boolean isHookEvidence() {
        return source == Source.LSPOSED_WRAPPER || source == Source.LSPOSED_RUNTIME;
    }

    public String displayText() {
        String sourceText = switch (source) {
            case STATIC_APK -> "静态";
            case ROOT_MAPS -> "ROOT 运行时";
            case LSPOSED_WRAPPER -> "LSPosed 包装层已创建（不代表底层内核）";
            case LSPOSED_RUNTIME -> "LSPosed 内核映射";
        };
        return engine.label + " · " + sourceText + (detail.isEmpty() ? "" : " · " + detail);
    }
}
