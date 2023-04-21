package uk.ac.bris.cs.scotlandyard.ui.ai;

import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Ai;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public class MrXAI implements Ai {

    private GameTree gameTree;

    @Nonnull
    @Override
    public String name() {
        return "MR.X";
    }

    @Nonnull
    @Override
    public Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair) {
        final long startTime = System.currentTimeMillis();
        final long oneSecond = 1000;

        BiFunction<Integer, Move, Double> score = (Integer d, Move m) -> gameTree.itNegaMax(
                new ImmutableGameState(board, m.source()).newState(m),
                d, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Optional.of(m.source()), startTime, timeoutPair);

        Move move = null;
        for (int d = 0; d < 4; d++) {
            long curTime = System.currentTimeMillis();  // check if almost timeOut
            if (timeoutPair.left() * 1000 - (curTime - startTime) < oneSecond) {
                break;
            }

            // due to Java loop mechanics, d is not final, but we need a finally typed value
            final Integer depth = d;
            move = board
                    .getAvailableMoves()
                    .stream()
                    .parallel()
                    .max(Comparator.comparingDouble(m -> score.apply(depth, m)))
                    .get();
        }

        try {
            assert move != null;
        } catch (NullPointerException e) {
            System.out.println(e.getMessage() + " in MrX AI");
        }

        return move;
    }

    @Override
    public void onStart() {
        this.gameTree = new GameTree();
    }

    @Override
    public void onTerminate() {
    }

}
