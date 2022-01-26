package net.arksea.httpclient.asker;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.routing.RoundRobinPool;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import scala.concurrent.Future;

import java.util.UUID;

import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2017/5/27.
 */
public class FuturedHttpClient implements IFuturedHttpClient {
    public final ActorSystem system;
    private final ActorRef httpAsker;
    private final IAskStat askStat;
    public final String name;

    public FuturedHttpClient(ActorSystem system) {
        HttpAsyncClientBuilder builder = HttpAsyncClients.custom()
            .setMaxConnTotal(4)
            .setMaxConnPerRoute(2);
        RequestConfig cfg = RequestConfig.custom().setConnectTimeout(10000).setSocketTimeout(10000).build();
        builder.setDefaultRequestConfig(cfg);
        this.system = system;
        this.askStat = createIdleAskStat();
        this.name = "httpClientAsker-"+ UUID.randomUUID().toString().substring(0,8);
        Props props = AsyncHttpAsker.props(name, builder);
        this.httpAsker = system.actorOf(props,name);
    }

    @Deprecated
    public FuturedHttpClient(ActorSystem system, String askerName, CloseableHttpAsyncClient client) {
        this(system, askerName, client, createIdleAskStat());
    }

    @Deprecated
    public FuturedHttpClient(ActorSystem system, String askerName, CloseableHttpAsyncClient client, IAskStat askStat) {
        this.system = system;
        this.askStat = askStat;
        this.name = askerName;
        Props props = AsyncHttpAsker.props(askerName, client);
        this.httpAsker = system.actorOf(props,askerName);
    }

    @Deprecated
    //当需要并发的连接数很大时，可以考虑创建多个asker来处理
    public FuturedHttpClient(int poolSize,
                             ActorSystem system,
                             String askerName,
                             CloseableHttpAsyncClient client) {
        this(poolSize, system, askerName, client, createIdleAskStat());
    }

    @Deprecated
    public FuturedHttpClient(int poolSize,
                             ActorSystem system,
                             String askerName,
                             CloseableHttpAsyncClient client,
                             IAskStat askStat) {
        this.system = system;
        this.askStat = askStat;
        this.name = askerName;
        Props props = AsyncHttpAsker.props(askerName, client);
        Props pooledProps = poolSize>1 ? props.withRouter(new RoundRobinPool(poolSize)) : props;
        httpAsker = system.actorOf(pooledProps, askerName);
    }


    public FuturedHttpClient(ActorSystem system, String askerName, HttpAsyncClientBuilder builder) {
        this(system, askerName, builder, createIdleAskStat());
    }

    public FuturedHttpClient(ActorSystem system, String askerName, HttpAsyncClientBuilder builder, IAskStat askStat) {
        this.system = system;
        this.askStat = askStat;
        this.name = askerName;
        Props props = AsyncHttpAsker.props(askerName, builder, askStat);
        this.httpAsker = system.actorOf(props,askerName);
    }

    //当需要并发的连接数很大时，可以考虑创建多个asker来处理
    public FuturedHttpClient(int poolSize,
                             ActorSystem system,
                             String askerName,
                             HttpAsyncClientBuilder builder) {
        this(poolSize, system, askerName, builder, createIdleAskStat());
    }

    public FuturedHttpClient(int poolSize,
                             ActorSystem system,
                             String askerName,
                             HttpAsyncClientBuilder builder,
                             IAskStat askStat) {
        this.system = system;
        this.askStat = askStat;
        this.name = askerName;
        Props props = AsyncHttpAsker.props(askerName, builder, askStat);
        Props pooledProps = poolSize>1 ? props.withRouter(new RoundRobinPool(poolSize)) : props;
        httpAsker = system.actorOf(pooledProps, askerName);
    }

    private static IAskStat createIdleAskStat() {
        return new IAskStat() {};
    }

    public Future<HttpResult> ask(HttpRequestBase request, int askTimeout) {
        return ask(new HttpAsk(request), askTimeout);
    }

    public Future<HttpResult> ask(HttpRequestBase request, Object tag, int askTimeout) {
        return ask(new HttpAsk(request, tag), askTimeout);
    }

    public Future<HttpResult> ask(HttpAsk httpAsk,int askTimeout) {
        askStat.onAsk(name);
        Future<HttpResult> f = Patterns.ask(httpAsker,httpAsk, askTimeout).mapTo(classTag(HttpResult.class));
        f.onComplete(new OnComplete<HttpResult>() {
            @Override
            public void onComplete(Throwable failure, HttpResult success) {
                askStat.onResponded(name, System.currentTimeMillis() - httpAsk.getCreateTime());
            }
        }, system.dispatcher());
        return f;
    }
}
