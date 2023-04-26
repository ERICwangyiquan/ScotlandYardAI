package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.*;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.collectingAndThen;

public final class ImmutableGameState implements Board.GameState {
    private final GameSetup setup;
    private final ImmutableSet<Piece> winner;
    private ImmutableSet<Piece> remaining; // which pieces still need to make a move
    private ImmutableList<LogEntry> log;
    private Player mrX;
    private List<Player> detectives;
    private ImmutableSet<Move> moves;

    private ImmutableGameState(
            final GameSetup setup,
            final ImmutableSet<Piece> remaining,
            final ImmutableList<LogEntry> log,
            @Nonnull final Player mrX,
            @Nonnull final List<Player> detectives) {
        // check that there are no null detectives
        if (detectives.stream().anyMatch(Objects::isNull))
            throw new NullPointerException();
        // check that pieces given are in the correct order
        if (mrX.piece() != Piece.MrX.MRX || detectives.stream().anyMatch(d -> d.piece() == Piece.MrX.MRX))
            throw new IllegalArgumentException();
        // check there are no duplicate pieces
        if (detectives.stream().distinct().count() != detectives.size())
            throw new IllegalArgumentException();
        // check no pieces overlap
        if (detectives.stream().map(Player::location).distinct().count() != detectives.size())
            throw new IllegalArgumentException();
        // check players only have the tickets they are allowed to have
        if (detectives.stream()
                .anyMatch(d -> d.tickets().get(ScotlandYard.Ticket.SECRET) > 0 || d.tickets().get(ScotlandYard.Ticket.DOUBLE) > 0))
            throw new IllegalArgumentException();
        // check that the game setup is valid
        if (setup.moves.isEmpty())
            throw new IllegalArgumentException();
        if (setup.graph.nodes().isEmpty())
            throw new IllegalArgumentException();
        // finish by initialising values
        this.setup = setup;
        this.remaining = remaining;
        this.log = log;
        this.mrX = mrX;
        this.detectives = detectives;
        this.moves = calculateAvailableMoves();
        this.winner = calculateWinner();
        if (!this.winner.isEmpty())
            this.moves = ImmutableSet.of();
    }

    public ImmutableGameState(Board board, int destForMrX) {   // needs to pass in the n
        this.setup = board.getSetup();
        this.log = board.getMrXTravelLog();
        if (board.getAvailableMoves().stream().anyMatch(m -> m.commencedBy() == Piece.MrX.MRX))
            this.remaining = ImmutableSet.of(Piece.MrX.MRX);
        else this.remaining = board.getPlayers().stream().filter(p -> !p.equals(Piece.MrX.MRX))
                .collect(collectingAndThen(Collectors.toSet(), ImmutableSet::copyOf));
        HashMap<ScotlandYard.Ticket, Integer> mrXTickets = new HashMap<>();
        for (var ticket : ScotlandYard.Ticket.values()) {   // to new a mrX
            mrXTickets.put(ticket, Objects.requireNonNull(board.getPlayerTickets(Piece.MrX.MRX).orElse(null)).getCount(ticket));
        }
        this.mrX = new Player(Piece.MrX.MRX, ImmutableMap.copyOf(mrXTickets), destForMrX);   // needs to pass MrX's location explicitly
        List<Player> detects = new ArrayList<>();   // to new detectives
        for (Piece detect : board.getPlayers().stream().filter(d -> !d.equals(Piece.MrX.MRX)).toList()) {
            HashMap<ScotlandYard.Ticket, Integer> detectTickets = new HashMap<>();
            for (var ticket : ScotlandYard.Ticket.values()) {
                detectTickets.put(ticket, Objects.requireNonNull(board.getPlayerTickets(detect).orElse(null)).getCount(ticket));
            }
            detects.add(new Player(detect, ImmutableMap.copyOf(detectTickets), board.getDetectiveLocation((Piece.Detective) detect).orElse(null)));
        }
        this.detectives = detects;
        this.moves = board.getAvailableMoves();
        this.winner = board.getWinner();
    }

    private ImmutableGameState(ImmutableGameState gameState) {
        this.setup = gameState.setup;
        // all copy-on-write
        this.remaining = gameState.remaining.stream().collect(collectingAndThen(Collectors.toSet(), ImmutableSet::copyOf));
        this.log = gameState.log.stream().collect(collectingAndThen(Collectors.toList(), ImmutableList::copyOf));
        this.mrX = new Player(gameState.mrX.piece(), gameState.mrX.tickets(), gameState.mrX.location());
        this.detectives = List.copyOf(gameState.detectives);
        this.moves = gameState.moves.stream().collect(collectingAndThen(Collectors.toSet(), ImmutableSet::copyOf));
        this.winner = gameState.winner.stream().collect(collectingAndThen(Collectors.toSet(), ImmutableSet::copyOf));
    }

    private ImmutableGameState cloneState() {
        return new ImmutableGameState(this);
    }

