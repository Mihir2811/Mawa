package services;

import com.typesafe.config.Config;
import models.Post;
import models.SearchResult;
import models.SearchStatistics;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Default {@link MastodonService} implementation.
 *
 * <p>Responsibilities (kept separate from {@link MastodonClient} on purpose,
 * per the "isolate Mastodon from the rest of the app" design goal in section
 * 9.4):</p>
 * <ul>
 *   <li>Cache (hashtag, page) -&gt; result, so repeatedly searching the same
 *   hashtag/page doesn't keep hammering the Mastodon API (section 5.3).</li>
 *   <li>Run {@link SentimentAnalyzer} over whatever posts we got back.</li>
 *   <li>Work out the {@code nextMaxId} pagination cursor for the view.</li>
 * </ul>
 *
 * @author MAWA Team
 */
@Singleton
public class MastodonServiceImpl implements MastodonService {

    private final MastodonClient client;
    private final SentimentAnalyzer sentimentAnalyzer;
    private final ResultCache cache;
    private final int maxPostsPerRequest;

    @Inject
    public MastodonServiceImpl(MastodonClient client,
                                SentimentAnalyzer sentimentAnalyzer,
                                ResultCache cache,
                                Config config) {
        this.client = client;
        this.sentimentAnalyzer = sentimentAnalyzer;
        this.cache = cache;
        this.maxPostsPerRequest = config.getInt("mastodon.maxPostsPerRequest");
    }

    @Override
    public CompletionStage<SearchResult> search(String hashtag, Optional<String> maxId) {
        String normalizedHashtag = hashtag == null ? "" : hashtag.trim().toLowerCase();

        if (normalizedHashtag.isEmpty()) {
            return failedFuture(new MastodonApiException(MastodonApiException.Reason.INVALID_INPUT,
                    "Please enter a hashtag to search for."));
        }

        String cacheKey = "tag:" + normalizedHashtag + ":" + maxId.orElse("first");

        return cache.getOrCompute(cacheKey, () ->
                client.fetchTagTimeline(normalizedHashtag, maxId, maxPostsPerRequest)
                        .thenApply(posts -> buildResult(normalizedHashtag, posts)));
    }

    private SearchResult buildResult(String hashtag, List<Post> posts) {
        sentimentAnalyzer.analyzeAll(posts);
        SearchStatistics stats = sentimentAnalyzer.summarize(posts);

        // If Mastodon handed back a full page, assume there might be more
        // and offer a "next" cursor pointing at the oldest post we have.
        // If it handed back fewer than we asked for, we've hit the end.
        Optional<String> nextMaxId = posts.size() >= maxPostsPerRequest && !posts.isEmpty()
                ? Optional.of(posts.get(posts.size() - 1).getId())
                : Optional.empty();

        return new SearchResult(hashtag, posts, stats, nextMaxId);
    }

    private static CompletionStage<SearchResult> failedFuture(Throwable t) {
        CompletableFuture<SearchResult> future = new CompletableFuture<>();
        future.completeExceptionally(t);
        return future;
    }
}
