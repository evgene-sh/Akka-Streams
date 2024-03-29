import akka.actor.AbstractActor;
import akka.japi.pf.ReceiveBuilder;

import java.util.HashMap;
import java.util.Map;

import static config.Config.HAVENT;

public class ActorExplorer extends AbstractActor {
    private Map<String, Long> store = new HashMap<>();

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(FindMessage.class, msg -> {
                    if(!store.containsKey(msg.getUrl())) {
                        sender().tell(HAVENT,self());
                    } else {
                        sender().tell(new TestMessage(msg.getUrl(), store.get(msg.getUrl())), self());
                    }
                })
                .match(TestMessage.class, msg ->
                        store.put(msg.getUrl(), msg.getCount()))
            .build();
    }
}
