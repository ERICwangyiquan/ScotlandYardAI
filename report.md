---
author:
- Eric Wang
- Piotr Kozicki
header-includes: |
  \usepackage{fancyhdr}
  \pagestyle{fancy}
  \fancyhead[L]{Eric Wang ; Piotr Kozicki}
  \fancyhead[R]{COMS100117 OOP report}
---

<!---
document compilation: requires [pandoc](https://pandoc.org/)
% pandoc report.md -o report.pdf
--->

Requirements: `(we expect this to be at least 1 page as a summary of what has been done,
and 2 pages maximum reflecting on achievements. A strict maximum of 3 pages applies 
where the report should include some brief critical reflection on the merits and 
limitations of your work).`

# `What has been done`
Our program of closed task has passed all the tests without any warnings or errors.

For open task, we made AI for both Mr.X and Detectives by constructing a game tree using 
Negamax algorithms and pruning the tree using Alpha-Beta-pruning. 

First, a Java class named `Dijkstra` has been created to implement Dijkstra's shortest
path algorithm. Then to solve the problem that "Board" cannot be instantiated,
we made a class named `ImmutableGameState` which implements "Board.GameState" and 
can clone a relatively independent instance of the original instance, all other methods
are same copy from the close task.

### `GameTree`
We defined a GameTree class that is used for creating and searching a game tree for the
Scotland Yard game. The game tree is used to search for the best move for the AI to make 
in a given game state. 

The trans field is a transposition table that maps game states to 
negamax results. The table is used as a heuristic for ordering moves to improve the
alpha-beta runtime. HashTable is used here instead of HashMap to assure thread safety. 

The `ItNegamax` method first checks whether it has timed out or whether Mr. X has won or lost the 
game. If so, it returns the appropriate negamax value. Otherwise, if the search has
reached the maximum depth, the method calculates and returns the score for the given
game state. Otherwise, the method generates the available moves for the current game
state and iterates through them to recursively search the game tree for the best move.
The method uses the alpha-beta algorithm to prune the search space as much as possible.

### `MrXAI` 
This class defining the behavior of an AI player as MrX. The AI player is 
named "Mr.X" and it uses a GameTree to determine the best move to make.

The pickMove method works by creating a score BiFunction that takes an integer representing
the depth of the search and a Move object representing a potential move to make. The score
function uses the ItNegamax method of the gameTree object to score the given move. 
The pickMove method performs an iterative deepening negamax search of the game tree 
to determine the score of the given move. It then uses a loop to try different search depths, 
starting with a depth of 0 and incrementing up to a depth of 3. For each search depth, 
the method uses a stream to iterate over all.

### `DetectivesAI` 
The method first obtains the last revealed location of MrX, if any. Similar to `MrXAI`, 
the function performs iterative deepening up to a depth of 4, and for each depth level,
it parallelizes the evaluation of available moves and selects the move with the highest
score. Finally, it returns the selected move.



# `Achievements`
### `cw-model`

lorem ipsum

### `cw-ai`

lorem ipsum




# `merits and limitations`
## `merits`
draft:
### `cw-model`
- visitor pattern in advance()

### `cw-ai`
- Iterative deepening Negamax algorithm
- factory pattern & adapter pattern for clone() in `GameState`
- visitor pattern in ItNegamax()
- alpha-beta pruning

## `limitations`

lorem ipsum
