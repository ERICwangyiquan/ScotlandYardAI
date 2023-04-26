---
author:
- Eric Wang
- Piotr Kozicki
documentclass: article
classoption: twocolumn
header-includes: |
  \usepackage[left=20mm, right=20mm, bottom=20mm]{geometry}
  \usepackage{fancyhdr}
  \pagestyle{fancy}
  \fancyhead[L]{Eric Wang ; Piotr Kozicki}
  \fancyhead[R]{COMS100117 OOP report}
  \usepackage{hyperref}
---

<!---
document compilation: requires [pandoc](https://pandoc.org/)
% pandoc new-report.md -o report.pdf
--->

# `cw-model`

<!---
TODO highlight keywords and signposts (e.g. names of design patterns,
"justifies"... the parts of the report that we should get particular attention)
using italics (_text_) and bold (**text**).
--->

We have provided an implementation of `cw-model` which compiles and passes all
the tests. As per the coursework, we implemented a **factory** class and
demonstrated an understanding of the **observer** design pattern, via the
`MyModel` class which needs to make use of the `Observer` class, as well as the
**visitor** pattern. Wherever possible, we have tried to make use of Java's
`Stream` class, providing a **functional** approach, which permits easy
**parallelization** (much more in this in [`cw-ai`]). The **iterable** pattern
was also useful throughout.

We have provided some utility functions as well, in particular
`getPlayerByPiece`, as we discovered that often in the closed coursework we
needed access to a `Player` but were only given a `Piece`; this method returns
an `Optional<Player>` and we use it throughout the code to **avoid repeating**
the same piece of code. The getter methods provided are also reused in
[`cw-ai`].

The `advance` method uses the **visitor pattern**: if the move passed to it is
for `MrX` then we need to be able to distinguish between single and double
moves. This is so that we can access the destination(s) of the move, as the
`Move` interface does not provide any method to find out what the destination of
a move is; it follows that the parameter needs to implement either the
`SingleMove` or `DoubleMove` interface so that we can access the destination and
so that we can know if there are two destinations which we need to add to the
log. Since we needed to find out what was the "actual" type of the parameter,
which only the parameter had access to, we made use of the visitor pattern by
providing **anonymous classes** inside the `advance` method. There are two
Visitor classes because detectives and `MrX` move differently --- in particular,
when detectives move we need to give the ticket they used to `MrX`. We exploited
the fact that inside of the anonymous visitor class we have access to the
private methods and attributes of `MyGameState`, because the anonymous class is
inside the definition of `MyGameState`. In particular, to avoid code repetition
inside the double move implementation for `MrX`
(`advance/Visitor(anonymous)/visit(DoubleMove)`), we identified that a double
move is just a **composition** of two moves with some caveats: since after the
first move by `MrX` it is the detectives turn, we need to manually overwrite the
remaining pieces after the recursive call to `advance` and recalculate the
moves. This _justifies_ why `remaining` and `moves` are not final and why
`calculateAvailableMoves` is a separate method and not just inside the
constructor.

# `cw-ai`

(NB: the submitted `cw-ai/` may not compile because it contains two copies of
every class, from two branches. The second branch introduces pre-calcuation of
all the distances via a SQL database)

- `GameTree` class
  - negamax
    - negamax variant of minimax means we don't check whose turn it is and will
      instead check if the turn is _changing_ to pass different parameters in
      the recursive call.
    - time-bounded
    - alpha-beta pruning
    - iterative deepening
    - visitor pattern to access MrX destination after move
  - static scoring function using Dijkstra
    - spread distance between detectives
    - bonus for having secret and double tickets
- functional programming style
  - trivial parallelization with streams
- Dijkstra's shortest path algorithm
  - encapsulation and hiding attributes, using getters
  - we do _not_ account for the spaces covered by detectives
- `ImmutableGameState` class
  - given `Board` "raise" it to `MyGameState` from [`cw-model`] --- like adapter
  - `cloneState` to provide _independent_ copies of an instance
  - ensured thread-safety
  - all merits from [`cw-model`], since just extending that
  - however, the space allocated to many instances is big

---

The reason we had to write the `ImmutableGameState` class was that we were only
given a `Board` but we wanted to use methods like `advance` and `getRemaining`
like in the closed part. The easiest solution was to copy the `MyGameState`
class from the closed part and then extend it to work like an adapter/proxy for
a `Board` which we pass to it.

One possible extension we could have made to the `Dijkstra` class is to
pre-calculate all the distances and store them in a table. We tried this before
by simply storing literals (calculate, print, then store in a static class so we
never have to calculate again) however when we tried to compile this incurred an
error because "the Java file [was] too large".

## Extensions

Here we have some more description on the extensions mentioned in `GameTree`.

- time-limited work
- parallelization (at the top level only)
- alpha-beta pruning
- iterative deepening

### time-limiting

- check time left on each recursive call
  - if there is not much time left, just do static analysis and return.

### parallelization

- easy to implement using Java `Stream` class
- exists at top level, in `MrXAI` and `DetectivesAI`
  - calculate scores of moves in parallel then choose the highest
- `Stream::parallel` wraps `ForkJoinPool` so we avoid manual threading
- functional style reduces overhead on sharing memory

Opted not to use parallelization inside of negamax, so as to avoid additional
overhead due to switching between threads. With parallelization only at the top
level, we already hit high CPU usage.

---

We initially considered making our own multithread version with smaller
granularity due to concern that using `Stream::parallel` would result in too big
granularity. Ultimately we opted to use it because

- it made parallelization much easier
- `Stream::parallel` wraps `ForkJoinWorkerThread` from the Fork/Join framework,
  which has a work-stealing algorithm, suitable to our game tree as some moves
  will result in much bigger subtrees than other moves.

### alpha-beta pruning

- This is a classic and well-documented extension which removes a lot of the
  work which we might otherwise need to do.
- the moves we iterate over inside the game tree are not sorted _and_ limited to
  8 random moves, so the pruning may not be as efficient as it's supposed to be.

### iterative deepening

- The problem with classic negamax is that it is purely depth-first. So by
  limiting computation on time we would not necessarily be able to inspect all
  of the interesting moves at all.
- The solution to this problem is to use a breadth-first search. Iterative
  deepening makes a depth-first algorithm into a breadth-first algorithm.
- This means we should be able to inspect more of the moves at a given depth to
  get a good move even if we run out of time.

### MySQL

- This was intended to provide $O(1)$ access to the distances between points as
  a heuristic for static scoring.
- However, it introduces some overhead in accessing the database

Initially, we tried to store distances as literals, however this would not compile.
