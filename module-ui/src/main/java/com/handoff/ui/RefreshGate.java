package com.handoff.ui;

/** Main-thread refresh coalescing. Explicit refreshes must survive an in-flight background read. */
final class RefreshGate {
    private boolean running, forced;
    private String snapshot;

    boolean begin(boolean force) {
        forced |= force;
        if (running) return false;
        running = true;
        return true;
    }

    boolean finish(String next) {
        boolean changed = forced || !next.equals(snapshot);
        snapshot = next;
        running = false;
        forced = false;
        return changed;
    }

    void cancel() { running = false; forced = false; }
}
