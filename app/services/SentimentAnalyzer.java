package services;

import models.Post;
import models.SearchStatistics;
import models.Sentiment;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Computes a happy / sad / neutral sentiment for each retrieved post, and
 * rolls those per-post sentiments up into the aggregate
 * {@link SearchStatistics} shown at the top of the results page.
 *
 * <p>This class is my individual contribution to the project (sentiment
 * analysis + statistical analysis, assignment section 4.3). Both parts are
 * required to use the Java Streams API, which is why everything below is
 * written as stream pipelines rather than classic for-loops, even in a
 * couple of spots where a for-loop would honestly be shorter.</p>
 *
 * <h2>Design decision: how is "more than 70%" computed?</h2>
 * <p>The assignment text says: <i>"if a post contains more than 70% 'happy'
 * strings, return a happy sentiment, for more than 70% sad, return an
 * unhappy emoticon, otherwise a neutral one."</i> That's a little ambiguous
 * about the denominator - 70% of what, exactly? Dividing by the total word
 * count of the post would mean almost nothing could ever cross 70% (most
 * posts are mostly sentiment-neutral words like "the", "going", "Montreal",
 * etc.). So the interpretation we went with is: of the words/emoji/emoticons
 * in the post that actually appear in our happy or sad lists (i.e. ignoring
 * neutral words entirely), what fraction are happy vs. sad? That matches the
 * spirit of "more happy than sad, decisively" a lot better. A post with zero
 * sentiment-bearing words/emoji is NEUTRAL by definition (division by zero
 * guard below).</p>
 *
 * @author MAWA Team (sentiment analysis - individual part)
 */
@Singleton
public class SentimentAnalyzer {

    /** Threshold from the assignment spec - a strict majority isn't enough, it has to be > 70%. */
    private static final double HAPPY_SAD_THRESHOLD = 0.70;

    // Our own curated "happy" / "sad" word lists (assignment: "create your
    // own happy/sad lists"). Kept deliberately small and easy to extend -
    // this is not meant to be a research-grade sentiment lexicon, just
    // enough to demonstrate the required Streams-based pipeline.
    private static final Set<String> HAPPY_WORDS = Set.of(
            "happy", "glad", "great", "awesome", "love", "loved", "loving",
            "excited", "amazing", "fantastic", "wonderful", "excellent",
            "yay", "woohoo", "congrats", "congratulations", "nice", "cool",
            "fun", "thanks", "thankyou", "proud", "delighted", "joy",
            "beautiful", "win", "winning", "best", "good", "goat"
    );

    private static final Set<String> SAD_WORDS = Set.of(
            "sad", "unhappy", "angry", "mad", "hate", "hated", "hating",
            "terrible", "awful", "horrible", "worst", "annoyed", "annoying",
            "frustrated", "frustrating", "disappointed", "disappointing",
            "depressed", "depressing", "broken", "crying", "cry", "upset",
            "sorry", "sucks", "sucked", "bad", "fail", "failed", "failure",
            "tired", "exhausted", "miserable"
    );

    // Emoji/emoticons are matched as raw substrings of the content, since
    // they don't tokenize cleanly on word boundaries like "happy"/"sad" do
    // (e.g. ":)" would be destroyed by splitting on non-word characters).
    private static final List<String> HAPPY_SYMBOLS = List.of(
            ":-)", ":)", ":-D", ":D", "=)", "\uD83D\uDE00", "\uD83D\uDE02", // 😀 😂
            "\uD83D\uDE0A", "\uD83D\uDE04", "\u2764\uFE0F", "\uD83D\uDC4D" // 😊 😄 ❤️ 👍
    );

    private static final List<String> SAD_SYMBOLS = List.of(
            ":-(", ":(", ":'(", "=(", "\uD83D\uDE22", "\uD83D\uDE2D", // 😢 😭
            "\uD83D\uDE1E", "\uD83D\uDC4E", "\uD83D\uDE1F" // 😞 👎 😟
    );

