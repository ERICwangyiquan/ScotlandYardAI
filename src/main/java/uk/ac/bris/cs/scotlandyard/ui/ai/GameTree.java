package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;

import io.atlassian.fugue.Pair;

import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.model.Move.DoubleMove;
import uk.ac.bris.cs.scotlandyard.model.Move.SingleMove;

public final class GameTree {

    // transposition table, mapping GameState::hashCode() to negamax result
    // used as a heuristic for ordering moves to improve α-β runtime
    private Map<Integer, Double> trans;

    GameTree() {
        this.trans = new HashMap<>();
    }

    GameTree(Map<Integer, Double> trans) {
        this.trans = trans;
    }

    public Double ItNegamax(ImmutableGameState state, Integer depth, Double alpha, Double beta,
            Optional<Integer> mrXLocation,
            final Long startTime, final Pair<Long, TimeUnit> timeoutPair) {
        // TODO check timeout
//        long curTime = timeoutPair.right().convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);  // check if almost timeOut
//        long halfSecond = timeoutPair.right().convert(10L, TimeUnit.SECONDS);
//        if (curTime - startTime + halfSecond > timeoutPair.left()) {
//            return maximising ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
//        }


        // is the next move by a different player?
        boolean isMrX = state.getRemaining().contains(Piece.MrX.MRX);  // TODO (to delete) if like last version, there will be no difference between mrX and last detective in detectives

        if (state.getWinner().contains(Piece.MrX.MRX)) {
            trans.put(state.hashCode(), Double.POSITIVE_INFINITY);
            return Double.POSITIVE_INFINITY;
        } else if (!state.getWinner().isEmpty()) {
            trans.put(state.hashCode(), Double.NEGATIVE_INFINITY);
            return Double.NEGATIVE_INFINITY;
        }

        if (depth == 0) {
            Double score = score(state, null, mrXLocation, depth);
            trans.put(state.hashCode(), score);
            return score;
        }

        double value = Double.NEGATIVE_INFINITY;
        List<Move> moves = state.getAvailableMoves()// TODO
                .stream()
                .sorted(Comparator
                        .comparingDouble((Move m) ->
                                trans.getOrDefault(state.clone().advance(m).hashCode(),
                                        score(state.clone().advance(m), m, mrXLocation, depth)))
                        .reversed())
                .toList();

        for (Move m : moves) {
            // NB for this bit we're going to have to know what MrX's location is, or pass
            // the empty value
            final Optional<Integer> nextMrXLocation = mrXLocation.isPresent()
                    ? isMrX
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
            value = Math.max(value, !isMrX
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
    private static Double score(ImmutableGameState gameState, Move usedMove, Optional<Integer> mrXLocation,
            int curDepth) {
        double sum = 0;
        if (mrXLocation.isPresent()) {
            Integer location = mrXLocation.get();
            // calculate score by distance from detectives
            int weight = 18;
            boolean weightNeeded = false;
            double lastDist = Double.NEGATIVE_INFINITY;
            List<Double> distList = new ArrayList<>();
            Dijkstra d = new Dijkstra(gameState, location);
            for (Player detective : gameState.getDetectives()) {
                distList.add(Math.log(d.distTo[detective.location()]));
            }
            distList = distList.stream().sorted(Comparator.comparingDouble(dist -> dist)).toList();
            if (distList.get(distList.size() - 1) - distList.get(0) > 6) {
                weightNeeded = true;
            }
            for (Double dist : distList) {
                // *** greedy ***
                // multiply `curDepth` since the right next move is more important than other
                // future moves
                // multiply `weight` since more close the detective is to MrX, more important to
                // get away from him ASAP
                // logarithm is doing the same thing here but might be less obvious if the all
                // the distances are small;
                sum += (curDepth + 1) * dist * (weightNeeded ? weight : 1);
                if (lastDist + 1 < dist) { // e.g. "dist 5" * "weight 6" == "dist 6" * "weight 5", not letting this
                                           // happen
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
                int newLocation = usedMove.getClass().equals(Move.DoubleMove.class)
                        ? ((Move.DoubleMove) usedMove).destination2
                        : ((Move.SingleMove) usedMove).destination;
                sum += 0.4 * gameState.getSetup().graph.adjacentNodes(newLocation).size();
                // if mrX can be caught in next round
                if (gameState.getAvailableMoves().stream().anyMatch(move -> {
                    int nextLocation = move.getClass().equals(Move.DoubleMove.class)
                            ? ((Move.DoubleMove) move).destination2
                            : ((Move.SingleMove) move).destination;
                    return nextLocation == newLocation;
                })) {
                    sum = Double.NEGATIVE_INFINITY; // give up this move
                }
            }

            return sum;
        } else { // let every detective spread over the map if the location of MrX is unknown
            for (Player detective : gameState.getDetectives()) {
                Dijkstra d = new Dijkstra(gameState, detective.location());
                for (Player otherDetective : gameState.getDetectives()) {
                    if (detective.equals(otherDetective))
                        continue;
                    sum += Math.log(d.distTo[otherDetective.location()]);
                }
            }
            return -sum;
        }
    }
}
