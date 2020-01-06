package net.arksea.httpclient.asker;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.routing.RoundRobinPool;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import scala.concurrent.Future;

import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2017/5/27.
 */
public class FuturedHttpClient {
    public final ActorRef httpAsker;
    public final ActorSystem system;

    public FuturedHttpClient(ActorSystem system) {
        this(system,4,2,30,
            RequestConfig.custom().setConnectTimeout(10000).setSocketTimeout(10000).build());
    }

    public FuturedHttpClient(ActorSystem system,
                             int maxConnectionTotal,
                             int maxConnectionPerRoute,
                             int keepAliveSeconds,
                             RequestConfig defaultRequestConfig) {
        this.system = system;
        Props props = AsyncHttpAsker.props(maxConnectionTotal,maxConnectionPerRoute,keepAliveSeconds,defaultRequestConfig);
        this.httpAsker = system.actorOf(props,"defaultHttpClientAsker");
    }

    public FuturedHttpClient(ActorSystem system, String askerName, CloseableHttpAsyncClient client) {
        this.system = system;
        Props props = AsyncHttpAsker.props(client);
        this.httpAsker = system.actorOf(props,askerName);
    }

    //当设置的maxConnection很大时，可以考虑创建多个asker来处理
    public FuturedHttpClient(int poolSize,
                             ActorSystem system,
                             String askerName,
                             CloseableHttpAsyncClient client) {
        this.system = system;
        Props props = AsyncHttpAsker.props(client);
        Props pooledProps = poolSize>1 ? props.withRouter(new RoundRobinPool(poolSize)) : props;
        httpAsker = system.actorOf(pooledProps, askerName);
    }

    public Future<HttpResult> ask(HttpRequestBase request, int askTimeout) {
        return ask(new HttpAsk(request), askTimeout);
    }

    public Future<HttpResult> ask(HttpRequestBase request, Object tag, int askTimeout) {
        return ask(new HttpAsk(request, tag), askTimeout);
    }

    public Future<HttpResult> ask(HttpAsk httpAsk,int askTimeout) {
        return Patterns.ask(httpAsker,httpAsk, askTimeout).mapTo(classTag(HttpResult.class));
    }
}
