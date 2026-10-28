package services;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import models.Post;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link MastodonClientImpl}, with {@link WSClient} fully mocked
 * so these never touch the network or a real Mastodon instance (assignment
 * 5.6). Each branch of {@code parseResponse}/{@code toPost} gets its own
 * test for the same reason as {@code MastodonControllerTest} - 100% branch
 * coverage means every HTTP-status / malformed-data path needs an explicit
 * case.
 *
 * @author MAWA Team
 */
@ExtendWith(MockitoExtension.class)
class MastodonClientImplTest {

    @Mock
    private WSClient wsClient;
    @Mock
    private WSRequest wsRequest;
    @Mock
    private WSResponse wsResponse;

    private MastodonClientImpl client;

    @BeforeEach
    void setUp() {
        Config config = ConfigFactory.parseMap(Map.of(
                "mastodon.instance", "test.social",
                "mastodon.accessToken", "test-token"
        ));
        client = new MastodonClientImpl(wsClient, config);

        when(wsClient.url(anyString())).thenReturn(wsRequest);
        when(wsRequest.addQueryParameter(anyString(), anyString())).thenReturn(wsRequest);
        when(wsRequest.addHeader(anyString(), anyString())).thenReturn(wsRequest);
    }

    @Test
    void fetchTagTimeline_emptyHashtag_failsWithoutCallingWs() {
        CompletionStage<List<Post>> stage = client.fetchTagTimeline("", Optional.empty(), 40);

        MastodonApiException ex = assertApiException(stage);
        assertEquals(MastodonApiException.Reason.INVALID_INPUT, ex.getReason());
        verify(wsClient, never()).url(anyString());
    }

    @Test
    void fetchTagTimeline_blankHashtag_failsWithInvalidInput() {
        CompletionStage<List<Post>> stage = client.fetchTagTimeline("   ", Optional.empty(), 40);
        assertEquals(MastodonApiException.Reason.INVALID_INPUT, assertApiException(stage).getReason());
    }

    @Test
    void fetchTagTimeline_networkFailure_wrapsAsApiUnavailable() {
        CompletableFuture<WSResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new java.net.ConnectException("refused"));
        when(wsRequest.get()).thenReturn(failed);

        CompletionStage<List<Post>> stage = client.fetchTagTimeline("java", Optional.empty(), 40);

