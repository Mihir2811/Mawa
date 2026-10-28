package models;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Everything the controller needs to render one "page" of search results:
 * the hashtag that was searched, the posts themselves (already
 * sentiment-tagged), the aggregate stats, and a cursor for fetching the next
 * page.
 *
 * @author MAWA Team
 */
public final class SearchResult {

    private final String hashtag;
    private final List<Post> posts;
    private final SearchStatistics statistics;

    /**
     * The Mastodon status id to use as {@code max_id} for the next page, if
     * there might be more results. Empty when we got back fewer posts than
     * we asked for (a reasonable signal that we've reached the end).
     */
    private final Optional<String> nextMaxId;

    public SearchResult(String hashtag, List<Post> posts, SearchStatistics statistics, Optional<String> nextMaxId) {
        this.hashtag = hashtag;
        this.posts = Collections.unmodifiableList(posts);
        this.statistics = statistics;
        this.nextMaxId = nextMaxId;
    }

    public String getHashtag() {
        return hashtag;
    }

    public List<Post> getPosts() {
        return posts;
    }

    public SearchStatistics getStatistics() {
        return statistics;
    }

    public Optional<String> getNextMaxId() {
        return nextMaxId;
    }

    public boolean hasMore() {
        return nextMaxId.isPresent();
    }
}
