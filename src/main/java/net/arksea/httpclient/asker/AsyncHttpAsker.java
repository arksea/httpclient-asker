package net.arksea.httpclient.asker;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;

/**
 * 代理对HttpClientService的请求，目的是为了做AsyncHttpClient回调到Future的模式转换
 * Created by xiaohaixing on 2017/2/24.
 */
public class AsyncHttpAsker extends UntypedActor {

    private HttpClientService httpClient;
    private int askTimeout;

    public AsyncHttpAsker(HttpAsyncClientBuilder builder, int askTimeout) {
        this.httpClient = new HttpClientService(builder);
        this.askTimeout = askTimeout;
    }

    @Override
    public void onReceive(Object o) throws Throwable {
        if (o instanceof HttpAsk) {
            HttpAsk get = (HttpAsk) o;
            handleAsk(get);
        } else if (o instanceof SendToConsumer) {
            SendToConsumer msg = (SendToConsumer) o;
            //直接转发给消费者
            msg.consumer.tell(msg.result, self());
        } else {
            unhandled(o);
        }
    }

    private void handleAsk(HttpAsk ask) {
        final ActorRef consumer = sender();
        final ActorRef requester = self();
        httpClient.ask(ask, new FutureCallback<HttpResult>() {
            @Override
            public void completed(HttpResult result) {
                requester.tell(new SendToConsumer(result, consumer), ActorRef.noSender());
            }

            @Override
            public void failed(Exception ex) {
                HttpResult result = new HttpResult(ask.tag, ex, null);
                requester.tell(new SendToConsumer(result, consumer), ActorRef.noSender());
            }

            @Override
            public void cancelled() {
                HttpResult result = new HttpResult(ask.tag, new Exception("request cancelled"), null);
                requester.tell(new SendToConsumer(result, consumer), ActorRef.noSender());
            }
        },askTimeout);
    }
}

