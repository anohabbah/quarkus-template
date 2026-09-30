package dev.abbah.infra.spi.rest.salesforce.authorizationrequest;

import dev.abbah.domain.authorizationrequest.AuthorizationRequest;
import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Message;
import dev.abbah.domain.authorizationrequest.AuthorizationRequestPort;
import dev.abbah.domain.authorizationrequest.RequestIdReusedException;
import dev.abbah.domain.authorizationrequest.SubmissionFailedException;
import dev.abbah.domain.authorizationrequest.UnknownRequesterException;
import io.quarkus.oidc.client.OidcClientException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.UUID;

@ApplicationScoped
public class AuthorizationRequestAdapter implements AuthorizationRequestPort {

    private static final String REQUESTER_NOT_FOUND = "REQUESTER_NOT_FOUND";
    private static final String REQUEST_ID_REUSED = "REQUEST_ID_REUSED";

    private final AuthorizationRequestClient client;
    private final AuthorizationRequestPayloadMapper mapper;

    public AuthorizationRequestAdapter(@RestClient AuthorizationRequestClient client,
                                       AuthorizationRequestPayloadMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public String submit(UUID requestId, AuthorizationRequest request, Message message) {
        AuthorizationRequestPayload.Response response;
        try {
            response = client.submit(mapper.toRequest(requestId, request, message));
        } catch (WebApplicationException e) {
            if (hasError(e.getResponse(), 422, REQUESTER_NOT_FOUND)) {
                throw new UnknownRequesterException(request.requestedBy(), e);
            }
            if (hasError(e.getResponse(), 409, REQUEST_ID_REUSED)) {
                throw new RequestIdReusedException(requestId, e);
            }
            throw new SubmissionFailedException(e);
        } catch (ProcessingException | OidcClientException | TimeoutException e) {
            throw new SubmissionFailedException(e);
        }
        if (response == null || response.caseNumber() == null || response.caseNumber().isBlank()) {
            throw new SubmissionFailedException(new IllegalStateException("Salesforce response has no case number"));
        }
        return response.caseNumber();
    }

    private static boolean hasError(Response response, int status, String errorCode) {
        if (response.getStatus() != status) {
            return false;
        }
        try {
            return errorCode.equals(response.readEntity(AuthorizationRequestPayload.Error.class).errorCode());
        } catch (ProcessingException e) {
            return false;
        }
    }
}
