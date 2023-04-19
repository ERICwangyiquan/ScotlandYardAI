package uk.ac.bris.cs.scotlandyard.ui.ai;

import io.atlassian.fugue.Pair;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.*;

import javax.annotation.Nonnull;

import uk.ac.bris.cs.scotlandyard.model.*;

public class DetectivesAI implements Ai {

    private GameTree gameTree;


    @Nonnull
    @Override
    public String name() {
        return "Detectives";
    }

    @Nonnull
    @Override
    public Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair) {
        final long startTime = System.currentTimeMillis();
        final long oneSecond = 1000;

        // Contains the last revealed location of MrX. If no location has yet been
        // revealed, then empty
        final Optional<Integer> mrXLocation = board.getMrXTravelLog()
                .stream()
                .map(LogEntry::location)
                .filter(Optional::isPresent)
                .reduce((fst, snd) -> snd) // take last element of (finite) stream
                .orElse(Optional.empty());

        BiFunction<Integer, Move, Double> score = (Integer d, Move m) -> gameTree.ItNegamax(
                new ImmutableGameState(board, mrXLocation.orElse(1)).clone().advance(m),
                d, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, mrXLocation, startTime, timeoutPair);

        Move move = null;
        for (int d = 0; d < 4; d++) {
            long curTime = System.currentTimeMillis();  // check if almost timeOut
            if (timeoutPair.left() * 1000 - (curTime - startTime) < oneSecond) {
                break;
            }
            // BUG?
            // Haven't tested, but since it's the same as in MrXAI... it's probably too slow
            // and the iterative deepening implementation is probably shoddy.

            // Think I've just misunderstood how to make use of it? The idea is
            // to use iterative depening to order the moves via a transposition
            // table, however I now don't think I really understood how to make
            // that work -- that would mean the sorting is useless and so
            // there's not enough pruning. The main way in which we can improve runtime
            // is with better algorithms and heuristics, in particular pruning. We can use
            // parallel programming here, but overusing it is a bit useless...

            // We also need to find a way to make proper use of time management
            // AT THE SAME TIME as parallel programming -- the use of iterative
            // deepening should help in a similar way and might be intertwined.

            // due to Java loop mechanics, d is not final, but we need finally typed value
            final Integer depth = d;
            move = board
                    .getAvailableMoves()
                    .stream()
                    .parallel()
                    .min(Comparator.comparingDouble(m -> score.apply(depth, m)))
                    .get();
        }

        try {
            assert move != null;
        } catch (NullPointerException e) {
            System.out.println(e.getMessage() + " in detectives AI");
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
