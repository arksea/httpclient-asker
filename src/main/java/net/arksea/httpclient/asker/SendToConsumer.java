package net.arksea.httpclient.asker;

import akka.actor.ActorRef;

/**
 *
 * Created by xiaohaixing on 2017/5/18.
 */
public class SendToConsumer {
    final HttpResult result;
    final ActorRef consumer;
    public SendToConsumer(HttpResult result, ActorRef consumer) {
        this.result = result;
        this.consumer = consumer;
    }
}
