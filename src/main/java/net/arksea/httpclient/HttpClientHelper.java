package net.arksea.httpclient;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import net.arksea.httpclient.asker.AsyncHttpAsker;
import org.apache.http.HeaderElement;
import org.apache.http.HttpResponse;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;

/**
 *
 * Created by xiaohaixing on 2017/5/18.
 */
public class HttpClientHelper {

    public static ConnectionKeepAliveStrategy createKeepAliveStrategy(int aliveSeconds) {
        return new ConnectionKeepAliveStrategy() {
            @Override
            public long getKeepAliveDuration(final HttpResponse response, final HttpContext context) {
                Args.notNull(response, "HTTP response");
                final BasicHeaderElementIterator it = new BasicHeaderElementIterator(response.headerIterator(HTTP.CONN_KEEP_ALIVE));
                while (it.hasNext()) {
                    final HeaderElement he = it.nextElement();
                    final String param = he.getName();
                    final String value = he.getValue();
                    if (value != null && param.equalsIgnoreCase("timeout")) {
                        try {
                            return Long.parseLong(value) * 1000;
                        } catch (final NumberFormatException ignore) {
                            return aliveSeconds * 1000;
                        }
                    }
                }
                return aliveSeconds * 1000;
            }
        };
    }

    public static ActorSelection createAsker(ActorSystem system, int socketTimeout) {
        return createAsker(system,"httpAsker", socketTimeout,4,2,30);
    }

    public static ActorSelection createAsker(ActorSystem system, String askerName,
                                                                 int socketTimeout,
                                                                 int maxConnectionTotal,
                                                                 int maxConnectionPerRoute,
                                                                 int keepAliveSeconds) {
        Props props = AsyncHttpAsker.props(socketTimeout,maxConnectionTotal,maxConnectionPerRoute,keepAliveSeconds);
        system.actorOf(props, askerName);
        return system.actorSelection("/user/"+askerName);
    }
}
