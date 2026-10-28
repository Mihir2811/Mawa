package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import models.Post;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Real implementation of {@link MastodonClient}, talking to
 * {@code GET /api/v1/timelines/tag/{hashtag}} on the configured Mastodon
 * instance using Play's non-blocking {@link WSClient}.
 *
 * <p>Everything in here runs asynchronously - there is no {@code .get()} or
 * {@code .join()} anywhere (that's a hard requirement, see assignment 5.5).
 * We only ever transform the {@link CompletionStage} with
 * {@code thenApply}/{@code handle}, never block on it.</p>
 *
 * @author MAWA Team
 */
@Singleton
public class MastodonClientImpl implements MastodonClient {

    private final WSClient ws;
    private final String instance;
    private final String accessToken;

    @Inject
    public MastodonClientImpl(WSClient ws, Config config) {
        this.ws = ws;
        this.instance = config.getString("mastodon.instance");
        this.accessToken = config.getString("mastodon.accessToken");
    }

    @Override
    public CompletionStage<List<Post>> fetchTagTimeline(String hashtag, Optional<String> maxId, int limit) {
        if (hashtag == null || hashtag.isBlank()) {
            // Fail fast rather than sending a garbage request to Mastodon.
            return failedFuture(new MastodonApiException(MastodonApiException.Reason.INVALID_INPUT,
                    "Hashtag must not be empty."));
        }

        String url = "https://" + instance + "/api/v1/timelines/tag/" + encode(hashtag);

        WSRequest request = ws.url(url)
                .addQueryParameter("limit", String.valueOf(Math.min(limit, 40)));
        maxId.ifPresent(id -> request.addQueryParameter("max_id", id));

        if (accessToken != null && !accessToken.isBlank()) {
            request.addHeader("Authorization", "Bearer " + accessToken);
        }

        return request.get().handle((response, throwable) -> {
            if (throwable != null) {
                // Network-level failure (DNS, connection refused, timeout...)
                throw new MastodonApiException(MastodonApiException.Reason.API_UNAVAILABLE,
                        "Could not reach the Mastodon instance (" + instance + ").", throwable);
            }
            return parseResponse(response);
        });
    }

    /**
     * Turns a raw {@link WSResponse} into our domain {@link Post} list,
     * raising a {@link MastodonApiException} with a precise reason for
     * every failure mode called out in assignment 5.2.
     */
    private List<Post> parseResponse(WSResponse response) {
        int status = response.getStatus();

        if (status == 401 || status == 403) {
            throw new MastodonApiException(MastodonApiException.Reason.AUTH_FAILURE,
                    "Authentication with Mastodon failed. Check MASTODON_ACCESS_TOKEN.");
        }
        if (status >= 500) {
            throw new MastodonApiException(MastodonApiException.Reason.API_UNAVAILABLE,
                    "Mastodon returned a server error (HTTP " + status + ").");
        }
        if (status >= 400) {
            throw new MastodonApiException(MastodonApiException.Reason.HTTP_ERROR,
                    "Mastodon rejected the request (HTTP " + status + "). Is the hashtag valid?");
        }

        JsonNode body;
        try {
            body = response.asJson();
        } catch (RuntimeException e) {
            throw new MastodonApiException(MastodonApiException.Reason.MALFORMED_RESPONSE,
                    "Mastodon returned a response that wasn't valid JSON.", e);
        }

        if (body == null || !body.isArray()) {
            throw new MastodonApiException(MastodonApiException.Reason.MALFORMED_RESPONSE,
                    "Unexpected response shape from Mastodon (expected a JSON array of statuses).");
        }

        List<Post> posts = new ArrayList<>();
        for (JsonNode node : body) {
            posts.add(toPost(node));
        }
        return posts;
    }

    /**
     * Maps one Mastodon "status" JSON object to our domain {@link Post}.
     * Any single malformed entry turns into a {@code MALFORMED_RESPONSE}
     * exception rather than a silent NPE further down the pipeline.
     */
    private Post toPost(JsonNode node) {
        try {
            String id = node.get("id").asText();

            JsonNode account = node.get("account");
            String displayName = textOrNull(account, "display_name");
            String handle = textOrNull(account, "acct");
            if (displayName == null || displayName.isBlank()) {
                displayName = handle; // Mastodon allows an empty display name
            }

            Instant createdAt;
            try {
                createdAt = Instant.parse(node.get("created_at").asText());
            } catch (DateTimeParseException e) {
                createdAt = null; // not fatal - we just won't show a timestamp
            }

            String language = textOrNull(node, "language");
            String rawHtmlContent = textOrNull(node, "content");
            // Strip tags down to plain, readable text (assignment 4.2).
            String plainContent = Jsoup.clean(rawHtmlContent == null ? "" : rawHtmlContent, Safelist.none())
                    .replace("&amp;", "&");

            String url = textOrNull(node, "url");
            String visibility = textOrNull(node, "visibility");

            return new Post(id, displayName, handle, createdAt, language, plainContent, url, visibility);
        } catch (RuntimeException e) {
            throw new MastodonApiException(MastodonApiException.Reason.MALFORMED_RESPONSE,
                    "Could not parse a post returned by Mastodon.", e);
        }
    }

    private static String textOrNull(JsonNode parent, String field) {
        if (parent == null || !parent.hasNonNull(field)) {
            return null;
        }
        return parent.get(field).asText();
    }

    private static String encode(String hashtag) {
        // Hashtags are user input - make sure nothing weird ends up in the URL path.
        return java.net.URLEncoder.encode(hashtag, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static <T> CompletionStage<T> failedFuture(Throwable t) {
        java.util.concurrent.CompletableFuture<T> f = new java.util.concurrent.CompletableFuture<>();
        f.completeExceptionally(t);
        return f;
    }
}
