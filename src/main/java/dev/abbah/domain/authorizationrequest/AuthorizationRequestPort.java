package dev.abbah.domain.authorizationrequest;

public interface AuthorizationRequestPort {

    void save(AuthorizationRequest.Submitted submitted);
}
