package uk.ac.bris.cs.scotlandyard.ui.ai;

import redis.clients.jedis.Jedis;

public class RedisMap {
    private static final String key = "syMap";
    public static void createTable(ImmutableGameState gameState) {
        //create a table in Redis if it's not existed
        try (Jedis jedis = new Jedis("redis://localhost:6379")){
            if (jedis.hvals(key).size() == 0) {
                for (int start : gameState.getSetup().graph.nodes()) {
                    Dijkstra dijkstra = new Dijkstra(gameState, start);
                    for (int end : gameState.getSetup().graph.nodes()) {
                        jedis.hset(key, String.valueOf((start - 1) * 199 + end), String.valueOf(dijkstra.getDistTo(end)));
                    }
                }
                jedis.expire(key, Integer.MAX_VALUE);
            }
        }
    }
}
