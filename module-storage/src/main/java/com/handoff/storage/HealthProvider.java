package com.handoff.storage;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import com.handoff.api.health.HealthPolicy;

/** Cross-UID write-only bridge for QQ. It cannot read diagnostics, change settings, or clear data. */
public final class HealthProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        if (!"report".equals(method) || !HealthPolicy.mayReport(uid, Process.myUid(),
                getContext().getPackageManager().getPackagesForUid(uid))) throw new SecurityException("Unauthorized report");
        HealthStore.receive(getContext(), extras);
        Bundle response = new Bundle(); response.putBoolean("accepted", true); return response;
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new SecurityException("Write-only"); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new SecurityException("Use typed report"); }
    @Override public int delete(Uri u, String s, String[] a) { throw new SecurityException("Write-only"); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new SecurityException("Write-only"); }
}
