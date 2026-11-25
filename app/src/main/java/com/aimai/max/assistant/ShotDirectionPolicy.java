package com.aimai.max.assistant;

/** Maps the planner's striker-to-aim vector to the on-screen touch stroke. */
final class ShotDirectionPolicy {
    private ShotDirectionPolicy() {}

    /** The game follows the finger drag; only an explicitly requested pull-back reverses it. */
    static float strokeSign(boolean pullBack) {
        return pullBack ? -1f : 1f;
    }
}
