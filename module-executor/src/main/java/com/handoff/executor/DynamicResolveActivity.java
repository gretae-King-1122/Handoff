package com.handoff.executor;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.handoff.api.links.DynamicLinkPolicy;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owned discovery/chooser screen. QQ's UI thread never waits for package discovery or HTTP. */
public class DynamicResolveActivity extends Activity {
    static final String FALLBACK = "qq_fallback";
    static final String RESULT = "resolution_result";
    private PendingIntent fallback;
    private ResultReceiver receiver;
    private Job job;
    private boolean terminal;
    private boolean resumed;
    private boolean authorized;
    private boolean choosing;
    private android.app.AlertDialog chooser;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            fallback = getIntent().getParcelableExtra(FALLBACK);
            receiver = getIntent().getParcelableExtra(RESULT);
            String link = DynamicLinkPolicy.normalize(getIntent().getDataString());
            if (link == null || fallback == null || !"com.tencent.mobileqq".equals(getCallingPackage())
                    || !"com.tencent.mobileqq".equals(fallback.getCreatorPackage())
                    || (Build.VERSION.SDK_INT >= 31 && (!fallback.isImmutable() || !fallback.isActivity()))) {
                terminal = true;
                finish(); // Invalid exported requests must not cause network or arbitrary launches.
                return;
            }
            authorized = true;
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER);
            layout.addView(new ProgressBar(this));
            TextView label = new TextView(this);
            label.setText("正在寻找可接收链接的 App…\n按返回键取消");
            label.setGravity(Gravity.CENTER);
            label.setPadding(24, 32, 24, 0);
            layout.addView(label);
            setContentView(layout);
            Object retained = getLastNonConfigurationInstance();
            job = retained instanceof Job ? (Job) retained : new Job(link, getIntent().getBooleanExtra(DynamicLauncher.NETWORK, false), createResolver(), createWorker());
            job.owner = new WeakReference<>(this);
            job.start();
        } catch (RuntimeException ignored) {
            terminal = true;
            if (job != null) job.cancel();
            if (authorized) returnToQq();
            finish();
        }
    }

    DynamicLinkResolver createResolver() {
        android.content.Context application = getApplicationContext();
        return new DynamicLinkResolver(url -> DynamicAppResolver.find(application, url));
    }
    ExecutorService createWorker() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Handoff-Dynamic");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        deliver();
    }
    @Override protected void onPause() { resumed = false; super.onPause(); }
    @Override protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations() && !terminal) cancelByUser();
    }
    @Override public Object onRetainNonConfigurationInstance() { return job; }
    @Override protected void onDestroy() {
        if (job != null && job.owner.get() == this) job.owner.clear();
        if (chooser != null) { chooser.setOnCancelListener(null); chooser.dismiss(); }
        if (!isChangingConfigurations() && !terminal) cancelByUser();
        super.onDestroy();
    }
    @Override public void onBackPressed() { cancelByUser(); }

    private void cancelByUser() {
        if (terminal) return;
        terminal = true;
        if (job != null) job.cancel();
        if (fallback != null) fallback.cancel();
        report(DynamicLauncher.CANCELLED);
        finish();
    }

    private void deliver() {
        if (terminal || choosing || !resumed || job == null || !job.completed) return;
        DynamicLinkResolver.Result result = job.result;
        if (result == null || result.candidates().isEmpty()) { finishWith(null, null); return; }
        if (result.candidates().size() == 1 && result.candidates().get(0).domainApproved()) {
            finishWith(result.url(), result.candidates().get(0));
            return;
        }
        choosing = true;
        String[] labels = result.candidates().stream()
                .map(c -> c.label() + "\n" + c.component().getPackageName()).toArray(String[]::new);
        chooser = new android.app.AlertDialog.Builder(this).setTitle("选择接收链接的 App（未验证时需确认）")
                .setItems(labels, (dialog, index) -> finishWith(result.url(), result.candidates().get(index)))
                .setNegativeButton("仍用 QQ 打开", (dialog, which) -> finishWith(null, null))
                .setOnCancelListener(dialog -> cancelByUser()).show();
    }

    private void finishWith(String url, DynamicAppResolver.Candidate candidate) {
        if (terminal || !resumed) return;
        terminal = true;
        if (job != null) job.cancel();
        if (candidate != null && launchCandidate(url, candidate)) {
            fallback.cancel();
            report(DynamicLauncher.STARTED);
        } else { returnToQq(); }
        finish();
    }

    boolean launchCandidate(String url, DynamicAppResolver.Candidate candidate) {
        return DynamicAppResolver.launch(this, url, candidate);
    }

    private void returnToQq() {
        try {
            // Sends the original QQ browser request once, with its extras intact; no re-resolution loop.
            Bundle options = null;
            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions activityOptions = ActivityOptions.makeBasic();
                activityOptions.setPendingIntentBackgroundActivityStartMode(Build.VERSION.SDK_INT >= 36
                        ? ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                        : ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                options = activityOptions.toBundle();
            }
            fallback.send(this, 0, null, null, null, null, options);
            report(DynamicLauncher.FAILED);
        } catch (PendingIntent.CanceledException | RuntimeException ignored) {
            report(DynamicLauncher.FALLBACK_FAILED);
            Toast.makeText(this, "未能打开链接，请返回 QQ 重试", Toast.LENGTH_LONG).show();
        } finally { fallback.cancel(); }
    }

    private void report(int code) {
        try { if (receiver != null) receiver.send(code, Bundle.EMPTY); } catch (RuntimeException ignored) { }
    }

    private static final class Job {
        final String input;
        final boolean network;
        final DynamicLinkResolver resolver;
        final Handler main = new Handler(Looper.getMainLooper());
        final ExecutorService worker;
        WeakReference<DynamicResolveActivity> owner = new WeakReference<>(null);
        boolean started, completed, cancelled;
        DynamicLinkResolver.Result result;
        final Runnable timeout = () -> complete(null);

        Job(String input, boolean network, DynamicLinkResolver resolver, ExecutorService worker) {
            this.input = input; this.network = network; this.resolver = resolver; this.worker = worker;
        }
        void start() {
            if (started) return;
            started = true;
            main.postDelayed(timeout, 7000);
            worker.execute(() -> {
                DynamicLinkResolver.Result resolved = resolver.resolve(input, network);
                main.post(() -> complete(resolved));
            });
        }
        void complete(DynamicLinkResolver.Result resolved) {
            if (cancelled || completed) return;
            completed = true;
            result = resolved;
            main.removeCallbacks(timeout);
            // Also stop I/O on timeout while detached for a configuration change.
            worker.shutdownNow();
            resolver.cancel();
            DynamicResolveActivity activity = owner.get();
            if (activity != null) activity.deliver();
        }
        void cancel() {
            cancelled = true;
            main.removeCallbacks(timeout);
            resolver.cancel();
            worker.shutdownNow();
        }
    }
}
