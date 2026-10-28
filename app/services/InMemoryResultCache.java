package services;

import com.typesafe.config.Config;
import models.SearchResult;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Simple in-process, TTL-based implementation of {@link ResultCache}.
 *
 * <p>Good enough for a single-instance course project; in a "real" deployment
 * with multiple app instances behind a load balancer you'd want a shared
 * cache (Redis, Memcached, Play's distributed cache module, ...) instead so
 * every instance isn't independently re-querying Mastodon for the same
 * hashtag. Swapping that in later is exactly why this is hidden behind the
 * {@link ResultCache} interface rather than used directly.</p>
 *
 * <p>Note: there's a small, deliberately-accepted race condition here - two
 * concurrent cache misses for the same key could both call the supplier
 * before either result is stored. For this project's traffic levels (one
 * TA clicking "search" during a demo) that's an acceptable trade-off for
 * keeping the implementation simple; a production version would use
 * {@code ConcurrentHashMap#compute} with a single in-flight future per key
 * to close that gap entirely.</p>
 *
 * @author MAWA Team
 */
@Singleton
public class InMemoryResultCache implements ResultCache {

    private static final class Entry {
        final CompletionStage<SearchResult> future;
        final Instant expiresAt;

        Entry(CompletionStage<SearchResult> future, Instant expiresAt) {
            this.future = future;
            this.expiresAt = expiresAt;
        }
    }

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final Duration ttl;

    @Inject
    public InMemoryResultCache(Config config) {
        this.ttl = Duration.ofSeconds(config.getInt("mastodon.cacheTtlSeconds"));
    }

    @Override
    public CompletionStage<SearchResult> getOrCompute(String key, Supplier<CompletionStage<SearchResult>> supplier) {
        Instant now = Instant.now();
        Entry existing = store.get(key);
        if (existing != null && existing.expiresAt.isAfter(now)) {
            return existing.future;
        }

        Entry fresh = new Entry(supplier.get(), now.plus(ttl));
        store.put(key, fresh);

        // If the fetch fails, don't leave a poisoned/failed future sitting
        // in the cache for the rest of the TTL - evict it so the very next
        // search for the same hashtag gets a real retry instead of
        // immediately re-throwing the same cached error.
        fresh.future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                store.remove(key, fresh);
            }
        });

        return fresh.future;
    }
}