    /* calculate and return the possible moves, using the value of the class attrubute `remaining`.
     */
    private ImmutableSet<Move> calculateAvailableMoves() {
        Set<Move> possibleMoves = new HashSet<>();
        for (Piece piece : remaining) {
            final Player player = getPlayerByPiece(piece).get(); // piece in remaining guarantees presence
            int source = player.location();
            for (Integer dest1 : setup.graph.adjacentNodes(source)) {
                // HACK should avoid using continue
                if (detectives.stream().anyMatch((d) -> d.location() == dest1))
                    continue;
                for (ScotlandYard.Transport t1 : setup.graph.edgeValueOrDefault(source, dest1, ImmutableSet.of())) {
                    ScotlandYard.Ticket ticket1 = t1.requiredTicket();
                    if (player.has(ticket1)) {
                        possibleMoves.add(new Move.SingleMove(piece, source, ticket1, dest1));
                    }
                    if (player.has(ScotlandYard.Ticket.SECRET)) {
                        possibleMoves.add(new Move.SingleMove(piece, source, ScotlandYard.Ticket.SECRET, dest1));
                    }
                    // check also that there are enough remaining moves
                    if (player.has(ScotlandYard.Ticket.DOUBLE) && setup.moves.size() - log.size() >= 2) {
                        for (Integer dest2 : setup.graph.adjacentNodes(dest1)) {
                            // HACK as above
                            if (detectives.stream().anyMatch((d) -> d.location() == dest2))
                                continue;
                            for (ScotlandYard.Transport t2 : setup.graph.edgeValueOrDefault(dest1, dest2, ImmutableSet.of())) {
                                ScotlandYard.Ticket ticket2 = t2.requiredTicket();
                                if ((ticket1.equals(ticket2) && player.hasAtLeast(ticket1, 2))
                                        || (!ticket1.equals(ticket2) && player.has(ticket1)
                                        && player.has(ticket2))) {
                                    possibleMoves.add(
                                            new Move.DoubleMove(piece, source, ticket1, dest1, ticket2, dest2));
                                }
                                if (player.has(ScotlandYard.Ticket.SECRET) && !ticket2.equals(ScotlandYard.Ticket.SECRET)) {
                                    possibleMoves.add(new Move.DoubleMove(piece, source, ScotlandYard.Ticket.SECRET, dest1,
                                            ticket2, dest2));
                                }
                                if (player.has(ScotlandYard.Ticket.SECRET) && !ticket1.equals(ScotlandYard.Ticket.SECRET)) {
                                    possibleMoves.add(new Move.DoubleMove(piece, source, ticket1, dest1,
                                            ScotlandYard.Ticket.SECRET, dest2));
                                }
                                if (player.hasAtLeast(ScotlandYard.Ticket.SECRET, 2)) {
                                    possibleMoves.add(new Move.DoubleMove(piece, source, ScotlandYard.Ticket.SECRET, dest1,
                                            ScotlandYard.Ticket.SECRET, dest2));
                                }
                            }
                        }
                    }
                }
            }
        }
        return ImmutableSet.copyOf(possibleMoves);
    }

    private ImmutableSet<Piece> calculateWinner() {
        // convenient constants to use as return values
        final ImmutableSet<Piece> detectiveSet = detectives.stream().map(Player::piece)
                .collect(ImmutableSet.toImmutableSet());
        final ImmutableSet<Piece> mrXSet = ImmutableSet.of(mrX.piece());

        final Set<Integer> detectiveLocations = detectives.stream().map(Player::location)
                .collect(Collectors.toSet());

        if (detectives.stream().anyMatch((d) -> d.location() == mrX.location()))
            return detectiveSet;

        if (remaining.contains(mrX.piece())) {
            if (log.size() == setup.moves.size())
                return mrXSet;
            // mrX doesn't have any tickets to go somewhere that a detective isn't covering
            if (setup.graph.adjacentNodes(mrX.location())
                    .stream()
                    .filter(n -> !detectiveLocations.contains(n))
                    .noneMatch(n -> Objects.requireNonNull(setup.graph.edgeValueOrDefault(n, mrX.location(), ImmutableSet.of()))
                            .stream()
                            .anyMatch(t -> mrX.has(t.requiredTicket()) || mrX.has(ScotlandYard.Ticket.SECRET))))
                return detectiveSet;
        }

        // no detective has a ticket to go somewhere not already covered
        if (detectives.stream()
                .noneMatch(d -> setup.graph.adjacentNodes(d.location()).stream()
                        .anyMatch(n -> setup.graph.edgeValueOrDefault(d.location(), n, ImmutableSet.of()).stream()
                                .anyMatch(t -> d.has(t.requiredTicket())))))
            return mrXSet;

        return ImmutableSet.of();
    }

    private Optional<Player> getPlayerByPiece(Piece piece) {
        if (piece.isMrX())
            return Optional.of(mrX);
        else
            return detectives.stream().filter((d) -> d.piece() == piece).findAny();
    }

    @Nonnull
    @Override
    public GameSetup getSetup() {
        return setup;
    }

