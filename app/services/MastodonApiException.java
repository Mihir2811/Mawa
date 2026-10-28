package services;

/**
 * Wraps every way a call to the Mastodon API can go wrong, so that
 * {@link controllers.MastodonController} only ever has to handle one
 * exception type instead of guessing which checked/unchecked exception a
 * given failure mode throws (timeouts, HTTP errors, bad JSON, etc).
 *
 * <p>See assignment section 5.2 (Error Handling) - the point of this class
 * is that nothing below the controller is allowed to leak a raw stack trace
 * or a library-specific exception up to the user.</p>
 *
 * @author MAWA Team
 */
public class MastodonApiException extends RuntimeException {

    /** Buckets so the controller can pick a sensible HTTP status + message. */
    public enum Reason {
        INVALID_INPUT,
        AUTH_FAILURE,
        API_UNAVAILABLE,
        HTTP_ERROR,
        MALFORMED_RESPONSE
    }

    private final Reason reason;

    public MastodonApiException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public MastodonApiException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
