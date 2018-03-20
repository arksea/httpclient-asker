package net.arksea.httpclient.asker;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import net.arksea.httpclient.HttpClientHelper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;

/**
 * 代理对HttpClientService的请求，目的是为了做AsyncHttpClient回调到Future的模式转换
 * Created by xiaohaixing on 2017/2/24.
 */
public class AsyncHttpAsker extends UntypedActor {

    private HttpClientService httpClient;

    public AsyncHttpAsker(HttpClientService httpClient) {
        this.httpClient = httpClient;
    }

    public static Props props(int maxConnectionTotal,
                              int maxConnectionPerRoute,
                              int keepAliveSeconds) {
        return props(maxConnectionTotal,maxConnectionPerRoute,keepAliveSeconds, null);
    }

    public static Props props(int maxConnectionTotal,
                              int maxConnectionPerRoute,
                              int keepAliveSeconds,
                              RequestConfig defaultRequestConfig) {
        HttpAsyncClientBuilder builder = HttpAsyncClients.custom()
            .setMaxConnTotal(maxConnectionTotal)
            .setMaxConnPerRoute(maxConnectionPerRoute)
            .setKeepAliveStrategy(HttpClientHelper.createKeepAliveStrategy(keepAliveSeconds));
        if (defaultRequestConfig != null) {
            builder.setDefaultRequestConfig(defaultRequestConfig);
        }
        return props(builder);
    }

    public static Props props(HttpAsyncClientBuilder builder) {
        HttpClientService httpClient = new HttpClientService(builder);
        return Props.create(AsyncHttpAsker.class, new Creator<AsyncHttpAsker>() {
            @Override
            public AsyncHttpAsker create() throws Exception {
                return new AsyncHttpAsker(httpClient);
            }
        });
    }

    @Override
    public void onReceive(Object o) throws Throwable {
        if (o instanceof HttpAsk) {
            HttpAsk get = (HttpAsk) o;
            handleAsk(get);
        } else {
            unhandled(o);
        }
    }

    @Override
    public void postStop() throws Exception {
        httpClient.close();
    }

    private void handleAsk(HttpAsk ask) {
        final ActorRef consumer = sender();
        final ActorRef requester = self();
        httpClient.ask(ask, new FutureCallback<HttpResult>() {
            @Override
            public void completed(HttpResult result) {
                consumer.tell(result, requester);
            }

            @Override
            public void failed(Exception ex) {
                HttpResult result = new HttpResult(ask.tag, ex, null);
                consumer.tell(result, requester);
            }

            @Override
            public void cancelled() {
                HttpResult result = new HttpResult(ask.tag, new Exception("request cancelled"), null);
                consumer.tell(result, requester);
            }
        });
    }
}

