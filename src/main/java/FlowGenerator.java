import akka.NotUsed;
import akka.actor.ActorRef;
import akka.http.javadsl.model.*;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.Supervision;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import javafx.util.Pair;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static config.Config.*;
import static org.asynchttpclient.Dsl.asyncHttpClient;

public class FlowGenerator {

    static Flow<HttpRequest, HttpResponse, NotUsed> createFlow(ActorRef explorer, ActorMaterializer materializer) {

        return Flow.of(HttpRequest.class)
                .map(msg -> {
                    Query q = msg.getUri().query();
                    String url = q.get(ULR_PARAMETER).get();
                    int count = Integer.parseInt(q.get(COUNT_PARAMETER).get());
                    return new Pair<>(url, count);

                }).mapAsync(MAX_STREAMS,  msg ->
                        Patterns.ask(explorer, new FindMessage(msg.getKey()), TIMEOUT)
                                .thenCompose(answer ->
                                        answer.getClass() == TestMessage.class ?
                                                CompletableFuture.completedFuture((TestMessage) answer)
                                                :takeSource(msg, materializer))

                ).map(answer -> {
                    explorer.tell(answer, ActorRef.noSender());
                    return HttpResponse
                            .create()
                            .withStatus(StatusCodes.OK)
                            .withEntity(
                                    HttpEntities.create(
                                            answer.getUrl() + " " + answer.getCount()));
                });
    }

    private static CompletionStage<TestMessage> takeSource (Pair<String, Integer> pair, Materializer materializer) {
        return Source.from(Collections.singletonList(pair))
                .toMat(testSink(), Keep.right())
                .run(materializer)
                .thenCompose(sum -> CompletableFuture.completedFuture(new TestMessage(pair.getKey(),sum/pair.getValue()/NANO_SIZE)));
    }

    private static Sink<Pair<String, Integer>, CompletionStage<Long>> testSink() {
        return Flow.<Pair<String, Integer>>create()
                .mapConcat(msg ->
                        Collections.nCopies(msg.getValue(), msg.getKey()))
                .mapAsync(MAX_STREAMS, url -> {
                    long zeroTime = System.nanoTime();
                    AsyncHttpClient client = asyncHttpClient();

                    return client
                            .prepareGet(url)
                            .execute()
                            .toCompletableFuture()
                            .exceptionally(throwable -> (Response) Supervision.stop())
                            .thenApply(response -> System.nanoTime() - zeroTime);
                }).toMat(Sink.fold(0L, Long::sum), Keep.right());
    }

}
