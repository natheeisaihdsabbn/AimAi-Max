package com.aimai.max.assistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class AutoPlayGateTest {
    private String reason(boolean connected, boolean fresh, boolean settling,
                          boolean steady, boolean coverHold, boolean sliding,
                          boolean currentPlan, boolean anyPlan, boolean cooldown,
                          boolean aimUsable) {
        return AutoPlayGate.blockedReason(connected, fresh, settling, steady,
                coverHold, sliding, currentPlan, anyPlan, cooldown, aimUsable);
    }

    private String ready() {
        return reason(true, true, false, true, false, false,
                true, true, true, true);
    }

    @Test
    public void reportsDisconnectedAccessibilityBeforeOtherConditions() {
        assertEquals("Accessibility service is disconnected",
                reason(false, false, true, false, false, false, false, false, false, false));
    }

    @Test
    public void requiresFreshAnalysisAndSettledBoard() {
        assertEquals("Waiting for a fresh board scan",
                reason(true, false, false, true, false, false, true, true, true, true));
        assertEquals("Waiting for the last shot to settle",
                reason(true, true, true, true, false, false, true, true, true, true));
        assertEquals("Waiting for the board to stop moving",
                reason(true, true, false, false, false, false, true, true, true, true));
    }

    @Test
    public void explainsSliderAndMissingSafeShotWithoutTreatingThemAsFireable() {
        assertEquals("Positioning striker; waiting for a fresh scan",
                reason(true, true, false, true, false, true, false, true, true, true));
        assertEquals("No safe shot found; scanning for another route",
                reason(true, true, false, true, false, false, false, false, true, true));
        assertEquals("Checking striker position against the plan",
                reason(true, true, false, true, false, false, false, true, true, true));
    }

    @Test
    public void reportsCooldownAimAndCoverReasons() {
        assertEquals("Waiting for a safe queen-cover shot",
                reason(true, true, false, true, true, false, true, true, true, true));
        assertEquals("Shot cooldown",
                reason(true, true, false, true, false, false, true, true, false, true));
        assertEquals("Aim point is invalid or too close to the striker",
                reason(true, true, false, true, false, false, true, true, true, false));
    }

    @Test
    public void eligibleCurrentPlanHasNoBlockingReason() {
        assertNull(ready());
    }
}
