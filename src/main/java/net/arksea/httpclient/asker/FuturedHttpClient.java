package net.arksea.httpclient.asker;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.Mapper;
import akka.pattern.Patterns;
import akka.routing.RoundRobinPool;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import scala.concurrent.Future;

import java.util.function.Function;

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

    public FuturedHttpClient(ActorSystem system,String askerName,HttpAsyncClientBuilder builder) {
        this.system = system;
        Props props = AsyncHttpAsker.props(builder);
        this.httpAsker = system.actorOf(props,askerName);
    }

    //特殊情况下，一个Actor忙不过来时可以创建多个asker
    public FuturedHttpClient(int poolSize,
                             ActorSystem system,
                             String askerName,
                             HttpAsyncClientBuilder builder) {
        this.system = system;
        Props props = AsyncHttpAsker.props(builder);
        RoundRobinPool pool = new RoundRobinPool(poolSize);
        httpAsker = system.actorOf(pool.props(props), askerName);
    }

    public Future<HttpResult> ask(HttpAsk httpAsk,int askTimeout) {
        return Patterns.ask(httpAsker,httpAsk, askTimeout).mapTo(classTag(HttpResult.class));
    }

    public Future<HttpResult> ask(HttpAsk httpAsk, int askTimeout, int successCode) {
        return ask(httpAsk, askTimeout, new int[]{successCode});
    }

    /**
     * 此处的ask封装，目的是为了简化使用者的错误处理程序，只要简单的处理failed回调，不用再判断ret.error和返回的错误码
     * @param req
     * @param askTimeout
     * @param successCodes
     * @return
     */
    public Future<HttpResult> ask(HttpAsk req, int askTimeout, int[] successCodes) {
        return Patterns.ask(httpAsker,req, askTimeout).mapTo(classTag(HttpResult.class)).map(mapper(
            (HttpResult ret) -> {
                if (ret.error == null) {
                    int code = ret.response.getStatusLine().getStatusCode();
                    boolean success = false;
                    for (int n : successCodes) {
                        if (code == n) {
                            success = true;
                            break;
                        }
                    }
                    if (success) {
                        return ret;
                    } else {
                        throw new RuntimeException("error code=" + code);
                    }
                } else {
                    throw new RuntimeException(ret.error);
                }
            }), system.dispatcher()
        );
    }

    private static Mapper<HttpResult, HttpResult> mapper(final Function<HttpResult, HttpResult> func) {
        return new Mapper<HttpResult, HttpResult>() {
            public HttpResult apply(HttpResult t) {
                return func.apply(t);
            }
        };
    }
}
