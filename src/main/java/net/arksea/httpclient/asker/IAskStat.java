package net.arksea.httpclient.asker;

import org.apache.http.pool.PoolStats;

public interface IAskStat {
    default void onAsk(String askerName) {}
    default void onHandleAsk(String askerName, long waitTime) {}
    default void onResponded(String askerName, long ttl) {}
    default void onLogStats(PoolStats stats, String askerName) {}
}
