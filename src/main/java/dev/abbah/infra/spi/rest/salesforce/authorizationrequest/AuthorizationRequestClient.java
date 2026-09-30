package dev.abbah.infra.spi.rest.salesforce.authorizationrequest;

import io.quarkus.oidc.client.filter.OidcClientFilter;
import io.smallrye.faulttolerance.api.RetryWhen;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.function.Predicate;

@RegisterRestClient(configKey = "salesforce")
@OidcClientFilter
@Path("/services/apexrest/authorization-requests/v1")
public interface AuthorizationRequestClient {

    @POST
    @Retry(maxRetries = 2, delay = 500, jitter = 250)
    @RetryWhen(exception = TransientFailure.class)
    @Timeout(7000)
    AuthorizationRequestPayload.Response submit(AuthorizationRequestPayload.Request request);

    /**
     * Tells which failures of {@link #submit} are worth another attempt.
     */
    class TransientFailure implements Predicate<Throwable> {

        @Override
        public boolean test(Throwable failure) {
            return failure instanceof WebApplicationException e && isTransient(e.getResponse().getStatus())
                    || failure instanceof ProcessingException
                    || failure instanceof TimeoutException;
        }

        // A 401 is retried because the OIDC filter fetches a fresh token after it (refresh-on-unauthorized).
        private static boolean isTransient(int status) {
            return status == 401 || status >= 500;
        }
    }
}
