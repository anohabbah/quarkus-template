package dev.abbah.infra.spi.rest.salesforce.authorizationrequest;

import io.quarkus.oidc.client.filter.OidcClientFilter;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "salesforce")
@OidcClientFilter
@Path("/services/apexrest/authorization-requests/v1")
public interface AuthorizationRequestClient {

    @POST
    AuthorizationRequestPayload.Response submit(AuthorizationRequestPayload.Request request);
}
