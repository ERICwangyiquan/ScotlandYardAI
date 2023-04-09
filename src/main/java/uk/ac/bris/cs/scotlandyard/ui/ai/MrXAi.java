package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.*;

import javax.annotation.Nonnull;

import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

public class MrXAi implements Ai {

	private GameTree gameTree;

	@Nonnull
	@Override
	public String name() {
		return "MR.X";
	}

	@Nonnull
	@Override
	public Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair) {
		final Long startTime = timeoutPair.right().convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
		BiFunction<Integer, Move, Double> score = (Integer d, Move m) -> gameTree.ItNegamax(
				new ImmutableGameState(board, m.source()).clone().advance(m),
				d, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Optional.of(m.source()), startTime, timeoutPair);
		Move move = null;
		for (Integer d = 0; d < 3; d++) {
			// BUG
			// - too slow
			// - needs to run until time low
			// - iterative deepening sorting ineffective?
			// - not clear on which moves are being sorted...

			// due to Java loop mechanics, d is not final, but we need a finally typed value
			final Integer depth = d;
			move = board
					.getAvailableMoves()
					.stream()
					.parallel()
					.max(Comparator.comparingDouble(m -> score.apply(depth, m)))
					.get();
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
