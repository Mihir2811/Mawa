package models;

/**
 * Aggregate, at-a-glance numbers shown at the top of the search results page
 * (assignment section 4.3, "Statistical Analysis" individual task).
 *
 * <p>This is a plain immutable value holder - all the actual counting work
 * happens in {@link services.SentimentAnalyzer#summarize(java.util.List)}
 * using the Java Streams API (filter / groupingBy / counting), not here.</p>
 *
 * @author MAWA Team
 */
public final class SearchStatistics {

    private final long totalPosts;
    private final long positivePosts;
    private final long negativePosts;
    private final long neutralPosts;

    public SearchStatistics(long totalPosts, long positivePosts, long negativePosts, long neutralPosts) {
        this.totalPosts = totalPosts;
        this.positivePosts = positivePosts;
        this.negativePosts = negativePosts;
        this.neutralPosts = neutralPosts;
    }

    /** @return an all-zero statistics object, used for empty result sets */
    public static SearchStatistics empty() {
        return new SearchStatistics(0, 0, 0, 0);
    }

    public long getTotalPosts() {
        return totalPosts;
    }

    public long getPositivePosts() {
        return positivePosts;
    }

    public long getNegativePosts() {
        return negativePosts;
    }

    public long getNeutralPosts() {
        return neutralPosts;
    }
}
