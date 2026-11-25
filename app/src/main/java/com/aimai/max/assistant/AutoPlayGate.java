package com.aimai.max.assistant;

/** Explains why Auto Play is not sending a shot without weakening its safety checks. */
final class AutoPlayGate {
    private AutoPlayGate() {}

    static String blockedReason(boolean accessibilityConnected,
                                boolean freshAnalysis,
                                boolean waitingForSettle,
                                boolean boardSteady,
                                boolean coverHold,
                                boolean wantSlide,
                                boolean planAtCurrentPosition,
                                boolean anyPlanFound,
                                boolean cooldownElapsed,
                                boolean aimUsable) {
        if (!accessibilityConnected) return "Accessibility service is disconnected";
        if (!freshAnalysis) return "Waiting for a fresh board scan";
        if (waitingForSettle) return "Waiting for the last shot to settle";
        if (!boardSteady) return "Waiting for the board to stop moving";
        if (coverHold) return "Waiting for a safe queen-cover shot";
        if (wantSlide) return "Positioning striker; waiting for a fresh scan";
        if (!planAtCurrentPosition) {
            return anyPlanFound ? "Checking striker position against the plan"
                    : "No safe shot found; scanning for another route";
        }
        if (!cooldownElapsed) return "Shot cooldown";
        if (!aimUsable) return "Aim point is invalid or too close to the striker";
        return null;
    }
}
