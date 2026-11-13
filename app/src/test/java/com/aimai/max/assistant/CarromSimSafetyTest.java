package com.aimai.max.assistant;

import com.aimai.max.assistant.CarromSim.Disc;
import com.aimai.max.assistant.CarromSim.Result;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

public class CarromSimSafetyTest {

    @Test
    public void strikerCannotEnterPocketSmallerThanItsBody() {
        List<Disc> discs = new ArrayList<>();
        Disc striker = new Disc();
        striker.x = 150f;
        striker.y = 150f;
        striker.r = 36f;
        striker.mass = 1.4f;
        striker.owner = CarromSim.STRIKER;
        discs.add(striker);

        Result result = CarromSim.run(discs, 0, -0.7071068f, -0.7071068f, 25f,
                0f, 0f, 1000f, 1000f,
                new float[]{40f}, new float[]{40f}, 32f, false);

        assertFalse("striker must not fall through a pocket smaller than its radius", result.strikerPotted);
    }

    @Test
    public void ownCoinCanStillEnterPocket() {
        List<Disc> discs = new ArrayList<>();
        Disc striker = new Disc();
        striker.x = 500f;
        striker.y = 900f;
        striker.r = 36f;
        striker.mass = 1.4f;
        striker.owner = CarromSim.STRIKER;
        discs.add(striker);

        Disc mine = new Disc();
        mine.x = 110f;
        mine.y = 110f;
        mine.r = 20f;
        mine.mass = 1f;
        mine.owner = CarromSim.MINE;
        discs.add(mine);

        Result result = CarromSim.run(discs, 0, 0f, -1f, 1f,
                0f, 0f, 1000f, 1000f,
                new float[]{40f}, new float[]{40f}, 32f, false);

        // Directly move the own coin with a second run by making it the striker body.
        List<Disc> coinRun = new ArrayList<>();
        Disc coin = CarromSim.copy(mine);
        coin.owner = CarromSim.MINE;
        coinRun.add(coin);
        Result coinResult = CarromSim.run(coinRun, 0, -0.7071068f, -0.7071068f, 20f,
                0f, 0f, 1000f, 1000f,
                new float[]{40f}, new float[]{40f}, 32f, false);

        assertEquals(1, coinResult.minePotted);
    }
}
