package io.github.snownico0722.hookweb;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebView;

import io.github.libxposed.service.XposedService;
import io.github.snownico0722.hookweb.model.AppScanResult;
import io.github.snownico0722.hookweb.model.EngineEvidence;
import io.github.snownico0722.hookweb.root.RootShell;
import io.github.snownico0722.hookweb.runtime.RootRuntimeScanner;
import io.github.snownico0722.hookweb.runtime.RuntimeEventStore;
import io.github.snownico0722.hookweb.scan.ApkScanner;
import io.github.snownico0722.hookweb.ui.AppListAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class MainActivity extends Activity implements HookWebApp.ServiceStateListener {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<AppScanResult> results = new ArrayList<>();

    private RootShell rootShell;
    private AppListAdapter adapter;
    private TextView rootStatus;
    private TextView lspStatus;
    private TextView webViewStatus;
    private TextView progressStatus;
    private CheckBox includeSystem;
    private Button scopeButton;
    private XposedService xposedService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootShell = new RootShell(this);
        buildUi();
        updateSystemWebViewStatus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        HookWebApp.addServiceStateListener(this, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mergeStoredRuntimeEvents();
    }

    @Override
    protected void onStop() {
        HookWebApp.removeServiceStateListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onServiceStateChanged(XposedService service) {
        xposedService = service;
        runOnUiThread(() -> {
            if (service == null) {
                lspStatus.setText("LSPosed: 未连接（静态/ROOT 扫描仍可用）");
                scopeButton.setEnabled(false);
                return;
            }
            try {
                String scope = String.valueOf(service.getScope().size());
                lspStatus.setText("LSPosed: " + service.getFrameworkName() + " · API " + service.getApiVersion() + " · scope " + scope);
                scopeButton.setEnabled(true);
            } catch (Throwable t) {
                lspStatus.setText("LSPosed: 服务异常 · " + t.getClass().getSimpleName());
                scopeButton.setEnabled(false);
            }
        });
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(8));

        TextView title = new TextView(this);
        title.setText("HookWeb 内核扫描器");
        title.setTextSize(22);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("○ = APK 静态集成   ● = 运行时确认");
        subtitle.setPadding(0, dp(4), 0, dp(8));
        root.addView(subtitle);

        rootStatus = statusText("ROOT: 尚未申请");
        lspStatus = statusText("LSPosed: 正在连接…");
        webViewStatus = statusText("系统 WebView: 检测中…");
        progressStatus = statusText("尚未扫描");
        root.addView(rootStatus);
        root.addView(lspStatus);
        root.addView(webViewStatus);
        root.addView(progressStatus);

        LinearLayout row1 = buttonRow();
        Button rootButton = button("申请 ROOT");
        Button scanButton = button("扫描 APK");
        row1.addView(rootButton, weighted());
        row1.addView(scanButton, weighted());
        root.addView(row1);

        LinearLayout row2 = buttonRow();
        scopeButton = button("申请 LSPosed 作用域");
        scopeButton.setEnabled(false);
        Button runtimeButton = button("扫描运行中进程");
        row2.addView(scopeButton, weighted());
        row2.addView(runtimeButton, weighted());
        root.addView(row2);

        LinearLayout row3 = buttonRow();
        Button refreshButton = button("刷新 Hook 记录");
        Button clearButton = button("清空 Hook 记录");
        row3.addView(refreshButton, weighted());
        row3.addView(clearButton, weighted());
        root.addView(row3);

        includeSystem = new CheckBox(this);
        includeSystem.setText("包含系统应用");
        includeSystem.setChecked(false);
        root.addView(includeSystem);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("搜索 App / 包名 / 内核");
        root.addView(search, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        ListView list = new ListView(this);
        adapter = new AppListAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> showDetails(adapter.getItem(position)));
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        setContentView(root);

        rootButton.setOnClickListener(v -> requestRoot());
        scanButton.setOnClickListener(v -> scanInstalledApps());
        runtimeButton.setOnClickListener(v -> scanRuntimeMaps());
        scopeButton.setOnClickListener(v -> requestXposedScope());
        refreshButton.setOnClickListener(v -> mergeStoredRuntimeEvents());
        clearButton.setOnClickListener(v -> {
            RuntimeEventStore.clear(this);
            rebuildWithoutRuntimeEvidence();
            Toast.makeText(this, "Hook 运行记录已清空", Toast.LENGTH_SHORT).show();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { adapter.setQuery(String.valueOf(s)); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void requestRoot() {
        progressStatus.setText("正在请求 ROOT…");
        worker.execute(() -> {
            boolean ok = rootShell.requestRoot();
            runOnUiThread(() -> {
                rootStatus.setText(ok ? "ROOT: 已授权" : "ROOT: 未授权 / su 不可用");
                progressStatus.setText(ok ? "ROOT 可用" : "可继续静态扫描，但受 APK/运行进程访问限制");
            });
        });
    }

    private void scanInstalledApps() {
        progressStatus.setText("准备扫描…");
        final boolean shouldIncludeSystem = includeSystem.isChecked();
        worker.execute(() -> {
            if (!rootShell.isRootAvailable()) {
                boolean root = rootShell.requestRoot();
                runOnUiThread(() -> rootStatus.setText(root ? "ROOT: 已授权" : "ROOT: 未授权 / su 不可用"));
            }

            List<ApplicationInfo> apps = installedApplications();
            if (!shouldIncludeSystem) {
                apps.removeIf(info -> (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            }
            apps.removeIf(info -> getPackageName().equals(info.packageName));

            final int total = apps.size();
            runOnUiThread(() -> progressStatus.setText("扫描 0 / " + total));

            ExecutorService pool = Executors.newFixedThreadPool(Math.min(3, Math.max(1, Runtime.getRuntime().availableProcessors() / 2)));
            CompletionService<AppScanResult> completion = new ExecutorCompletionService<>(pool);
            ApkScanner scanner = new ApkScanner(this, rootShell);
            for (ApplicationInfo app : apps) completion.submit(() -> scanner.scan(app));

            ArrayList<AppScanResult> scanned = new ArrayList<>();
            try {
                for (int i = 0; i < total; i++) {
                    Future<AppScanResult> future = completion.take();
                    AppScanResult result = future.get();
                    result = result.withExtraEvidence(RuntimeEventStore.get(this, result.packageName));
                    scanned.add(result);
                    int done = i + 1;
                    if (done == total || done % 3 == 0) {
                        int detected = countDetected(scanned);
                        runOnUiThread(() -> progressStatus.setText("扫描 " + done + " / " + total + " · 已发现 " + detected + " 个 Web 候选"));
                    }
                }
            } catch (Throwable t) {
                runOnUiThread(() -> Toast.makeText(this, "扫描中断: " + t.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                pool.shutdownNow();
            }

            sortResults(scanned);
            synchronized (results) {
                results.clear();
                results.addAll(scanned);
            }
            runOnUiThread(() -> {
                adapter.setItems(scanned);
                progressStatus.setText("完成 · " + scanned.size() + " 个 App · " + countDetected(scanned) + " 个有 Web 内核痕迹");
            });
        });
    }

    private void scanRuntimeMaps() {
        List<AppScanResult> snapshot;
        synchronized (results) {
            snapshot = new ArrayList<>(results);
        }
        if (snapshot.isEmpty()) {
            Toast.makeText(this, "先执行一次 APK 扫描", Toast.LENGTH_SHORT).show();
            return;
        }
        progressStatus.setText("ROOT 扫描当前运行进程…");
        worker.execute(() -> {
            if (!rootShell.isRootAvailable() && !rootShell.requestRoot()) {
                runOnUiThread(() -> progressStatus.setText("运行进程扫描需要 ROOT"));
                return;
            }
            HashSet<String> packages = new HashSet<>();
            for (AppScanResult result : snapshot) packages.add(result.packageName);
            Map<String, List<EngineEvidence>> runtime = new RootRuntimeScanner(rootShell).scan(packages);
            ArrayList<AppScanResult> merged = new ArrayList<>();
            for (AppScanResult item : snapshot) {
                AppScanResult value = item.withExtraEvidence(runtime.getOrDefault(item.packageName, Collections.emptyList()));
                value = value.withExtraEvidence(RuntimeEventStore.get(this, item.packageName));
                merged.add(value);
            }
            sortResults(merged);
            synchronized (results) {
                results.clear();
                results.addAll(merged);
            }
            runOnUiThread(() -> {
                adapter.setItems(merged);
                progressStatus.setText("运行时扫描完成 · " + runtime.size() + " 个运行 App 命中内核映射");
            });
        });
    }

    private void requestXposedScope() {
        XposedService service = xposedService;
        if (service == null) {
            Toast.makeText(this, "LSPosed API 102 服务未连接", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> targets = new ArrayList<>();
        synchronized (results) {
            for (AppScanResult result : results) {
                if (result.hasWebEvidence()) targets.add(result.packageName);
            }
        }
        if (targets.isEmpty()) {
            Toast.makeText(this, "先扫描 APK；当前没有候选 App", Toast.LENGTH_SHORT).show();
            return;
        }
        progressStatus.setText("请求 LSPosed 作用域: " + targets.size() + " 个 App…");
        try {
            service.requestScope(targets, new XposedService.OnScopeEventListener() {
                @Override
                public void onScopeRequestApproved(List<String> approved) {
                    runOnUiThread(() -> {
                        progressStatus.setText("LSPosed 已批准 " + approved.size() + " 个作用域；重新打开目标 App 后会记录实际 WebView 创建");
                        Toast.makeText(MainActivity.this, "作用域已更新", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onScopeRequestFailed(String message) {
                    runOnUiThread(() -> progressStatus.setText("LSPosed 作用域申请失败: " + message));
                }
            });
        } catch (Throwable t) {
            progressStatus.setText("LSPosed 作用域申请异常: " + t.getClass().getSimpleName());
        }
    }

    private void mergeStoredRuntimeEvents() {
        ArrayList<AppScanResult> merged = new ArrayList<>();
        synchronized (results) {
            if (results.isEmpty()) return;
            for (AppScanResult item : results) {
                merged.add(item.withExtraEvidence(RuntimeEventStore.get(this, item.packageName)));
            }
            sortResults(merged);
            results.clear();
            results.addAll(merged);
        }
        adapter.setItems(merged);
    }

    private void rebuildWithoutRuntimeEvidence() {
        ArrayList<AppScanResult> cleaned = new ArrayList<>();
        synchronized (results) {
            for (AppScanResult item : results) {
                ArrayList<EngineEvidence> staticOnly = new ArrayList<>();
                for (EngineEvidence evidence : item.evidence()) {
                    if (evidence.source != EngineEvidence.Source.LSPOSED_RUNTIME) staticOnly.add(evidence);
                }
                cleaned.add(new AppScanResult(item.label, item.packageName, item.systemApp, item.apkPaths, staticOnly, item.error));
            }
            results.clear();
            results.addAll(cleaned);
        }
        sortResults(cleaned);
        adapter.setItems(cleaned);
    }

    private List<ApplicationInfo> installedApplications() {
        PackageManager pm = getPackageManager();
        if (Build.VERSION.SDK_INT >= 33) {
            return new ArrayList<>(pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_ALL)));
        }
        //noinspection deprecation
        return new ArrayList<>(pm.getInstalledApplications(PackageManager.MATCH_ALL));
    }

    private void updateSystemWebViewStatus() {
        try {
            PackageInfo current = WebView.getCurrentWebViewPackage();
            if (current == null) {
                webViewStatus.setText("系统 WebView: 未检测到 provider");
            } else {
                webViewStatus.setText("系统 WebView: " + current.packageName + " · " + current.versionName);
            }
        } catch (Throwable t) {
            webViewStatus.setText("系统 WebView: " + t.getClass().getSimpleName());
        }
    }

    private void showDetails(AppScanResult item) {
        StringBuilder text = new StringBuilder();
        text.append(item.label).append('\n').append(item.packageName).append("\n\n");
        if (item.evidence().isEmpty()) {
            text.append("未发现已知 Web 内核特征。\n");
        } else {
            for (EngineEvidence evidence : item.evidence()) {
                text.append("• ").append(evidence.displayText()).append('\n');
            }
        }
        if (item.error != null) text.append("\n扫描问题: ").append(item.error).append('\n');
        text.append("\nAPK:\n");
        for (String path : item.apkPaths) text.append(path).append('\n');
        new AlertDialog.Builder(this)
                .setTitle("内核详情")
                .setMessage(text.toString())
                .setPositiveButton("关闭", null)
                .show();
    }

    private static void sortResults(List<AppScanResult> list) {
        list.sort(Comparator
                .comparing((AppScanResult item) -> !hasRuntime(item))
                .thenComparing(item -> !item.hasWebEvidence())
                .thenComparing(item -> item.label, String.CASE_INSENSITIVE_ORDER));
    }

    private static boolean hasRuntime(AppScanResult item) {
        for (EngineEvidence evidence : item.evidence()) {
            if (evidence.source != EngineEvidence.Source.STATIC_APK) return true;
        }
        return false;
    }

    private static int countDetected(List<AppScanResult> list) {
        int count = 0;
        for (AppScanResult item : list) if (item.hasWebEvidence()) count++;
        return count;
    }

    private TextView statusText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setPadding(0, dp(2), 0, dp(2));
        return view;
    }

    private LinearLayout buttonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, 0);
        return row;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginEnd(dp(4));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
