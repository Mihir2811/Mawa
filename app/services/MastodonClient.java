package services;

import models.Post;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Talks to the Mastodon REST API and turns the response into our own
 * {@link Post} objects. This is the <em>only</em> place in the whole app
 * that knows the Mastodon JSON shape exists - everything else works with
 * {@code Post}.
 *
 * <p>It's an interface (rather than just a class) specifically so that unit
 * tests can swap in a mock implementation instead of hitting the real,
 * live Mastodon API (assignment 5.6: "Never call the live Mastodon API from
 * a unit test"). {@link MastodonClientImpl} is the real implementation,
 * bound via Guice in {@code Module.java} for production use, and tests just
 * new up a Mockito mock of this interface directly.</p>
 *
 * @author MAWA Team
 */
public interface MastodonClient {

    /**
     * Fetches one page of public posts for a given hashtag.
     *
     * @param hashtag the hashtag to search, WITHOUT the leading '#'
     * @param maxId   if present, only return posts older than this status id
     *                (this is how pagination / "Load More" works - see
     *                assignment 5.1)
     * @param limit   max number of posts to request (caller is responsible
     *                for keeping this <= 40, see assignment 4.1)
     * @return a future list of posts, newest first, as returned by Mastodon
     * @throws MastodonApiException (via the failed future) wrapping
     *                               whatever went wrong - auth failure,
     *                               network error, bad JSON, etc.
     */
    CompletionStage<List<Post>> fetchTagTimeline(String hashtag, Optional<String> maxId, int limit);
}
