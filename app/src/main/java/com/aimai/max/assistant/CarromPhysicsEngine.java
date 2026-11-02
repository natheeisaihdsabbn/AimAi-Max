package com.aimai.max.assistant;

public final class CarromPhysicsEngine {

    private CarromPhysicsEngine() {}

    public static class CollisionPrediction {
        public final float targetDirectionX;
        public final float targetDirectionY;
        public final float strikerDirectionX;
        public final float strikerDirectionY;
        public final float contactX;
        public final float contactY;
        public final boolean valid;

        CollisionPrediction(float tx, float ty, float sx, float sy,
                            float cx, float cy, boolean valid) {
            targetDirectionX = tx;
            targetDirectionY = ty;
            strikerDirectionX = sx;
            strikerDirectionY = sy;
            contactX = cx;
            contactY = cy;
            this.valid = valid;
        }
    }

    public static CollisionPrediction predictCollision(
            float strikerX, float strikerY,
            float targetX, float targetY) {

        float dx = targetX - strikerX;
        float dy = targetY - strikerY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance < 0.0001f) {
            return new CollisionPrediction(0f, 0f, 0f, 0f, 0f, 0f, false);
        }

        float nx = dx / distance;
        float ny = dy / distance;

        // Idealized head-on collision direction.
        float targetXDirection = nx;
        float targetYDirection = ny;

        // Equal-mass, stationary-target approximation:
        // striker stops after a perfectly head-on collision.
        float strikerXDirection = 0f;
        float strikerYDirection = 0f;

        float[] contact = calculateContactPoint(
                targetX, targetY, 0f, 0f, nx, ny);

        return new CollisionPrediction(
                targetXDirection, targetYDirection,
                strikerXDirection, strikerYDirection,
                contact[0], contact[1], true);
    }

    public static float[] calculateContactPoint(
            float targetX, float targetY,
            float targetRadius,
            float strikerRadius,
            float directionX, float directionY) {

        float length = (float) Math.sqrt(
                directionX * directionX + directionY * directionY);

        if (length < 0.0001f) return null;

        float nx = directionX / length;
        float ny = directionY / length;
        float contactDistance = Math.max(0f, targetRadius)
                + Math.max(0f, strikerRadius);

        return new float[] {
                targetX - nx * contactDistance,
                targetY - ny * contactDistance
        };
    }

    public static float[] calculateCollisionContact(
            float strikerX, float strikerY,
            float targetX, float targetY,
            float strikerRadius, float targetRadius) {

        float dx = targetX - strikerX;
        float dy = targetY - strikerY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);

        if (length < 0.0001f) return null;

        return calculateContactPoint(
                targetX, targetY,
                targetRadius, strikerRadius,
                dx / length, dy / length);
    }

    public static float[] calculateCollisionContactXY(
            float strikerX, float strikerY,
            float targetX, float targetY,
            float strikerRadiusX, float strikerRadiusY,
            float targetRadiusX, float targetRadiusY) {

        float dx = targetX - strikerX;
        float dy = targetY - strikerY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);

        if (length < 0.0001f) return null;

        float nx = dx / length;
        float ny = dy / length;

        float strikerSupport = (float) Math.sqrt(
                strikerRadiusX * strikerRadiusX * nx * nx
                + strikerRadiusY * strikerRadiusY * ny * ny);

        float targetSupport = (float) Math.sqrt(
                targetRadiusX * targetRadiusX * nx * nx
                + targetRadiusY * targetRadiusY * ny * ny);

        float contactDistance = strikerSupport + targetSupport;
        float contactX = targetX - nx * contactDistance;
        float contactY = targetY - ny * contactDistance;

        return new float[] {contactX, contactY};
    }

    public static float distance(float x1, float y1,
                                 float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public static float[] calculatePostCollisionDirection(
            float strikerX, float strikerY,
            float targetX, float targetY,
            float targetRadius) {
        float dx = targetX - strikerX;
        float dy = targetY - strikerY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.0001f) return new float[]{0f, 0f};
        float nx = dx / length;
        float ny = dy / length;
        return new float[]{nx, ny, targetX + nx * targetRadius,
                targetY + ny * targetRadius};
    }

}
