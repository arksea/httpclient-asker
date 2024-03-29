package net.arksea.httpclient.asker;

import org.apache.http.client.methods.HttpRequestBase;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 *
 * Created by xiaohaixing on 2017/5/4.
 */
public class HttpAsk {
    final HttpRequestBase request;
    final Object tag;
    final AtomicInteger retryCount;
    final List<Integer> retryCodes;
    final List<Integer> successCodes;
    final Consumer<Exception> retryCauseConsumer;
    private final long createTime;
    public HttpAsk(HttpRequestBase request) {
        this(request, request);
    }
    public HttpAsk(HttpRequestBase request, Object tag) {
        this(request, tag, 0, new LinkedList<>(), new LinkedList<>(), null);
    }
    public HttpAsk(HttpRequestBase request, Object tag, int retryCount,
                   List<Integer> retryCodes, List<Integer> successCodes,
                   Consumer<Exception> retryCauseConsumer) {
        this.tag = tag;
        this.request = request;
        this.retryCount = new AtomicInteger(retryCount);
        this.retryCodes = retryCodes;
        this.successCodes = successCodes;
        this.retryCauseConsumer = retryCauseConsumer;
        this.createTime = System.currentTimeMillis();
    }
    public Object getTag() {
        return tag;
    }
    public HttpRequestBase getRequest() {
        return request;
    }
    public static HttpAskBuilder builder(HttpRequestBase request) {
        return new HttpAskBuilder(request);
    }
    public long getCreateTime() {
        return createTime;
    }
}
