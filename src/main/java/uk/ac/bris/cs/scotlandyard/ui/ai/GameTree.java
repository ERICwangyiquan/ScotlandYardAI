package uk.ac.bris.cs.scotlandyard.ui.ai;

import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Move.DoubleMove;
import uk.ac.bris.cs.scotlandyard.model.Move.SingleMove;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.Player;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard;

import java.sql.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

public final class GameTree {

    private static final double SECRET_TICKET_BONUS = 1.0;
    private static final double DOUBLE_TICKET_BONUS = 0.5;
    private static final double DISTANCE_WEIGHT = 1.0;
    private static final double LOG_DISTANCE_WEIGHT = 20.0;
    private static final double AVAILABLE_MOVES_BONUS = 0.4;

    private static Double score(ImmutableGameState gameState, Optional<Integer> mrXLocation,
                                int curDepth, boolean isMrX, Connection conn) {


        // if the move is a detective move AND we don't know where MrX has been
        if (mrXLocation.isEmpty()) {
            // MySQL version
            BiFunction<Integer, Integer, Integer> mysqlFunc = (loc, detectiveLoc) -> {
                String checkSql = "SELECT * FROM all_distances WHERE searchKey=" + ((loc-1)*199 + detectiveLoc) + ";";
                try (Statement stmt = conn.createStatement();
                     ResultSet checkRs = stmt.executeQuery(checkSql)) {
                    if (checkRs.next()) { // move cursor to first row
                        int distance = checkRs.getInt("distance");
                        return distance;
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                return 0;
            };

            // Real-time dijkstra version
//            BiFunction<Integer, Integer, Integer> dijkstraFunc = (loc, detectiveLoc) -> new Dijkstra(gameState, loc).distTo[detectiveLoc];

            double sum = gameState.getDetectives().stream()
                    .flatMap(detective -> gameState.getDetectives().stream()
                            .filter(otherDetective -> !detective.equals(otherDetective))
                            .map(otherDetective -> Math
//                                    .log(dijkstraFunc.apply(detective.location(), otherDetective.location()))
                                    .log(mysqlFunc.apply(detective.location(), otherDetective.location()))
                                    * LOG_DISTANCE_WEIGHT))
                    .reduce(0.0, Double::sum);

            sum += gameState.getAvailableMoves().size() * AVAILABLE_MOVES_BONUS;

            return -sum;
        }

        Integer location = mrXLocation.get();
//        Dijkstra dijkstra = new Dijkstra(gameState, location);

        // calculate score by distance from detectives
        List<Double> distList = gameState.getDetectives().stream()
//                .map(detective -> Math.log((dijkstra.distTo[detective.location()] - 1) / 3.5) * LOG_DISTANCE_WEIGHT)
                .map(detective -> {
                    int distance = 0;
                    String checkSql = "SELECT distance FROM all_distances WHERE searchKey=" + ((location-1)*199 + detective.location()) + ";";
                    try (Statement stmt = conn.createStatement();
                         ResultSet checkRs = stmt.executeQuery(checkSql)) {
                        if (checkRs.next()) { // move cursor to first row
                            distance = checkRs.getInt("distance");
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    return Math.log((distance - 1) / 3.5) * LOG_DISTANCE_WEIGHT;
                })
                .sorted()
                .toList();

        // if the minimum distance from any detectives is less than 3, make this aspect higher priority
        AtomicBoolean weightNeeded = new AtomicBoolean(distList.get(0) <= 5);
        double sum = distList.stream()
                .map(dist -> {
                    // greedy
                    // - Multiply `curDepth` since the right next move is
                    // more important than other future moves
                    // - Multiply `weight` since more close the detective
                    // is to MrX, more important to get away from them
                    // - Logarithm is doing the same thing here but might be less
                    // obvious if the all the distances are small;
                    double moveScore = (curDepth + 1) * dist * (weightNeeded.get() ? 15 : 1) *
                            DISTANCE_WEIGHT;
                    if (dist > 5 && weightNeeded.get()) {
                        weightNeeded.set(false);
                    }
                    return moveScore;
                })
                .reduce(0.0, Double::sum);

        // Add bonuses for secret tickets and double moves
        sum += gameState.getPlayerTickets(Piece.MrX.MRX).get().getCount(ScotlandYard.Ticket.SECRET) * SECRET_TICKET_BONUS;
        sum += gameState.getPlayerTickets(Piece.MrX.MRX).get().getCount(ScotlandYard.Ticket.DOUBLE) * DOUBLE_TICKET_BONUS;

        // Add bonuses for have more available moves.
        // the value of sum smaller is better to detectives, vice versa
        sum += (isMrX ? 1 : -1) * gameState.getAvailableMoves().size() * AVAILABLE_MOVES_BONUS;

        return sum;
    }

    public Double itNegaMax(ImmutableGameState state, Integer depth, Double alpha, Double beta,
                            Optional<Integer> mrXLocation,
                            final Long startTime, final Pair<Long, TimeUnit> timeoutPair, Connection conn) {

        // is the next move by a different player?
        boolean changeSign = state.getRemaining().size() == 1;

        // check timeout
        boolean isMrX = state.getRemaining().contains(Piece.MrX.MRX);
        long curTime = System.currentTimeMillis(); // check if almost timeOut
        long oneSecond = 1000;
        if (timeoutPair.left() * 1000 - (curTime - startTime) < oneSecond) {
            return isMrX ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }

        if (state.getWinner().contains(Piece.MrX.MRX)) {
            return Double.POSITIVE_INFINITY;
        } else if (!state.getWinner().isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }

        if (depth == 0) {
            return score(state, mrXLocation, depth, isMrX, conn);
        }

        double value = Double.NEGATIVE_INFINITY;
        List<Move> moves = state.getAvailableMoves()
                .stream()
                .unordered()    //shuffle the list and choose random 8 moves, 8 is small,
                // but we use iterative deepening BFS, so the `real-current` list for next moves of AI
                // will be shuffled 3 times in total (when max depth is 3)
                .parallel()
                .limit(8)
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
                    ? -itNegaMax(state.newState(m), depth - 1, -beta, -alpha, nextMrXLocation, startTime,
                    timeoutPair, conn)
                    : itNegaMax(state.newState(m), depth - 1, alpha, beta, nextMrXLocation, startTime,
                    timeoutPair, conn));

            alpha = Math.max(alpha, value);
            if (alpha >= beta) {
                break;
            }
        }
        return value;
    }
}
