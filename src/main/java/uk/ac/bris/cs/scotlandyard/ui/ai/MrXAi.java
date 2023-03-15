package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.Random;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Ai;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.Board.TicketBoard;

public class MrXAi implements Ai {

	private Optional<TicketBoard> tickets;
	private Integer location;

	@Nonnull @Override public String name() {
		return "MR.X";
	}

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		// returns a random move, replace with your own implementation
		var moves = board.getAvailableMoves().asList();
		this.tickets = board.getPlayerTickets(Piece.MrX.MRX);
		this.location = board.getAvailableMoves().stream().map(m -> m.source()).findAny().get();
		return moves.get(new Random().nextInt(moves.size()));
	}

	// we can't access from a Board, mrx's location
	// so we need to pass it separately
	private Integer scoreBoard(Board board, Integer depth) {
		if (board.getWinner().contains(Piece.MrX.MRX)) return Integer.MAX_VALUE;
		else if (! board.getWinner().isEmpty()) return 0;
		// NOTE at this point, b.getAvailableMoves().isEmpty() is FALSE
		Boolean mrXTurn = board.getAvailableMoves().stream().anyMatch(m -> m.commencedBy() == Piece.MrX.MRX);
		if (mrXTurn) { // base case
			return null;
		} else { // recursion
			return null;
		}
	}

	@Override
	public void onStart() {}

	@Override
	public void onTerminate() {}
}
