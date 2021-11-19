package net.arksea.httpclient.asker;

public interface IAskStat {
    void onAsk(String askerName);
    void onHandleAsk(String askerName);
    void onResponded(String askerName);
}
