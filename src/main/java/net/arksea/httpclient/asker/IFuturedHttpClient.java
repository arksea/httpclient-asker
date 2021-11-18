package net.arksea.httpclient.asker;

import org.apache.http.client.methods.HttpRequestBase;
import scala.concurrent.Future;

/**
 * Create by xiaohaixing on 2020/7/2
 */
public interface IFuturedHttpClient {

    default Future<HttpResult> ask(HttpRequestBase request, int askTimeout) {
        return ask(new HttpAsk(request), askTimeout);
    }

    default Future<HttpResult> ask(HttpRequestBase request, Object tag, int askTimeout) {
        return ask(new HttpAsk(request, tag), askTimeout);
    }

    Future<HttpResult> ask(HttpAsk httpAsk,int askTimeout);
}
