package actors;

import models.Post;
import models.SearchStatistics;
import models.Sentiment;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import services.SentimentAnalyzer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SentimentAnalysisActor}, using the Java Pekko Testkit
 * ({@link ActorTestKit} + {@link TestProbe}) as required for Delivery 2
 * ("You must add JUnit tests for your actors and ActorSystem, using the
 * Java Pekko Testkit shown in the lectures").
 *
 * <p>{@link ActorTestKit#create()} spins up a real (local, in-process)
 * {@code ActorSystem} for the duration of the test class, which is also
 * what satisfies the "...and ActorSystem" half of that requirement - every
 * test here is implicitly exercising actor creation/message-passing inside
 * a real actor system, not a hand-rolled fake.</p>
 *
 * <p>We spawn a fresh actor instance per test method rather than sharing
 * one across the class - it's cheap (this actor holds no mutable state)
 * and keeps each test fully independent.</p>
 *
 * @author MAWA Team (Delivery 2 - individual part)
 */
class SentimentAnalysisActorTest {

    private static ActorTestKit testKit;

    @BeforeAll
    static void setUpActorSystem() {
        testKit = ActorTestKit.create();
    }

    @AfterAll
    static void tearDownActorSystem() {
        testKit.shutdownTestKit();
    }

    @Test
    void analyzePosts_taggesEachPostAndReturnsMatchingStatistics() {
        ActorRef<SentimentAnalysisCommand> actor = testKit.spawn(SentimentAnalysisActor.create(new SentimentAnalyzer()));
        TestProbe<SentimentAnalysisResponse> probe = testKit.<SentimentAnalysisResponse>createTestProbe();

        List<Post> posts = List.of(
                post("1", "I am so happy and excited, this is amazing and wonderful!"),
                post("2", "this is terrible, I am so sad and frustrated, it sucks"),
                post("3", "just pushed a commit to main")
        );

        actor.tell(new AnalyzePosts(posts, probe.getRef()));
        SentimentAnalysisResponse response = probe.receiveMessage();

        assertEquals(Sentiment.HAPPY, response.getAnalyzedPosts().get(0).getSentiment());
        assertEquals(Sentiment.SAD, response.getAnalyzedPosts().get(1).getSentiment());
        assertEquals(Sentiment.NEUTRAL, response.getAnalyzedPosts().get(2).getSentiment());

        SearchStatistics stats = response.getStatistics();
        assertEquals(3, stats.getTotalPosts());
        assertEquals(1, stats.getPositivePosts());
        assertEquals(1, stats.getNegativePosts());
        assertEquals(1, stats.getNeutralPosts());
    }

    @Test
    void analyzePosts_emptyList_returnsEmptyStatisticsNotAnError() {
        ActorRef<SentimentAnalysisCommand> actor = testKit.spawn(SentimentAnalysisActor.create(new SentimentAnalyzer()));
        TestProbe<SentimentAnalysisResponse> probe = testKit.<SentimentAnalysisResponse>createTestProbe();

        actor.tell(new AnalyzePosts(List.of(), probe.getRef()));
        SentimentAnalysisResponse response = probe.receiveMessage();

        assertTrue(response.getAnalyzedPosts().isEmpty());
        assertEquals(0, response.getStatistics().getTotalPosts());
    }

    @Test
    void analyzePosts_singlePostAllHappyWords_isHappy() {
        ActorRef<SentimentAnalysisCommand> actor = testKit.spawn(SentimentAnalysisActor.create(new SentimentAnalyzer()));
        TestProbe<SentimentAnalysisResponse> probe = testKit.<SentimentAnalysisResponse>createTestProbe();

        actor.tell(new AnalyzePosts(List.of(post("1", "happy happy happy, love it!")), probe.getRef()));
        SentimentAnalysisResponse response = probe.receiveMessage();

        assertEquals(Sentiment.HAPPY, response.getAnalyzedPosts().get(0).getSentiment());
        assertEquals(1, response.getStatistics().getPositivePosts());
    }

    @Test
    void actor_handlesMultipleSequentialRequestsIndependently() {
        // Same actor instance, two separate AnalyzePosts messages sent one
        // after another - checks the actor correctly stays stateless
        // between messages instead of leaking data from the first request
        // into the second.
        ActorRef<SentimentAnalysisCommand> actor = testKit.spawn(SentimentAnalysisActor.create(new SentimentAnalyzer()));
        TestProbe<SentimentAnalysisResponse> probe = testKit.<SentimentAnalysisResponse>createTestProbe();

        actor.tell(new AnalyzePosts(List.of(post("1", "happy happy happy")), probe.getRef()));
        SentimentAnalysisResponse first = probe.receiveMessage();
        assertEquals(Sentiment.HAPPY, first.getAnalyzedPosts().get(0).getSentiment());

        actor.tell(new AnalyzePosts(List.of(post("2", "sad sad sad")), probe.getRef()));
        SentimentAnalysisResponse second = probe.receiveMessage();
        assertEquals(Sentiment.SAD, second.getAnalyzedPosts().get(0).getSentiment());
    }

    @Test
    void actor_canReceiveMultipleConcurrentRepliesAtTheSameProbe() {
        // Two different actor instances both told to reply to the same
        // probe - exercises that replies carry the right data each time
        // rather than the probe just happening to see the most recent one.
        ActorRef<SentimentAnalysisCommand> actorA = testKit.spawn(SentimentAnalysisActor.create(new SentimentAnalyzer()));
        ActorRef<SentimentAnalysisCommand> actorB = testKit.spawn(SentimentAnalysisActor.create(new SentimentAnalyzer()));
        TestProbe<SentimentAnalysisResponse> probe = testKit.<SentimentAnalysisResponse>createTestProbe();

        actorA.tell(new AnalyzePosts(List.of(post("a", "great, love this, amazing!")), probe.getRef()));
        actorB.tell(new AnalyzePosts(List.of(post("b", "awful, hate this, terrible")), probe.getRef()));

        // Using receiveMessage() twice (rather than a bulk-receive helper)
        // to stick to the small, well-known subset of the TestProbe API.
        Sentiment first = probe.receiveMessage().getAnalyzedPosts().get(0).getSentiment();
        Sentiment second = probe.receiveMessage().getAnalyzedPosts().get(0).getSentiment();

        assertTrue((first == Sentiment.HAPPY && second == Sentiment.SAD)
                || (first == Sentiment.SAD && second == Sentiment.HAPPY));
    }

    private static Post post(String id, String content) {
        return new Post(id, "Someone", "someone@example.social", Instant.now().minus(1, ChronoUnit.MINUTES),
                "en", content, "https://example.social/@someone/" + id, "public");
    }
}
