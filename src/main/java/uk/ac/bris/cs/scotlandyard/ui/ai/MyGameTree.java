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
    public static double miniMax(ImmutableGameState gameState, int depth, double alpha, double beta, int mrXLocation,
                                 final long startTime, final Pair<Long, TimeUnit> timeoutPair) {
        if (mrXLocation == -1) {    // for the rounds when we do not know where MrX have been (i.e. first 2 rounds)
            // only Score the current move tp let detective spread for future rounds
            return score(gameState, null, mrXLocation, depth);
        }

        boolean maximising = gameState.getRemaining().contains(Piece.MrX.MRX);

        long curTime = timeoutPair.right().convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);  // check if almost timeOut
        long halfSecond = timeoutPair.right().convert(1L, TimeUnit.SECONDS);
        if (curTime - startTime + halfSecond > timeoutPair.left()) {
            return score(gameState, null, mrXLocation, depth);
        }

        if (depth == 0) return score(gameState, null, mrXLocation, depth);
        if (gameState.getWinner().contains(Piece.MrX.MRX))  return Double.POSITIVE_INFINITY;
        else if (!gameState.getWinner().isEmpty()) return Double.NEGATIVE_INFINITY;

        if (maximising) {
            double maxScore = Double.NEGATIVE_INFINITY;
            List<Move> sortedMoves = gameState.getAvailableMoves().stream() // sort all the moves before keep going down in the tree
                .sorted(Comparator
                        .comparingDouble((Move move) ->
                                      score(gameState.clone().advance(move), move,
                                            move.getClass().equals(Move.DoubleMove.class) ?
                                            ((Move.DoubleMove) move).destination2 : ((Move.SingleMove) move).destination, depth))
                        .reversed())
                .toList();  // descending
            for (Move move : sortedMoves.stream().limit(8).toList()) {
                int newMrXLocation = move.getClass().equals(Move.DoubleMove.class) ?
                    ((Move.DoubleMove) move).destination2 : ((Move.SingleMove) move).destination;
                double curScore = miniMax(gameState.clone().advance(move), depth-1, alpha, beta, newMrXLocation, startTime, timeoutPair);
                maxScore = Math.max(maxScore, curScore);
                alpha = Math.max(alpha, curScore);
                if (beta <= alpha) {
                    break;
                }
            }
            return maxScore;
        } else {
            double minScore = Double.POSITIVE_INFINITY;
            List<Move> sortedMoves = gameState.getAvailableMoves().stream()
                .sorted(Comparator
                        .comparingDouble((Move move) -> score(gameState.clone().advance(move), move, mrXLocation, depth)))
                .toList();
            for (Move move : sortedMoves.stream().limit(8).toList()) {
                double curScore = miniMax(gameState.clone().advance(move), depth - 1, alpha, beta, mrXLocation, startTime, timeoutPair);
                minScore = Math.min(minScore, curScore);
                beta = Math.min(beta, curScore);
                if (beta <= alpha) {
                    break;
                }
            }
            return minScore;
        }
    }

    private static double score(ImmutableGameState gameState, Move usedMove, int mrXLocation, int curDepth) {
        double sum = 0;
        if (mrXLocation >= 0) {
            // calculate score by distance from detectives
            int weight = 18;
            boolean weightNeeded = false;
            double lastDist = Double.NEGATIVE_INFINITY;
            List<Double> distList = new ArrayList<>();
            Dijkstra d = new Dijkstra(gameState, mrXLocation);
            for (Player detective : gameState.getDetectives()) {
                distList.add(Math.log(d.distTo[detective.location()]));
            }
            distList = distList.stream().sorted(Comparator.comparingDouble(dist -> dist)).toList();
            if (distList.get(distList.size() - 1) - distList.get(0) > 6) {
                weightNeeded = true;
            }
            for (Double dist: distList) {
                // *** greedy ***
                // multiply `curDepth` since the right next move is more important than other future moves
                // multiply `weight` since more close the detective is to MrX, more important to get away from him ASAP
                // logarithm is doing the same thing here but might be less obvious if the all the distances are small;
                sum += (curDepth + 1) * dist * (weightNeeded ? weight : 1);
                if (lastDist + 1 < dist) {    // e.g. "dist 5" * "weight 6" == "dist 6" * "weight 5", not letting this happen
                    weight -= 3;
                }
                lastDist = dist;
            }

            // prefer to have secret tickets
            sum += 0.8 * gameState.getPlayerTickets(Piece.MrX.MRX).get().getCount(ScotlandYard.Ticket.SECRET);
            // prefer to have double moves, slightly LESS important than SECRET tickets
            sum += 0.6 * gameState.getPlayerTickets(Piece.MrX.MRX).get().getCount(ScotlandYard.Ticket.DOUBLE);
            if (usedMove != null) {
                // amount of tickets left
                for (ScotlandYard.Ticket ticket : usedMove.tickets()) {
                    sum += 0.4 * gameState.getPlayerTickets(usedMove.commencedBy()).get().getCount(ticket);
                }
                // amount of new choices
                int newLocation = usedMove.getClass().equals(Move.DoubleMove.class) ?
                        ((Move.DoubleMove) usedMove).destination2 : ((Move.SingleMove) usedMove).destination;
                sum += 0.4 * gameState.getSetup().graph.adjacentNodes(newLocation).size();
                // if mrX can be caught in next round
                if (gameState.getAvailableMoves().stream().anyMatch(move -> {
                    int nextLocation = move.getClass().equals(Move.DoubleMove.class) ?
                            ((Move.DoubleMove) move).destination2 : ((Move.SingleMove) move).destination;
                    return nextLocation == newLocation;
                })) {
                    sum = Double.NEGATIVE_INFINITY; // give up this move
                }
            }

            return sum;
        } else {    // let every detective spread over the map if the location of MrX is unknown
            for (Player detective : gameState.getDetectives()) {
                Dijkstra d = new Dijkstra(gameState, detective.location());
                for (Player otherDetective : gameState.getDetectives()) {
                    if (detective.equals(otherDetective)) continue;
                    sum += Math.log(d.distTo[otherDetective.location()]);
                }
            }
            return -sum;
        }
    }
}
