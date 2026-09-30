package dev.abbah.infra.spi.rest.salesforce.authorizationrequest;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

/**
 * Checks the production retry budget, which {@code SalesforceStub} overrides in every other test.
 */
class AuthorizationRequestClientTest {

    private static final Duration CALLER_BUDGET = Duration.ofSeconds(25);

    private static Retry retry;
    private static Timeout timeout;
    private static Properties config;

    @BeforeAll
    static void readProductionSettings() throws NoSuchMethodException, IOException {
        var submit = AuthorizationRequestClient.class.getMethod("submit", AuthorizationRequestPayload.Request.class);
        retry = submit.getAnnotation(Retry.class);
        timeout = submit.getAnnotation(Timeout.class);
        config = new Properties();
        try (InputStream in = AuthorizationRequestClient.class.getResourceAsStream("/application.properties")) {
            config.load(in);
        }
    }

    @Test
    void everyAttemptFitsTheCallerBudget() {
        Duration attempt = Duration.of(timeout.value(), timeout.unit());
        Duration longestWait = Duration.of(retry.delay() + retry.jitter(), retry.delayUnit());
        int attempts = retry.maxRetries() + 1;

        Duration worstCase = attempt.multipliedBy(attempts).plus(longestWait.multipliedBy(retry.maxRetries()));

        assertThat(worstCase, lessThanOrEqualTo(CALLER_BUDGET));
    }

    @Test
    void connectAndReadFitInOneAttempt() {
        Duration connect = Duration.ofMillis(Long.parseLong(config.getProperty("quarkus.rest-client.salesforce.connect-timeout")));
        Duration read = Duration.ofMillis(Long.parseLong(config.getProperty("quarkus.rest-client.salesforce.read-timeout")));

        assertThat(connect.plus(read), lessThanOrEqualTo(Duration.of(timeout.value(), timeout.unit())));
    }
}
