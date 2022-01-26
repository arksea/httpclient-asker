package net.arksea.httpclient.asker;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 代理对HttpClientService的请求，目的是为了做AsyncHttpClient回调到Future的模式转换
 * Created by xiaohaixing on 2017/2/24.
 */
public class AsyncHttpAsker extends AbstractActor {
    private static final Logger logger = LogManager.getLogger(AsyncHttpAsker.class);
    private final HttpClientService httpClient;
    private final IAskStat askStat;
    private final String askerName;
    private Cancellable statTimer;

    private AsyncHttpAsker(String askerName, HttpClientService httpClient, IAskStat askStat) {
        this.httpClient = httpClient;
        this.askerName = askerName;
        if (askStat == null) {
            this.askStat = new IAskStat() {};
        } else {
            this.askStat = askStat;
        }
    }

    /**
     * 不建议使用，应使用props(HttpAsyncClientBuilder builder)代替，
     * 目的是为了在CloseableHttpAsyncClient出现异常时可以在Actor重启时用builder重新创建。
     * @param client
     * @return
     */
    @Deprecated
    static Props props(String askerName, CloseableHttpAsyncClient client) {
        return props(askerName, client, null);
    }

    @Deprecated
    static Props props(String askerName, CloseableHttpAsyncClient client, IAskStat askStat) {
        HttpClientService httpClient = new HttpClientService(client);
        return Props.create(AsyncHttpAsker.class, (Creator<AsyncHttpAsker>) () -> new AsyncHttpAsker(askerName, httpClient, askStat));
    }

    static Props props(String askerName, HttpAsyncClientBuilder builder) {
        return props(askerName, builder, null);
    }

    static Props props(String askerName, HttpAsyncClientBuilder builder, IAskStat askStat) {
        return Props.create(AsyncHttpAsker.class, (Creator<AsyncHttpAsker>) () -> {
            HttpClientService httpClient = new HttpClientService(builder);
            return new AsyncHttpAsker(askerName, httpClient, askStat);
        });
    }

    private static class OnLogStats {}

    @Override
    public void preStart() {
        FiniteDuration period = Duration.create(60_000, TimeUnit.MILLISECONDS);
        this.statTimer = context().system().scheduler().schedule(
                period,
                period,
                self(),
                new OnLogStats(),
                context().dispatcher(),
                self());
    }

    @Override
    public void postStop() {
        httpClient.close();
        if (statTimer != null) {
            statTimer.cancel();
            statTimer = null;
        }
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(HttpAsk.class, this::handleAsk)
                .match(OnLogStats.class, this::handleOnStat)
                .build();
    }

    private void handleAsk(HttpAsk ask) {
        this.askStat.onHandleAsk(askerName, System.currentTimeMillis() - ask.getCreateTime());
        final ActorRef consumer = sender();
        final ActorRef requester = self();
        retryAsk(ask,consumer,requester,ask.retryCount);
    }

    private void handleOnStat(OnLogStats msg) {
        if (msg != null) {
            this.httpClient.getTotalStats().ifPresent(s -> {
                this.askStat.onLogStats(s, askerName);
            });
        }
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

