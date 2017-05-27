package net.arksea.httpclient.asker;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.Mapper;
import akka.pattern.Patterns;
import scala.concurrent.Future;

import java.util.function.Function;

import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2017/5/27.
 */
public class FuturedHttpClient {
    ActorSelection httpAsker;
    ActorSystem system;
    int socketTimeout;
    String askerName;
    public FuturedHttpClient(ActorSystem system, int socketTimeout) {
        this(system,"defaultHttpClientAsker", socketTimeout,4,2,30);
    }
    public FuturedHttpClient(ActorSystem system,
                             String askerName,
                             int socketTimeout,
                             int maxConnectionTotal,
                             int maxConnectionPerRoute,
                             int keepAliveSeconds) {
        this.system = system;
        this.socketTimeout = socketTimeout;
        this.askerName = askerName;
        Props props = AsyncHttpAsker.props(socketTimeout,maxConnectionTotal,maxConnectionPerRoute,keepAliveSeconds);
        system.actorOf(props, askerName);
        httpAsker = system.actorSelection("/user/"+askerName);

    }

    public Future<HttpResult> ask(HttpAsk httpAsk) {
        return Patterns.ask(httpAsker,httpAsk, socketTimeout).mapTo(classTag(HttpResult.class)).map(mapper(
            (HttpResult ret) -> {
                if (ret.error == null) {
                    return ret;
                } else {
                    throw new RuntimeException(ret.error);
                }
            }), system.dispatcher()
        );
    }

    public Future<HttpResult> ask(HttpAsk httpAsk, int successCode) {
        return ask(httpAsk, new int[]{successCode});
    }

    /**
     * 此处的ask封装，目的是为了简化使用者的错误处理程序，只要简单的处理failed回调，不用再判断ret.error和返回的错误码
     * @param httpAsk
     * @param successCodes
     * @return
     */
    public Future<HttpResult> ask(HttpAsk httpAsk, int[] successCodes) {
        return Patterns.ask(httpAsker,httpAsk, socketTimeout).mapTo(classTag(HttpResult.class)).map(mapper(
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
