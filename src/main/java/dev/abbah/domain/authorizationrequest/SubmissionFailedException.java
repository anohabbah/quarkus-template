package dev.abbah.domain.authorizationrequest;

/**
 * The destination of a submitted request failed or couldn't be reached.
 */
public class SubmissionFailedException extends RuntimeException {

    public SubmissionFailedException(Throwable cause) {
        super("Submission failed", cause);
    }
}
