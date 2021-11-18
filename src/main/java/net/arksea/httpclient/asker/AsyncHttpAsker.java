package net.arksea.httpclient.asker;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import net.arksea.httpclient.HttpClientHelper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.Option;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 代理对HttpClientService的请求，目的是为了做AsyncHttpClient回调到Future的模式转换
 * Created by xiaohaixing on 2017/2/24.
 */
public class AsyncHttpAsker extends AbstractActor {
    private static final Logger logger = LogManager.getLogger(AsyncHttpAsker.class);
    private HttpClientService httpClient;

    private AsyncHttpAsker(HttpClientService httpClient) {
        this.httpClient = httpClient;
    }

    static Props props(int maxConnectionTotal,
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
        CloseableHttpAsyncClient client = builder.build();
        client.start();
        return props(client);
    }

    static Props props(CloseableHttpAsyncClient client) {
        HttpClientService httpClient = new HttpClientService(client);
        return Props.create(AsyncHttpAsker.class, (Creator<AsyncHttpAsker>) () -> new AsyncHttpAsker(httpClient));
    }

    @Override
    public void postStop() {
        httpClient.close();
    }

    /**
     * 覆盖Actor.preRestart实现，不调用postStop
     * 这样在重启时不会关闭httpClient，但退出时将会关闭httpClient
     */
    @Override
    public void preRestart(Throwable reason, Option<Object> message) {
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create().match(HttpAsk.class, this::handleAsk).build();
    }

    private void handleAsk(HttpAsk ask) {
        final ActorRef consumer = sender();
        final ActorRef requester = self();
        retryAsk(ask,consumer,requester,ask.retryCount);
    }

    private void retryAsk(HttpAsk ask, ActorRef consumer, ActorRef requester, AtomicInteger retryCount) {
        final long start = System.currentTimeMillis();
        httpClient.ask(ask, new FutureCallback<HttpResult>() {
            @Override
            public void completed(HttpResult ret) {
                int code = ret.response.getStatusLine().getStatusCode();
                boolean isRetryCode = false;
                for (int n : ask.retryCodes) {
                    if (code == n) {
                        isRetryCode = true;
                        break;
                    }
                }
                boolean success;
                if (isRetryCode) {
                    success = false;
                } else if (ask.successCodes.isEmpty()) {
                    success = true;
                } else {
                    success = false;
                    for (int n : ask.successCodes) {
                        if (code == n) {
                            success = true;
                            break;
                        }
                    }
                }
                if (success) {
                    consumer.tell(ret, requester);
                } else {
                    Exception ex = new RuntimeException("error code=" + code);
                    if (isRetryCode) {
                        int c = retryCount.getAndDecrement();
                        logger.debug("http ask failed, rest retry count={},time={}ms",c,System.currentTimeMillis()-start,ex);
                        if (c > 0 && !httpClient.isStopped()) {
                            if (ask.retryCauseConsumer != null) {
                                ask.retryCauseConsumer.accept(ex);
                            }
                            retryAsk(ask, consumer, requester, retryCount);
                        } else {
                            HttpResult result = new HttpResult(ask.tag, ex, null);
                            consumer.tell(result, requester);
                        }
                    } else {
                        HttpResult result = new HttpResult(ask.tag, ex, null);
                        consumer.tell(result, requester);
                    }
                }
            }

            @Override
            public void failed(Exception ex) {
                int c = retryCount.getAndDecrement();
                logger.debug("http ask failed, rest retry count={},time={}ms",c,System.currentTimeMillis()-start,ex);
                if (c > 0 && !httpClient.isStopped()) {
                    if (ask.retryCauseConsumer != null) {
                        ask.retryCauseConsumer.accept(ex);
                    }
                    retryAsk(ask, consumer, requester, retryCount);
                } else {
                    HttpResult result = new HttpResult(ask.tag, ex, null);
                    consumer.tell(result, requester);
                }
            }

            @Override
            public void cancelled() {
                HttpResult result = new HttpResult(ask.tag, new Exception("request cancelled"), null);
                consumer.tell(result, requester);
            }
        });
    }
}

