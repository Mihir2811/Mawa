package actors;

import models.Post;
import models.SearchStatistics;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import services.SentimentAnalyzer;

import java.util.List;

/**
 * Actor-based equivalent of {@link SentimentAnalyzer}, for Delivery 2's
 * individual-part requirement: "create a new Pekko Actor to perform the
 * same operation as defined in Delivery 1, together with appropriate
 * message classes and unit tests".
 *
 * <p>This is written using <b>Pekko Typed</b> (the API the course notes
 * recommend over Classic). It deliberately does <em>not</em> duplicate the
 * sentiment-analysis logic itself - it just wraps the existing, already
 *-tested {@link SentimentAnalyzer} and exposes it through an actor message
 * protocol ({@link AnalyzePosts} in, {@link SentimentAnalysisResponse} out).
 * That mirrors how the rest of this codebase isolates logic behind
 * interfaces: the actor is a thin adapter, not a second implementation that
 * would have to be kept in sync with the first.</p>
 *
 * <h2>How this fits into the bigger Delivery 2 picture</h2>
 * <p>The full reactive design (not built yet - this is just the individual
 * part) will have one actor per WebSocket connection plus a supervisor
 * that restarts failing children, with a separate actor class per
 * analysis task (this one, plus whichever ones teammates build for
 * readability/author-analysis/word-stats). A per-connection "user actor"
 * would hold a {@code ActorRef<SentimentAnalysisCommand>} child (spawned via
 * {@code context.spawn(SentimentAnalysisActor.create(...), "sentiment")})
 * and forward {@link AnalyzePosts} commands to it as new posts arrive from
 * the Mastodon streaming API, instead of calling
 * {@link SentimentAnalyzer} directly the way {@code MastodonServiceImpl}
 * does today. This class is written so that wiring is a small, additive
 * change later rather than a rewrite.</p>
 *
 * @author MAWA Team (Delivery 2 - individual part)
 */
public class SentimentAnalysisActor extends AbstractBehavior<SentimentAnalysisCommand> {

    private final SentimentAnalyzer sentimentAnalyzer;

    /**
     * Factory method - this is how you get a {@link Behavior} to pass to
     * {@code context.spawn(...)} or an {@code ActorTestKit}. Pekko Typed
     * actors are constructed this way (via {@link Behaviors#setup}) rather
     * than with {@code new} directly, so the framework controls the
     * actor's lifecycle.
     *
     * @param sentimentAnalyzer the (stateless, reusable) analyzer to delegate to
     * @return a behavior ready to be spawned
     */
    public static Behavior<SentimentAnalysisCommand> create(SentimentAnalyzer sentimentAnalyzer) {
        return Behaviors.setup(context -> new SentimentAnalysisActor(context, sentimentAnalyzer));
    }

    private SentimentAnalysisActor(ActorContext<SentimentAnalysisCommand> context, SentimentAnalyzer sentimentAnalyzer) {
        super(context);
        this.sentimentAnalyzer = sentimentAnalyzer;
    }

    /**
     * Declares which message types this actor handles and which method
     * handles each one. Pekko Typed checks this against the declared
     * {@code SentimentAnalysisCommand} type at compile time - there's no
     * way to {@code tell()} this actor something it doesn't know how to
     * handle.
     */
    @Override
    public Receive<SentimentAnalysisCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(AnalyzePosts.class, this::onAnalyzePosts)
                .build();
    }

    /**
     * Handles an {@link AnalyzePosts} command: runs the same analysis
     * Delivery 1 does synchronously, then {@code tell}s the result back to
     * whoever asked. Returning {@code this} means "keep using the same
     * behavior for the next message" - this actor is stateless, so there's
     * never a reason to switch behaviors.
     */
    private Behavior<SentimentAnalysisCommand> onAnalyzePosts(AnalyzePosts command) {
        List<Post> analyzed = sentimentAnalyzer.analyzeAll(command.getPosts());
        SearchStatistics statistics = sentimentAnalyzer.summarize(analyzed);
        command.getReplyTo().tell(new SentimentAnalysisResponse(analyzed, statistics));
        return this;
    }
}
