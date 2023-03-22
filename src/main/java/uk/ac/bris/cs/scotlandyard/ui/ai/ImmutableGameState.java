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
    // attributes: (线程不安全)graph; mrX's location; detective's locations; remaining players Immu-Set,最开始是mrX; (线程安全)HashTable存放每个人剩余tickets
    //				availableMoves;
    // methods: getAvailableMoves; changeLocation(who, move);


    private final GameSetup setup;
    private ImmutableSet<Piece> remaining; // which pieces still need to make a move
    private ImmutableList<LogEntry> log;
    private Player mrX;
    private List<Player> detectives;
    private ImmutableSet<Move> moves;
    private final ImmutableSet<Piece> winner;

    // TODO 在这创建一个单粒模式对象来作为constructor的synchronized锁 (0.1% of using lock like this, Piotr please ignore this)

    private ImmutableGameState(
            final GameSetup setup,
            final ImmutableSet<Piece> remaining,
            final ImmutableList<LogEntry> log,
            @Nonnull final Player mrX,
            @Nonnull final List<Player> detectives) {
        // check that there are no null detectives
        for (Player detective : detectives) if (detective == null) throw new NullPointerException();
        // check that pieces given are in the correct order
        if (mrX.piece() != Piece.MrX.MRX) throw new IllegalArgumentException();
        for (Player detective : detectives)
            if (detective.piece() == Piece.MrX.MRX) throw new IllegalArgumentException();
        // check there are no duplicate pieces
        Set<Piece> pieces = new HashSet<>();
        for (Player detective : detectives) {
            if (pieces.contains(detective.piece())) {
                throw new IllegalArgumentException();
            } else {
                pieces.add(detective.piece());
            }
        }
        // check no pieces overlap
        Set<Integer> locations = new HashSet<>();
        for (Player detective : detectives) {
            if (locations.contains(detective.location())) {
                throw new IllegalArgumentException();
            } else {
                locations.add(detective.location());
            }
        }
        // check players only have the tickets they are allowed to have
        for (Player detective : detectives)
            if (detective.tickets().get(ScotlandYard.Ticket.SECRET) != 0)
                throw new IllegalArgumentException();
        for (Player detective : detectives)
            if (detective.tickets().get(ScotlandYard.Ticket.DOUBLE) != 0)
                throw new IllegalArgumentException();
        // check that the game setup is valid
        if (setup.moves.isEmpty()) throw new IllegalArgumentException();
        if (setup.graph.nodes().isEmpty()) throw new IllegalArgumentException();
        // finish by initialising values
        this.setup = setup;
        this.remaining = remaining;
        this.log = log;
        this.mrX = mrX;
        this.detectives = detectives;
        this.moves = calculateAvailableMoves();
        this.winner = calculateWinner();
        if (!this.winner.isEmpty()) this.moves = ImmutableSet.of();
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


    protected ImmutableGameState clone() {
        return ImmutableGameState.of(this);
    }

    /* calculate and return the possible moves, using the value of the class attrubute `remaining`.
     */
    private ImmutableSet<Move> calculateAvailableMoves() {
        Set<Move> possibleMoves = new HashSet<>();
        for (Piece piece : remaining) {
            final Player player = getPlayerByPiece(piece);
            int source = player.location();
            for (Integer dest1 : setup.graph.adjacentNodes(source)) {
                // HACK should avoid using continue
                if (detectives.stream().anyMatch((d) -> d.location() == dest1)) continue;
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
                            if (detectives.stream().anyMatch((d) -> d.location() == dest2)) continue;
                            for (ScotlandYard.Transport t2 : setup.graph.edgeValueOrDefault(dest1, dest2, ImmutableSet.of())) {
                                ScotlandYard.Ticket ticket2 = t2.requiredTicket();
                                if ((ticket1.equals(ticket2) && player.hasAtLeast(ticket1, 2))
                                        || (!ticket1.equals(ticket2) && player.has(ticket1) && player.has(ticket2))) {
                                    possibleMoves.add(new Move.DoubleMove(piece, source, ticket1, dest1, ticket2, dest2));
                                }
                                if (player.has(ScotlandYard.Ticket.SECRET) && !ticket2.equals(ScotlandYard.Ticket.SECRET)) {
                                    possibleMoves.add(new Move.DoubleMove(piece, source, ScotlandYard.Ticket.SECRET, dest1, ticket2, dest2));
                                }
                                if (player.has(ScotlandYard.Ticket.SECRET) && !ticket1.equals(ScotlandYard.Ticket.SECRET)) {
                                    possibleMoves.add(new Move.DoubleMove(piece, source, ticket1, dest1, ScotlandYard.Ticket.SECRET, dest2));
                                }
                                if (player.hasAtLeast(ScotlandYard.Ticket.SECRET, 2)) {
                                    possibleMoves.add(new Move.DoubleMove(piece, source, ScotlandYard.Ticket.SECRET, dest1, ScotlandYard.Ticket.SECRET, dest2));
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
        ImmutableSet<Piece> detectiveSet = detectives.stream().map(Player::piece).collect(ImmutableSet.toImmutableSet());
        ImmutableSet<Piece> mrXSet = ImmutableSet.of(mrX.piece());
        if (detectives.stream().anyMatch((d) -> d.location() == mrX.location())) return detectiveSet;

        if (remaining.contains(mrX.piece())) {
            if (log.size() == setup.moves.size()) return mrXSet;
            boolean mrXStuck = true;
            for (int adjacentNode : setup.graph.adjacentNodes(mrX.location())) {
                boolean IfOccupied = detectives.stream().anyMatch(d -> d.location() == adjacentNode);
                if (!IfOccupied) {
                    for (ScotlandYard.Transport t : setup.graph.edgeValueOrDefault(adjacentNode, mrX.location(), ImmutableSet.of())) {
                        if ((mrX.has(t.requiredTicket()) || mrX.has(ScotlandYard.Ticket.SECRET))) mrXStuck = false;
                    }
                }
            }
            if (mrXStuck) return detectiveSet;
        }

        // TODO "mr x cornered but can escape"
        // need to figure out what this means, but currently this is giving a detective win when the game is not yet over

        // if it's the detectives' turn now, mrX might get a ticket and then be able to make a move
        // so just because he's stuck currently, does not mean he will be stuck and captured when his turn comes around
        // in short: mrx should only lose from being cornered if it is his turn

        /*
         * NB the rules for whether the game is over are slightly different depending on whose turn it is
         *
         */

        // only interested in checking if mrX is cornered if it is his turn?
        // calculations about whether mrX is cornered are actually more complex than first appears...
        //
        // what if all the detectives who are surrounding mrX have finished their moves?
        // ideally, we only need to check on mrX's turn, because we're lazy
        // but there's a couple of extra criteria
        // - suppose MRX needs a TAXI ticket to move somewhere, but doesn't have any
        //   - if no detectives have any TAXI tickets, then MRX can't move when his turn comes around. Even though it might not be his turn now, he has already lost
        //   - if there are detectives that have a TAXI ticket, MRX might be able to move once his turn comes around
        // - suppose MRX is surrounded
        //   - if the detectives who are surrounding him have all finished their moves (NB this does not mean ALL the detectives have finished their moves!), then MRX has lost
        //   - however, if at least on of those detectives still needs to make a move, there may be a space after their move

        // first, look at what transport options mrX requires
        // then see if any of the detectives have them
        // then see if there are any spots which mrX can move to
        // but it's more lenient, because we want to know if the detective who is (if there is) there, has made a move

//			Set<Ticket> mrXNeedsAtLeastOne = new HashSet<>(){{add(Ticket.SECRET);}};
//			for (Integer dest : setup.graph.adjacentNodes(mrX.location())) {
//				for (Transport t: setup.graph.edgeValueOrDefault(mrX.location(), dest, ImmutableSet.of())) {
//					mrXNeedsAtLeastOne.add(t.requiredTicket());
//				}
//			}

//			Set <Ticket> mrXMightHave = null;
//			// TODO get the tickets which mrX currently has U { if mrX's turn, then empty ; else, detective's tickets who have not yet moved }
//
//			// TODO use that above
//
//			// now looking at if mrX is cornered
//			Boolean mrXCornered = Set.copyOf(setup.graph.adjacentNodes(mrX.location())).stream()
//					.filter((l) -> true) // det <- detAt l ; case det of Nothing -> true; Just d -> detective can and must move
//					.collect(Collectors.toSet()).isEmpty();
//
//			if (remaining.contains(mrX) && log.size() == setup.moves.size()) return mrXSet;
//
        // NB detectives cannot get more tickets, so the above doesn't really apply
        boolean allDetectivesStuck = true;
        for (Player detective : detectives) {
            for (Integer dest : setup.graph.adjacentNodes(detective.location())) {
                for (ScotlandYard.Transport t : setup.graph.edgeValueOrDefault(detective.location(), dest, ImmutableSet.of())) {
                    if (detective.has(t.requiredTicket())) {
                        allDetectivesStuck = false;
                    }
                }
            }
        }
        if (allDetectivesStuck) return mrXSet;
        return ImmutableSet.of();
    }

    private Player getPlayerByPiece(Piece piece) {
        Player player = null;
        if (piece.isMrX()) player = mrX;
        else {
            try {
                player = detectives.stream().filter((d) -> d.piece() == piece).findAny().get();
            } catch (NoSuchElementException e) {
                player = null;
            }
        }
        return player;
    }

    @Override
    public GameSetup getSetup() {
        return setup;
    }

    @Override
    public ImmutableSet<Piece> getPlayers() {
        return Stream.concat(
                        Stream.of(mrX),
                        detectives.stream())
                .map((Player p) -> p.piece())
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
        final Player player = getPlayerByPiece(piece);
        if (player == null) {
            return Optional.empty();
        } else {
            return Optional.of(new TicketBoard() {
                @Override
                public int getCount(ScotlandYard.Ticket ticket) {
                    return player.tickets().get(ticket);
                }
            });
        }
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
    public ImmutableGameState advance(Move move) {         // TODO maybe multi-thread here? ReentrantReadWriteLock/synchronized
        if (!moves.contains(move)) throw new IllegalArgumentException();
        // want to use visitor pattern to distinguish between SingleMove and DoubleMove
        // Move.FunctionalVisitor<T> ?
        // then remove the tickets from the player, set their new location?
        // aha, Player#at : int newLocation -> Player -- so technically both, FP
        // in doing so we make a new "set" of players, the setup doesn't change I think
        // NOTE detective tickets `give` to mrX, mrX tickets are used up.
        // NOTE do we need to reveal moves here? YES, using constructors LogEntry#{hidden,reveal}
        // because of how double moves behave, this means we will need a special behaviour for these
        // ... do we actually care if there is a double or single move?
        // we can just carry out a very quick check because it can be convenient, I guess...
        // but we could just write it to be general in the first place

        // TODO this snippet shows up multiple times, refactor
        final Player player;
        if (move.commencedBy().isMrX()) player = mrX;
        else player = detectives.stream().filter((d) -> d.piece() == move.commencedBy()).findAny().get();

        Integer moveNumber = log.size();
        if (move.commencedBy().isMrX()) {
            return move.accept(new Move.Visitor<ImmutableGameState>() {

                @Override
                public ImmutableGameState visit(Move.SingleMove move) {
                    // TODO 上锁 {
                    mrX = mrX.use(move.ticket).at(move.destination);
                    List<LogEntry> l = new ArrayList<>(log.asList());
                    if (setup.moves.get(moveNumber))
                        l.add(LogEntry.reveal(move.ticket, move.destination));
                    else l.add(LogEntry.hidden(move.ticket));
                    log = ImmutableList.copyOf(l);
                    // TODO }
                    return new ImmutableGameState(setup, ImmutableSet.copyOf(detectives.stream().map(Player::piece).collect(Collectors.toSet())),
                            log, mrX, detectives);
                }

                /* here I am trying to do some composition which is easier than some special handling,
                 * but it's a bit sketchy so visit this if there's some weird behaviour
                 */
                @Override
                public ImmutableGameState visit(Move.DoubleMove move) {
                    // TODO 上锁 {
                    mrX = mrX.use(ScotlandYard.Ticket.DOUBLE);
                    final Move.SingleMove firstMove = new Move.SingleMove(move.commencedBy(), move.source(), move.ticket1, move.destination1);
                    final Move.SingleMove secondMove = new Move.SingleMove(move.commencedBy(), move.destination1, move.ticket2, move.destination2);
                    final ImmutableGameState InterGameState = this.visit(firstMove);
                    InterGameState.remaining = ImmutableSet.of(mrX.piece());
                    // if we do not recalculate the available moves, the advance method will throw because it should be a detective's turn.
                    InterGameState.moves = InterGameState.calculateAvailableMoves();
                    // TODO }
                    return InterGameState.advance(secondMove);
                }

            });
        } else { // commenced by detective
            return move.accept(new Move.Visitor<ImmutableGameState>() {

                @Override
                public ImmutableGameState visit(Move.SingleMove move) {
                    // TODO 上锁 {
                    Player detective = player;
                    detective = detective.use(move.ticket).at(move.destination);
                    mrX = mrX.give(move.ticket);
                    Player finalDetective = detective;
                    detectives = detectives.stream().map(d -> {
                        if (d.piece().equals(finalDetective.piece())) return finalDetective;
                        else return d;
                    }).collect(Collectors.toList());
                    List<Piece> l = new ArrayList<>(remaining.asList());
                    l.remove(detective.piece());// +
                    remaining = ImmutableSet.copyOf(l);
                    // TODO }
                    var gameStateHasLeftDetectives = new ImmutableGameState(setup, remaining, log, mrX, detectives);
                    var gameStateForNextRound = new ImmutableGameState(setup, ImmutableSet.of(Piece.MrX.MRX), log, mrX, detectives);
                    if (!remaining.isEmpty())
                        if (gameStateHasLeftDetectives.calculateAvailableMoves().isEmpty())
                            return gameStateForNextRound;
                        else return gameStateHasLeftDetectives;
                    else return gameStateForNextRound;
                }

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

    private static @Nonnull ImmutableGameState of(ImmutableGameState gameState) {
        return new ImmutableGameState(gameState);
    }
}
