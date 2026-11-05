package io.github.snownico0722.hookweb.ui;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import io.github.snownico0722.hookweb.model.AppScanResult;
import io.github.snownico0722.hookweb.model.EngineEvidence;
import io.github.snownico0722.hookweb.model.EngineKind;

import static org.junit.Assert.*;

public final class AppListAdapterTest {
    @Test
    public void wrapperAloneIsCandidateButNotConfirmedCore() {
        AppScanResult app = result(new EngineEvidence(EngineKind.TBS_X5,
                EngineEvidence.Source.LSPOSED_RUNTIME, "constructor:com.tencent.smtt.sdk.WebView"));
        assertTrue(AppListAdapter.FilterMode.WEB_CANDIDATE.matches(app));
        assertTrue(AppListAdapter.FilterMode.TBS_X5.matches(app));
        assertFalse(AppListAdapter.FilterMode.RUNTIME.matches(app));
        assertFalse(AppListAdapter.FilterMode.ATTENTION.matches(app));
        assertEquals("◇ 腾讯 TBS / X5", AppListAdapter.summary(app));
    }

    @Test
    public void systemFallbackIsNotListedAsThirdPartyRuntime() {
        AppScanResult app = result(
                new EngineEvidence(EngineKind.TBS_X5, EngineEvidence.Source.LSPOSED_RUNTIME,
                        "constructor:com.tencent.smtt.sdk.WebView"),
                new EngineEvidence(EngineKind.SYSTEM_WEBVIEW, EngineEvidence.Source.LSPOSED_RUNTIME,
                        "self-maps"));
        assertTrue(AppListAdapter.FilterMode.RUNTIME.matches(app));
        assertFalse(AppListAdapter.FilterMode.ATTENTION.matches(app));
        assertEquals("◇ 腾讯 TBS / X5   ● 系统 WebView", AppListAdapter.summary(app));
    }

    @Test
    public void thirdPartyNativeEvidenceIsIncludedFromEitherRuntimeSource() {
        for (EngineEvidence.Source source : Arrays.asList(
                EngineEvidence.Source.ROOT_MAPS, EngineEvidence.Source.LSPOSED_RUNTIME)) {
            AppScanResult app = result(new EngineEvidence(EngineKind.TBS_X5, source, "self-maps"));
            assertTrue(AppListAdapter.FilterMode.ATTENTION.matches(app));
            assertTrue(AppListAdapter.FilterMode.RUNTIME.matches(app));
            assertEquals("● 腾讯 TBS / X5", AppListAdapter.summary(app));
        }
    }

    @Test
    public void summaryUsesStrongestEvidenceForEachEngine() {
        EngineEvidence apk = new EngineEvidence(EngineKind.TBS_X5, EngineEvidence.Source.STATIC_APK, "dex");
        EngineEvidence wrapper = new EngineEvidence(EngineKind.TBS_X5,
                EngineEvidence.Source.LSPOSED_WRAPPER, "constructor:view");
        EngineEvidence nativeCore = new EngineEvidence(EngineKind.TBS_X5,
                EngineEvidence.Source.ROOT_MAPS, "maps");
        assertEquals("○ 腾讯 TBS / X5", AppListAdapter.summary(result(apk)));
        assertEquals("◇ 腾讯 TBS / X5", AppListAdapter.summary(result(wrapper, apk)));
        assertEquals("● 腾讯 TBS / X5", AppListAdapter.summary(result(nativeCore, apk, wrapper)));
    }

    private static AppScanResult result(EngineEvidence... evidence) {
        return new AppScanResult("Example", "example.app", false,
                Collections.emptyList(), Arrays.asList(evidence), null);
    }
}
