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
---

# `cw-ai`

For the AI part of the coursework we created two AIs --- one for Mr. X and the
other for the detectives --- with moves selected via a **negamax**-generated
**game tree**, trying to follow a **functional** style using Java's `Stream`
class. The static evaluation function takes into account distances using
**Dijkstra's** shortest path algorithm in addition to some weights which force
Mr. X to (a) make distance from _all_ the detectives (as opposed to being far
most but close to one) and (b) suggest that double move tickets and secret move
tickets are particularly valuable.

We opted to use **negamax** instead of minimax to avoid code repetition. They
are almost the same algorithm, but negamax makes use of the lemma $\min (a, b)
= - \max (-a, -b)$. In particular, we don't need to first determine if we should
be minimising or maximising and then write two similar pieces of code differing
only by one's use of $\min$ and the other's use of $\max$ --- instead we just
have one piece of code which always takes $\max$ and if the next player is on
the other "team" (that is, Mr. X versus the detectives) the second parameter to
$\max$ is $-\text{negamax}(...)$ with some changes inside the parameters as
well, primarily pertaining to alpha-beta pruning.

<!---
TODO:
Eric:
- scoring - how is it implemented? why? what are the drawbacks?
  (hint: just walk through every step of the scoring function, things like
  moving detectives away from each other, the mapping and logarithm, bonus for
  having secret tickets, etc. limitation: the coefficients are BS).
  
Piotr:
- iterative deepening
- alpha beta

DetectivesAI: prediction for MrXAI location? worth writing about?
--->

---

First, we made an independent class named `Dijkstra` for the shortest distance algorithm.

- Encapsulation as OOP style
- Clever use of Data Structures

TODO IF we go to MySQL THEN include MySQL.

---

Then we created `GameTree` class which contains `score` method and `itNegaMax` method

- Greedy algorithm to calculate the distance between MrX and Detectives
- Comprehensive considerations for each circumstance
- Time-limit check to insure the maximum calculations in the limited time
- Heuristic Programming when iterating the possible next moves 
- alpha-beta pruning
- Cleaner code by implementation of NegaMax algorithm instead of MiniMax algorithm

If we have more time, we could find better weights for each aspect considered by
AI in score function.

TODO how does the score function work?

TODO `itNegaMax` uses the **visitor** pattern to get MrX's `destination(2)`.

---

For better and independent simulations of the Board, we made the class `ImmutableGameState`,

- Wrapper `newState()` for a better encapsulation
- Factory pattern & Adapter pattern for `cloneState()`
- Ensure thread-safety
- All merits for cw-model task

Space allocated by the instantiations of `ImmutableGameState` instance is big.

TODO IF this is rewritten to proxy THEN rewrite to reflect changes

---

TODO ideally we would have some more stuff to do with actual design patterns...
truth is that we didn't have too much use for them, though I'm sure we could
still shoehorn them in...

---

## Extensions

We then extended the game tree generation with the following, each justified in
turn:

- time-limited work
- parallelization (at the top level only)
- alpha-beta pruning
- iterative deepening

### time-limiting

<!--- ERIC --->

TODO merits and limitations

### parallelization

TODO merits and limitations (sample in comments)

<!-- The main advantage of using the `Stream` class (as mentioned also in -->
<!-- [`cw-model`]) is that it makes **parallelization** trivial to accomplish. We use -->
<!-- the method `Stream::parallel` throughout the code, particularly at the top-level -->
<!-- (that is, in the classes `MrXAI` and `DetectivesAI`) so that we can calculate -->
<!-- the scores of moves in parallel and so determine much faster which move is the -->
<!-- best. As well as `Stream::parallel` wrapping the `ForkJoinPool` class, so we do -->
<!-- not have to manage threads manually, we also benefited from the **functional** -->
<!-- style which streams encourage as it meant we did not have to manage any shared -->
<!-- memory which would have introduced overhead in the form of mutual exclusion and -->
<!-- locks. We also opted not to use parallelization _inside_ of the game tree as CPU -->
<!-- usage was already high so using more parallelization would likely have only -->
<!-- introduced more overhead in switching between threads. -->

### alpha-beta pruning

TODO merits and limitations

### iterative deepening

TODO merits and limitations
