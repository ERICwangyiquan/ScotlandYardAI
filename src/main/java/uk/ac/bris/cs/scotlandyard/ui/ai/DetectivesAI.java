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
		final Long startTime = timeoutPair.right().convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
		// Contains the last revealed location of MrX. If no location has yet been
		// revealed, then empty
		final Optional<Integer> mrXLocation = board.getMrXTravelLog()
				.stream()
				.sequential()
				.map((LogEntry l) -> l.location())
				.filter((Optional<Integer> l) -> l.isPresent())
				.reduce((fst, snd) -> snd) // take last element of (finite) stream
				.orElse(Optional.empty());
		Function<Move, Double> score = (Move m) -> gameTree.ItNegamax(
				new ImmutableGameState(board, mrXLocation.orElse(1)).clone().advance(m),
				3, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, mrXLocation, startTime,
				timeoutPair);
		// throws when getAvailableMoves is empty
		return board.getAvailableMoves()
				.stream()
				.parallel()
				.min(Comparator.comparing(m -> score.apply(m)))
				.get();
	}

	@Override
	public void onStart() {
		this.gameTree = new GameTree();
	}

	@Override
	public void onTerminate() {
	} // TODO 每次timeout检查可以把这个函数传入minimax

}
