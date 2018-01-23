package net.arksea.httpclient.asker;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

/**
 *
 * Created by xiaohaixing on 2016/12/30.
 */
public class HttpClientService {
    private static final Logger log = LogManager.getLogger(HttpClientService.class);
    private volatile CloseableHttpAsyncClient httpAsyncClient;
    HttpAsyncClientBuilder httpAsyncClientBuilder;

    public HttpClientService(HttpAsyncClientBuilder builder) {
        httpAsyncClientBuilder = builder;
    }

    /**
     * 异步请求
     *
     * @param ask http ask
     * @param callback 回调函数
     */
    public void ask(final HttpAsk ask, final FutureCallback<HttpResult> callback) {
        log.debug("Http Request URI:{}", ask.request.getURI());
        synchronized (this) {
            try {
                if (httpAsyncClient == null) {
                    httpAsyncClient = createAsyncHttpClient();
                }
                httpAsyncClient.execute(ask.request, new FutureCallback<HttpResponse>() {
                    @Override
                    public void completed(HttpResponse response) {
                        HttpEntity entity = response.getEntity();
                        if (entity != null) {
                            final StringBuilder sb = new StringBuilder();
                            try {
                                InputStreamReader reader;
                                Header h1 = response.getLastHeader("Content-Encoding");
                                if (h1 != null && "gzip".equals(h1.getValue())) {
                                    reader = new InputStreamReader(
                                        new GZIPInputStream(response.getEntity().getContent()), "UTF-8");
                                } else {
                                    reader = new InputStreamReader(response.getEntity().getContent(), "UTF-8");
                                }
                                char[] cbuf = new char[128];
                                int len;
                                while ((len = reader.read(cbuf)) > -1) {
                                    sb.append(cbuf, 0, len);
                                }
                                String data = sb.toString();
                                log.debug("Http Respond(status={}): {}", response.getStatusLine().getStatusCode(), data);
                                callback.completed(new HttpResult(ask.tag, data, response));
                            } catch (Exception ex) {
                                callback.failed(ex);
                            }
                        } else {
                            log.debug("Http Respond(status={}): ",response.getStatusLine().getStatusCode());
                            callback.completed(new HttpResult(ask.tag, "", response));
                        }
                    }

                    @Override
                    public void failed(Exception ex) {
                        callback.failed(ex);
                    }

                    @Override
                    public void cancelled() {
                        callback.cancelled();
                    }
                });
            } catch (IllegalStateException ex) {
                log.warn("HttpAsyncClient状态异常,将重建连接", ex);
                callback.failed(ex);
                try {
                    httpAsyncClient.close();
                } catch (Exception e1) {
                    log.warn(e1);
                }
                httpAsyncClient = null;
            } catch (Exception ex) {
                log.warn("HttpAsyncClient请求失败", ex);
                callback.failed(ex);
            }
        }
    }

    private CloseableHttpAsyncClient createAsyncHttpClient() {
        CloseableHttpAsyncClient client = httpAsyncClientBuilder.build();
        client.start();
        return client;
    }

    public void close() {
        log.debug("HttpClientService.close()");
        synchronized (this) {
            try {
                if (httpAsyncClient!=null) {
                    httpAsyncClient.close();
                }
            } catch (Exception e1) {
                log.warn(e1);
            }
            httpAsyncClient = null;
        }
    }

}
