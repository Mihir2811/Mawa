package models;

import java.time.Instant;
import java.util.Objects;

/**
 * Application-level representation of a single Mastodon post ("status").
 *
 * <p>This is intentionally <b>our own</b> model, separate from whatever JSON
 * shape the Mastodon REST API happens to return. That's not just tidiness -
 * it's required by the design goals in section 8 of the assignment: in
 * Delivery 2 we'll be getting these same fields from the Mastodon
 * <i>streaming</i> API instead of the REST timeline endpoint, and the rest
 * of the app (views, sentiment analysis, stats) shouldn't have to care which
 * source a given Post came from. {@link services.MastodonClient} is the only
 * class that knows about Mastodon's raw JSON format; everything downstream
 * of it only ever sees a {@code Post}.</p>
 *
 * <p>Instances are immutable - once a Post is built from the API response it
 * doesn't change, except that we attach a {@link Sentiment} to it after the
 * fact (sentiment is computed, not part of the raw Mastodon data).</p>
 *
 * @author MAWA Team
 */
public final class Post {

    private final String id;
    private final String authorDisplayName;
    private final String authorHandle;
    private final Instant createdAt;
    private final String language;
    /** Plain-text content (HTML tags already stripped - see section 4.2). */
    private final String content;
    private final String url;
    private final String visibility;

    /** Set after construction, once sentiment analysis has run. May be null. */
    private Sentiment sentiment;

    public Post(String id, String authorDisplayName, String authorHandle, Instant createdAt,
                String language, String content, String url, String visibility) {
        this.id = Objects.requireNonNull(id, "id");
        this.authorDisplayName = authorDisplayName;
        this.authorHandle = authorHandle;
        this.createdAt = createdAt;
        this.language = language;
        this.content = content == null ? "" : content;
        this.url = url;
        this.visibility = visibility;
    }

    public String getId() {
        return id;
    }

    public String getAuthorDisplayName() {
        return authorDisplayName;
    }

    public String getAuthorHandle() {
        return authorHandle;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getLanguage() {
        return language;
    }

    public String getContent() {
        return content;
    }

    public String getUrl() {
        return url;
    }

    public String getVisibility() {
        return visibility;
    }

    public Sentiment getSentiment() {
        return sentiment;
    }

    /**
     * Attaches the computed sentiment to this post. Package-private-ish on
     * purpose (public because views.html templates in app/views need it, but
     * only {@link services.SentimentAnalyzer} should actually call this).
     *
     * @param sentiment the sentiment computed for this post's content
     */
    public void setSentiment(Sentiment sentiment) {
        this.sentiment = sentiment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Post)) return false;
        Post post = (Post) o;
        return id.equals(post.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        // mostly here so test failure messages are actually readable
        return "Post{id='" + id + "', author='" + authorHandle + "', sentiment=" + sentiment + "}";
    }
}
