package services;

import models.SearchResult;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * A tiny cache-aside abstraction: "give me the value for this key, computing
 * and storing it via the supplier if it's not already cached (or has
 * expired)".
 *
 * <p>We rolled our own instead of depending directly on Play's cache module
 * API inside {@link MastodonServiceImpl} for two reasons: (1) it keeps the
 * service logic trivially unit-testable without needing a running Play
 * application or a Guice container in tests, and (2) it keeps the exact
 * caching backend (in-memory now, could be Redis/Ehcache later) an
 * implementation detail behind one interface, consistent with how we
 * isolated the Mastodon REST API behind {@link MastodonClient}.</p>
 *
 * @author MAWA Team
 */
public interface ResultCache {

    /**
     * Returns the cached value for {@code key} if present and not expired;
     * otherwise calls {@code supplier}, caches its result, and returns it.
     *
     * @param key      cache key, e.g. {@code "tag:java:first"}
     * @param supplier produces the value on a cache miss (only invoked on a miss)
     * @return a future that resolves to either the cached or freshly computed value
     */
    CompletionStage<SearchResult> getOrCompute(String key, Supplier<CompletionStage<SearchResult>> supplier);
}
