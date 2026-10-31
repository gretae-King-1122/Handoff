package com.handoff.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class RefreshGateTest {
    @Test public void unchangedPollingDoesNotRebuildViews() {
        RefreshGate gate = new RefreshGate();
        assertTrue(gate.begin(false)); assertTrue(gate.finish("same"));
        assertTrue(gate.begin(false)); assertFalse(gate.finish("same"));
    }
    @Test public void explicitRefreshDuringPollSurvivesUnchangedSnapshot() {
        RefreshGate gate = new RefreshGate();
        gate.begin(false); gate.finish("same");
        assertTrue(gate.begin(false));
        assertFalse(gate.begin(true));
        assertFalse(gate.begin(false));
        assertTrue(gate.finish("same"));
        assertTrue(gate.begin(false)); assertFalse(gate.finish("same"));
    }
    @Test public void changedDataAlwaysRefreshesAndOnlyOneReadRuns() {
        RefreshGate gate = new RefreshGate();
        assertTrue(gate.begin(false)); assertFalse(gate.begin(false));
        assertTrue(gate.finish("first"));
        assertTrue(gate.begin(false)); assertTrue(gate.finish("second"));
    }
    @Test public void cancelledReadDoesNotPermanentlyBlockRefresh() {
        RefreshGate gate = new RefreshGate();
        gate.begin(true); gate.cancel();
        assertTrue(gate.begin(false)); assertTrue(gate.finish("first"));
    }
}
