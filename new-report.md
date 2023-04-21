---
author:
- Eric Wang
- Piotr Kozicki
header-includes: |
  \usepackage[left=20mm, right=20mm, bottom=20mm]{geometry}
  \usepackage{fancyhdr}
  \pagestyle{fancy}
  \fancyhead[L]{Eric Wang ; Piotr Kozicki}
  \fancyhead[R]{COMS100117 OOP report}
---

<!---
document compilation: requires [pandoc](https://pandoc.org/)
% pandoc report.md -o report.pdf
--->

# `cw-model`

<!---
write about:
- tests passed
- convenience functions
- FP and streams
- factory and observer
--->

We have provided an implementation of `cw-model` which compiles and passes all
the tests. As per the coursework, we implemented a factory class and
demonstrated an understanding of the observer design pattern, via the `MyModel`
class which needs to make use of the `Observer` class, as well as the visitor
pattern. Wherever possible, we have tried to make use of Java's `Stream` class,
providing a functional approach, which permits easy parallelisation (much more
in this in [`cw-ai`]). The Iterable pattern was also useful throughout.

We have provided some utility functions as well, in particular
`getPlayerByPiece`, as we discovered that often in the closed coursework we
needed access to a `Player` but were only given a `Piece`; this method returns
an `Optional<Player>` and we use it throughout the code to avoid repeating the
same piece of code. The getter methods provided are also reused in [`cw-ai`].

The `advance` method uses the visitor pattern: if the move passed to it is for
`MrX` then we need to be able to distinguish between single and double moves.
This is so that we can access the destination(s) of the move, as the `Move`
interface does not provide any method to find out what the destination of a move
is; it follows that the parameter needs to implement either the `SingleMove` or
`DoubleMove` interface so that we can access the destination and so that we can
know if there are two destinations which we need to add to the log. Since we
needed to find out what was the "actual" type of the parameter, which only the
parameter had access to, we made use of the visitor pattern by providing
anonymous classes inside the `advance` method. There are two Visitor classes
because detectives and `MrX` move differently --- in particular, when detectives
move we need to give the ticket they used to `MrX`. We exploited the fact that
inside of the anonymous visitor class we have access to the private methods and
attributes of `MyGameState`, because the anonymous class is inside the
definition of `MyGameState`. In particular, to avoid code repetition inside the
double move implementation for `MrX`
(`advance/Visitor(anonymous)/visit(DoubleMove)`), we identified that a double
move is just a composition of two moves with some caveats: since after the first
move by `MrX` it is the detectives turn, we need to manually overwrite the
remaining pieces after the recursive call to `advance` and recalculate the
moves. This justifies why `remaining` and `moves` are not final and why
`calculateAvailableMoves` is a separate method and not just inside the
constructor.

# `cw-ai`

<!---
TODO:
- Eric, write about time limiting, Dijkstra, scoring function, ImmutableGameState
- Piotr, write about ItNegamax, FP
- decide who will write about alpha-beta and GameTree
--->
