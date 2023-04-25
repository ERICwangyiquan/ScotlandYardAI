package uk.ac.bris.cs.scotlandyard.ui.ai;

import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Move.DoubleMove;
import uk.ac.bris.cs.scotlandyard.model.Move.SingleMove;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class GameTree {

    private static final double SECRET_TICKET_BONUS = 1.0;
    private static final double DOUBLE_TICKET_BONUS = 0.5;
    private static final double DISTANCE_WEIGHT = 1.0;
    private static final double LOG_DISTANCE_WEIGHT = 20.0;
    private static final double AVAILABLE_MOVES_BONUS = 0.4;

    private static double score(ImmutableGameState gameState, Optional<Integer> mrXLocation,
                                int curDepth, boolean isMrX, Connection conn) {
        if (mrXLocation.isEmpty()) {
            // if the move is a detective move AND we don't know where MrX has been
            double sum = calculateDetectiveScore(gameState, conn);
            sum += gameState.getAvailableMoves().size() * AVAILABLE_MOVES_BONUS;
            return -sum;
        }

        int location = mrXLocation.get();
        double sum = calculateMrXScore(gameState, location, conn, curDepth);
        sum += calculateBonuses(gameState, isMrX);
        return sum;
    }

    private static double calculateDetectiveScore(ImmutableGameState gameState, Connection conn) {
        List<Double> distances = gameState.getDetectives().stream()
                .flatMap(detective1 -> gameState.getDetectives().stream()
                        .filter(detective2 -> !detective1.equals(detective2))
                        .map(detective2 -> calculateLogDistance(gameState, conn, detective1.location(), detective2.location())))
                .sorted()
                .toList();
        double sum = distances.stream()
                .reduce(0.0, Double::sum);
        return sum;
    }

    private static double calculateMrXScore(ImmutableGameState gameState, int location, Connection conn, int curDepth) {
        List<Double> distances = gameState.getDetectives().stream()
                .map(detective -> calculateLogDistance(gameState, conn, location, detective.location()))
                .sorted()
                .toList();
        double sum = distances.stream()
                .map(dist -> {
                    double moveScore = (curDepth + 1) * dist * DISTANCE_WEIGHT;
                    if (dist <= 5) {
                        moveScore *= 15;
                    }
                    return moveScore;
                })
                .reduce(0.0, Double::sum);
        return sum;
    }

    private static double calculateLogDistance(ImmutableGameState gameState, Connection conn, int location1, int location2) {
        String checkSql = "SELECT distance FROM all_distances WHERE searchKey=" + ((location1 - 1) * 199 + location2) + ";";
        try (Statement stmt = conn.createStatement(); ResultSet checkRs = stmt.executeQuery(checkSql)) {
            if (checkRs.next()) { // move cursor to first row
                int distance = checkRs.getInt("distance");
                return Math.log((distance - 1) / 3.5) * LOG_DISTANCE_WEIGHT;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    private static double calculateBonuses(ImmutableGameState gameState, boolean isMrX) {
        double sum = 0.0;
        sum += gameState.getPlayerTickets(Piece.MrX.MRX).get().getCount(ScotlandYard.Ticket.SECRET) * SECRET_TICKET_BONUS;
        sum += gameState.getPlayerTickets(Piece.MrX.MRX).get().getCount(ScotlandYard.Ticket.DOUBLE) * DOUBLE_TICKET_BONUS;
        sum += (isMrX ? 1 : -1) * gameState.getAvailableMoves().size() * AVAILABLE_MOVES_BONUS;
        return sum;
    }

    public Double itNegaMax(ImmutableGameState state, int depth, double alpha, double beta,
                            Optional<Integer> mrXLocation, long startTime, Pair<Long, TimeUnit> timeoutPair,
                            Connection conn) {
        boolean changeSign = state.getRemaining().size() == 1;
        boolean isMrX = state.getRemaining().contains(Piece.MrX.MRX);

        // Check timeout
        long curTime = System.currentTimeMillis();
        long oneSecond = 1000;
        if (timeoutPair.left() * 1000 - (curTime - startTime) < oneSecond) {
            return isMrX ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }

        // Check for winner
        if (state.getWinner().contains(Piece.MrX.MRX)) {
            return Double.POSITIVE_INFINITY;
        } else if (!state.getWinner().isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }

        // Compute score at maximum depth
        if (depth == 0) {
            return score(state, mrXLocation, depth, isMrX, conn);
        }

        // Find available moves
        List<Move> moves = state.getAvailableMoves()
                .stream()
                .unordered()
                .parallel()
                .limit(8)
                .toList();

        double value = Double.NEGATIVE_INFINITY;
        for (Move m : moves) {
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

            double newValue = changeSign
                    ? -itNegaMax(state.newState(m), depth - 1, -beta, -alpha, nextMrXLocation, startTime,
                    timeoutPair, conn)
                    : itNegaMax(state.newState(m), depth - 1, alpha, beta, nextMrXLocation, startTime,
                    timeoutPair, conn);

            value = Math.max(value, newValue);
            alpha = Math.max(alpha, value);
            if (alpha >= beta) {
                break;
            }
        }
        return value;
    }

}
