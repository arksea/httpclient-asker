package net.arksea.httpclient.asker;

import org.apache.http.pool.PoolStats;

public interface IAskStat {
    default void onAsk(String askerName, HttpAsk ask) {}
    default void onHandleAsk(String askerName, HttpAsk ask, long waitTime) {}
    default void onResponded(String askerName, HttpAsk ask, long ttl) {}
    default void onLogStats(PoolStats stats, String askerName) {}
}
