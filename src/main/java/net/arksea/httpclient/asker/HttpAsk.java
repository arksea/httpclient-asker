package net.arksea.httpclient.asker;

import org.apache.http.client.methods.HttpRequestBase;

import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * Created by xiaohaixing on 2017/5/4.
 */
public class HttpAsk {
    public final Object tag;
    public final HttpRequestBase request;
    public final AtomicInteger retryCount;

    public HttpAsk(final Object tag, final HttpRequestBase req) {
        this.tag = tag;
        this.request = req;
        this.retryCount = new AtomicInteger(0);
    }

    public HttpAsk(final Object tag, final HttpRequestBase req, final AtomicInteger retryCount) {
        this.tag = tag;
        this.request = req;
        this.retryCount = retryCount;
    }
}
