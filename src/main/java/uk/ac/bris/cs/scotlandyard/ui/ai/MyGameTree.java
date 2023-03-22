package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.Player;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class MyGameTree {
    public static int miniMax(ImmutableGameState gameState, int depth, int alpha, int beta, int mrXLocation, final long startTime, final Pair<Long, TimeUnit> timeoutPair) {
        boolean isMrX = gameState.getRemaining().contains(Piece.MrX.MRX);

//        long curTime = timeoutPair.right().convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);  // check if almost timeOut
//        long halfSecond = timeoutPair.right().convert(10L, TimeUnit.SECONDS);
//        if (curTime - startTime + halfSecond > timeoutPair.left()) {
//            return isMrX ? Integer.MAX_VALUE : Integer.MIN_VALUE;
//        }

        if (depth == 0) return score(gameState, mrXLocation);
        if (gameState.getWinner().contains(Piece.MrX.MRX))  return Integer.MAX_VALUE;
        else if (!gameState.getWinner().isEmpty()) return Integer.MIN_VALUE;

        if (isMrX) {
//            System.out.println("isMrX");
            int maxScore = Integer.MIN_VALUE;
//            List<Move> sortedMove = gameState.getAvailableMoves().stream() // sort all the moves before keep going down in the tree
//                .sorted(Comparator
//                        .comparingInt((Move move) ->
//                                      score(gameState.clone().advance(move),
//                                            move.getClass().equals(Move.DoubleMove.class) ?
//                                            ((Move.DoubleMove) move).destination2 : ((Move.SingleMove) move).destination))
//                        .reversed())
//                .toList();  // descending
            for (Move move : gameState.getAvailableMoves()) {
                int newMrXLocation = move.getClass().equals(Move.DoubleMove.class) ?
                        ((Move.DoubleMove) move).destination2 : ((Move.SingleMove) move).destination;
                int curScore = miniMax(gameState.clone().advance(move), depth-1, alpha, beta, newMrXLocation, startTime, timeoutPair);
                maxScore = Math.max(maxScore, curScore);
                alpha = Math.max(alpha, curScore);
                if (beta <= alpha) break;   // prune
            }
            return maxScore;
        } else {
//            System.out.println("NOT isMrX");
            int minScore = Integer.MAX_VALUE;
            List<Move> sortedMove = gameState.getAvailableMoves().stream()
                    .sorted(Comparator
                            .comparingInt((Move move) ->
                                    score(gameState.clone().advance(move),
                                            mrXLocation)))
                    .toList();; //ascending order   // TODO map给每个detective最好的5个move，permutation基于这个更高效


            ImmutableSet<Move> moves = gameState.getAvailableMoves();
            List<List<Move>> permutations = new ArrayList<>();
            //numDetectives
            Set<Piece> availableDetectives = new HashSet<>();
            availableDetectives = moves.stream().map(Move::commencedBy).collect(Collectors.toSet());
            Stack<Move> stack = new Stack<>();
            Set<Piece> existedPlayers = new HashSet<>();
            getPermutationOfMoves(moves, permutations, availableDetectives.size(), 0, stack, existedPlayers, startTime, timeoutPair);

            for (List<Move> movesQueue : permutations) {   // get the permutations of moves first, then building tree
                    ImmutableGameState initGameState = gameState.clone();
                    for (int i = 0; i < movesQueue.size() - 1; i++) {
                        initGameState = initGameState.advance(movesQueue.get(i));
                    }
                    int curScore = miniMax(initGameState.advance(movesQueue.get(movesQueue.size() - 1)), depth - 1, alpha, beta, mrXLocation, startTime, timeoutPair);
//                       TODO timeoutpair
//                        System.out.println("curScore: " + curScore);
                    minScore = Math.min(minScore, curScore);
                    beta = Math.min(beta, curScore);
                    if (beta <= alpha) break;   // prune
            }
            return minScore;
        }
    }

    private static void getPermutationOfMoves(ImmutableSet<Move> moves, List<List<Move>> permutations, int numDetectives,
                                              int curDepth, Stack<Move> stack, Set<Piece> existedPlayers,  final long startTime, Pair<Long, TimeUnit> timeoutPair) {
        if (curDepth == numDetectives) {
            permutations.add(stack.stream().toList());
            return;
        }

        for (Move move : moves) {
//            long curTime = timeoutPair.right().convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);  // check if almost timeOut
//            long halfSecond = timeoutPair.right().convert(10L, TimeUnit.SECONDS);
//            if (curTime - startTime + halfSecond > timeoutPair.left()) {
//                return;
//            }
            if (stack.contains(move) || stack.stream().anyMatch(m -> {
                int existedLocation = m.getClass().equals(Move.DoubleMove.class) ?
                        ((Move.DoubleMove) m).destination2 : ((Move.SingleMove) m).destination;
                int newLocation = move.getClass().equals(Move.DoubleMove.class) ?
                        ((Move.DoubleMove) move).destination2 : ((Move.SingleMove) move).destination;
                return newLocation == existedLocation;
            }) || existedPlayers.contains(move.commencedBy())) continue;
            stack.add(move);
            existedPlayers.add(move.commencedBy());
            getPermutationOfMoves(moves, permutations, numDetectives, curDepth+1, stack, existedPlayers, startTime, timeoutPair);
            existedPlayers.remove(move.commencedBy());
            stack.pop();
        }
    }

    private static int score(ImmutableGameState gameState, int mrXLocation) { // TODO: more conditions
        if (mrXLocation != Integer.MAX_VALUE) {
            int sum = 0;
            for (Player detective : gameState.getDetectives()) {
                Dijkstra d = new Dijkstra(gameState, detective.location(), mrXLocation);
                sum += d.distTo[mrXLocation];
            }
            return sum;
        } else {
            return -1; // TODO detectiveAI
        }
    }
}
