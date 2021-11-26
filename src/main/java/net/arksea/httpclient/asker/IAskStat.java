package net.arksea.httpclient.asker;

public interface IAskStat {
    void onAsk(String askerName);
    void onHandleAsk(String askerName, long waitTime);
    void onResponded(String askerName, long ttl);
}
