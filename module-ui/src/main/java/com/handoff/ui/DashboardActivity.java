package com.handoff.ui;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textview.MaterialTextView;
import com.handoff.api.health.HealthEvent;
import com.handoff.api.health.HealthPolicy;
import com.handoff.api.health.QqAdapters;
import com.handoff.storage.HealthStore;
import com.handoff.storage.RegressionStore;
import io.github.libxposed.service.XposedService;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Native, accessible, offline-first module companion. No root commands or automatic QQ activity starts. */
public final class DashboardActivity extends AppCompatActivity {
    private int BG, PANEL, TEXT, MUTED, CYAN, PINK, AMBER, ON_PRIMARY;
    private UiStyle style;
    private UiStyleStore styleStore;
    private boolean styleSaving;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable serviceChanged = () -> handler.post(() -> { if (this.resumed) refresh(); });
    private final Runnable tick = new Runnable() { @Override public void run() { refresh(false); handler.postDelayed(this, 4000); } };
    private LinearLayout root, body;
    private DashboardNavigation nav;
    private MaterialToolbar toolbar;
    private ScrollView scroll;
    private int page;
    private boolean resumed;
    private final RefreshGate refreshGate = new RefreshGate();
    private final DashboardMotion motion = new DashboardMotion();
    private JSONObject health = new JSONObject();
    private JSONArray runs = new JSONArray();
    private List<HealthPolicy.Evidence> evidence = List.of();
    private long moduleCode, qqCode;
    private String moduleName = "—", qqName = "未安装", framework = "unknown", scope = "未知";
    private boolean frameworkConnected, scoped, biliInstalled, douyinInstalled, kuaishouInstalled;
    private boolean checked;
    private FrameworkSnapshot frameworkSnapshot = FrameworkSnapshot.unavailable();
    private Map<String, Boolean> settings = Map.of();
    private boolean settingsReadable;

