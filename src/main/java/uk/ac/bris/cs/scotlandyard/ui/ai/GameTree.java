package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.atlassian.fugue.Pair;

import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.model.Move.DoubleMove;
import uk.ac.bris.cs.scotlandyard.model.Move.SingleMove;

public final class GameTree {

    // transposition table, mapping GameState::hashCode() to negamax result
    // used as a heuristic for ordering moves to improve α-β runtime
    private final Map<Integer, Double> trans;
    private static final double SECRET_TICKET_BONUS = 0.5;
    private static final double DOUBLE_TICKET_BONUS = 0.5;
    private static final double DISTANCE_WEIGHT = 1.0;
    private static final double LOG_DISTANCE_WEIGHT = 100.0;

    GameTree() {
        this.trans = new Hashtable<>();
    }   // for thread-safe

    public Double ItNegamax(ImmutableGameState state, Integer depth, Double alpha, Double beta,
                            Optional<Integer> mrXLocation,
                            final Long startTime, final Pair<Long, TimeUnit> timeoutPair) {

        // is the next move by a different player?
        boolean changeSign = state.getRemaining().size() == 1;

        // check timeout
        boolean isMrX = state.getRemaining().contains(Piece.MrX.MRX);
        long curTime = System.currentTimeMillis();  // check if almost timeOut
        long oneSecond = 1000;
        if (timeoutPair.left() * 1000 - (curTime - startTime) < oneSecond) {
            return isMrX ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }

        if (state.getWinner().contains(Piece.MrX.MRX)) {
            trans.put(state.hashCode(), Double.POSITIVE_INFINITY);
            return Double.POSITIVE_INFINITY;
        } else if (!state.getWinner().isEmpty()) {
            trans.put(state.hashCode(), Double.NEGATIVE_INFINITY);
            return Double.NEGATIVE_INFINITY;
        }

        if (depth == 0) {
            Double score = score(state, mrXLocation, depth);
            trans.put(state.hashCode(), score);
            return score;
        }

        double value = Double.NEGATIVE_INFINITY;
        List<Move> moves = state.getAvailableMoves()
                .stream()
                .limit(10)
                .toList();

        for (Move m : moves) {
            // NB for this bit we're going to have to know what MrX's location is, or pass
            // the empty value
            final Optional<Integer> nextMrXLocation = mrXLocation.isPresent()
                    ? changeSign
                    ? Optional.of(m.accept(new Move.Visitor<>() {
                @Override
                public Integer visit(SingleMove move) {
                    return move.destination;
                }

                @Override
                public Integer visit(DoubleMove move) {
                    return move.destination2;
                }
            }))
                    : mrXLocation
                    : Optional.empty();
            value = Math.max(value, changeSign
                    ? -ItNegamax(state.clone().advance(m), depth - 1, -beta, -alpha, nextMrXLocation, startTime,
                    timeoutPair)
                    : ItNegamax(state.clone().advance(m), depth - 1, alpha, beta, nextMrXLocation, startTime,
                    timeoutPair));

            alpha = Math.max(alpha, value);
            if (alpha >= beta) {
                break;
            }
        }
        trans.put(state.hashCode(), value);
        return value;
    }

    // TODO this can probably be much simpler *and* much better...
    //  and should DRY
    // TODO and should follow FP...
    private static Double score(ImmutableGameState gameState, Optional<Integer> mrXLocation,
                                int curDepth) {
        if (mrXLocation.isEmpty()) {
            // If the location of Mr. X is unknown, let every detective spread over the map.
            Function<Integer, Dijkstra> dijkstraFunc = (Integer loc) -> new Dijkstra(gameState, loc);

            double sum = gameState.getDetectives().stream()
                    .flatMap(detective -> gameState.getDetectives().stream()
                            .filter(otherDetective -> !detective.equals(otherDetective))
                            .map(otherDetective -> Math.log(dijkstraFunc.apply(detective.location()).distTo[otherDetective.location()] * LOG_DISTANCE_WEIGHT)))
                    .reduce(0.0, Double::sum);
            return -sum;
        }

        Integer location = mrXLocation.get();
        Dijkstra dijkstra = new Dijkstra(gameState, location);

        // calculate score by distance from detectives
        List<Double> distList = gameState.getDetectives().stream()
                .map(detective -> Math.log(dijkstra.distTo[detective.location()] * LOG_DISTANCE_WEIGHT))
                .sorted()
                .toList();

        final int[] weight = {15}; // as a final list only for lambda later
        AtomicBoolean weightNeeded = new AtomicBoolean(distList.get(0) <= 5);  // if the minimum distance from any detectives is less than 3, make this aspect higher priority

        double sum = distList.stream()
                .map(dist -> {
                    // *** greedy ***      // TODO put this into report
                    // multiply `curDepth` since the right next move is more important than other future moves
                    // multiply `weight` since more close the detective is to MrX, more important to get away from them
                    // logarithm is doing the same thing here but might be less obvious if the all the distances are small;
                    double moveScore = (curDepth + 1) * dist * (weightNeeded.get() ? weight[0] : 1) * DISTANCE_WEIGHT;
                    if (dist > 5 && weightNeeded.get()) {
                        weightNeeded.set(false);
                    }
                    return moveScore;
                })
                .reduce(0.0, Double::sum);

        // TODO if the next move reveals the location, use DOUBLE is better choice (can increase the score)
        //  also plus some credit if gameState.getAvailableMoves() is high
        // Add bonuses for secret tickets and double moves
        sum += gameState.getPlayerTickets(Piece.MrX.MRX).get().getCount(ScotlandYard.Ticket.SECRET) * SECRET_TICKET_BONUS;
        sum += gameState.getPlayerTickets(Piece.MrX.MRX).get().getCount(ScotlandYard.Ticket.DOUBLE) * DOUBLE_TICKET_BONUS;

        return sum;
    }
}
