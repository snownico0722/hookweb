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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebView;

import io.github.libxposed.service.XposedService;
import io.github.snownico0722.hookweb.history.ScanHistoryStore;
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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity implements HookWebApp.ServiceStateListener {
    private static final int HISTORY_CHECKPOINT_EVERY = 8;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<AppScanResult> results = new ArrayList<>();

    private RootShell rootShell;
    private ScanHistoryStore historyStore;
    private AppListAdapter adapter;
    private TextView rootStatus;
    private TextView lspStatus;
    private TextView webViewStatus;
    private TextView progressStatus;
    private CheckBox includeSystem;
    private Button scanButton;
    private Button stopButton;
    private Button historyButton;
    private Button runtimeButton;
    private Button scopeButton;
    private XposedService xposedService;
    private volatile ScanSession activeScan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootShell = new RootShell(this);
        historyStore = new ScanHistoryStore(this);
        adapter = new AppListAdapter(this);
        buildUi();
        updateSystemWebViewStatus();
        loadLatestHistory();
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
        ScanSession session = activeScan;
        if (session != null) session.cancel();
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
                scopeButton.setEnabled(activeScan == null);
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
        progressStatus = statusText("正在读取历史记录…");
        root.addView(rootStatus);
        root.addView(lspStatus);
        root.addView(webViewStatus);
        root.addView(progressStatus);

        LinearLayout row1 = buttonRow();
        Button rootButton = button("申请 ROOT");
        scanButton = button("扫描 APK");
        stopButton = button("停止扫描");
        stopButton.setEnabled(false);
        row1.addView(rootButton, weighted());
        row1.addView(scanButton, weighted());
        row1.addView(stopButton, weighted());
        root.addView(row1);

        LinearLayout row2 = buttonRow();
        historyButton = button("历史记录");
        scopeButton = button("申请 LSPosed 作用域");
        scopeButton.setEnabled(false);
        runtimeButton = button("扫描运行中进程");
        row2.addView(historyButton, weighted());
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

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView filterLabel = new TextView(this);
        filterLabel.setText("筛选：");
        Spinner filterSpinner = new Spinner(this);
        ArrayList<String> filterLabels = new ArrayList<>();
        for (AppListAdapter.FilterMode mode : AppListAdapter.FilterMode.values()) {
            filterLabels.add(mode.label);
        }
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                filterLabels
        );
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(filterAdapter);
        filterSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                AppListAdapter.FilterMode[] modes = AppListAdapter.FilterMode.values();
                adapter.setFilterMode(position >= 0 && position < modes.length ? modes[position] : AppListAdapter.FilterMode.ALL);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                adapter.setFilterMode(AppListAdapter.FilterMode.ALL);
            }
        });
        filterRow.addView(filterLabel);
        filterRow.addView(filterSpinner, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(filterRow);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("搜索 App / 包名 / 内核");
        root.addView(search, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        ListView list = new ListView(this);
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
        stopButton.setOnClickListener(v -> stopScan());
        historyButton.setOnClickListener(v -> showHistory());
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

    private void loadLatestHistory() {
        worker.execute(() -> {
            List<ScanHistoryStore.Snapshot> snapshots = historyStore.list();
            ScanHistoryStore.Snapshot latest = snapshots.isEmpty() ? null : snapshots.get(0);
            int historyCount = snapshots.size();
            runOnUiThread(() -> {
                historyButton.setText("历史记录" + (historyCount > 0 ? " (" + historyCount + ")" : ""));
                if (latest == null) {
                    progressStatus.setText("尚未扫描");
                    return;
                }
                applySnapshot(latest, false);
            });
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
        if (activeScan != null) {
            Toast.makeText(this, "扫描正在进行", Toast.LENGTH_SHORT).show();
            return;
        }

        final boolean shouldIncludeSystem = includeSystem.isChecked();
        final ScanSession session = new ScanSession(System.currentTimeMillis(), shouldIncludeSystem);
        activeScan = session;
        setScanningUi(true);
        progressStatus.setText("准备扫描…");

        worker.execute(() -> runApkScan(session));
    }

    private void runApkScan(ScanSession session) {
        session.runner = Thread.currentThread();
        String finalStatus = ScanHistoryStore.COMPLETED;
        String failureMessage = null;
        ArrayList<AppScanResult> scanned = new ArrayList<>();
        int done = 0;
        int total = 0;
        try {
            if (!rootShell.isRootAvailable() && !session.cancelled.get()) {
                boolean root = rootShell.requestRoot();
                runOnUiThread(() -> rootStatus.setText(root ? "ROOT: 已授权" : "ROOT: 未授权 / su 不可用"));
            }
            if (session.cancelled.get()) throw new CancellationException();

            List<ApplicationInfo> apps = installedApplications();
            if (!session.includeSystem) {
                apps.removeIf(info -> (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            }
            apps.removeIf(info -> getPackageName().equals(info.packageName));
            total = apps.size();
            session.total = total;
            final int totalForUi = total;
            runOnUiThread(() -> progressStatus.setText("扫描 0 / " + totalForUi));

            int threads = Math.min(3, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
            int window = Math.max(threads, threads * 2);
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            session.pool = pool;
            CompletionService<AppScanResult> completion = new ExecutorCompletionService<>(pool);
            ApkScanner scanner = new ApkScanner(this, rootShell, session.cancelled::get);

            int next = 0;
            int inFlight = 0;
            while (next < total && inFlight < window && !session.cancelled.get()) {
                ApplicationInfo app = apps.get(next++);
                completion.submit(() -> scanner.scan(app));
                inFlight++;
            }

            while (done < total && !session.cancelled.get()) {
                Future<AppScanResult> future = completion.poll(250, TimeUnit.MILLISECONDS);
                if (future == null) continue;
                inFlight--;
                try {
                    AppScanResult result = future.get();
                    if (result != null) {
                        result = result.withExtraEvidence(RuntimeEventStore.get(this, result.packageName));
                        scanned.add(result);
                    }
                    done++;
                    session.scanned = done;
                } catch (CancellationException cancelled) {
                    if (session.cancelled.get()) break;
                    done++;
                    session.scanned = done;
                } catch (Throwable taskFailure) {
                    if (session.cancelled.get()) break;
                    done++;
                    session.scanned = done;
                }

                if (done == total || done % 2 == 0) {
                    publishScanProgress(scanned, done, total);
                }
                if (done > 0 && done % HISTORY_CHECKPOINT_EVERY == 0) {
                    saveSnapshotQuietly(session, ScanHistoryStore.RUNNING, scanned, done, total);
                }

                while (next < total && inFlight < window && !session.cancelled.get()) {
                    ApplicationInfo app = apps.get(next++);
                    completion.submit(() -> scanner.scan(app));
                    inFlight++;
                }
            }

            if (session.cancelled.get()) {
                finalStatus = ScanHistoryStore.STOPPED;
            } else {
                publishScanProgress(scanned, done, total);
            }
        } catch (CancellationException cancelled) {
            finalStatus = ScanHistoryStore.STOPPED;
        } catch (InterruptedException interrupted) {
            if (session.cancelled.get()) {
                finalStatus = ScanHistoryStore.STOPPED;
            } else {
                finalStatus = ScanHistoryStore.FAILED;
                failureMessage = "扫描协调线程被意外中断";
            }
        } catch (Throwable t) {
            finalStatus = ScanHistoryStore.FAILED;
            failureMessage = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
        } finally {
            session.runner = null;
            ExecutorService pool = session.pool;
            if (pool != null) pool.shutdownNow();
        }

        sortResults(scanned);
        ScanHistoryStore.Snapshot snapshot = saveSnapshotQuietly(session, finalStatus, scanned, done, total);
        synchronized (results) {
            results.clear();
            results.addAll(scanned);
        }

        String statusForUi = finalStatus;
        String errorForUi = failureMessage;
        runOnUiThread(() -> {
            adapter.setItems(scanned);
            if (ScanHistoryStore.COMPLETED.equals(statusForUi)) {
                progressStatus.setText("完成 · " + scanned.size() + " 个 App · " + countDetected(scanned) + " 个有 Web 内核痕迹");
            } else if (ScanHistoryStore.STOPPED.equals(statusForUi)) {
                progressStatus.setText("已停止并保存 · 已扫描 " + scanned.size() + " / " + session.total + " · 命中 " + countDetected(scanned));
            } else {
                progressStatus.setText("扫描失败，已保存部分结果" + (errorForUi == null ? "" : " · " + errorForUi));
            }
            if (snapshot != null) scanButton.setText("重新扫描 APK");
            activeScan = null;
            setScanningUi(false);
            refreshHistoryCount();
        });
    }

    private void publishScanProgress(List<AppScanResult> scanned, int done, int total) {
        ArrayList<AppScanResult> snapshot = new ArrayList<>(scanned);
        sortResults(snapshot);
        synchronized (results) {
            results.clear();
            results.addAll(snapshot);
        }
        int detected = countDetected(snapshot);
        runOnUiThread(() -> {
            adapter.setItems(snapshot);
            progressStatus.setText("扫描 " + done + " / " + total + " · 已发现 " + detected + " 个 Web 候选");
        });
    }

    private void stopScan() {
        ScanSession session = activeScan;
        if (session == null) return;
        stopButton.setEnabled(false);
        progressStatus.setText("正在停止并保存已扫描结果…");
        session.cancel();
    }

    private ScanHistoryStore.Snapshot saveSnapshotQuietly(
            ScanSession session,
            String status,
            List<AppScanResult> scanned,
            int done,
            int total
    ) {
        ArrayList<AppScanResult> copy = new ArrayList<>(scanned);
        sortResults(copy);
        ScanHistoryStore.Snapshot snapshot = new ScanHistoryStore.Snapshot(
                session.id,
                session.id,
                System.currentTimeMillis(),
                status,
                session.includeSystem,
                Math.max(done, copy.size()),
                Math.max(total, session.total),
                copy
        );
        try {
            historyStore.save(snapshot);
            return snapshot;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void showHistory() {
        if (activeScan != null) {
            Toast.makeText(this, "扫描进行中，停止后再切换历史记录", Toast.LENGTH_SHORT).show();
            return;
        }
        progressStatus.setText("读取历史记录…");
        worker.execute(() -> {
            List<ScanHistoryStore.Snapshot> snapshots = historyStore.list();
            runOnUiThread(() -> {
                if (snapshots.isEmpty()) {
                    progressStatus.setText("没有历史扫描记录");
                    Toast.makeText(this, "没有历史扫描记录", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] labels = new String[snapshots.size()];
                for (int i = 0; i < snapshots.size(); i++) labels[i] = snapshots.get(i).displayTitle();
                new AlertDialog.Builder(this)
                        .setTitle("扫描历史（最多保留 12 次）")
                        .setItems(labels, (dialog, which) -> applySnapshot(snapshots.get(which), true))
                        .setNeutralButton("清空历史", (dialog, which) -> confirmClearHistory())
                        .setNegativeButton("关闭", null)
                        .show();
                progressStatus.setText("历史记录 " + snapshots.size() + " 次");
            });
        });
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle("清空扫描历史？")
                .setMessage("只删除历史快照；当前屏幕上的结果和 Hook 运行记录不会被清除。")
                .setPositiveButton("清空", (dialog, which) -> worker.execute(() -> {
                    historyStore.clear();
                    runOnUiThread(() -> {
                        historyButton.setText("历史记录");
                        progressStatus.setText("扫描历史已清空；当前结果仍保留");
                    });
                }))
                .setNegativeButton("取消", null)
                .show();
    }

    private void applySnapshot(ScanHistoryStore.Snapshot snapshot, boolean fromHistoryPicker) {
        ArrayList<AppScanResult> copy = new ArrayList<>(snapshot.results);
        sortResults(copy);
        synchronized (results) {
            results.clear();
            results.addAll(copy);
        }
        adapter.setItems(copy);
        includeSystem.setChecked(snapshot.includeSystem);
        scanButton.setText("重新扫描 APK");

        String state = switch (snapshot.status) {
            case ScanHistoryStore.COMPLETED -> "完成";
            case ScanHistoryStore.STOPPED -> "已停止";
            case ScanHistoryStore.RUNNING -> "上次异常中断";
            default -> "失败";
        };
        progressStatus.setText((fromHistoryPicker ? "已载入历史 · " : "已恢复上次结果 · ")
                + state + " · " + snapshot.scannedApps + "/" + snapshot.totalApps
                + " · 命中 " + snapshot.detectedCount());
    }

    private void refreshHistoryCount() {
        worker.execute(() -> {
            int count = historyStore.list().size();
            runOnUiThread(() -> historyButton.setText("历史记录" + (count > 0 ? " (" + count + ")" : "")));
        });
    }

    private void setScanningUi(boolean scanning) {
        scanButton.setEnabled(!scanning);
        stopButton.setEnabled(scanning);
        historyButton.setEnabled(!scanning);
        runtimeButton.setEnabled(!scanning);
        includeSystem.setEnabled(!scanning);
        scopeButton.setEnabled(!scanning && xposedService != null);
    }

    private void scanRuntimeMaps() {
        if (activeScan != null) {
            Toast.makeText(this, "先停止 APK 扫描", Toast.LENGTH_SHORT).show();
            return;
        }
        List<AppScanResult> snapshot;
        synchronized (results) {
            snapshot = new ArrayList<>(results);
        }
        if (snapshot.isEmpty()) {
            Toast.makeText(this, "没有可用结果；先扫描一次或载入历史", Toast.LENGTH_SHORT).show();
            return;
        }
        progressStatus.setText("ROOT 扫描当前运行进程…");
        runtimeButton.setEnabled(false);
        worker.execute(() -> {
            if (!rootShell.isRootAvailable() && !rootShell.requestRoot()) {
                runOnUiThread(() -> {
                    progressStatus.setText("运行进程扫描需要 ROOT");
                    runtimeButton.setEnabled(true);
                });
                return;
            }
            HashSet<String> packages = new HashSet<>();
            for (AppScanResult result : snapshot) packages.add(result.packageName);
            Map<String, List<EngineEvidence>> runtime = new RootRuntimeScanner(rootShell).scan(packages);
            ArrayList<AppScanResult> merged = new ArrayList<>();
            for (AppScanResult item : snapshot) {
                AppScanResult value = item.withoutEvidenceSource(EngineEvidence.Source.ROOT_MAPS);
                value = value.withExtraEvidence(runtime.getOrDefault(item.packageName, Collections.emptyList()));
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
                runtimeButton.setEnabled(true);
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
            Toast.makeText(this, "当前结果没有候选 App", Toast.LENGTH_SHORT).show();
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
        if (adapter == null || activeScan != null) return;
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
                cleaned.add(item.withoutEvidenceSource(EngineEvidence.Source.LSPOSED_RUNTIME));
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
                .comparing((AppScanResult item) -> !item.hasRuntimeEvidence())
                .thenComparing(item -> !item.hasWebEvidence())
                .thenComparing(item -> item.label, String.CASE_INSENSITIVE_ORDER));
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

    private static final class ScanSession {
        final long id;
        final boolean includeSystem;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        volatile ExecutorService pool;
        volatile Thread runner;
        volatile int total;
        volatile int scanned;

        ScanSession(long id, boolean includeSystem) {
            this.id = id;
            this.includeSystem = includeSystem;
        }

        void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;
            ExecutorService currentPool = pool;
            if (currentPool != null) currentPool.shutdownNow();
            Thread currentRunner = runner;
            if (currentRunner != null) currentRunner.interrupt();
        }
    }
}
