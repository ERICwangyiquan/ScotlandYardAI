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

//	private Optional<TicketBoard> tickets;
//	private Integer location;

	@Nonnull @Override public String name() {
		return "MR.X";
	}

	@Nonnull @Override public Move pickMove(	// TODO 二维数组提前算好所有的Dijkstra 异步
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {		//timeoutPair.left() is duration in timeoutPair.right()（秒）单位下
		final long startTime = timeoutPair.right().convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);

		//  for every time calling GameTree，collect the move with maximum score，return
		int max = Integer.MIN_VALUE;
		Move bestMove = null;
		for (Move move : board.getAvailableMoves()) {
//			long curTime = timeoutPair.right().convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
//			long halfSecond = timeoutPair.right().convert(10L, TimeUnit.SECONDS);
//			if (curTime - startTime + halfSecond > timeoutPair.left()) {
//				break;
//			}

			int score = 0;
			int mrXLocation = move.source();
			score = MyGameTree.miniMax(new ImmutableGameState(board, move.source()).clone().advance(move), // use the current ticket
					 ImmutableGameState.maxDepth, Integer.MIN_VALUE, Integer.MAX_VALUE, mrXLocation, startTime, timeoutPair);
			// TODO **** here always return the same `score`,		MiniMax depth-1变成depth就好了
			//  the maximum score and minimum score are the same for every time of calling the `miniMax()`
			System.out.println("score " + score);
			if (max < score) {
				max = score;
				bestMove = move;
			}
		}
		try {
			assert bestMove != null;
		} catch (NullPointerException e) {
			throw new NullPointerException("expected not null");
		}
//		System.out.println("max: " + max);
		return bestMove;

//
// 		// returns a random move, replace with your own implementation
//		var moves = board.getAvailableMoves().asList();
////		this.tickets = board.getPlayerTickets(Piece.MrX.MRX);
////		this.location = board.getAvailableMoves().stream().map(m -> m.source()).findAny().get();
//		return moves.get(new Random().nextInt(moves.size()));
	}

//	// we can't access from a Board, mrx's location
//	// so we need to pass it separately
//	private Integer scoreBoard(Board board, Integer depth) {
//		if (board.getWinner().contains(Piece.MrX.MRX)) return Integer.MAX_VALUE;
//		else if (! board.getWinner().isEmpty()) return 0;
//		// NOTE at this point, b.getAvailableMoves().isEmpty() is FALSE
//		boolean mrXTurn = board.getAvailableMoves().stream().anyMatch(m -> m.commencedBy() == Piece.MrX.MRX);
//		if (mrXTurn) { // base case
//			return null;
//		} else { // recursion
//			return null;
//		}
//	}

	@Override
	public void onStart() {}

	@Override
	public void onTerminate() {}


}
