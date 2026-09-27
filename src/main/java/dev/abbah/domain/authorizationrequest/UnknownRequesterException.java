package dev.abbah.domain.authorizationrequest;

/**
 * The destination of a submitted request doesn't know its requester.
 */
public class UnknownRequesterException extends RuntimeException {

    public UnknownRequesterException(String requestedBy, Throwable cause) {
        super("Unknown requester: " + mask(requestedBy), cause);
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 2) {
            return "******" + email.substring(at);
        }
        return email.charAt(0) + "******" + email.charAt(at - 1) + email.substring(at);
    }
}
