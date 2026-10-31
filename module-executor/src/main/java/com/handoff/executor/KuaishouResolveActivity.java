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
import com.handoff.api.links.ParsedLink;
import com.handoff.rules.links.KuaishouLinkParser;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Only short links visit this owned, cancellable screen. QQ's UI thread never waits for HTTP. */
public class KuaishouResolveActivity extends Activity {
    static final String FALLBACK = "qq_fallback";
    static final String RESULT = "resolution_result";
    private PendingIntent fallback;
    private ResultReceiver receiver;
    private Job job;
    private boolean terminal;
    private boolean resumed;
    private boolean authorized;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            fallback = getIntent().getParcelableExtra(FALLBACK);
            receiver = getIntent().getParcelableExtra(RESULT);
            ParsedLink link = new KuaishouLinkParser().parseUrl(getIntent().getDataString());
            if (link == null || link.kind() != ParsedLink.Kind.SHORT_LINK || fallback == null
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
            label.setText("正在解析快手链接…\n按返回键取消");
            label.setGravity(Gravity.CENTER);
            label.setPadding(24, 32, 24, 0);
            layout.addView(label);
            setContentView(layout);
            Object retained = getLastNonConfigurationInstance();
            job = retained instanceof Job ? (Job) retained : new Job(link, createResolver(), createWorker());
            job.owner = new WeakReference<>(this);
            job.start();
        } catch (RuntimeException ignored) {
            terminal = true;
            if (authorized) returnToQq();
            finish();
        }
    }

    KuaishouShortResolver createResolver() { return new KuaishouShortResolver(); }
    ExecutorService createWorker() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Handoff-Kuaishou");
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
        if (!isChangingConfigurations() && !terminal) cancelByUser();
        super.onDestroy();
    }
    @Override public void onBackPressed() { cancelByUser(); }

    private void cancelByUser() {
        if (terminal) return;
        terminal = true;
        if (job != null) job.cancel();
        if (fallback != null) fallback.cancel();
        report(KuaishouLauncher.CANCELLED);
        finish();
    }

    private void deliver() {
        if (terminal || !resumed || job == null || !job.completed) return;
        terminal = true;
        ParsedLink video = job.result;
        job.cancel();
        if (video != null && KuaishouLauncher.launch(this, video)) {
            fallback.cancel();
            report(KuaishouLauncher.STARTED);
        } else {
            returnToQq();
        }
        finish();
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
            report(KuaishouLauncher.FAILED);
        } catch (PendingIntent.CanceledException | RuntimeException ignored) {
            report(KuaishouLauncher.FALLBACK_FAILED);
            Toast.makeText(this, "未能打开链接，请返回 QQ 重试", Toast.LENGTH_LONG).show();
        } finally { fallback.cancel(); }
    }

    private void report(int code) {
        try { if (receiver != null) receiver.send(code, Bundle.EMPTY); } catch (RuntimeException ignored) { }
    }

    private static final class Job {
        final ParsedLink input;
        final KuaishouShortResolver resolver;
        final Handler main = new Handler(Looper.getMainLooper());
        final ExecutorService worker;
        WeakReference<KuaishouResolveActivity> owner = new WeakReference<>(null);
        boolean started, completed, cancelled;
        ParsedLink result;
        final Runnable timeout = () -> complete(null);

        Job(ParsedLink input, KuaishouShortResolver resolver, ExecutorService worker) {
            this.input = input; this.resolver = resolver; this.worker = worker;
        }
        void start() {
            if (started) return;
            started = true;
            main.postDelayed(timeout, 7000);
            worker.execute(() -> {
                ParsedLink resolved = resolver.resolve(input);
                main.post(() -> complete(resolved));
            });
        }
        void complete(ParsedLink resolved) {
            if (cancelled || completed) return;
            completed = true;
            result = resolved;
            main.removeCallbacks(timeout);
            // Also stop I/O on timeout while detached for a configuration change.
            worker.shutdownNow();
            resolver.cancel();
            KuaishouResolveActivity activity = owner.get();
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
