package io.github.snownico0722.hookweb.model;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public final class AppScanResultTest {
    @Test
    public void legacyConstructorAndDelayedEventsAreWrappers() {
        for (String detail : Arrays.asList("constructor:com.tencent.smtt.sdk.WebView",
                "delayed:com.tencent.smtt.sdk.WebView · example.app")) {
            EngineEvidence evidence = hook(EngineKind.TBS_X5, detail);
            assertEquals(EngineEvidence.Source.LSPOSED_WRAPPER, evidence.source);
            assertTrue(evidence.isHookEvidence());
            assertFalse(evidence.isRuntimeCore());
            assertFalse(result(evidence).hasRuntimeEvidence());
        }
    }

    @Test
    public void fallbackConfirmsSystemCoreWithoutConfirmingX5Core() {
        AppScanResult result = result(
                hook(EngineKind.TBS_X5, "constructor:com.tencent.smtt.sdk.WebView"),
                hook(EngineKind.SYSTEM_WEBVIEW, "self-maps · example.app"));
        assertTrue(result.hasRuntimeEvidence());
        assertTrue(result.hasRuntimeEngine(EngineKind.SYSTEM_WEBVIEW));
        assertFalse(result.hasRuntimeEngine(EngineKind.TBS_X5));
    }

    @Test
    public void clearingHooksRemovesOldSnapshotEventsButPreservesApkAndRootEvidence() {
        EngineEvidence apk = new EngineEvidence(EngineKind.TBS_X5, EngineEvidence.Source.STATIC_APK, "dex");
        EngineEvidence root = new EngineEvidence(EngineKind.SYSTEM_WEBVIEW, EngineEvidence.Source.ROOT_MAPS, "maps");
        AppScanResult oldSnapshot = result(apk, root,
                hook(EngineKind.TBS_X5, "constructor:com.tencent.smtt.sdk.WebView"),
                hook(EngineKind.TBS_X5, "self-maps"));

        AppScanResult cleared = oldSnapshot.withCurrentHookEvidence(Collections.emptyList());
        assertEquals(Arrays.asList(apk, root), cleared.evidence());
        assertFalse(cleared.hasRuntimeEngine(EngineKind.TBS_X5));
        assertEquals(4, oldSnapshot.evidence().size());
    }

    @Test
    public void currentHookRecordsReplaceStaleNativeAndWrapperRecords() {
        AppScanResult oldSnapshot = result(
                hook(EngineKind.TBS_X5, "constructor:com.tencent.smtt.sdk.WebView"),
                hook(EngineKind.TBS_X5, "self-maps"));
        EngineEvidence current = hook(EngineKind.SYSTEM_WEBVIEW, "self-maps");
        AppScanResult refreshed = oldSnapshot.withCurrentHookEvidence(Collections.singletonList(current));

        assertEquals(Collections.singletonList(current), refreshed.evidence());
        assertFalse(refreshed.engines().contains(EngineKind.TBS_X5));
        assertTrue(refreshed.hasRuntimeEngine(EngineKind.SYSTEM_WEBVIEW));
    }

    @Test
    public void repeatedRefreshDoesNotAccumulateHookEvents() {
        EngineEvidence current = hook(EngineKind.SYSTEM_WEBVIEW, "self-maps");
        AppScanResult refreshed = result(current)
                .withCurrentHookEvidence(Collections.singletonList(current))
                .withCurrentHookEvidence(Collections.singletonList(current));
        assertEquals(1, refreshed.evidence().size());
    }

    private static EngineEvidence hook(EngineKind engine, String detail) {
        return new EngineEvidence(engine, EngineEvidence.Source.LSPOSED_RUNTIME, detail);
    }

    private static AppScanResult result(EngineEvidence... evidence) {
        return new AppScanResult("Example", "example.app", false,
                Collections.emptyList(), Arrays.asList(evidence), null);
    }
}
