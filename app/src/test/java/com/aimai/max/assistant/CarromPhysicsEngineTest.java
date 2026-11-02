package com.aimai.max.assistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class CarromPhysicsEngineTest {

    @Test
    public void calculatesEllipticalCollisionContact() {
        float[] result = CarromPhysicsEngine.calculateCollisionContactXY(
                0.2f, 0.5f, 0.8f, 0.5f,
                0.05f, 0.03f, 0.04f, 0.02f
        );

        assertNotNull(result);
        assertEquals(0.71f, result[0], 0.00001f);
        assertEquals(0.5f, result[1], 0.00001f);
    }

    @Test
    public void returnsNullWhenPiecesHaveSamePosition() {
        float[] result = CarromPhysicsEngine.calculateCollisionContactXY(
                0.5f, 0.5f, 0.5f, 0.5f,
                0.05f, 0.03f, 0.04f, 0.02f
        );

        assertNull(result);
    }
}
