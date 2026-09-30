package dev.abbah.domain.authorizationrequest;

import java.util.UUID;

public interface AuthorizationRequestPort {

    String submit(UUID requestId, AuthorizationRequest request, AuthorizationRequest.Message message);
}
