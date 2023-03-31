package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.Random;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.model.Board.TicketBoard;
import uk.ac.bris.cs.scotlandyard.model.MyGameStateFactory;

import static java.lang.Thread.sleep;

public class MrXAi implements Ai {
	@Nonnull @Override public String name() {
		return "MR.X";
	}

	//timeoutPair.left() is duration in timeoutPair.right()
	@Nonnull @Override public Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair) {
		final long startTime = timeoutPair.right().convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
		// TODO 把startTIme用onStart() method 接收

		//  for every time calling GameTree，collect the move with maximum score，return
		double max = Double.NEGATIVE_INFINITY;
		Move bestMove = null;
		for (Move move : board.getAvailableMoves()) {
			int mrXLocation = move.source();
			double score = MyGameTree.miniMax(new ImmutableGameState(board, move.source()).clone().advance(move),
									   3, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, mrXLocation, startTime, timeoutPair);
//			System.out.println("score: " + score);
			if (max <= score) {
				max = score;
				bestMove = move;
			}
		}
		if (bestMove == null) throw new NullPointerException("expected not null");
//		System.out.println("max: " + max);
		return bestMove;
	}

	@Override
	public void onStart() {}

	@Override
	public void onTerminate() {}


}
