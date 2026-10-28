package controllers;

import models.SearchResult;
import models.SearchStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import services.MastodonApiException;
import services.MastodonApiException.Reason;
import services.MastodonService;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static play.mvc.Http.Status.BAD_GATEWAY;
import static play.mvc.Http.Status.BAD_REQUEST;
import static play.mvc.Http.Status.INTERNAL_SERVER_ERROR;
import static play.mvc.Http.Status.OK;

/**
 * Tests for {@link MastodonController}.
 *
 * <p>The service layer is mocked throughout (never calls the real Mastodon
 * API, assignment 5.6). Each {@link MastodonApiException.Reason} gets its
 * own test, since that switch statement in
 * {@code MastodonController#renderError} is exactly the kind of branching
 * logic the 100% branch coverage requirement (assignment 5.6) is aimed at.</p>
 *
 * @author MAWA Team
 */
@ExtendWith(MockitoExtension.class)
class MastodonControllerTest {

    @Mock
    private MastodonService mastodonService;

    private MastodonController controller;
    private Http.Request request;

    @BeforeEach
    void setUp() {
        controller = new MastodonController(mastodonService);
        request = Helpers.fakeRequest().build();
    }

    @Test
    void index_rendersSearchForm() {
        Result result = controller.index(request);
        assertEquals(OK, result.status());
    }

    @Test
    void search_successfulSearch_rendersResultsPageWithOkStatus() {
        SearchResult searchResult = new SearchResult("java", Collections.emptyList(),
                SearchStatistics.empty(), Optional.empty());
        when(mastodonService.search(eq("java"), eq(Optional.empty())))
                .thenReturn(completed(searchResult));

        Result result = controller.search(request, "java", Optional.empty()).toCompletableFuture().join();

        assertEquals(OK, result.status());
    }

    @Test
    void search_invalidInput_returnsBadRequest() {
        when(mastodonService.search(eq(""), any()))
                .thenReturn(failed(new MastodonApiException(Reason.INVALID_INPUT, "Please enter a hashtag.")));

        Result result = controller.search(request, "", Optional.empty()).toCompletableFuture().join();

        assertEquals(BAD_REQUEST, result.status());
    }

    @Test
    void search_authFailure_returnsInternalServerError() {
        when(mastodonService.search(eq("java"), any()))
                .thenReturn(failed(new MastodonApiException(Reason.AUTH_FAILURE, "bad token")));

        Result result = controller.search(request, "java", Optional.empty()).toCompletableFuture().join();

        assertEquals(INTERNAL_SERVER_ERROR, result.status());
    }

    @Test
    void search_apiUnavailable_returnsBadGateway() {
        when(mastodonService.search(eq("java"), any()))
                .thenReturn(failed(new MastodonApiException(Reason.API_UNAVAILABLE, "down")));

        Result result = controller.search(request, "java", Optional.empty()).toCompletableFuture().join();

        assertEquals(BAD_GATEWAY, result.status());
    }

    @Test
    void search_httpError_returnsBadRequest() {
        when(mastodonService.search(eq("java"), any()))
                .thenReturn(failed(new MastodonApiException(Reason.HTTP_ERROR, "rejected")));

        Result result = controller.search(request, "java", Optional.empty()).toCompletableFuture().join();

        assertEquals(BAD_REQUEST, result.status());
    }

    @Test
    void search_malformedResponse_returnsInternalServerError() {
        when(mastodonService.search(eq("java"), any()))
                .thenReturn(failed(new MastodonApiException(Reason.MALFORMED_RESPONSE, "bad json")));

        Result result = controller.search(request, "java", Optional.empty()).toCompletableFuture().join();

        assertEquals(INTERNAL_SERVER_ERROR, result.status());
    }

    @Test
    void search_unexpectedException_returnsInternalServerErrorWithoutLeakingStackTrace() {
        when(mastodonService.search(eq("java"), any()))
                .thenReturn(failed(new RuntimeException("something nobody anticipated")));

        Result result = controller.search(request, "java", Optional.empty()).toCompletableFuture().join();

        assertEquals(INTERNAL_SERVER_ERROR, result.status());
        String body = Helpers.contentAsString(result);
        // The whole point of the catch-all branch: the raw exception message
        // / stack trace must never reach the rendered page.
        assertEquals(false, body.contains("something nobody anticipated"));
    }

    @Test
    void search_withMaxId_passesItThroughToService() {
        SearchResult searchResult = new SearchResult("java", Collections.emptyList(),
                SearchStatistics.empty(), Optional.empty());
        when(mastodonService.search(eq("java"), eq(Optional.of("12345"))))
                .thenReturn(completed(searchResult));

        Result result = controller.search(request, "java", Optional.of("12345")).toCompletableFuture().join();

        assertEquals(OK, result.status());
    }

    private static CompletionStage<SearchResult> completed(SearchResult result) {
        return CompletableFuture.completedFuture(result);
    }

    private static CompletionStage<SearchResult> failed(Throwable t) {
        CompletableFuture<SearchResult> future = new CompletableFuture<>();
        future.completeExceptionally(t);
        return future;
    }
}