    /**
     * Analyzes a single post's plain-text content and returns its sentiment.
     * Does not mutate the post - see {@link #analyzeAll(List)} for that.
     *
     * @param content plain-text post content (HTML already stripped)
     * @return HAPPY, SAD or NEUTRAL, per the >70% rule described above
     */
    public Sentiment analyze(String content) {
        if (content == null || content.isBlank()) {
            return Sentiment.NEUTRAL;
        }

        String lower = content.toLowerCase();

        // Word-level matches, via a Streams pipeline: split into tokens,
        // strip punctuation, count how many land in each list.
        List<String> words = Arrays.stream(lower.split("\\W+"))
                .filter(w -> !w.isBlank())
                .collect(Collectors.toList());

        long happyWordHits = words.stream().filter(HAPPY_WORDS::contains).count();
        long sadWordHits = words.stream().filter(SAD_WORDS::contains).count();

        // Symbol-level matches (emoji/emoticons), also via a stream -
        // count how many times each known symbol occurs in the raw content.
        long happySymbolHits = HAPPY_SYMBOLS.stream()
                .mapToLong(symbol -> countOccurrences(content, symbol))
                .sum();
        long sadSymbolHits = SAD_SYMBOLS.stream()
                .mapToLong(symbol -> countOccurrences(content, symbol))
                .sum();

        long happyTotal = happyWordHits + happySymbolHits;
        long sadTotal = sadWordHits + sadSymbolHits;
        long sentimentBearingTotal = happyTotal + sadTotal;

        if (sentimentBearingTotal == 0) {
            return Sentiment.NEUTRAL;
        }

        double happyRatio = happyTotal / (double) sentimentBearingTotal;
        double sadRatio = sadTotal / (double) sentimentBearingTotal;

        if (happyRatio > HAPPY_SAD_THRESHOLD) {
            return Sentiment.HAPPY;
        }
        if (sadRatio > HAPPY_SAD_THRESHOLD) {
            return Sentiment.SAD;
        }
        return Sentiment.NEUTRAL;
    }

    /**
     * Runs {@link #analyze(String)} over every post in the list and attaches
     * the result to each {@link Post} via {@link Post#setSentiment(Sentiment)}.
     *
     * @param posts posts to tag in place
     * @return the same list, for convenient chaining
     */
    public List<Post> analyzeAll(List<Post> posts) {
        posts.forEach(post -> post.setSentiment(analyze(post.getContent())));
        return posts;
    }

    /**
     * Rolls up a list of already-sentiment-tagged posts into
     * {@link SearchStatistics}, using {@code groupingBy}/{@code counting} as
     * suggested by the assignment text (section 4.3, "Statistical
     * Analysis").
     *
     * @param posts posts that have already had {@link #analyzeAll(List)} run on them
     * @return aggregate counts of total / positive / negative / neutral posts
     */
    public SearchStatistics summarize(List<Post> posts) {
        if (posts.isEmpty()) {
            return SearchStatistics.empty();
        }

        Map<Sentiment, Long> counts = posts.stream()
                .collect(Collectors.groupingBy(
                        // Posts should always be tagged by the time this runs, but
                        // fall back to NEUTRAL instead of throwing an NPE just in case.
                        (Function<Post, Sentiment>) p -> p.getSentiment() == null ? Sentiment.NEUTRAL : p.getSentiment(),
                        Collectors.counting()));

        long total = posts.size();
        long positive = counts.getOrDefault(Sentiment.HAPPY, 0L);
        long negative = counts.getOrDefault(Sentiment.SAD, 0L);
        long neutral = counts.getOrDefault(Sentiment.NEUTRAL, 0L);

        return new SearchStatistics(total, positive, negative, neutral);
    }

    private static long countOccurrences(String haystack, String needle) {
        if (needle.isEmpty()) {
            return 0;
        }
        return (haystack.length() - haystack.replace(needle, "").length()) / needle.length();
    }
}
