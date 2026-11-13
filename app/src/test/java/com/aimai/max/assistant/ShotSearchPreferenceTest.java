package com.aimai.max.assistant;

import com.aimai.max.assistant.CarromShotPlanner.Shot;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class ShotSearchPreferenceTest {

    @Test
    public void directPotRemovesSuccessfulBankRoutes() {
        List<ShotSearch.Outcome> outcomes = new ArrayList<>();

        ShotSearch.Outcome direct = outcome(0, true);
        ShotSearch.Outcome coinBank = outcome(1, true);
        ShotSearch.Outcome strikerBank = outcome(2, true);
        outcomes.add(coinBank);
        outcomes.add(direct);
        outcomes.add(strikerBank);

        ShotSearch.preferDirectPotOutcomes(outcomes);

        assertEquals(1, outcomes.size());
        assertSame(direct, outcomes.get(0));
    }

    @Test
    public void bankRouteRemainsWhenNoDirectPotExists() {
        List<ShotSearch.Outcome> outcomes = new ArrayList<>();
        ShotSearch.Outcome bank = outcome(1, true);
        outcomes.add(bank);

        ShotSearch.preferDirectPotOutcomes(outcomes);

        assertEquals(1, outcomes.size());
        assertSame(bank, outcomes.get(0));
    }

    @Test
    public void directShotThatDoesNotPotDoesNotSuppressBankPot() {
        List<ShotSearch.Outcome> outcomes = new ArrayList<>();
        outcomes.add(outcome(0, false));
        ShotSearch.Outcome bank = outcome(2, true);
        outcomes.add(bank);

        ShotSearch.preferDirectPotOutcomes(outcomes);

        assertEquals(2, outcomes.size());
        assertSame(bank, outcomes.get(1));
    }

    private static ShotSearch.Outcome outcome(int kind, boolean potsSomething) {
        Shot shot = new Shot();
        shot.kind = kind;
        ShotSearch.Outcome outcome = new ShotSearch.Outcome();
        outcome.shot = shot;
        outcome.potsSomething = potsSomething;
        outcome.foulsStriker = false;
        return outcome;
    }

    @Test
    public void cutPowerUsesInverseCosineAndCapsExtremeAngles() {
        assertEquals(1f, ShotSearch.cutPowerCompensation(0f), 0.001f);
        assertEquals(1.1547f, ShotSearch.cutPowerCompensation(30f), 0.002f);
        assertEquals(2.0f, ShotSearch.cutPowerCompensation(60f), 0.002f);
        assertEquals(2.15f, ShotSearch.cutPowerCompensation(75f), 0.002f);
    }
}
