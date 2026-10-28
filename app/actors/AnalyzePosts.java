package actors;

import models.Post;
import org.apache.pekko.actor.typed.ActorRef;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Request message for {@link SentimentAnalysisActor}: "please analyze these
 * posts and send the result back to {@code replyTo}".
 *
 * <p>This mirrors what {@link services.SentimentAnalyzer#analyzeAll(List)}
 * does in Delivery 1 - this message is the actor-based equivalent of
 * calling that method directly, per the Delivery 2 individual-part
 * requirement ("create a new Pekko Actor to perform the same operation as
 * defined in Delivery 1").</p>
 *
 * <p>Immutable, like every Pekko message should be - two actors could in
 * principle be processing messages built from the same {@code posts} list
 * concurrently, so nothing about this message is allowed to change after
 * construction.</p>
 *
 * @author MAWA Team (Delivery 2 - individual part)
 */
public final class AnalyzePosts implements SentimentAnalysisCommand {

    private final List<Post> posts;
    private final ActorRef<SentimentAnalysisResponse> replyTo;

    /**
     * @param posts   the posts to analyze (not mutated; defensively copied into an unmodifiable list)
     * @param replyTo where to send the {@link SentimentAnalysisResponse} once analysis is done
     */
    public AnalyzePosts(List<Post> posts, ActorRef<SentimentAnalysisResponse> replyTo) {
        this.posts = Collections.unmodifiableList(Objects.requireNonNull(posts, "posts"));
        this.replyTo = Objects.requireNonNull(replyTo, "replyTo");
    }

    public List<Post> getPosts() {
        return posts;
    }

    public ActorRef<SentimentAnalysisResponse> getReplyTo() {
        return replyTo;
    }
}
