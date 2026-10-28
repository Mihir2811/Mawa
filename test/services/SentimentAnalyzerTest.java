package services;

import models.Post;
import models.SearchStatistics;
import models.Sentiment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link SentimentAnalyzer}.
 *
 * <p>Test cases below were designed around equivalence class partitioning
 * (assignment 5.6): "clearly happy", "clearly sad", "mixed/neutral",
 * "no sentiment-bearing content at all", and "right on the 70% boundary"
 * (both just-under and just-over) are each their own partition, since the
 * threshold check is the one place a small off-by-one could hide.</p>
 *
 * @author MAWA Team
 */
class SentimentAnalyzerTest {

    private SentimentAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new SentimentAnalyzer();
    }

    @Test
    void analyze_allHappyWords_returnsHappy() {
        Sentiment result = analyzer.analyze("I am so happy and excited, this is amazing and wonderful!");
        assertEquals(Sentiment.HAPPY, result);
    }

    @Test
    void analyze_allSadWords_returnsSad() {
        Sentiment result = analyzer.analyze("This is terrible, I am so sad and frustrated, it sucks.");
        assertEquals(Sentiment.SAD, result);
    }

    @Test
    void analyze_noSentimentWords_returnsNeutral() {
        Sentiment result = analyzer.analyze("Just deployed the new build to staging this morning.");
        assertEquals(Sentiment.NEUTRAL, result);
    }

    @Test
    void analyze_emptyContent_returnsNeutral() {
        assertEquals(Sentiment.NEUTRAL, analyzer.analyze(""));
    }

    @Test
    void analyze_nullContent_returnsNeutral() {
        assertEquals(Sentiment.NEUTRAL, analyzer.analyze(null));
    }

    @Test
    void analyze_blankContent_returnsNeutral() {
        assertEquals(Sentiment.NEUTRAL, analyzer.analyze("   "));
    }

    @Test
    void analyze_evenMixOfHappyAndSad_returnsNeutral() {
        // 1 happy, 1 sad -> 50/50, neither crosses the 70% threshold.
        Sentiment result = analyzer.analyze("happy but also sad today");
        assertEquals(Sentiment.NEUTRAL, result);
    }

    @Test
    void analyze_exactlySeventyPercentHappy_isStillNeutral() {
        // 7 happy words, 3 sad words -> exactly 70% happy. Spec says
        // STRICTLY more than 70%, so this must stay NEUTRAL, not HAPPY.
        String content = "happy happy happy happy happy happy happy sad sad sad";
        assertEquals(Sentiment.NEUTRAL, analyzer.analyze(content));
    }

    @Test
    void analyze_justOverSeventyPercentHappy_isHappy() {
        // 8 happy words, 3 sad words -> 8/11 = 72.7%, crosses the threshold.
        String content = "happy happy happy happy happy happy happy happy sad sad sad";
        assertEquals(Sentiment.HAPPY, analyzer.analyze(content));
    }

    @Test
    void analyze_justOverSeventyPercentSad_isSad() {
        // Mirror of the above test, for the sad side of the threshold.
        String content = "sad sad sad sad sad sad sad sad happy happy happy";
        assertEquals(Sentiment.SAD, analyzer.analyze(content));
    }

    @Test
    void analyze_happyEmoticon_returnsHappy() {
        assertEquals(Sentiment.HAPPY, analyzer.analyze("just shipped it :-) :-) :-)"));
    }

    @Test
    void analyze_sadEmoticon_returnsSad() {
        assertEquals(Sentiment.SAD, analyzer.analyze("build failed again :-( :-("));
    }

    @Test
    void analyze_isCaseInsensitive() {
        assertEquals(Sentiment.HAPPY, analyzer.analyze("HAPPY HAPPY HAPPY, so EXCITED!"));
    }

    @Test
    void analyzeAll_setsSentimentOnEveryPost() {
        Post happyPost = buildPost("1", "so happy and excited!");
        Post sadPost = buildPost("2", "this is terrible and sad");
        Post neutralPost = buildPost("3", "pushed a commit to main");

        List<Post> posts = List.of(happyPost, sadPost, neutralPost);
        analyzer.analyzeAll(posts);

        assertEquals(Sentiment.HAPPY, happyPost.getSentiment());
        assertEquals(Sentiment.SAD, sadPost.getSentiment());
        assertEquals(Sentiment.NEUTRAL, neutralPost.getSentiment());
    }

    @Test
    void summarize_emptyList_returnsAllZeroes() {
        SearchStatistics stats = analyzer.summarize(List.of());
        assertEquals(0, stats.getTotalPosts());
        assertEquals(0, stats.getPositivePosts());
        assertEquals(0, stats.getNegativePosts());
        assertEquals(0, stats.getNeutralPosts());
    }

    @Test
    void summarize_mixedPosts_countsEachBucketCorrectly() {
        Post happyPost = buildPost("1", "so happy and excited!");
        Post sadPost = buildPost("2", "this is terrible and sad");
        Post neutralPostA = buildPost("3", "pushed a commit to main");
        Post neutralPostB = buildPost("4", "meeting rescheduled to 3pm");

        List<Post> posts = List.of(happyPost, sadPost, neutralPostA, neutralPostB);
        analyzer.analyzeAll(posts);
        SearchStatistics stats = analyzer.summarize(posts);

        assertEquals(4, stats.getTotalPosts());
        assertEquals(1, stats.getPositivePosts());
        assertEquals(1, stats.getNegativePosts());
        assertEquals(2, stats.getNeutralPosts());
    }

    @Test
    void summarize_postWithoutSentimentSet_isTreatedAsNeutral() {
        // Defensive case: summarize() is called on a post that never went
        // through analyzeAll() (sentiment == null). Should not throw, and
        // should be counted as neutral rather than crashing the stats page.
        Post untaggedPost = buildPost("5", "whatever content");
        SearchStatistics stats = analyzer.summarize(List.of(untaggedPost));

        assertEquals(1, stats.getTotalPosts());
        assertEquals(1, stats.getNeutralPosts());
        assertEquals(0, stats.getPositivePosts());
        assertEquals(0, stats.getNegativePosts());
    }

    private static Post buildPost(String id, String content) {
        return new Post(id, "Someone", "someone@example.social", Instant.now(), "en", content,
                "https://example.social/@someone/" + id, "public");
    }
}
