package dev.abbah.domain.authorizationrequest;

public interface AuthorizationRequestPort {

    String submit(AuthorizationRequest request, AuthorizationRequest.Message message);
}
