package uk.ac.bris.cs.scotlandyard.ui.ai;

import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.Player;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class MyGameTree {
    public static int miniMax(ImmutableGameState gameState, int depth, int alpha, int beta, int mrXLocation) {
        if (depth == 0) return score(gameState, gameState.getDetectives(), mrXLocation);
        if (gameState.getWinner().contains(Piece.MrX.MRX))  return Integer.MAX_VALUE;
        else if (!gameState.getWinner().isEmpty()) return Integer.MIN_VALUE;

        // BUG nonsense. Should check for isMrX, so the boolean definition is wrong.
        boolean isMrX = gameState.getRemaining().contains(Piece.MrX.MRX);
        if (! isMrX) {
            System.out.println("isMrX");
            int maxScore = Integer.MIN_VALUE;
            List<Move> sortedMove = gameState.getAvailableMoves().stream()
                .sorted(Comparator
                        .comparingInt((Move move) ->
                                      score(gameState.clone().advance(move),
                                            gameState.getDetectives(),
                                            move.getClass().equals(Move.DoubleMove.class) ?
                                            ((Move.DoubleMove) move).destination2 : ((Move.SingleMove) move).destination))
                        .reversed())
                .toList();
            // TODO probably can prune earlier? Come back later or leave for Eric
            for (Move move : sortedMove) {
                int newMrXLocation = move.getClass().equals(Move.DoubleMove.class) ?
                        ((Move.DoubleMove) move).destination2 : ((Move.SingleMove) move).destination;
                int curScore = miniMax(gameState.clone().advance(move), depth-1, alpha, beta, newMrXLocation);
                maxScore = Math.max(maxScore, curScore);
                alpha = Math.max(alpha, curScore);
                if (beta <= alpha) break;   // prune
            }
            return maxScore;
        } else {
            System.out.println("NOT isMrX");
            int minScore = Integer.MAX_VALUE;
            List<Move> sortedMove = gameState.getAvailableMoves().stream().toList(); // sort all the moves before keep going down in the tree

            // TODO need to sort sortedMove by the score after each move, code snippet below is wrong, should check the detectives location afterwards
//            sortedMove = sortedMove.stream().sorted((Move move1, Move move2) -> score(gameState.clone().advance(move2), gameState.getDetectives(), mrXLocation)
//                            - score(gameState.clone().advance(move1), gameState.getDetectives(), mrXLocation)).collect(Collectors.toList()); //descending order
            for (Move move : sortedMove) {
                synchronized (gameState) {
                    int curScore = miniMax(gameState.clone().advance(move), depth-1, alpha, beta, mrXLocation);
//                    System.out.println("curScore: " + curScore);
                    minScore = Math.min(minScore, curScore);
                    beta = Math.min(beta, curScore);
                }
                if (beta <= alpha) break;   // prune
            }
            return minScore;
        }
    }

    private static int score(ImmutableGameState gameState, List<Player> detectives, int mrXLocation) { // TODO: more conditions
        if (mrXLocation != Integer.MAX_VALUE) {
            int sum = 0;
            for (Player detective : detectives) {
                Dijkstra d = new Dijkstra(gameState, detective.location(), mrXLocation);
                sum += d.distTo[mrXLocation];
            }
            return sum;
        } else {
            return -1; // TODO detectiveAI
        }
    }
}
