package dev.abbah.domain.authorizationrequest;

import java.util.UUID;

/**
 * The destination of a submitted request already holds a different request under its request id.
 */
public class RequestIdReusedException extends RuntimeException {

    public RequestIdReusedException(UUID requestId, Throwable cause) {
        super("Request id reused: " + requestId, cause);
    }
}
