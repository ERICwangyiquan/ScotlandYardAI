package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.concurrent.TimeUnit;
import java.util.function.*;

import javax.annotation.Nonnull;

import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

public class MrXAi implements Ai {

	@Nonnull
	@Override
	public String name() {
		return "MR.X";
	}

	@Nonnull
	@Override
	public Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair) {
		final Long startTime = timeoutPair.right().convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
		Function<Move, Double> score = (Move m) -> MyGameTree.miniMax(
				new ImmutableGameState(board, m.source()).clone().advance(m),
				3, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, m.source(), startTime, timeoutPair);
		// throws when getAvailableMoves is empty
		return board.getAvailableMoves()
				.stream()
				.parallel()
				.max((Move m1, Move m2) -> Double.compare(score.apply(m1), score.apply(m2)))
				.get();
	}

	@Override
	public void onStart() {
	}

	@Override
	public void onTerminate() {
	}

}
