package net.arksea.httpclient.asker;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

/**
 *
 * Created by xiaohaixing on 2016/12/30.
 */
public class HttpClientService {
    private static final Logger log = LogManager.getLogger(HttpClientService.class);
    private volatile CloseableHttpAsyncClient client; //定义为volatile防止DCL出错
    private final HttpAsyncClientBuilder clientBuilder;
    private final String serviceName;
    private boolean stopped;

    public HttpClientService(String serviceName, HttpAsyncClientBuilder builder) {
        this.stopped = false;
        this.serviceName = serviceName;
        this.clientBuilder = builder;
        this.client = createAsyncHttpClient();
    }

    public HttpClientService(HttpAsyncClientBuilder builder) {
        this.stopped = false;
        this.serviceName = "default";
        this.clientBuilder = builder;
        this.client = createAsyncHttpClient();
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
        try {
            if (client == null) {
                synchronized (this) {
                    if (client == null) {
                        if (stopped) { //stoppedx状态用于阻止在close()后才执行的ask调用重新创建client实例
                            callback.failed(new IllegalStateException("HttpClientService({"+serviceName+"}) is stopped"));
                            return;
                        } else {
                            client = createAsyncHttpClient();
                        }
                    }
                }
            }
            doAsk(request, tag, callback);
        } catch (Exception ex) {
            //有可能在调用client.execute时抛出IllegalStateException
            //引起异常的原因有可能是对象已closed、系统或设备异常等造成的非ACTIVE状态
            //参见  org.apache.http.impl.nio.client.InternalHttpAsyncClient 中对状态的断言处理：
            /* Asserts.check(status == Status.ACTIVE, "Request cannot be executed; " +
                     "I/O reactor status: %s", status);
            */
            callback.failed(ex);
            closeClient(false);
            log.warn("HttpClientService({})状态异常, 将重新创建", serviceName, ex);
        }
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

    private CloseableHttpAsyncClient createAsyncHttpClient() {
        CloseableHttpAsyncClient client = clientBuilder.build();
        client.start();
        log.info("HttpClientService({}) started", serviceName);
        return client;
    }

    private void closeClient(boolean stopService) {
        log.info("HttpClientService({}) closed", serviceName);
        synchronized (this) {
            if (client !=null) {
                try {
                    client.close();
                } catch (Exception e1) {
                    log.warn(e1);
                }
            }
            if (stopService) {
                this.stopped = true;
            }
            client = null;
        }
    }

    public void close() {
        closeClient(true);
    }

}
