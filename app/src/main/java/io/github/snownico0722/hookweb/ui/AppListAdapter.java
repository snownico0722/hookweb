package io.github.snownico0722.hookweb.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.snownico0722.hookweb.model.AppScanResult;
import io.github.snownico0722.hookweb.model.EngineEvidence;
import io.github.snownico0722.hookweb.model.EngineKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AppListAdapter extends BaseAdapter {
    public enum FilterMode {
        ALL("全部"),
        WEB_CANDIDATE("有 Web 内核"),
        ATTENTION("运行时第三方（建议关注）"),
        RUNTIME("运行时已确认"),
        SYSTEM_WEBVIEW("系统 WebView"),
        TBS_X5("TBS / X5"),
        UC_U4("UC / U4"),
        XWEB("XWeb"),
        GECKO_CEF("Gecko / CEF"),
        UNKNOWN("未识别");

        public final String label;

        FilterMode(String label) {
            this.label = label;
        }

        boolean matches(AppScanResult item) {
            return switch (this) {
                case ALL -> true;
                case WEB_CANDIDATE -> item.hasWebEvidence();
                case ATTENTION -> hasThirdPartyRuntime(item);
                case RUNTIME -> item.hasRuntimeEvidence();
                case SYSTEM_WEBVIEW -> item.engines().contains(EngineKind.SYSTEM_WEBVIEW);
                case TBS_X5 -> item.engines().contains(EngineKind.TBS_X5);
                case UC_U4 -> item.engines().contains(EngineKind.UC_U4);
                case XWEB -> item.engines().contains(EngineKind.XWEB);
                case GECKO_CEF -> item.engines().contains(EngineKind.GECKOVIEW)
                        || item.engines().contains(EngineKind.CEF)
                        || item.engines().contains(EngineKind.CROSSWALK)
                        || item.engines().contains(EngineKind.BUNDLED_CHROMIUM);
                case UNKNOWN -> !item.hasWebEvidence();
            };
        }

        private static boolean hasThirdPartyRuntime(AppScanResult item) {
            for (EngineEvidence evidence : item.evidence()) {
                if (evidence.source != EngineEvidence.Source.STATIC_APK
                        && evidence.engine != EngineKind.SYSTEM_WEBVIEW) {
                    return true;
                }
            }
            return false;
        }
    }

    private final Context context;
    private final List<AppScanResult> all = new ArrayList<>();
    private final List<AppScanResult> visible = new ArrayList<>();
    private String query = "";
    private FilterMode filterMode = FilterMode.ALL;

    public AppListAdapter(Context context) {
        this.context = context;
    }

    public void setItems(List<AppScanResult> items) {
        all.clear();
        all.addAll(items);
        refilter();
    }

    public void setQuery(String query) {
        this.query = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        refilter();
    }

    public void setFilterMode(FilterMode mode) {
        filterMode = mode == null ? FilterMode.ALL : mode;
        refilter();
    }

    public FilterMode getFilterMode() {
        return filterMode;
    }

    @Override
    public int getCount() {
        return visible.size();
    }

    @Override
    public AppScanResult getItem(int position) {
        return visible.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).packageName.hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if (convertView == null) {
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            int padH = dp(16);
            int padV = dp(10);
            root.setPadding(padH, padV, padH, padV);

            TextView title = new TextView(context);
            title.setTextSize(16);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            TextView pkg = new TextView(context);
            pkg.setTextSize(12);
            TextView engine = new TextView(context);
            engine.setTextSize(13);
            engine.setPadding(0, dp(4), 0, 0);

            root.addView(title);
            root.addView(pkg);
            root.addView(engine);
            holder = new Holder(title, pkg, engine);
            root.setTag(holder);
            convertView = root;
        } else {
            holder = (Holder) convertView.getTag();
        }

        AppScanResult item = getItem(position);
        holder.title.setText(item.label + (item.systemApp ? "  [系统]" : ""));
        holder.pkg.setText(item.packageName);
        holder.engine.setText(summary(item));
        return convertView;
    }

    private void refilter() {
        visible.clear();
        for (AppScanResult item : all) {
            if (!filterMode.matches(item)) continue;
            if (query.isEmpty() || matchesQuery(item, query)) visible.add(item);
        }
        notifyDataSetChanged();
    }

    private static boolean matchesQuery(AppScanResult item, String query) {
        if (item.label.toLowerCase(Locale.ROOT).contains(query)) return true;
        if (item.packageName.toLowerCase(Locale.ROOT).contains(query)) return true;
        for (EngineKind engine : item.engines()) {
            if (engine.label.toLowerCase(Locale.ROOT).contains(query)
                    || engine.code.toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    private static String summary(AppScanResult item) {
        if (item.evidence().isEmpty()) {
            return item.error == null ? "未发现明显 Web 内核痕迹" : "扫描不完整 · " + item.error;
        }
        LinkedHashMap<EngineKind, Boolean> runtime = new LinkedHashMap<>();
        for (EngineEvidence evidence : item.evidence()) {
            boolean isRuntime = evidence.source != EngineEvidence.Source.STATIC_APK;
            runtime.merge(evidence.engine, isRuntime, (a, b) -> a || b);
        }
        ArrayList<String> parts = new ArrayList<>();
        for (Map.Entry<EngineKind, Boolean> entry : runtime.entrySet()) {
            parts.add((entry.getValue() ? "● " : "○ ") + entry.getKey().label);
        }
        return String.join("   ", parts);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class Holder {
        final TextView title;
        final TextView pkg;
        final TextView engine;

        Holder(TextView title, TextView pkg, TextView engine) {
            this.title = title;
            this.pkg = pkg;
            this.engine = engine;
        }
    }
}