        assertEquals(MastodonApiException.Reason.API_UNAVAILABLE, assertApiException(stage).getReason());
    }

    @Test
    void fetchTagTimeline_401Response_isAuthFailure() {
        mockResponse(401, Json.newArray());
        assertEquals(MastodonApiException.Reason.AUTH_FAILURE, runAndGetException());
    }

    @Test
    void fetchTagTimeline_403Response_isAuthFailure() {
        mockResponse(403, Json.newArray());
        assertEquals(MastodonApiException.Reason.AUTH_FAILURE, runAndGetException());
    }

    @Test
    void fetchTagTimeline_500Response_isApiUnavailable() {
        mockResponse(500, Json.newArray());
        assertEquals(MastodonApiException.Reason.API_UNAVAILABLE, runAndGetException());
    }

    @Test
    void fetchTagTimeline_404Response_isHttpError() {
        mockResponse(404, Json.newArray());
        assertEquals(MastodonApiException.Reason.HTTP_ERROR, runAndGetException());
    }

    @Test
    void fetchTagTimeline_nonArrayJson_isMalformedResponse() {
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(Json.newObject().put("not", "an array"));
        when(wsRequest.get()).thenReturn(CompletableFuture.completedFuture(wsResponse));

        assertEquals(MastodonApiException.Reason.MALFORMED_RESPONSE, runAndGetException());
    }

    @Test
    void fetchTagTimeline_invalidJsonBody_isMalformedResponse() {
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenThrow(new RuntimeException("not valid json"));
        when(wsRequest.get()).thenReturn(CompletableFuture.completedFuture(wsResponse));

        assertEquals(MastodonApiException.Reason.MALFORMED_RESPONSE, runAndGetException());
    }

    @Test
    void fetchTagTimeline_successfulResponse_mapsFieldsCorrectly() {
        com.fasterxml.jackson.databind.node.ArrayNode statuses = Json.newArray();
        com.fasterxml.jackson.databind.node.ObjectNode status = Json.newObject();
        status.put("id", "111");
        status.put("created_at", "2026-01-15T10:30:00.000Z");
        status.put("language", "en");
        status.put("content", "<p>Hello <b>world</b> #java</p>");
        status.put("url", "https://test.social/@someone/111");
        status.put("visibility", "public");

        com.fasterxml.jackson.databind.node.ObjectNode account = Json.newObject();
        account.put("display_name", "Someone Cool");
        account.put("acct", "someone@test.social");
        status.set("account", account);

        statuses.add(status);
        mockResponse(200, statuses);

        List<Post> posts = client.fetchTagTimeline("java", Optional.empty(), 40)
                .toCompletableFuture().join();

        assertEquals(1, posts.size());
        Post post = posts.get(0);
        assertEquals("111", post.getId());
        assertEquals("Someone Cool", post.getAuthorDisplayName());
        assertEquals("someone@test.social", post.getAuthorHandle());
        assertEquals("en", post.getLanguage());
        // HTML tags must be stripped (assignment 4.2: readable text, not raw HTML).
        assertTrue(post.getContent().contains("Hello world #java"));
        assertEquals(false, post.getContent().contains("<p>"));
        assertEquals("public", post.getVisibility());
    }

    @Test
    void fetchTagTimeline_emptyResultArray_returnsEmptyList() {
        mockResponse(200, Json.newArray());

        List<Post> posts = client.fetchTagTimeline("java", Optional.empty(), 40)
                .toCompletableFuture().join();

        assertTrue(posts.isEmpty());
    }

    @Test
    void fetchTagTimeline_missingDisplayName_fallsBackToHandle() {
        com.fasterxml.jackson.databind.node.ArrayNode statuses = Json.newArray();
        com.fasterxml.jackson.databind.node.ObjectNode status = Json.newObject();
        status.put("id", "222");
        status.put("created_at", "2026-01-15T10:30:00.000Z");
        status.put("content", "no display name set");

        com.fasterxml.jackson.databind.node.ObjectNode account = Json.newObject();
        account.put("display_name", "");
        account.put("acct", "justahandle@test.social");
        status.set("account", account);

        statuses.add(status);
        mockResponse(200, statuses);

        Post post = client.fetchTagTimeline("java", Optional.empty(), 40)
                .toCompletableFuture().join().get(0);

        assertEquals("justahandle@test.social", post.getAuthorDisplayName());
    }

    @Test
    void fetchTagTimeline_withMaxId_addsMaxIdQueryParameter() {
        mockResponse(200, Json.newArray());

        client.fetchTagTimeline("java", Optional.of("98765"), 40).toCompletableFuture().join();

        verify(wsRequest).addQueryParameter("max_id", "98765");
    }

    private void mockResponse(int status, com.fasterxml.jackson.databind.JsonNode body) {
        when(wsResponse.getStatus()).thenReturn(status);
        if (status < 400) {
            when(wsResponse.asJson()).thenReturn(body);
        }
        when(wsRequest.get()).thenReturn(CompletableFuture.completedFuture(wsResponse));
    }

    private MastodonApiException.Reason runAndGetException() {
        CompletionStage<List<Post>> stage = client.fetchTagTimeline("java", Optional.empty(), 40);
        return assertApiException(stage).getReason();
    }

    private MastodonApiException assertApiException(CompletionStage<List<Post>> stage) {
        try {
            stage.toCompletableFuture().join();
            throw new AssertionError("Expected the future to fail, but it completed successfully.");
        } catch (CompletionException e) {
            assertTrue(e.getCause() instanceof MastodonApiException,
                    "Expected a MastodonApiException, got: " + e.getCause());
            return (MastodonApiException) e.getCause();
        }
    }
}
