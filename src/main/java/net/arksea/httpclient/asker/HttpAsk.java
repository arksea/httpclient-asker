package net.arksea.httpclient.asker;

import org.apache.http.client.methods.HttpRequestBase;

/**
 *
 * Created by xiaohaixing on 2017/5/4.
 */
public class HttpAsk {
    public Object tag;
    public HttpRequestBase request;

    public HttpAsk(final Object tag, final HttpRequestBase req) {
        this.tag = tag;
        this.request = req;
    }
}
