package com.handoff.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class ActivationStatusTest {
    private FrameworkSnapshot framework(boolean scopeKnown, boolean scoped, FrameworkSnapshot.RuntimeState runtime) {
        return new FrameworkSnapshot(true, "LSPosed", "2.2.1", 7268, 102, scopeKnown, scoped, runtime);
    }
    @Test public void initialReadIsNotReportedAsInactiveOrActive() {
        var status = ActivationStatus.resolve(false, FrameworkSnapshot.unavailable());
        assertFalse(status.active()); assertEquals("正在检查激活状态", status.title());
    }
    @Test public void disconnectedFrameworkDoesNotClaimNoRoot() {
        var status = ActivationStatus.resolve(true, FrameworkSnapshot.unavailable());
        assertFalse(status.active()); assertEquals("未确认激活", status.title());
        assertTrue(status.detail().contains("不等于没有 root"));
    }
    @Test public void connectionOrScopeAloneNeverProvesActivation() {
        var status = ActivationStatus.resolve(true, framework(false, false, FrameworkSnapshot.RuntimeState.CURRENT));
        assertFalse(status.active()); assertEquals("激活状态未知", status.title());
        status = ActivationStatus.resolve(true, framework(true, false, FrameworkSnapshot.RuntimeState.CURRENT));
        assertFalse(status.active()); assertEquals("QQ 作用域未授权", status.title());
    }
    @Test public void onlyCurrentRuntimeConfirmsActivation() {
        for (var runtime : FrameworkSnapshot.RuntimeState.values()) {
            var status = ActivationStatus.resolve(true, framework(true, true, runtime));
            assertEquals(runtime == FrameworkSnapshot.RuntimeState.CURRENT, status.active());
        }
    }
    @Test public void staleAndUnavailableRuntimeGiveDifferentInstructions() {
        assertEquals("需要重启 QQ", ActivationStatus.resolve(true, framework(true, true, FrameworkSnapshot.RuntimeState.OUTDATED)).title());
        assertEquals("等待 QQ 启动", ActivationStatus.resolve(true, framework(true, true, FrameworkSnapshot.RuntimeState.NOT_RUNNING)).title());
        assertEquals("激活状态未知", ActivationStatus.resolve(true, framework(true, true, FrameworkSnapshot.RuntimeState.UNKNOWN)).title());
    }
}
