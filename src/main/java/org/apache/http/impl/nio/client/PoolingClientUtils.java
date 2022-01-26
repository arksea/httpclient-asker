package org.apache.http.impl.nio.client;

import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.nio.conn.NHttpClientConnectionManager;
import org.apache.http.pool.PoolStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.Optional;

public class PoolingClientUtils {
    private static final Logger logger = LogManager.getLogger(PoolingClientUtils.class);
    public static Optional<PoolStats> getTotalStats(CloseableHttpAsyncClient client) {
        try {
            if (client instanceof InternalHttpAsyncClient) {
                Field f = InternalHttpAsyncClient.class.getDeclaredField("connmgr");
                f.setAccessible(true);
                NHttpClientConnectionManager connmgr = (NHttpClientConnectionManager) f.get(client);
                if (connmgr instanceof PoolingNHttpClientConnectionManager) {
                    PoolingNHttpClientConnectionManager p = (PoolingNHttpClientConnectionManager) connmgr;
                    return Optional.of(p.getTotalStats());
                }
            }
        } catch (Exception ex) {
            logger.warn("get PoolStats failed", ex);
        }
        return Optional.empty();
    }
}
