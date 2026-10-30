package com.handoff.hook;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HookPlanTest {
    @Test void normalMainInstallsBrowserAndMiniHandoffWithoutDiscovery() {
        assertEquals(List.of("BROWSER", "MINI_SDK", "MINI_HANDOFF", "MINI_CONTAINER"), HookPlan.steps("com.tencent.mobileqq", false)
                .stream().map(Enum::name).toList());
    }

    @Test void normalAuxiliaryProcessesDoNotLoadAnyProbeClasses() {
        for (String process : List.of("com.tencent.mobileqq:mini", "com.tencent.mobileqq:tool",
                "com.tencent.mobileqq:MSF")) {
            assertTrue(HookPlan.steps(process, false).isEmpty());
        }
    }

    @Test void diagnosticsPreservesDiscoveryButInstallsHandoffFirst() {
        List<HookPlan.Step> steps = HookPlan.steps("com.tencent.mobileqq", true);
        assertEquals(HookPlan.Step.BROWSER, steps.get(0));
        assertEquals(HookPlan.Step.values().length, steps.size());
        assertEquals(steps.size(), steps.stream().distinct().count());
        assertEquals(List.of(HookPlan.Step.CONTROLLER, HookPlan.Step.LAUNCHER, HookPlan.Step.MINI_RUNTIME),
                HookPlan.steps("com.tencent.mobileqq:mini", true));
    }

    @Test void unknownProcessesAreNeverTreatedAsQqMain() {
        for (boolean diagnostics : List.of(false, true)) {
            assertTrue(HookPlan.steps(null, diagnostics).isEmpty());
            assertTrue(HookPlan.steps("", diagnostics).isEmpty());
            assertTrue(HookPlan.steps("com.tencent.mobileqq.fake", diagnostics).isEmpty());
        }
    }
}
