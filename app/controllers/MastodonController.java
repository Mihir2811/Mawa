package controllers;

import models.SearchResult;
import play.mvc.Controller;
import play.mvc.Http.Request;
import play.mvc.Result;
import services.MastodonApiException;
import services.MastodonService;
import views.html.error;
import views.html.index;
import views.html.results;

import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * The single controller for this app (as required by assignment 5.3 -
 * "write a single controller for your app, integrating the individual
 * parts"). It is intentionally thin: all the real work (fetching posts,
 * caching, sentiment analysis, stats) lives in {@link MastodonService} and
 * {@link services.SentimentAnalyzer}. This class is just responsible for
 * reading the request, calling the service, and picking which view to
 * render.
 *
 * <p>Every action here is asynchronous - both methods return
 * {@code CompletionStage<Result>} and the result is built with
 * {@code thenApply}/{@code exceptionally}, never by blocking on a future
 * (assignment 5.5).</p>
 *
 * @author MAWA Team
 */
public class MastodonController extends Controller {

    private final MastodonService mastodonService;

    @Inject
    public MastodonController(MastodonService mastodonService) {
        this.mastodonService = mastodonService;
    }

    /**
     * Renders the empty search form. Nothing async happening here, it's
     * just a static page - we only take the {@link Request} because our
     * templates accept it for consistency with the results/error views
     * (useful if we ever need request-scoped data like the locale later).
     *
     * @param request the current HTTP request
     * @return 200 OK with the search form
     */
    public Result index(Request request) {
        return ok(index.render(request));
    }

    /**
     * Handles both the initial search and "Load More" pagination requests -
     * they're the same action, just with/without a {@code maxId} query
     * parameter (assignment 5.1).
     *
     * @param request the current HTTP request
     * @param hashtag the hashtag to search (without '#'); may be blank, which we treat as invalid input
     * @param maxId   optional pagination cursor from a previous page's "Load More" link
     * @return a future rendering of the results page, or a friendly error page on failure
     */
    public CompletionStage<Result> search(Request request, String hashtag, Optional<String> maxId) {
        return mastodonService.search(hashtag, maxId)
                .thenApply(result -> renderResults(request, result))
                .exceptionally(throwable -> renderError(request, throwable));
    }

    private Result renderResults(Request request, SearchResult result) {
        return ok(results.render(result, request));
    }

    /**
     * Turns any failure from the service layer into a user-friendly error
     * page with an appropriate HTTP status, instead of letting a stack
     * trace reach the browser (assignment 5.2).
     */
    private Result renderError(Request request, Throwable throwable) {
        Throwable cause = unwrap(throwable);

        if (cause instanceof MastodonApiException) {
            MastodonApiException apiException = (MastodonApiException) cause;
            String message = apiException.getMessage();

            switch (apiException.getReason()) {
                case INVALID_INPUT:
                    return badRequest(error.render("Invalid search", message, request));
                case AUTH_FAILURE:
                    return internalServerError(error.render("Configuration problem",
                            "The app couldn't authenticate with Mastodon. "
                                    + "(This is a server-side configuration issue, not something wrong "
                                    + "with your search.)", request));
                case API_UNAVAILABLE:
                    // Play doesn't have a badGateway() helper built in, so we
                    // set the 502 status explicitly - it's the most accurate
                    // code for "the upstream Mastodon instance failed us".
                    return status(BAD_GATEWAY, error.render("Mastodon is unavailable",
                            "Unable to retrieve posts from Mastodon. Please try again later.", request));
                case HTTP_ERROR:
                    return badRequest(error.render("Search failed", message, request));
                case MALFORMED_RESPONSE:
                default:
                    return internalServerError(error.render("Unexpected response",
                            "Mastodon returned something we didn't expect. Please try again later.", request));
            }
        }

        // Anything we didn't explicitly anticipate still gets a clean
        // message rather than a raw stack trace reaching the user.
        return internalServerError(error.render("Something went wrong",
                "An unexpected error occurred while processing your search. Please try again.", request));
    }

    /**
     * {@link CompletionStage#exceptionally(java.util.function.Function)}
     * wraps whatever exception actually occurred inside a
     * {@link java.util.concurrent.CompletionException}; this just unwraps
     * one layer so we can pattern-match on the real cause.
     */
    private static Throwable unwrap(Throwable t) {
        return (t instanceof java.util.concurrent.CompletionException && t.getCause() != null) ? t.getCause() : t;
    }
}
