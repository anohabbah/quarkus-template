package dev.abbah.infra.spi.rest.salesforce.authorizationrequest;

import dev.abbah.domain.authorizationrequest.AuthorizationRequest;
import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Message;
import dev.abbah.domain.authorizationrequest.AuthorizationRequestPort;
import dev.abbah.domain.authorizationrequest.SubmissionFailedException;
import dev.abbah.domain.authorizationrequest.UnknownRequesterException;
import io.quarkus.oidc.client.OidcClientException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class AuthorizationRequestAdapter implements AuthorizationRequestPort {

    private static final String REQUESTER_NOT_FOUND = "REQUESTER_NOT_FOUND";

    private final AuthorizationRequestClient client;
    private final AuthorizationRequestPayloadMapper mapper;

    public AuthorizationRequestAdapter(@RestClient AuthorizationRequestClient client,
                                       AuthorizationRequestPayloadMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public String submit(AuthorizationRequest request, Message message) {
        AuthorizationRequestPayload.Response response;
        try {
            response = client.submit(mapper.toRequest(request, message));
        } catch (WebApplicationException e) {
            if (isUnknownRequester(e.getResponse())) {
                throw new UnknownRequesterException(request.requestedBy(), e);
            }
            throw new SubmissionFailedException(e);
        } catch (ProcessingException | OidcClientException e) {
            throw new SubmissionFailedException(e);
        }
        if (response == null || response.caseNumber() == null || response.caseNumber().isBlank()) {
            throw new SubmissionFailedException(new IllegalStateException("Salesforce response has no case number"));
        }
        return response.caseNumber();
    }

    private static boolean isUnknownRequester(Response response) {
        if (response.getStatus() != 422) {
            return false;
        }
        try {
            return REQUESTER_NOT_FOUND.equals(response.readEntity(AuthorizationRequestPayload.Error.class).errorCode());
        } catch (ProcessingException e) {
            return false;
        }
    }
}
