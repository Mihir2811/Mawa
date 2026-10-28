package actors;

import models.Post;
import models.SearchStatistics;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Reply message sent back from {@link SentimentAnalysisActor} once it has
 * finished processing an {@link AnalyzePosts} command.
 *
 * <p>Carries the same two things
 * {@link services.SentimentAnalyzer#analyzeAll(List)} and
 * {@link services.SentimentAnalyzer#summarize(List)} produce in Delivery 1:
 * the posts (now tagged with a {@code Sentiment} each) and the rolled-up
 * {@link SearchStatistics}.</p>
 *
 * @author MAWA Team (Delivery 2 - individual part)
 */
public final class SentimentAnalysisResponse {

    private final List<Post> analyzedPosts;
    private final SearchStatistics statistics;

    public SentimentAnalysisResponse(List<Post> analyzedPosts, SearchStatistics statistics) {
        this.analyzedPosts = Collections.unmodifiableList(Objects.requireNonNull(analyzedPosts, "analyzedPosts"));
        this.statistics = Objects.requireNonNull(statistics, "statistics");
    }

    public List<Post> getAnalyzedPosts() {
        return analyzedPosts;
    }

    public SearchStatistics getStatistics() {
        return statistics;
    }
}