    @Nonnull
    @Override
    public ImmutableSet<Piece> getPlayers() {
        return Stream.concat(
                        Stream.of(mrX),
                        detectives.stream())
                .map(Player::piece)
                .collect(collectingAndThen(Collectors.toSet(), ImmutableSet::copyOf));
    }

    @Nonnull
    @Override
    public Optional<Integer> getDetectiveLocation(Piece.Detective detective) {
        for (Player d : detectives) {
            // NB Detective is a subclass of Piece, so we can compare them
            if (d.piece().equals(detective)) {
                return Optional.of(d.location());
            }
        }
        return Optional.empty();
    }

    @Nonnull
    @Override
    public Optional<TicketBoard> getPlayerTickets(Piece piece) {
        // (effectively) final variable required for use in inner class
        // initialised in block directly after, using non-final variable
        final Optional<Player> player = getPlayerByPiece(piece);
        return player.flatMap(p -> Optional.of(new TicketBoard() {
            @Override
            public int getCount(ScotlandYard.Ticket ticket) {
                return p.tickets().get(ticket);
            }
        }));
    }

    @Nonnull
    @Override
    public ImmutableList<LogEntry> getMrXTravelLog() {
        return log;
    }

    @Nonnull
    @Override
    public ImmutableSet<Piece> getWinner() {
        return winner;
    }

    @Nonnull
    public ImmutableSet<Piece> getRemaining() {
        return remaining;
    }

    @Nonnull
    @Override
    public ImmutableSet<Move> getAvailableMoves() {
        return moves;
    }

    @Override
    public ImmutableGameState advance(Move move) {
        if (!moves.contains(move))
            throw new IllegalArgumentException();

        // a move cannot be made by a piece which does not exist
        final Player player = getPlayerByPiece(move.commencedBy()).get();

        Integer moveNumber = log.size();
        if (move.commencedBy().isMrX()) {
            return move.accept(new Move.Visitor<ImmutableGameState>() {

                @Override
                public ImmutableGameState visit(Move.SingleMove move) {
                    mrX = mrX.use(move.ticket).at(move.destination);
                    List<LogEntry> l = new ArrayList<>(log.asList());
                    if (setup.moves.get(moveNumber))
                        l.add(LogEntry.reveal(move.ticket, move.destination));
                    else
                        l.add(LogEntry.hidden(move.ticket));
                    log = ImmutableList.copyOf(l);
                    return new ImmutableGameState(setup,
                            ImmutableSet.copyOf(detectives.stream().map(Player::piece).collect(Collectors.toSet())),
                            log, mrX, detectives);
                }

                /*
                 * Using composition of states (recalculation necessary midway) instead of
                 * repeating code
                 */
                @Override
                public ImmutableGameState visit(Move.DoubleMove move) {
                    mrX = mrX.use(ScotlandYard.Ticket.DOUBLE);
                    final Move.SingleMove firstMove = new Move.SingleMove(move.commencedBy(), move.source(), move.ticket1,
                            move.destination1);
                    final Move.SingleMove secondMove = new Move.SingleMove(move.commencedBy(), move.destination1,
                            move.ticket2, move.destination2);
                    final ImmutableGameState InterGameState = this.visit(firstMove);
                    InterGameState.remaining = ImmutableSet.of(mrX.piece());
                    // if we do not recalculate the available moves, the advance method will throw
                    // because it should be a detective's turn.
                    InterGameState.moves = InterGameState.calculateAvailableMoves();
                    return InterGameState.advance(secondMove);
                }

            });
        } else { // commenced by detective
            return move.accept(new Move.Visitor<ImmutableGameState>() {

                @Override
                public ImmutableGameState visit(Move.SingleMove move) {
                    Player detective = player;
                    detective = detective.use(move.ticket).at(move.destination);
                    mrX = mrX.give(move.ticket);
                    Player finalDetective = detective;
                    detectives = detectives.stream()
                            .map(d -> d.piece().equals(finalDetective.piece()) ? finalDetective : d)
                            .collect(Collectors.toList());
                    List<Piece> l = new ArrayList<>(remaining.asList());
                    l.remove(detective.piece());// +
                    remaining = ImmutableSet.copyOf(l);
                    var gameStateHasLeftDetectives = new ImmutableGameState(setup, remaining, log, mrX, detectives);
                    var gameStateForNextRound = new ImmutableGameState(setup, ImmutableSet.of(Piece.MrX.MRX), log, mrX,
                            detectives);
                    if (!remaining.isEmpty())
                        if (gameStateHasLeftDetectives.calculateAvailableMoves().isEmpty())
                            return gameStateForNextRound;
                        else
                            return gameStateHasLeftDetectives;
                    else
                        return gameStateForNextRound;
                }

                // detectives cannot make double moves
                @Override
                public ImmutableGameState visit(Move.DoubleMove move) {
                    return null;
                }

            });
        }
    }

    public List<Player> getDetectives() {
        return detectives;
    }

    public ImmutableGameState newState (Move move) {
        return this.cloneState().advance(move);
    }
}
