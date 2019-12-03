package net.arksea.httpclient.asker;

import org.apache.http.client.methods.HttpRequestBase;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Create by xiaohaixing on 2019/12/3
 */
public class HttpAskBuilder {
    private final HttpRequestBase request;
    private Object tag;
    private int retryCount;
    private List<Integer> retryCodes;
    private List<Integer> successCodes;
    private Consumer<Exception> retryCauseConsumer;
    public HttpAskBuilder(HttpRequestBase request) {
        this.request = request;
        this.tag = request;
        this.retryCodes = new LinkedList<>();
        this.successCodes = new LinkedList<>();
    }
    public HttpAsk build() {
        return new HttpAsk(request,tag,retryCount,
                retryCodes,successCodes,retryCauseConsumer);
    }
    public HttpAskBuilder setTag(Object tag) {
        this.tag = tag;
        return this;
    }
    public HttpAskBuilder setRetryCount(int count) {
        this.retryCount = count;
        return this;
    }
    public HttpAskBuilder addRetryCodes(int code) {
        this.retryCodes.add(code);
        return this;
    }
    public HttpAskBuilder addSuccessCodes(int code) {
        this.successCodes.add(code);
        return this;
    }
    public HttpAskBuilder setRetryCauseConsumer(Consumer<Exception> consumer) {
        this.retryCauseConsumer = consumer;
        return this;
    }
}
