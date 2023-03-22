package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.Player;
import uk.ac.bris.cs.scotlandyard.model.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class MyGameTree {
    public static double miniMax(ImmutableGameState gameState, int depth, double alpha, double beta, int mrXLocation, final long startTime, final Pair<Long, TimeUnit> timeoutPair) {
        boolean maximising = gameState.getRemaining().contains(Piece.MrX.MRX);

        long curTime = timeoutPair.right().convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);  // check if almost timeOut
        long halfSecond = timeoutPair.right().convert(10L, TimeUnit.SECONDS);
        if (curTime - startTime + halfSecond > timeoutPair.left()) {
            return maximising ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }

        if (depth == 0) return score(gameState, mrXLocation);
        if (gameState.getWinner().contains(Piece.MrX.MRX))  return Double.POSITIVE_INFINITY;
        else if (!gameState.getWinner().isEmpty()) return Double.NEGATIVE_INFINITY;

        if (maximising) {
            double maxScore = Double.NEGATIVE_INFINITY;
            List<Move> sortedMoves = gameState.getAvailableMoves().stream() // sort all the moves before keep going down in the tree
                .sorted(Comparator
                        .comparingDouble((Move move) ->
                                      score(gameState.clone().advance(move),
                                            move.getClass().equals(Move.DoubleMove.class) ?
                                            ((Move.DoubleMove) move).destination2 : ((Move.SingleMove) move).destination))
                        .reversed())
                .toList();  // descending
            for (Move move : sortedMoves.stream().limit(10).toList()) {
                int newMrXLocation = move.getClass().equals(Move.DoubleMove.class) ?
                    ((Move.DoubleMove) move).destination2 : ((Move.SingleMove) move).destination;
                double curScore = miniMax(gameState.clone().advance(move), depth-1, alpha, beta, newMrXLocation, startTime, timeoutPair);
                maxScore = Math.max(maxScore, curScore);
                alpha = Math.max(alpha, curScore);
                if (beta <= alpha) break;
            }
            return maxScore;
        } else {
            double minScore = Double.POSITIVE_INFINITY;
            List<Move> sortedMoves = gameState.getAvailableMoves().stream()
                .sorted(Comparator
                        .comparingDouble((Move move) -> score(gameState.clone().advance(move), mrXLocation)))
                .toList();;
            for (Move move : sortedMoves.stream().limit(5).toList()) {
                double curScore = miniMax(gameState.clone().advance(move), depth - 1, alpha, beta, mrXLocation, startTime, timeoutPair);
                minScore = Math.min(minScore, curScore);
                beta = Math.min(beta, curScore);
                if (beta <= alpha) break;
            }
            return minScore;
        }
    }

    private static double score(ImmutableGameState gameState, int mrXLocation) {
        if (mrXLocation != Integer.MAX_VALUE) {
            double sum = 0;
            // calculate score by distance from detectives
            for (Player detective : gameState.getDetectives()) {
                Dijkstra d = new Dijkstra(gameState, detective.location(), mrXLocation);
                // TODO choose an increasing function to code this
                sum += Math.sqrt(d.distTo[mrXLocation]);
            }
            // prefer to have secret tickets
            sum += 2 * gameState.getPlayerTickets(Piece.MrX.MRX).get().getCount(ScotlandYard.Ticket.SECRET);
            // prefer to have double moves
            sum += gameState.getPlayerTickets(Piece.MrX.MRX).get().getCount(ScotlandYard.Ticket.DOUBLE);
            return sum;
        } else {
            return -1;
        }
    }
}