    @Override public void onCreate(Bundle state) {
        styleStore = new UiStyleStore(this); style = styleStore.read(); setTheme(style.theme);
        super.onCreate(state);
        page = state == null ? 0 : Math.max(0, Math.min(3, state.getInt("page")));
        BG = tone(com.google.android.material.R.attr.colorSurface);
        PANEL = tone(com.google.android.material.R.attr.colorSurfaceContainer);
        TEXT = tone(com.google.android.material.R.attr.colorOnSurface);
        MUTED = tone(com.google.android.material.R.attr.colorOnSurfaceVariant);
        CYAN = tone(androidx.appcompat.R.attr.colorPrimary);
        ON_PRIMARY = tone(com.google.android.material.R.attr.colorOnPrimary);
        PINK = getColor(R.color.handoff_error); AMBER = getColor(R.color.handoff_warning);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean light = getResources().getBoolean(R.bool.handoff_light_system_bars);
        var barsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        barsController.setAppearanceLightStatusBars(light); barsController.setAppearanceLightNavigationBars(light);
        root = vertical(); root.setBackgroundColor(BG);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        toolbar = new MaterialToolbar(this); toolbar.setTitle(""); toolbar.setTitleTextColor(TEXT);
        toolbar.setSubtitleTextColor(MUTED); toolbar.setBackgroundColor(BG);
        toolbar.getMenu().add(0, R.id.action_refresh, 0, R.string.refresh_status).setIcon(R.drawable.ic_refresh)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.getMenu().findItem(R.id.action_refresh).getIcon().setTint(TEXT);
        toolbar.setOnMenuItemClickListener(item -> { refresh(); return true; });
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> updateCollapsedTitle(y));
        body = vertical(); body.setPadding(dp(style == UiStyle.MATERIAL ? 20 : 16), 0,
                dp(style == UiStyle.MATERIAL ? 20 : 16), dp(20)); scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        nav = new DashboardNavigation(this, style); nav.setId(R.id.dashboard_navigation);
        nav.setSelectedItemId(pageId(page));
        nav.setOnItemSelectedListener(item -> {
            int next = item.getItemId() == R.id.nav_home ? 0 : item.getItemId() == R.id.nav_diagnostics ? 1
                    : item.getItemId() == R.id.nav_regression ? 2 : 3;
            if (page != next) {
                page = next; render(0);
                motion.play(body, nav.findViewById(item.getItemId()));
            }
            return true;
        });
        root.addView(navigationDock(), new LinearLayout.LayoutParams(-1, -2));
        setContentView(root); ViewCompat.requestApplyInsets(root); FrameworkConnection.observe(serviceChanged); render();
    }
    private int tone(int attribute) { return MaterialColors.getColor(this, attribute, "Handoff"); }
    private View navigationDock() {
        // Separate sibling rather than overlay: every final row can scroll above either navigation style.
        boolean expressive = style == UiStyle.MATERIAL;
        FrameLayout dock = new FrameLayout(this);
        dock.setPadding(dp(expressive ? 24 : 0), dp(expressive ? 12 : 0), dp(expressive ? 24 : 0), dp(expressive ? 12 : 0));
        dock.setClipChildren(false); dock.setClipToPadding(false);
        if (!expressive) dock.setBackgroundColor(PANEL);
        MaterialCardView island = new MaterialCardView(this) {
            @Override protected void onMeasure(int width, int height) {
                int max = dp(expressive ? 480 : 640);
                if (MeasureSpec.getMode(width) != MeasureSpec.UNSPECIFIED && MeasureSpec.getSize(width) > max)
                    width = MeasureSpec.makeMeasureSpec(max, MeasureSpec.EXACTLY);
                super.onMeasure(width, height);
            }
        };
        island.setCardBackgroundColor(PANEL); island.setRadius(dp(expressive ? 32 : 0));
        island.setCardElevation(dp(expressive ? 6 : 0)); island.setStrokeWidth(0);
        island.setUseCompatPadding(false); island.setPreventCornerOverlap(false); island.setClipToOutline(true);
        island.addView(nav, new FrameLayout.LayoutParams(-1, -2));
        dock.addView(island, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL));
        return dock;
    }
    private void updateCollapsedTitle(int scrollY) {
        toolbar.setTitle(scrollY > dp(48) ? new String[]{"Handoff", "诊断", "回归矩阵", "设置"}[page] : "");
    }
    @Override protected void onResume() { super.onResume(); resumed = true; handler.post(tick); }
    @Override protected void onPause() { resumed = false; handler.removeCallbacks(tick); motion.stop(); super.onPause(); }
    @Override protected void onDestroy() {
        FrameworkConnection.unobserve(serviceChanged);
        motion.stop();
        handler.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putInt("page", page); super.onSaveInstanceState(state); }

    private void refresh() { refresh(true); }
    private void refresh(boolean force) {
        if (isFinishing() || isDestroyed() || !refreshGate.begin(force)) return;
        try { worker.execute(() -> {
            JSONObject nextHealth = HealthStore.read(this); JSONArray nextRuns = RegressionStore.read(this);
            PackageInfo own = pkg(getPackageName()), qq = pkg("com.tencent.mobileqq");
            XposedService service = FrameworkConnection.service;
            FrameworkSnapshot snapshot = FrameworkSnapshot.read(service, code(own));
            Map<String, Boolean> nextSettings = new HashMap<>(); boolean readable = false;
            if (snapshot.connected()) try {
                var preferences = service.getRemotePreferences("handoff_settings");
                nextSettings.put("enabled", preferences.getBoolean("enabled", true));
                for (var entry : HealthEvent.Entry.values()) if (entry != HealthEvent.Entry.SYSTEM)
                    nextSettings.put(entry.key, preferences.getBoolean(entry.key, entry.defaultEnabled()));
                readable = true;
            } catch (Throwable ignored) { nextSettings.clear(); }
            final boolean canEdit = readable;
            boolean bili = pkg("tv.danmaku.bili") != null;
            boolean douyin = pkg("com.ss.android.ugc.aweme") != null;
            boolean kuaishou = pkg("com.smile.gifmaker") != null;
            handler.post(() -> {
                if (isDestroyed()) return;
                health = nextHealth; runs = nextRuns; evidence = HealthStore.evidence(health);
                moduleCode = code(own); moduleName = own == null ? "—" : own.versionName;
                qqCode = code(qq); qqName = qq == null ? "未安装" : qq.versionName;
                checked = true; frameworkSnapshot = snapshot;
                framework = HealthPolicy.safe(snapshot.label()); scope = snapshot.scopeLabel();
                frameworkConnected = snapshot.connected(); scoped = snapshot.scoped(); biliInstalled = bili;
                douyinInstalled = douyin;
                kuaishouInstalled = kuaishou;
                settings = Map.copyOf(nextSettings); settingsReadable = canEdit;
                String nextSnapshot = health.toString() + runs + moduleCode + moduleName + qqCode + qqName + snapshot + bili + douyin + kuaishou
                        + new java.util.TreeMap<>(settings) + canEdit;
                // Keep focus, accessibility traversal and scroll stable when polling unchanged data.
                if (refreshGate.finish(nextSnapshot)) render();
            });
        }); } catch (RejectedExecutionException ignored) { refreshGate.cancel(); }
    }
    private int pageId(int index) { return new int[]{R.id.nav_home, R.id.nav_diagnostics, R.id.nav_regression, R.id.nav_settings}[index]; }
    private void render() { render(scroll == null ? 0 : scroll.getScrollY()); }
    private void render(int position) {
        motion.stop();
        body.removeAllViews();
        updateCollapsedTitle(position);
        if (page == 0) home(); else if (page == 1) overview(); else if (page == 2) matrix(); else adapters();
        scroll.post(() -> scroll.scrollTo(0, position));
    }
    private void heading(String title, String detail) {
        TextView largeTitle = text(title, style == UiStyle.MATERIAL ? 34 : 32, TEXT, true);
        ViewCompat.setAccessibilityHeading(largeTitle, true);
        add(largeTitle, 8, 8);
        add(text(detail, 13, MUTED, false), 0, 24);
    }
    private void home() {
        heading("Handoff", "让链接回到对应 App");
        ActivationStatus status = ActivationStatus.resolve(checked, frameworkSnapshot);
        boolean expressive = style == UiStyle.MATERIAL;
        int heroColor = status.active() ? expressive ? CYAN : getColor(R.color.miuix_success) : PANEL;
        int heroText = status.active() ? expressive ? ON_PRIMARY : getColor(R.color.miuix_on_success) : TEXT;
        LinearLayout hero = new StatusCard(this, style, status.active(), heroText,
                tone(com.google.android.material.R.attr.colorOutlineVariant));
        hero.setId(R.id.home_status); hero.setTag(heroColor);
        hero.setPadding(dp(22), dp(22), dp(22), dp(22)); hero.setMinimumHeight(dp(188));
        hero.addView(text("激活状态", 13, heroText, true)); spacer(hero, 12);
        hero.addView(text(status.title(), 27, heroText, true)); spacer(hero, 8);
        hero.addView(text(status.detail(), 13, heroText, false)); spacer(hero, 16);
        hero.addView(text("视频交接  ·  " + (!settingsReadable ? "设置状态未知" : settings.getOrDefault("enabled", true) ? "已开启" : "已关闭"), 13, heroText, true));
        add(hero, 0, 12);
        LinearLayout statistics = row();
        long mounted = current() ? java.util.Arrays.stream(HealthEvent.Entry.values())
                .filter(e -> e != HealthEvent.Entry.SYSTEM && evidence.stream().anyMatch(v -> v.event().entry() == e
                        && v.event().kind() == HealthEvent.Kind.INSTALLED)).count() : 0;
        statistic(statistics, current() ? String.valueOf(mounted) : "—", "入口挂载记录 / " + (HealthEvent.Entry.values().length - 1));
        statistic(statistics, String.valueOf(runs.length()), "本机回归轮次");
        add(statistics, 0, 24);

        LinearLayout lsp = card(); lsp.addView(text("LSPosed / 框架信息", 16, TEXT, true)); spacer(lsp, 8);
        info(lsp, "连接状态", !checked ? "检查中" : frameworkConnected ? "已连接" : "未连接");
        info(lsp, "框架名称", frameworkConnected ? frameworkSnapshot.name() : "未知");
        info(lsp, "框架版本", frameworkConnected ? frameworkSnapshot.version() : "未知");
        info(lsp, "框架版本号", frameworkConnected ? String.valueOf(frameworkSnapshot.versionCode()) : "未知");
        info(lsp, "libxposed API", frameworkConnected ? String.valueOf(frameworkSnapshot.api()) : "未知");
        info(lsp, "QQ 作用域", scope); add(lsp, 0, 16);
        if (frameworkConnected && frameworkSnapshot.scopeKnown() && !scoped)
            action(body, "申请 QQ 作用域", this::requestScope, true);

        LinearLayout versions = card(); versions.addView(text("版本与设备", 16, TEXT, true)); spacer(versions, 8);
        info(versions, "Handoff", "v" + moduleName + (moduleCode > 0 ? " (" + moduleCode + ")" : ""));
        info(versions, "QQ", qqName + (qqCode > 0 ? " (" + qqCode + ")" : ""));
        info(versions, "Android", Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT);
        info(versions, "设备型号", Build.MODEL);
        info(versions, "哔哩哔哩", biliInstalled ? "已安装国内版" : "未发现国内版"); add(versions, 0, 12);
        add(text("抖音／快手交接为可选试验：默认关闭，可在设置中独立开启；是否打开对应作品需手动确认。", 12, MUTED, false), 0, 12);
        action(body, "查看入口与交接诊断", () -> nav.setSelectedItemId(R.id.nav_diagnostics), false);
        add(text("激活只表示框架报告当前模块已在 QQ 主进程加载，不等于视频交接已经验证通过。", 12, MUTED, false), 0, 8);
    }
    private void overview() {
        heading("入口与交接诊断", "区分挂载、入口信号和实际交接，不将激活状态作为视频回归结果。");
        var last = latestOutcome();
        String headline = "等待真实点击", description = "打开 QQ 点击支持的卡片，然后返回这里刷新。";
        int accent = CYAN;
        if (qqCode == 0) { headline = "尚未发现 QQ"; description = "安装国内版 QQ 后，再配置模块作用域。"; accent = AMBER; }
        else if (!frameworkConnected) { headline = "框架未连接"; description = "无法确认模块是否启用；请检查兼容的 LSPosed 框架。未连接不等于设备没有 root。"; accent = AMBER; }
        else if (!frameworkSnapshot.scopeKnown()) { headline = "QQ 作用域未知"; description = "框架已连接，但作用域不可读；请到框架管理器检查。"; accent = AMBER; }
        else if (!scoped) { headline = "需要 QQ 作用域"; description = "框架已连接，但尚未授权模块在 QQ 中运行。"; accent = AMBER; }
        else if (!current()) { headline = "等待本版本上报"; description = "强停重开 QQ；旧模块或旧 QQ 环境的记录不用于当前判断。"; }
        else if (last != null) {
            switch (last.event().kind()) {
                case ACCEPTED -> { headline = "已记录视频交接"; description = "外跳请求已被接受；不代表用户已确认或视频已加载。"; }
                case LAUNCH_FAILED -> { headline = "外跳请求未成功";
                    description = last.event().entry() == HealthEvent.Entry.KUAISHOU ? last.event().reason().title
                            : "已保留 QQ 原流程。请检查国内版 B 站是否可用。"; accent = PINK; }
                case PASSED -> { headline = last.event().reason() == HealthEvent.Reason.KUAISHOU_CANCELLED
                            ? "已取消短链解析" : "本次保留 QQ 原流程";
                    description = last.event().reason().title; accent = AMBER; }
                case ENTERED -> { headline = "入口已进入，待确认结果"; description = "入口进入本身不算交接成功。"; }
                default -> { }
            }
        }
        LinearLayout hero = card();
        hero.addView(text("●  " + (current() ? "本机诊断记录" : "待验证"), 11, accent, true));
        spacer(hero, 14); hero.addView(text(headline, 22, TEXT, true)); spacer(hero, 8);
        hero.addView(text(description, 13, MUTED, false)); spacer(hero, 18);
        hero.addView(text("最近上报  " + time(health.optLong("at")) + "   ·   v" + moduleName, 11, MUTED, false)); add(hero, 0, 14);
        LinearLayout env = card(); env.addView(text("环境快照", 16, TEXT, true)); spacer(env, 10);
        info(env, "QQ", qqName + (qqCode > 0 ? " / " + qqCode : ""));
        info(env, "Android", Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT);
        info(env, "框架", frameworkConnected ? framework : "未连接 · 状态未知"); info(env, "作用域", scope);
        info(env, "B 站", biliInstalled ? "已安装国内版" : "未发现国内版"); add(env, 0, 14);
        add(text("抖音国内版：" + (douyinInstalled ? "已安装" : "未发现") + " · 短链由抖音处理，不代表已解析或播放", 12, MUTED, false), 0, 12);
        add(text("快手国内版：" + (kuaishouInstalled ? "已安装" : "未发现") + " · 支持作品长链及 v.kuaishou.com 短链，不代表当前设备交接已验证", 12, MUTED, false), 0, 12);
        if (frameworkConnected && frameworkSnapshot.scopeKnown() && !scoped) action(body, "申请 QQ 作用域", this::requestScope, true);
        LinearLayout entries = card(); entries.addView(text("入口状态", 16, TEXT, true)); spacer(entries, 6);
        for (var entry : HealthEvent.Entry.values()) if (entry != HealthEvent.Entry.SYSTEM) {
            boolean installed = current() && evidence.stream().anyMatch(e -> e.event().entry() == entry && e.event().kind() == HealthEvent.Kind.INSTALLED);
            long accepted = current() ? evidence.stream().filter(e -> e.event().entry() == entry && e.event().kind() == HealthEvent.Kind.ACCEPTED).count() : 0;
            boolean failed = current() && evidence.stream().anyMatch(e -> e.event().entry() == entry && e.event().kind() == HealthEvent.Kind.INSTALL_FAILED);
            info(entries, entry.title, accepted > 0 ? "有交接记录 · " + accepted : installed ? "已挂载 · 未验证" : failed ? "挂载失败" : "尚无记录");
        }
        add(entries, 0, 14);
        LinearLayout events = card(); events.addView(text("最近信号", 16, TEXT, true)); spacer(events, 8);
        if (evidence.isEmpty()) events.addView(text("暂无上报。可能是 QQ 尚未重启、作用域未启用，或报告通道不可用。", 13, MUTED, false));
        else for (int i = evidence.size() - 1; i >= Math.max(0, evidence.size() - 7); i--) {
            var e = evidence.get(i); String detail = e.event().reason() == HealthEvent.Reason.NONE ? kind(e.event().kind()) : e.event().reason().title;
            TextView line = text(time(e.at()) + "  " + e.event().entry().title + "\n" + detail, 12, e.event().kind() == HealthEvent.Kind.ACCEPTED ? CYAN : MUTED, false);
            line.setPadding(0, dp(style == UiStyle.MATERIAL ? 7 : 12), 0, dp(style == UiStyle.MATERIAL ? 7 : 12)); events.addView(line);
        }
        add(events, 0, 12); action(body, "导出脱敏诊断", this::share, false);
        add(text("离线保存 · 不读取聊天／链接／视频 ID · 不自动上传\n导出前仍建议核对设备型号、软件版本等环境信息。", 11, MUTED, false), 0, 10);
        action(body, "清除本机诊断记录", () -> new MaterialAlertDialogBuilder(this).setTitle("清除诊断记录？")
                .setMessage("不清理 QQ 数据，也不会清除回归矩阵。QQ 后续事件仍可继续上报。")
                .setNegativeButton("取消", null).setPositiveButton("清除", (d, w) -> mutate(() -> HealthStore.clear(this))).show(), false);
    }
    private void matrix() {
        heading("回归矩阵", "每轮测试绑定 QQ、Android、框架与模块版本。没有记录，就是未验证。");
        LinearLayout intro = card(); intro.addView(text("当前测试环境", 16, TEXT, true)); spacer(intro, 8);
        intro.addView(text("QQ " + qqName + "  ·  Android " + Build.VERSION.RELEASE + "\n" + Build.MODEL + "  ·  " + (frameworkConnected ? framework : "框架未知"), 13, MUTED, false));
        spacer(intro, 14); action(intro, "开始新一轮回归", () -> {
            if (qqCode == 0) { toast("请先安装 QQ"); return; }
            new MaterialAlertDialogBuilder(this).setTitle("开始新一轮？").setMessage("保留历史记录，新的四项测试初始均为未验证。正向通过必须有本轮实际交接信号，并由你确认结果。")
                    .setNegativeButton("取消", null).setPositiveButton("开始", (d, w) -> {
                        var env = environment(); mutate(() -> RegressionStore.start(this, env));
                    }).show();
        }, true); add(intro, 0, 16);
        JSONObject run = runs.optJSONObject(0);
        if (run != null) {
            add(text("当前轮次  " + date(run.optLong("started")), 12, CYAN, true), 0, 10);
            boolean same = environment().key().equals(run.optString("environment"));
            if (!same) add(text("环境已变化：本轮仅作为历史保留，请开始新一轮。", 12, AMBER, false), 0, 12);
            JSONObject results = run.optJSONObject("results");
            for (var test : HealthPolicy.Case.values()) {
                LinearLayout item = card(); String result = results == null ? "" : results.optString(test.name());
                info(item, test.title, resultLabel(result)); spacer(item, 7); item.addView(text(test.instruction, 12, MUTED, false));
                spacer(item, 10); item.addView(text(test.needsEvidence ? "通过需：本轮模块记录 + 手动确认" : "负向用例：手动确认，不自动判定", 11, test.needsEvidence ? CYAN : AMBER, false));
                spacer(item, 12); LinearLayout controls = row();
                small(controls, "通过", () -> mark(test, "PASS"), same); small(controls, "未通过", () -> mark(test, "FAIL"), same); small(controls, "跳过", () -> mark(test, "SKIP"), same);
                item.addView(controls); add(item, 0, 12);
            }
            action(body, "打开 QQ，执行测试", this::openQq, true);
        } else add(text("还没有测试轮次。这里不会预填开发设备的成功结果。", 13, MUTED, false), 0, 18);
        add(text("环境历史  /  最多保留 20 轮", 16, TEXT, true), 16, 12);
        for (int i = 0; i < Math.min(20, runs.length()); i++) {
            JSONObject old = runs.optJSONObject(i); if (old == null) continue;
            LinearLayout history = card(); JSONObject results = old.optJSONObject("results"); int pass = 0, fail = 0;
            if (results != null) for (var test : HealthPolicy.Case.values()) {
                if ("PASS".equals(results.optString(test.name()))) pass++;
                if ("FAIL".equals(results.optString(test.name()))) fail++;
            }
            history.addView(text(date(old.optLong("started")), 13, TEXT, true)); spacer(history, 6);
            history.addView(text("QQ " + old.optLong("qq") + " / API " + old.optInt("android") + " / " + old.optString("model")
                    + "\n" + old.optString("framework") + " / 模块 " + old.optLong("module")
                    + "\n通过 " + pass + " · 未通过 " + fail + " · 其余未验证或跳过", 12, MUTED, false)); add(history, 0, 10);
        }
        action(body, "导出矩阵与诊断", this::share, false);
        action(body, "清除回归矩阵", () -> new MaterialAlertDialogBuilder(this).setTitle("清除全部回归轮次？")
                .setNegativeButton("取消", null).setPositiveButton("清除", (d,w) -> mutate(() -> RegressionStore.clear(this))).show(), false);
    }
    private void adapters() {
        heading("设置", "外观与适配 · 仅保存在本机");
        LinearLayout appearance = card();
        appearance.addView(text("个性化", 16, TEXT, true)); spacer(appearance, 8);
        LinearLayout appearanceButton = row(); appearanceButton.setId(R.id.appearance_style);
        appearanceButton.setGravity(Gravity.CENTER_VERTICAL); appearanceButton.setMinimumHeight(dp(64));
        appearanceButton.setPadding(0, dp(8), 0, dp(8)); appearanceButton.setFocusable(true);
        appearanceButton.setContentDescription("界面风格，" + style.label + "，点按选择");
        appearanceButton.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View view, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(view, info); info.setClassName("android.widget.Button");
            }
        });
        LinearLayout styleLabel = vertical(); styleLabel.addView(text("界面风格", 16, TEXT, false)); spacer(styleLabel, 4);
        styleLabel.addView(text(style.label, 13, MUTED, false));
        appearanceButton.addView(styleLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text("›", 24, MUTED, false); arrow.setPadding(dp(12), 0, 0, 0);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); appearanceButton.addView(arrow);
        styleLabel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        appearanceButton.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf((CYAN & 0xFFFFFF) | 0x18000000), null,
                new android.graphics.drawable.ColorDrawable(-1)));
        appearanceButton.setOnClickListener(v -> chooseStyle()); appearance.addView(appearanceButton);
        spacer(appearance, 8); appearance.addView(text("两种风格均跟随系统浅色／深色。切换不修改模块开关，不需要重启 QQ。", 12, MUTED, false));
        add(appearance, 0, 24);
        add(text("适配管理", 18, TEXT, true), 0, 8);
        add(text("内置版本档案与严格签名探测，不下载执行远程代码。", 13, MUTED, false), 0, 16);
        var profile = QqAdapters.resolve(qqCode);
        LinearLayout selected = card(); selected.addView(text("当前匹配策略", 11, CYAN, true)); spacer(selected, 10);
        selected.addView(text(profile.title(), 20, TEXT, true)); spacer(selected, 8); selected.addView(text(profile.evidence(), 12, MUTED, false));
        spacer(selected, 12); selected.addView(text("实际挂载和交接结果请看「诊断」，版本命中不代表当前设备可用。", 12, AMBER, false)); add(selected, 0, 16);
        LinearLayout options = card(); options.addView(text("入口开关", 16, TEXT, true)); spacer(options, 6);
        options.addView(text(settingsReadable ? "通过框架共享设置。修改后强停重开 QQ，确保当前进程应用设置。"
                : frameworkConnected ? "框架已连接，但设置读取失败；开关只读，当前值未知。" : "框架未连接，开关只读；不会伪装成已修改。", 12, MUTED, false));
        toggle(options, "交接总开关", "enabled");
        for (var entry : HealthEvent.Entry.values()) if (entry != HealthEvent.Entry.SYSTEM)
            toggle(options, entry.title, entry.key, entry.defaultEnabled());
        spacer(options, 8); options.addView(text("抖音／快手试验默认关闭，开关相互独立。不猜作品 ID、不降级到首页。抖音短链交由抖音处理；快手支持作品长链及 v.kuaishou.com 短链，短链在模块后台联网解析，失败返回 QQ 浏览器，可按返回键取消。请求被接受不代表已打开作品。", 12, MUTED, false));
        add(options, 0, 16);
        for (var item : QqAdapters.profiles()) {
            LinearLayout card = card(); info(card, item.title(), item.observed() ? "有开发记录" : "未完整验证"); spacer(card, 8);
            card.addView(text(item.evidence(), 12, MUTED, false)); add(card, 0, 12);
        }
        LinearLayout contract = card(); contract.addView(text("当前契约", 16, TEXT, true)); spacer(contract, 10);
        contract.addView(text("网页派发\n" + profile.contract().browserDispatch() + "\n\n小程序 SDK\n" + profile.contract().miniSdk()
                + "\n\n小程序服务\n" + profile.contract().miniService()
                + "\n\n小程序模型\n" + profile.contract().miniModel()
                + "\n\nSDK／服务：精确四参数 void 签名\n容器：显式路由、无回调、已适配视频\n启动时按实际 ClassLoader 补挂", 11, MUTED, false)); add(contract, 0, 12);
        action(body, "导出当前适配报告", this::share, false);
    }
    private void chooseStyle() {
        if (styleSaving) return;
        UiStyle[] styles = UiStyle.values();
        new MaterialAlertDialogBuilder(this).setTitle("界面风格")
                .setSingleChoiceItems(new String[]{styles[0].label, styles[1].label}, style.ordinal(), (dialog, index) -> {
                    dialog.dismiss(); changeStyle(styles[index]);
                }).setNegativeButton("取消", null).show();
    }
    private void changeStyle(UiStyle next) {
        if (next == style || styleSaving || isDestroyed() || isFinishing()) return;
        styleSaving = true;
        worker.execute(() -> {
            boolean saved = styleStore.write(next);
            handler.post(() -> {
                if (isDestroyed() || isFinishing()) return;
                styleSaving = false;
                if (saved) recreate(); else toast("外观保存失败，未切换风格");
            });
        });
    }
    private void statistic(LinearLayout parent, String value, String label) {
        LinearLayout content = vertical(); content.setMinimumHeight(dp(88)); content.setPadding(dp(18), dp(14), dp(18), dp(14));
        content.addView(text(value, 26, style == UiStyle.MATERIAL ? CYAN : TEXT, true));
        content.addView(text(label, 12, MUTED, false));
        MaterialCardView card = new MaterialCardView(this); card.setCardBackgroundColor(PANEL);
        card.setRadius(dp(style == UiStyle.MATERIAL ? 22 : 20)); card.setStrokeWidth(0); card.setCardElevation(0); card.addView(content);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        if (parent.getChildCount() == 0) lp.setMarginEnd(dp(12)); parent.addView(card, lp);
    }
    private void toggle(LinearLayout parent, String title, String key) {
        toggle(parent, title, key, true);
    }
    private void toggle(LinearLayout parent, String title, String key, boolean defaultValue) {
        MaterialSwitch control = new MaterialSwitch(this); control.setText(title); control.setTextColor(TEXT); control.setTextSize(14);
        control.setPadding(0, dp(10), 0, dp(10)); control.setMinHeight(dp(48));
        control.setChecked(settings.getOrDefault(key, defaultValue)); control.setEnabled(settingsReadable);
        control.setOnCheckedChangeListener((button, checked) -> {
            control.setEnabled(false);
            worker.execute(() -> {
                boolean saved;
                try { saved = FrameworkConnection.service.getRemotePreferences("handoff_settings").edit().putBoolean(key, checked).commit(); }
                catch (Throwable ignored) { saved = false; }
                boolean success = saved;
                handler.post(() -> { if (!isDestroyed()) { toast(success ? "已保存，请强停重开 QQ" : "保存失败，未确认生效"); refresh(); } });
            });
        }); parent.addView(control);
    }
    private void requestScope() {
        XposedService service = FrameworkConnection.service; if (service == null) { toast("框架未连接"); return; }
        try { service.requestScope(List.of("com.tencent.mobileqq"), new XposedService.OnScopeEventListener() {
            @Override public void onScopeRequestApproved(List<String> approved) { handler.post(() -> { toast("作用域已更新，请重开 QQ"); refresh(); }); }
            @Override public void onScopeRequestFailed(String reason) { handler.post(() -> toast("作用域请求未完成，请到框架管理器检查")); }
        }); } catch (Throwable ignored) { toast("无法申请，请到 LSPosed 手动勾选 QQ"); }
    }
    private void mark(HealthPolicy.Case test, String result) {
        if ("PASS".equals(result)) new MaterialAlertDialogBuilder(this).setTitle("确认「" + test.title + "」通过？")
                .setMessage(test.needsEvidence ? "请确认实际进入" + (test == HealthPolicy.Case.DOUYIN ? "抖音对应作品"
                        : test == HealthPolicy.Case.KUAISHOU ? "快手对应作品" : " B 站") + "且未并行打开 QQ 内容。模块交接信号不能代替你的页面确认。" : "这是一条手动确认记录，不代表模块自动验证了负向结果。")
                .setNegativeButton("取消", null).setPositiveButton("确认", (d,w) -> saveResult(test, result)).show();
        else saveResult(test, result);
    }
    private void saveResult(HealthPolicy.Case test, String result) {
        var env = environment();
        mutate(() -> RegressionStore.result(this, env, test, result));
    }
    private void mutate(Runnable action) {
        if (isDestroyed() || isFinishing()) return;
        try { worker.execute(() -> {
            boolean saved;
            try { action.run(); saved = true; } catch (RuntimeException ignored) { saved = false; }
            boolean success = saved;
            handler.post(() -> {
                if (isDestroyed() || isFinishing()) return;
                if (!success) new MaterialAlertDialogBuilder(this).setTitle("未保存")
                        .setMessage("存储不可用、测试环境已变化，或缺少本轮匹配交接证据。请检查后重试；正向通过需要实际交接记录。")
                        .setPositiveButton("知道了", null).show();
                refresh();
            });
        }); } catch (RejectedExecutionException ignored) { }
    }
    private void share() {
        try {
            JSONObject report = new JSONObject().put("schema", 1).put("moduleVersion", moduleName).put("environment", environment().key())
                    .put("scope", scope).put("events", health.optJSONArray("events") == null ? new JSONArray() : health.optJSONArray("events"))
                    .put("runs", runs).put("adapter", QqAdapters.resolve(qqCode).id())
                    .put("notice", "交接记录不等于视频加载成功；负向结果为手动确认；不包含聊天、链接、视频ID或设备序列号。");
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Handoff 脱敏兼容性报告")
                    .putExtra(Intent.EXTRA_TEXT, report.toString(2)); startActivity(Intent.createChooser(share, "导出前请核对环境信息"));
        } catch (Exception ignored) { toast("暂时无法分享报告"); }
    }
    private HealthPolicy.Environment environment() {
        return new HealthPolicy.Environment(moduleCode, qqCode, Build.VERSION.SDK_INT, Build.MODEL, framework);
    }
    private boolean current() {
        return frameworkConnected && moduleCode > 0 && qqCode > 0 && health.optLong("module") == moduleCode
                && health.optLong("qq") == qqCode && framework.equals(health.optString("framework"));
    }
    private HealthPolicy.Evidence latestOutcome() {
        if (!current()) return null;
        for (int i = evidence.size() - 1; i >= 0; i--) {
            var e = evidence.get(i);
            if (e.event().kind() == HealthEvent.Kind.ACCEPTED || e.event().kind() == HealthEvent.Kind.LAUNCH_FAILED
                    || e.event().kind() == HealthEvent.Kind.PASSED || e.event().kind() == HealthEvent.Kind.ENTERED) return e;
        }
        return null;
    }
    private PackageInfo pkg(String name) { try { return getPackageManager().getPackageInfo(name, 0); } catch (Exception ignored) { return null; } }
    private long code(PackageInfo info) { return info == null ? 0 : Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; }
    private void openQq() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.tencent.mobileqq");
            if (intent == null) toast("无法启动 QQ"); else startActivity(intent);
        } catch (RuntimeException ignored) { toast("QQ 启动失败，请手动打开"); }
    }
    private String resultLabel(String result) { return switch(result) { case "PASS" -> "已确认通过"; case "FAIL" -> "未通过"; case "SKIP" -> "已跳过"; default -> "未验证"; }; }
    private String kind(HealthEvent.Kind kind) { return switch(kind) {
        case SESSION -> "QQ 主进程开始上报"; case LOADER_BOUND -> "已按运行时加载器补挂";
        case INSTALLED -> "已挂载，尚不代表交接成功"; case INSTALL_FAILED -> "目标签名挂载失败";
        case ENTERED -> "入口已进入"; case PASSED -> "保留 QQ 原流程";
        case ACCEPTED -> "外跳请求已接受"; case LAUNCH_FAILED -> "外跳请求失败，保留原流程";
    }; }
    private String time(long at) { return at <= 0 ? "暂无" : new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date(at)); }
    private String date(long at) { return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(at)); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new MaterialTextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("kern"); t.setLineSpacing(dp(3), 1); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private LinearLayout card() { return card(PANEL); }
    private LinearLayout card(int color) { LinearLayout c = vertical(); c.setPadding(dp(20), dp(20), dp(20), dp(20)); c.setTag(color); return c; }
    private void add(View view, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(top), 0, dp(bottom));
        if (view.getTag() instanceof Integer color) {
            MaterialCardView wrapper = new MaterialCardView(this); wrapper.setCardBackgroundColor(color);
            wrapper.setCardElevation(0); wrapper.setRadius(dp(style == UiStyle.MATERIAL ? 28 : 20)); wrapper.setStrokeWidth(0);
            wrapper.addView(view); body.addView(wrapper, p);
        } else body.addView(view, p);
    }
    private void spacer(LinearLayout parent, int height) { View s = new View(this); parent.addView(s, new LinearLayout.LayoutParams(1, dp(height))); }
    private void info(LinearLayout parent, String name, String value) {
        LinearLayout line = row(); line.setGravity(Gravity.CENTER_VERTICAL); line.setPadding(0, dp(style == UiStyle.MATERIAL ? 7 : 12), 0, dp(style == UiStyle.MATERIAL ? 7 : 12));
        line.addView(text(name, 14, style == UiStyle.MATERIAL ? MUTED : TEXT, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = text(value, 14, style == UiStyle.MATERIAL ? TEXT : MUTED, style == UiStyle.MATERIAL); v.setGravity(Gravity.END); line.addView(v, new LinearLayout.LayoutParams(0, -2, 1.5f)); parent.addView(line);
    }
    private void action(LinearLayout parent, String title, Runnable callback, boolean primary) {
        MaterialButton b = button(primary); b.setText(title); b.setTextSize(14); b.setAllCaps(false); b.setMinHeight(dp(48));
        b.setPadding(dp(12), dp(10), dp(12), dp(10)); b.setOnClickListener(v -> callback.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(6), 0, dp(10)); parent.addView(b, p);
    }
    private void small(LinearLayout parent, String title, Runnable callback, boolean enabled) {
        MaterialButton b = button(false); b.setText(title); b.setTextSize(12); b.setAllCaps(false); b.setMinHeight(dp(48));
        b.setMinWidth(0); b.setPadding(dp(4), dp(8), dp(4), dp(8)); b.setEnabled(enabled); b.setOnClickListener(v -> callback.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(dp(3), 0, dp(3), 0); parent.addView(b, lp);
    }
    private MaterialButton button(boolean primary) {
        MaterialButton result = primary ? new MaterialButton(this) : (MaterialButton) getLayoutInflater().inflate(R.layout.button_outlined, body, false);
        result.setCornerRadius(dp(style == UiStyle.MATERIAL ? 28 : 14)); return result;
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
