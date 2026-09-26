package dev.abbah.domain.authorizationrequest;

public interface AuthorizationRequestRendererPort {

    AuthorizationRequest.Message render(AuthorizationRequest request);
}
