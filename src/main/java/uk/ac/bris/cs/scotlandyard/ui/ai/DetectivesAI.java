package uk.ac.bris.cs.scotlandyard.ui.ai;

import io.atlassian.fugue.Option;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Ai;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.LogEntry;
import uk.ac.bris.cs.scotlandyard.model.Move;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class DetectivesAI implements Ai {
	@Nonnull @Override public String name() {
		return "Detectives";
	}

	//timeoutPair.left() is duration in timeoutPair.right()
	@Nonnull @Override public Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair) {	// also choose the highest score like MrXAi
		// TODO 把startTIme用onStart() method 接收
		final long startTime = timeoutPair.right().convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);

		//  for every time calling GameTree，collect the move with maximum score，return
		double min = Double.POSITIVE_INFINITY;
		Move bestMove = null;

		Optional<Integer> mrXLocation = Optional.empty(); // get the latest location of MrX
		for (LogEntry log : board.getMrXTravelLog().reverse()) {
			if (log.location().isPresent()) {
				mrXLocation = log.location();
				break;
			}
		}

		for (Move move : board.getAvailableMoves()) {
			double score = MyGameTree.miniMax(new ImmutableGameState(board, mrXLocation.orElse(1)).clone().advance(move),
									   3, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, mrXLocation.orElse(-1), startTime, timeoutPair);
//			System.out.println("score: " + score);
			if (min >= score) {
				min = score;
				bestMove = move;
			}
		}
		if (bestMove == null) throw new NullPointerException("expected not null");
//		System.out.println("min: " + min);
		return bestMove;
	}

	@Override
	public void onStart() {}

	@Override
	public void onTerminate() {}	// TODO 每次timeout检查可以把这个函数传入minimax


}
