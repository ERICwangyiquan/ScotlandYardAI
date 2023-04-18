package uk.ac.bris.cs.scotlandyard.ui.ai;


import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.stream.Collectors;


public final class Dijkstra {
    public static final class Edge {
        public int fromPoint;
        public int toPoint;
        public final int dist;

        private Edge(int fromPoint, int toPoint, int dist) {
            this.fromPoint = fromPoint;
            this.toPoint = toPoint;
            this.dist = dist;
        }
    }

    public int[] distTo;   // thread-safe
    private PriorityQueue<Edge> pq;

    public Dijkstra(@Nonnull ImmutableGameState gameState, int detectLocation) {
        distTo = new int[gameState.getSetup().graph.nodes().size() + 1];  // locations start from 1
        Arrays.fill(distTo, Integer.MAX_VALUE);
        distTo[detectLocation] = 0;     // start from detective's location, good for `detectiveAI`
        pq = new PriorityQueue<>(gameState.getSetup().graph.nodes().size(), (e1, e2) -> distTo[e1.toPoint] - distTo[e2.toPoint]); // ascending
        pq.add(new Edge(detectLocation, detectLocation, 0));

        while (!pq.isEmpty()) {
            Edge curEdge = pq.remove();
            int curEndLocation = curEdge.toPoint;
            for (int nearNodes : gameState.getSetup().graph.adjacentNodes(curEndLocation)) {
                relax(new Edge(curEndLocation, nearNodes, 1));
            }
        }
    }

    private void relax(Edge e) {
        int v = e.fromPoint, w = e.toPoint;
        if (distTo[w] > distTo[v] + e.dist) {
            distTo[w] = distTo[v] + e.dist;
            if (pq.stream().anyMatch(edge -> edge.toPoint == w)) {
                pq = pq.stream().filter(edge -> edge.toPoint != w).collect(Collectors.toCollection(PriorityQueue::new));
            } else {
                pq.add(e);
            }
        }
    }
}
