package com.aimai.max.assistant;

import com.aimai.max.assistant.CarromBoardAnalyzer.Analysis;
import com.aimai.max.assistant.CarromBoardAnalyzer.Piece;
import com.aimai.max.assistant.CarromBoardAnalyzer.PieceType;
import com.aimai.max.assistant.CarromBoardAnalyzer.Pocket;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class CarromShotPlannerTest {

    @Test
    public void neverPlansOpponentCoinsEvenWhenAnyCoinFlagIsSet() {
        Analysis a = new Analysis();
        a.board.left = 0f;
        a.board.top = 0f;
        a.board.right = 1000f;
        a.board.bottom = 1000f;
        a.board.detected = true;

        Piece striker = new Piece(500f, 900f, 36f, PieceType.STRIKER);
        Piece mine = new Piece(350f, 300f, 27f, PieceType.WHITE);
        mine.mine = true;
        Piece opponent = new Piece(700f, 300f, 27f, PieceType.BLACK);
        opponent.mine = false;
        a.striker = striker;
        a.pieces.add(striker);
        a.pieces.add(mine);
        a.pieces.add(opponent);
        a.pockets.add(new Pocket(40f, 40f, 40f));

        List<CarromShotPlanner.Shot> shots = CarromShotPlanner.planAll(a, true, false);
        assertFalse("expected at least one feasible own-coin shot", shots.isEmpty());
        for (CarromShotPlanner.Shot shot : shots) {
            assertTrue("planner selected an opponent coin", shot.target.mine
                    || shot.target.type == PieceType.QUEEN);
        }
    }

    @Test
    public void aimsAtDetectedPocketCenterWithoutCornerOffset() {
        Analysis a = new Analysis();
        a.board.left = 0f;
        a.board.top = 0f;
        a.board.right = 1000f;
        a.board.bottom = 1000f;
        a.board.detected = true;

        Piece striker = new Piece(500f, 900f, 36f, PieceType.STRIKER);
        Piece mine = new Piece(350f, 300f, 27f, PieceType.WHITE);
        mine.mine = true;
        a.striker = striker;
        a.pieces.add(striker);
        a.pieces.add(mine);
        a.pockets.add(new Pocket(40f, 40f, 40f));

        List<CarromShotPlanner.Shot> shots = CarromShotPlanner.planAll(a, false, false);
        assertFalse("expected a direct shot", shots.isEmpty());
        for (CarromShotPlanner.Shot shot : shots) {
            assertEquals(40f, shot.pocket.x, 0.001f);
            assertEquals(40f, shot.pocket.y, 0.001f);
        }
    }

    @Test
    public void ghostBallUsesExactSumOfCoinAndStrikerRadii() {
        float[] ghost = CarromShotPlanner.ghostBall(300f, 400f, 0f, 0f, 20f, 30f);
        assertEquals(330f, ghost[0], 0.001f);
        assertEquals(440f, ghost[1], 0.001f);
    }

    @Test
    public void pocketApproachRejectsPathThatClipsWoodenJaw() {
        assertTrue(CarromShotPlanner.pocketApproachClear(
                100f, 100f, 40f, 40f, 20f, 0f, 0f, 1000f, 1000f));
        assertFalse(CarromShotPlanner.pocketApproachClear(
                10f, 100f, 40f, 40f, 20f, 0f, 0f, 1000f, 1000f));
    }

    @Test
    public void sliderPlanningEvaluatesMultipleBaselinePositions() {
        Analysis a = new Analysis();
        a.board.left = 0f;
        a.board.top = 0f;
        a.board.right = 1000f;
        a.board.bottom = 1000f;
        a.board.detected = true;
        Piece striker = new Piece(500f, 900f, 36f, PieceType.STRIKER);
        Piece mine = new Piece(350f, 300f, 27f, PieceType.WHITE);
        mine.mine = true;
        a.striker = striker;
        a.pieces.add(striker);
        a.pieces.add(mine);
        a.pockets.add(new Pocket(40f, 40f, 40f));
        List<CarromShotPlanner.Shot> shots = CarromShotPlanner.planAll(a, false, true);
        boolean foundDifferentBaselineX = false;
        for (CarromShotPlanner.Shot shot : shots) {
            if (Math.abs(shot.strikerX - striker.x) > 1f) {
                foundDifferentBaselineX = true;
                break;
            }
        }
        assertTrue("planner must include alternative baseline placements", foundDifferentBaselineX);
    }
}
