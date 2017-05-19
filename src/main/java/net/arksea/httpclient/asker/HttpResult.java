package net.arksea.httpclient.asker;

import org.apache.http.HttpResponse;

/**
 *
 * Created by xiaohaixing on 2017/4/24.
 */
public class HttpResult {
    public final Object tag;
    public final String value;
    public HttpResponse response;
    public final Exception error;
    public HttpResult(Object tag, String value, HttpResponse response) {
        this.tag = tag;
        this.value = value;
        this.error = null;
        this.response = response;
    }
    public HttpResult(Object tag, Exception error, HttpResponse response) {
        this.tag = tag;
        this.value = null;
        this.response = response;
        this.error = error;
    }
}
