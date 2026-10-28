package services;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import models.Post;
import models.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link MastodonServiceImpl}. Uses a mocked {@link MastodonClient}
 * (never the live API, per assignment 5.6) plus a real {@link SentimentAnalyzer}
 * and {@link InMemoryResultCache}, since those are cheap, fast, and testing
 * through them is more realistic than mocking everything.
 *
 * @author MAWA Team
 */
@ExtendWith(MockitoExtension.class)
class MastodonServiceImplTest {

    @Mock
    private MastodonClient mastodonClient;

    private MastodonServiceImpl service;

    @BeforeEach
    void setUp() {
        Config config = ConfigFactory.parseMap(Map.of(
                "mastodon.maxPostsPerRequest", 40,
                "mastodon.cacheTtlSeconds", 60
        ));
        service = new MastodonServiceImpl(mastodonClient, new SentimentAnalyzer(),
                new InMemoryResultCache(config), config);
    }

    @Test
    void search_blankHashtag_failsWithInvalidInput() {
        CompletionStage<SearchResult> stage = service.search("   ", Optional.empty());

        CompletionException ex = assertThrows(CompletionException.class,
                () -> stage.toCompletableFuture().join());
        assertTrue(ex.getCause() instanceof MastodonApiException);
        assertEquals(MastodonApiException.Reason.INVALID_INPUT,
                ((MastodonApiException) ex.getCause()).getReason());

        verify(mastodonClient, never()).fetchTagTimeline(any(), any(), anyInt());
    }

    @Test
    void search_nullHashtag_failsWithInvalidInput() {
        CompletionStage<SearchResult> stage = service.search(null, Optional.empty());
        assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());
    }

    @Test
    void search_validHashtag_returnsSentimentTaggedResults() throws ExecutionException, InterruptedException {
        when(mastodonClient.fetchTagTimeline(eq("java"), eq(Optional.empty()), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        post("1", "I love this, so happy and excited!"),
                        post("2", "ugh, this is terrible and sad")
                )));

        SearchResult result = service.search("java", Optional.empty()).toCompletableFuture().get();

        assertEquals("java", result.getHashtag());
        assertEquals(2, result.getStatistics().getTotalPosts());
        assertEquals(1, result.getStatistics().getPositivePosts());
        assertEquals(1, result.getStatistics().getNegativePosts());
    }

    @Test
    void search_hashtagIsNormalized_caseAndWhitespaceDoNotMatterForCaching() {
        when(mastodonClient.fetchTagTimeline(eq("java"), eq(Optional.empty()), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(post("1", "neutral post here"))));

        service.search(" JAVA ", Optional.empty()).toCompletableFuture().join();
        service.search("java", Optional.empty()).toCompletableFuture().join();

        // Both calls normalize to the same cache key ("java"), so the
        // underlying client should only ever be hit once (section 5.3:
        // "do not query the API multiple times with the same hashtag").
        verify(mastodonClient, times(1)).fetchTagTimeline(eq("java"), eq(Optional.empty()), anyInt());
    }

    @Test
    void search_sameHashtagTwice_onlyCallsClientOnce() {
        when(mastodonClient.fetchTagTimeline(eq("java"), eq(Optional.empty()), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(post("1", "just a post"))));

        service.search("java", Optional.empty()).toCompletableFuture().join();
        service.search("java", Optional.empty()).toCompletableFuture().join();

        verify(mastodonClient, times(1)).fetchTagTimeline(eq("java"), eq(Optional.empty()), anyInt());
    }

    @Test
    void search_differentMaxId_isTreatedAsDifferentCacheEntry() {
        when(mastodonClient.fetchTagTimeline(eq("java"), eq(Optional.empty()), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(post("1", "page one"))));
        when(mastodonClient.fetchTagTimeline(eq("java"), eq(Optional.of("999")), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(post("2", "page two"))));

        service.search("java", Optional.empty()).toCompletableFuture().join();
        service.search("java", Optional.of("999")).toCompletableFuture().join();

        verify(mastodonClient, times(1)).fetchTagTimeline(eq("java"), eq(Optional.empty()), anyInt());
        verify(mastodonClient, times(1)).fetchTagTimeline(eq("java"), eq(Optional.of("999")), anyInt());
    }

    @Test
    void search_fullPageReturned_offersNextMaxId() {
        Config smallPageConfig = ConfigFactory.parseMap(Map.of(
                "mastodon.maxPostsPerRequest", 2,
                "mastodon.cacheTtlSeconds", 60
        ));
        MastodonServiceImpl smallPageService = new MastodonServiceImpl(mastodonClient, new SentimentAnalyzer(),
                new InMemoryResultCache(smallPageConfig), smallPageConfig);

        when(mastodonClient.fetchTagTimeline(eq("java"), eq(Optional.empty()), eq(2)))
                .thenReturn(CompletableFuture.completedFuture(List.of(post("1", "a"), post("2", "b"))));

        SearchResult result = smallPageService.search("java", Optional.empty()).toCompletableFuture().join();

        assertTrue(result.hasMore());
        assertEquals("2", result.getNextMaxId().orElseThrow());
    }

    @Test
    void search_partialPageReturned_hasNoNextMaxId() {
        when(mastodonClient.fetchTagTimeline(eq("java"), eq(Optional.empty()), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(post("1", "only one post"))));

        SearchResult result = service.search("java", Optional.empty()).toCompletableFuture().join();

        assertFalse(result.hasMore());
        assertTrue(result.getNextMaxId().isEmpty());
    }

    @Test
    void search_emptyResultSet_returnsEmptyStatisticsNotAnError() {
        when(mastodonClient.fetchTagTimeline(eq("obscure"), eq(Optional.empty()), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        SearchResult result = service.search("obscure", Optional.empty()).toCompletableFuture().join();

        assertTrue(result.getPosts().isEmpty());
        assertEquals(0, result.getStatistics().getTotalPosts());
        assertFalse(result.hasMore());
    }

    @Test
    void search_clientFailure_propagatesAsCompletionException() {
        CompletableFuture<List<Post>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new MastodonApiException(MastodonApiException.Reason.API_UNAVAILABLE, "down"));
        when(mastodonClient.fetchTagTimeline(eq("java"), eq(Optional.empty()), anyInt())).thenReturn(failed);

        CompletionStage<SearchResult> stage = service.search("java", Optional.empty());
        assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());
    }

    private static Post post(String id, String content) {
        return new Post(id, "Someone", "someone@example.social", Instant.now(), "en", content,
                "https://example.social/@someone/" + id, "public");
    }
}
