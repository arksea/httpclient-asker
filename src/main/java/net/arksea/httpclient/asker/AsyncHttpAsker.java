package net.arksea.httpclient.asker;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
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
    public final String askerName;
    private final HttpClientService httpClient;
    private final IAskStat askStat;

    private AsyncHttpAsker(HttpClientService httpClient, String askerName, IAskStat askStat) {
        this.httpClient = httpClient;
        this.askerName = askerName;
        if (askStat == null) {
            this.askStat = new IAskStat() {
                @Override
                public void onAsk(String name) {}
                @Override
                public void onHandleAsk(String name) {}
                @Override
                public void onResponded(String name) {}
            };
        } else {
            this.askStat = askStat;
        }
    }

    static Props props(CloseableHttpAsyncClient client, String askName, IAskStat askStat) {
        HttpClientService httpClient = new HttpClientService(client);
        return Props.create(AsyncHttpAsker.class, (Creator<AsyncHttpAsker>) () -> new AsyncHttpAsker(httpClient, askName, askStat));
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
        askStat.onHandleAsk(askerName);
        retryAsk(ask,consumer,requester,ask.retryCount, true);
    }

    private void retryAsk(HttpAsk ask, ActorRef consumer, ActorRef requester, AtomicInteger retryCount, boolean firstAsk) {
        final long start = System.currentTimeMillis();
        httpClient.ask(ask, new FutureCallback<HttpResult>() {
            @Override
            public void completed(HttpResult ret) {
                if (firstAsk) {
                    askStat.onResponded(askerName);
                }
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
                            retryAsk(ask, consumer, requester, retryCount, false);
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
                if (firstAsk) {
                    askStat.onResponded(askerName);
                }
                int c = retryCount.getAndDecrement();
                logger.debug("http ask failed, rest retry count={},time={}ms",c,System.currentTimeMillis()-start,ex);
                if (c > 0 && !httpClient.isStopped()) {
                    if (ask.retryCauseConsumer != null) {
                        ask.retryCauseConsumer.accept(ex);
                    }
                    retryAsk(ask, consumer, requester, retryCount, false);
                } else {
                    HttpResult result = new HttpResult(ask.tag, ex, null);
                    consumer.tell(result, requester);
                }
            }

            @Override
            public void cancelled() {
                if (firstAsk) {
                    askStat.onResponded(askerName);
                }
                HttpResult result = new HttpResult(ask.tag, new Exception("request cancelled"), null);
                consumer.tell(result, requester);
            }
        });
    }
}

