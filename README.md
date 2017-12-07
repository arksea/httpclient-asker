httpclient-asker
===========

通过Akka调用Apache AsyncHttpClient，返回Future类型结果，用于Callback模式到Future模式间的适配

#### 创建FuturedHttpClient

```
@Component
public class FuturedHttpClientFactory {
    @Autowired
    ActorSystem system;

    @Value("${httpclient.keepAliveSeconds}")
    private int keepAliveSeconds;
    @Value("${httpclient.socketTimeout}")
    private int socketTimeout;
    @Value("${httpclient.maxConnTotal}")
    private int maxConnTotal;
    @Value("${httpclient.maxConnPerRoute}")
    private int maxConnPerRoute;

    @Bean(name = "proxyHttpClient")
    public FuturedHttpClient createHttpRequester() {
        return new FuturedHttpClient(system, "proxyHttpClientAsker", socketTimeout,
            maxConnTotal, maxConnPerRoute, keepAliveSeconds);
    }
}

```

#### 使用 FuturedHttpClient

使用一个基于Akka的缓存（acache）数据源的例子：

acache见隔壁项目，文档有空会补上 ：）

```
@Component
public class WebCacheSource implements IDataSource<ProxyRequest,String> {

    private final long CACHE_DEFAULT_TIMEOUT = 3600_000L * 24; //1天

    @Resource(name="proxyHttpClient")
    FuturedHttpClient httpClient;

    @Autowired
    ActorSystem system;

    @Override
    public Future<TimedData<String>> request(ProxyRequest key) {
        HttpAsk get = new HttpAsk("proxy", new HttpGet(key.targetUrl));
        return httpClient.ask(get).map(
            mapper(it -> {
                long expiredTime = System.currentTimeMillis() + CACHE_DEFAULT_TIMEOUT;
                return new TimedData<>(expiredTime, it.value);
            }), system.dispatcher()
        );
    }
}
```


其中mapper的封装是这样的

```
import akka.dispatch.Mapper;

public final class FutureUtils {
    ...
    public static <T,R> Mapper<T,R> mapper(Function<T,R> func) {
          return new Mapper<T, R>() {
              @Override
              public R apply(T t) {
                  return func.apply(t);
              }
          };
    }

    ...
}
```
