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
providing a functional approach, which permits easy parallelization (much more
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
---

# `cw-ai`

<!---
TODO:
- Eric, write about time limiting, Dijkstra, scoring function, ImmutableGameState
- Piotr, write about ItNegamax, FP
- decide who will write about alpha-beta and GameTree
--->

For cw-ai part, we have created both MrXAI and DetectivesAI with a NegaMax game tree. 
-  Extremely neat and understandable code by using Functional Programming style 
---
First, we made an independent class named `Dijkstra` for the shortest distance algorithm.
- Encapsulation as OOP style
- Clever use of Data Structures 
---
Then we created `GameTree` class which contains `score` method and `itNegaMax` method
- Greedy algorithm to calculate the distance between MrX and Detectives
- Comprehensive considerations for each circumstance
- Time-limit check to insure the maximum calculations in the limited time
- Heuristic Programming when iterating the possible next moves 
- alpha-beta pruning
  - (TODO More details could be extended here)
- Cleaner code by implementation of NegaMax algorithm instead of MiniMax algorithm
  - (TODO More details could be extended here? look what Piotr sent to Eric on WhatsApp 
  about NegaMax and iterative deepening!!)

#### `limitations` 
If we have more time, we could find better weights for each aspect considered by AI
in score function. 
---
For better and independent simulations of the Board, we made the class `ImmutableGameState`.
- Wrapper `newState()` for a better encapsulation
- Factory pattern & Adapter pattern for `cloneState()`
- Ensure thread-safety
- All merits for cw-model task

#### `limitations`
Space allocated by the instantiations of `ImmutableGameState` instance is big.

---
Finally, for the top-level API, i.e. `MrXAI` 
- Iterative Deepening Breadth-First Search(IDBFS)
  - (TODO see the WhatsApp message for the benefits of IDBFS compared to DFS)
  - Compared to Breadth-First Search(BFS), because we predict the next possible
  move in the heuristic way in `itNegaMax()` in `GameTree`, IDBFS can let AI have 
  less chance to miss the optimal move.
- Usage of BiFunction class, improve the cleanness of the code
- Time-limit check in each level of IDBFS
- Parallelization(i.e. multi-threads) for each level of IDBFS

#### `limitations`
Big granularity of multi-threads.

---
And `DetectivesAI`
- All the merits for `MrXAI`
- Prediction for the possible current location for MrX

#### `limitations`
Same as `MrXAI`

---
#### `Thoughts for Multi-threads,`
#### `and Explainations for final implementation of it` - `by @Eric(YiQuan Wang)`
In both AI class,`.stream.parallel` allocate new thread when the AI comes to next 
iteration in Iterative-Deepening-Breadth-First-Search, he was afraid
the `Granularity` of multi-threads is too big if doing so, so he tried
to make a customised multiThread version with smaller granularity, and the second
big granularity for this program is to create a new thread when `ItNegamax()`
method is called recursively. And this will cause exponential amount of new
threads be created during the recursion, this might make the program slower
by making `Context Switching` happens too frequently, in this case is mainly
because threads will be switched out of the CPU when it exceeds its `time 
slice` and when the system transitions between user mode and kernel mode.

So `Eric` give up on this solution. After this, he searched that `.stream.parallel` 
will use `ForkJoinWorkerThread` class as underlying code, he considered if 
`ThreadPoolExecutor` class is more suitable under this scenario. The
difference he knows between this and `ThreadPoolExecutor` class is 
`ForkJoinWorkerThread` class uses Fork/Join framework and one of the most
significant features of `Fork/Join framework` is its Work-Stealing Algorithm.

But for our AI, some move will lead to the creation of a deep Negamax tree,
while some will finish the game early, this will cause the imbalance of each task
which is each thread, so the Work-Stealing algorithm is very useful in this 
case, so he chose to keep `.stream.parralel` at last.