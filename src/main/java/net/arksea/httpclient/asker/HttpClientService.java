package net.arksea.httpclient.asker;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.PoolingClientUtils;
import org.apache.http.pool.PoolStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * 工具类，对CloseableHttpAsyncClient调用的简单封装，
 * 读取返回数据，并转化为String格式的结果
 * Created by xiaohaixing on 2016/12/30.
 */
public class HttpClientService {
    private static final Logger log = LogManager.getLogger(HttpClientService.class);
    private final CloseableHttpAsyncClient client;
    private final String serviceName;

    @Deprecated
    public HttpClientService(String serviceName, CloseableHttpAsyncClient client) {
        this.serviceName = serviceName;
        this.client = client;
        if (!this.client.isRunning()) {
            client.start();
        }
    }

    @Deprecated
    public HttpClientService(CloseableHttpAsyncClient client) {
        this("default",client);
    }

    public HttpClientService(String serviceName, HttpAsyncClientBuilder builder) {
        this.serviceName = serviceName;
        this.client = builder.build();
        this.client.start();
    }

    public HttpClientService(HttpAsyncClientBuilder builder) {
        this("default",builder);
    }

    public boolean isStopped() {
        return !this.client.isRunning();
    }

    public Optional<PoolStats> getTotalStats() {
        return PoolingClientUtils.getTotalStats(this.client);
    }

    /**
     * 异步请求
     *
     * @param ask http ask
     * @param callback 回调函数
     */
    public void ask(final HttpAsk ask, final FutureCallback<HttpResult> callback) {
        ask(ask.request, ask.tag, callback);
    }

    public void ask(final HttpRequestBase request, final Object tag, final FutureCallback<HttpResult> callback) {
        log.debug("Http Request URI:{}", request.getURI());
        doAsk(request, tag, callback);
    }

    private void doAsk(final HttpRequestBase request, final Object tag, final FutureCallback<HttpResult> callback) {
        client.execute(request, new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse response) {
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    request.abort();
                    log.debug("Http Respond(status={}): ",response.getStatusLine().getStatusCode());
                    callback.completed(new HttpResult(tag, "", response));
                } else {
                    final StringBuilder sb = new StringBuilder();
                    InputStream in = null;
                    InputStreamReader reader = null;
                    try {
                        in = entity.getContent();
                        Header h1 = response.getLastHeader("Content-Encoding");
                        if (h1 != null && "gzip".equals(h1.getValue())) {
                            reader = new InputStreamReader(
                                new GZIPInputStream(in), "UTF-8");
                        } else {
                            reader = new InputStreamReader(in, "UTF-8");
                        }
                        char[] cbuf = new char[128];
                        int len;
                        while ((len = reader.read(cbuf)) > -1) {
                            sb.append(cbuf, 0, len);
                        }
                        String data = sb.toString();
                        log.debug("Http Respond(status={}): {}", response.getStatusLine().getStatusCode(), data);
                        callback.completed(new HttpResult(tag, data, response));
                    } catch (Exception ex) {
                        callback.failed(ex);
                    } finally {
                        if(reader != null) {
                            try {
                                reader.close();
                            } catch (Exception ex) {
                                log.debug("close stream failed",ex);
                            }
                        } else if (in != null) {
                            try {
                                in.close();
                            } catch (Exception ex) {
                                log.debug("close stream failed",ex);
                            }
                        }
                    }
                }
            }

            @Override
            public void failed(Exception ex) {
                request.abort();
                callback.failed(ex);
            }

            @Override
            public void cancelled() {
                request.abort();
                callback.cancelled();
            }
        });
    }

    public void close() {
        synchronized (this) {
            if (client !=null) {
                try {
                    client.close();
                } catch (Exception e1) {
                    log.warn(e1);
                }
                log.info("HttpClientService({}) closed", serviceName);
            }
        }
    }
}
